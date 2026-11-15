package com.retrocam.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.FlashState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.FocusMeteringResult
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX wrapper: Preview + ImageCapture (+ optional CameraEffect) bound to a LifecycleOwner.
 *
 * Threading: call everything from the main thread. [bind] and [capture] are suspend functions;
 * everything else is fire-and-forget. Heavy work (JPEG decode, rotation) runs on a private
 * background executor.
 *
 * Battery: bindToLifecycle makes CameraX close the camera when the owner stops, so nothing here
 * keeps the device open. The controller holds the owner weakly and drops it in [unbind]. Call
 * [bind] again from onStart (or when the owner becomes STARTED) to resume; [unbind] is optional
 * but frees the preview surface request. [close] ends the controller for good.
 */
class RetroCameraController(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val captureExecutorLazy: Lazy<ExecutorService> =
        lazy { Executors.newSingleThreadExecutor { Thread(it, "retrocam-capture") } }
    private val captureExecutor: ExecutorService get() = captureExecutorLazy.value

    private val _state = MutableStateFlow<CameraUiState?>(null)
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)

    val state: StateFlow<CameraUiState?> = _state

    /** For CameraXViewfinder. */
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    // Configuration (survives rebinds).
    private var provider: ProcessCameraProvider? = null
    private var ownerRef: WeakReference<LifecycleOwner>? = null
    private var effect: CameraEffect? = null
    private var facing = Facing.BACK
    private var flashMode = FlashMode.OFF
    private var targetRotation = Surface.ROTATION_0
    private var unlocked = false
    private var hasFront = false
    private var hasBack = false
    private var desiredZoom = 1f
    private var desiredEv = 0f // user's exposure compensation in EV; re-applied (clamped) after every bind
    private var aeLocked = false
    private var exposureInfo = ExposureInfo.UNSUPPORTED
    private var exposureIndex = 0

    // Bound state.
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var screenFlash: ImageCapture.ScreenFlash? = null
    private var zoomObserver: Observer<ZoomState>? = null
    private var zoomLive: androidx.lifecycle.LiveData<ZoomState>? = null
    private var activeLens = LensSpec(1f)
    private var backLenses: List<LensSpec> = listOf(LensSpec(1f))
    private var backDetected = false
    private var mainMin = 1f
    private var mainMax = 1f
    private val lensMax = HashMap<LensSpec, Float>()
    private var lastCameraZoom = 1f // raw CameraX zoom ratio of the bound camera

    private val lenses: List<LensSpec> get() = if (facing == Facing.BACK) backLenses else listOf(LensSpec(1f))
    private val opticalRatios: List<Float> get() = lenses.map { it.ratio }.filter { it != 1f }

    // ---- public API -------------------------------------------------------------------------

    /**
     * Awaits the ProcessCameraProvider, then binds Preview + ImageCapture (and [effect] if
     * non-null, targeting the preview) to [owner]. Safe to call again (e.g. from every onStart).
     */
    suspend fun bind(owner: LifecycleOwner, effect: CameraEffect?) {
        val p = provider ?: ProcessCameraProvider.getInstance(appContext).awaitOn(mainExecutor).also { provider = it }
        withContext(Dispatchers.Main.immediate) {
            mutex.withLock {
                ownerRef = WeakReference(owner)
                this@RetroCameraController.effect = effect
                hasBack = p.has(CameraSelector.DEFAULT_BACK_CAMERA)
                hasFront = p.has(CameraSelector.DEFAULT_FRONT_CAMERA)
                if (facing == Facing.BACK && !hasBack && hasFront) facing = Facing.FRONT
                if (facing == Facing.FRONT && !hasFront && hasBack) facing = Facing.BACK
                val lens = lensFor(desiredZoom)
                rebindLocked(lens)
                applyZoomLocked()
            }
        }
    }

    fun setFacing(facing: Facing) {
        scope.launch {
            mutex.withLock {
                if (this@RetroCameraController.facing == facing) return@withLock
                if (facing == Facing.FRONT && !hasFront) return@withLock
                if (facing == Facing.BACK && !hasBack) return@withLock
                this@RetroCameraController.facing = facing
                desiredZoom = 1f
                runCatching { rebindLocked(LensSpec(1f)) }.onFailure { Log.e(TAG, "setFacing failed", it) }
            }
        }
    }

    fun setFlashMode(mode: FlashMode) {
        flashMode = mode
        imageCapture?.let { applyFlashMode(it) }
    }

    /**
     * Light source for front-camera flash (FLASH_MODE_SCREEN). CameraX calls apply() only inside the capture
     * sequence and clear() when it ends, so the screen is untouched in preview. Survives rebinds.
     */
    fun setScreenFlash(flash: ImageCapture.ScreenFlash?) {
        screenFlash = flash
        val capture = imageCapture ?: return
        // CameraX rejects FLASH_MODE_SCREEN without a ScreenFlash: drop to OFF before removing it, set it before re-enabling.
        if (flash == null) applyFlashMode(capture)
        capture.screenFlash = flash
        if (flash != null) applyFlashMode(capture)
    }

    /** Sets the CameraX flash mode; screen flash only once a ScreenFlash is attached (otherwise OFF). */
    private fun applyFlashMode(capture: ImageCapture) {
        val mode = flashMode.toCameraX().let { if (it == ImageCapture.FLASH_MODE_SCREEN && screenFlash == null) ImageCapture.FLASH_MODE_OFF else it }
        try {
            capture.flashMode = mode
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "flash mode $mode rejected", e)
            runCatching { capture.flashMode = ImageCapture.FLASH_MODE_OFF }
        }
    }

    /** Clamped to the allowed range. May rebind (short preview blink) when crossing to a lens that zoom cannot reach. */
    fun setZoomRatio(ratio: Float) {
        desiredZoom = ratio.coerceIn(deviceMin(), allowedMax())
        scope.launch { mutex.withLock { applyZoomLocked() } }
    }

    /** Default limit: longest optical lens, or 2x. */
    fun setFullZoomUnlocked(unlocked: Boolean) {
        this.unlocked = unlocked
        if (!unlocked && desiredZoom > allowedMax()) desiredZoom = allowedMax()
        scope.launch {
            mutex.withLock {
                applyZoomLocked()
                publishState()
            }
        }
    }

    /** x, y in viewfinder pixels (0,0 top-left); width/height the viewfinder size in pixels. */
    fun focusAt(x: Float, y: Float, width: Float, height: Float) {
        startFocus(x, y, width, height)
    }

    /**
     * Focuses at the point and, once that focus run has finished (not when a newer tap replaced it), locks AE/AF/AWB
     * there. Used by the long press.
     */
    fun focusAndLockAt(x: Float, y: Float, width: Float, height: Float) {
        val cam = camera ?: return
        val future = startFocus(x, y, width, height)
        if (future == null) {
            setAeLocked(true)
            return
        }
        future.addListener(
            {
                val ok = try {
                    future.get()
                    true
                } catch (_: Exception) {
                    false
                }
                if (ok && camera === cam && !aeLocked) setAeLocked(true)
            },
            mainExecutor,
        )
    }

    private fun startFocus(x: Float, y: Float, width: Float, height: Float): ListenableFuture<FocusMeteringResult>? {
        val cam = camera ?: return null
        if (width <= 0f || height <= 0f) return null
        try {
            val factory = preview?.let { SurfaceOrientedMeteringPointFactory(width, height, it) }
                ?: SurfaceOrientedMeteringPointFactory(width, height)
            // The viewfinder mirrors the front camera; metering works on the unmirrored buffer.
            val px = if (facing == Facing.FRONT) width - x else x
            val point = factory.createPoint(px.coerceIn(0f, width), y.coerceIn(0f, height))
            // Focus and exposure stay on the tapped point until the next tap or rebind (no auto-cancel, so the
            // brightness does not jump back to default metering). While AE is locked only refocus, or the lock breaks.
            val flags = if (aeLocked) FocusMeteringAction.FLAG_AF else FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            val action = FocusMeteringAction.Builder(point, flags)
                .disableAutoCancel()
                .build()
            if (cam.cameraInfo.isFocusMeteringSupported(action)) return cam.cameraControl.startFocusAndMetering(action)
        } catch (t: Exception) {
            Log.w(TAG, "focusAt failed", t)
        }
        return null
    }

    /**
     * Exposure compensation in EV (applies to preview and capture; CameraX puts it in both request types).
     * Remembered across rebinds and clamped to the bound camera's range. Superseded calls are swallowed.
     */
    fun setExposureEv(ev: Float) {
        desiredEv = ev
        if (camera != null) {
            applyExposure()
            publishState()
        }
    }

    /** Same as [setExposureEv] with a raw index of the bound camera, clamped to its range. */
    fun setExposureIndex(index: Int) {
        val info = exposureInfo
        if (!info.supported) return
        setExposureEv(info.evFor(index.coerceIn(info.minIndex, info.maxIndex)))
    }

    /**
     * Locks (or unlocks) auto exposure, auto white balance and autofocus (AE/AF lock) so brightness and focus stop
     * drifting. Focus is held by switching the AF mode to AUTO, which stops continuous AF from re-focusing after the
     * tap-to-focus run (CameraX otherwise keeps CONTINUOUS_PICTURE on the tapped region); unlocking drops that override. Compensation is sent
     * first, then the lock, so the locked value is the one the user chose. Released on every rebind (camera, facing,
     * lens change, resume); the caller keeps its own switch in sync via [state].
     * Behaviour with EV changes while locked is device-dependent: many Camera2 HALs keep applying compensation to
     * a locked AE, others ignore it until unlock.
     */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun setAeLocked(locked: Boolean) {
        val cam = camera ?: return
        aeLocked = locked
        if (locked) applyExposure()
        applyAeLockOptions(cam, locked)
        publishState()
    }

    /** Surface.ROTATION_*; applies to captures only (the preview stays sensor-locked). */
    fun setTargetRotation(surfaceRotation: Int) {
        targetRotation = surfaceRotation
        imageCapture?.targetRotation = surfaceRotation
    }

    /**
     * Takes one picture and returns an upright, unmirrored bitmap. Cancelling the caller discards
     * the result (CameraX cannot abort an in-flight capture) without leaking the frame.
     *
     * flashFired: ImageInfo.getFlashState() == FIRED when CameraX reports it. If the device reports
     * UNKNOWN, falls back to a best guess: true only when flash mode was ON and the camera has a unit.
     */
    suspend fun capture(): CapturedFrame {
        val ic = imageCapture ?: throw IllegalStateException("Camera is not bound")
        val modeAtShutter = flashMode
        val st = _state.value
        val lens = st?.lensKind ?: ActiveLens.MAIN
        val facingAtShutter = st?.facing ?: Facing.BACK
        val hasUnit = st?.hasFlashUnit ?: false
        val takenAt = System.currentTimeMillis()
        val screenLit = modeAtShutter != FlashMode.OFF && facingAtShutter == Facing.FRONT
        try {
        return suspendCancellableCoroutine { cont ->
            ic.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val frame = try {
                            image.use {
                                val fired = when (it.imageInfo.flashState) {
                                    FlashState.FIRED -> true
                                    FlashState.NOT_FIRED, FlashState.UNAVAILABLE -> screenLit // the camera does not report a screen flash
                                    else -> modeAtShutter == FlashMode.ON && hasUnit
                                }
                                CapturedFrame(it.toUprightBitmap(), fired, lens, takenAt, facingAtShutter)
                            }
                        } catch (t: Throwable) {
                            if (cont.isActive) cont.resumeWithException(t)
                            return
                        }
                        if (!cont.isActive) {
                            frame.bitmap.recycle()
                            return
                        }
                        cont.resume(frame) { frame.bitmap.recycle() }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                },
            )
        }
        } finally {
            // CameraX clears the screen flash itself; this guarantees it on failure and cancellation too (clear() is idempotent).
            if (screenLit) mainExecutor.execute { screenFlash?.clear() }
        }
    }

    /** Unbinds all use cases (releases the camera) and clears the preview surface request. */
    fun unbind() {
        detachZoomObserver()
        runCatching { provider?.unbindAll() }
        camera = null
        preview = null
        imageCapture = null
        ownerRef = null
        _surfaceRequest.value = null
    }

    /** Unbinds and releases the controller's threads. The controller cannot be used afterwards. */
    fun close() {
        unbind()
        scope.cancel()
        if (captureExecutorLazy.isInitialized()) captureExecutor.shutdown()
    }

    // ---- binding ----------------------------------------------------------------------------

    private fun rebindLocked(lens: LensSpec) {
        val p = provider ?: return
        val owner = ownerRef?.get() ?: return
        detachZoomObserver()
        mainExecutor.execute { screenFlash?.clear() } // a camera switch mid-capture must not leave the screen bright
        p.unbindAll()

        val newPreview = Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build(),
            )
            .build()
        newPreview.setSurfaceProvider(mainExecutor) { request -> _surfaceRequest.value = request }

        val newCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flashMode.toCameraX().let { if (it == ImageCapture.FLASH_MODE_SCREEN) ImageCapture.FLASH_MODE_OFF else it })
            .setTargetRotation(targetRotation)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    // Aim for ~12 MP (4032x3024 / 4000x3000); 50 MP sensors must not deliver full res.
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(CAPTURE_WIDTH, CAPTURE_HEIGHT), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                    )
                    .build(),
            )
            .build()

        val group = UseCaseGroup.Builder().addUseCase(newPreview).addUseCase(newCapture)
        effect?.let { group.addEffect(it) }

        val selector = if (facing == Facing.BACK) LensDetector.backSelector(lens) else CameraSelector.DEFAULT_FRONT_CAMERA
        val cam = p.bindToLifecycle(owner, selector, group.build())

        camera = cam
        // A fresh camera session starts unlocked; the Camera2 interop options can outlive unbindAll, so clear them.
        if (aeLocked) applyAeLockOptions(cam, false)
        aeLocked = false
        preview = newPreview
        newCapture.screenFlash = screenFlash // before the screen flash mode, which CameraX rejects without it
        applyFlashMode(newCapture)
        imageCapture = newCapture
        activeLens = lens

        val live = cam.cameraInfo.zoomState
        val initial = live.value
        if (!lens.separate) {
            mainMin = initial?.minZoomRatio ?: 1f
            mainMax = initial?.maxZoomRatio ?: 1f
        } else {
            lensMax[lens] = (initial?.maxZoomRatio ?: 1f) * lens.ratio
        }
        if (facing == Facing.BACK && !backDetected && !lens.separate) {
            backDetected = true
            backLenses = LensDetector.detectBack(appContext, cam.cameraInfo, mainMin, mainMax, p.availableCameraInfos)
        }
        val obs = Observer<ZoomState> { zs ->
            lastCameraZoom = zs.zoomRatio
            if (!activeLens.separate) {
                mainMin = zs.minZoomRatio
                mainMax = zs.maxZoomRatio
            } else {
                lensMax[activeLens] = zs.maxZoomRatio * activeLens.ratio
            }
            publishState()
        }
        zoomObserver = obs
        zoomLive = live
        live.observeForever(obs)
        applyExposure()
        publishState()
    }

    /** Reads the bound camera's ExposureState, clamps [desiredEv] into it and sends it to CameraControl. */
    private fun applyExposure() {
        val cam = camera ?: return
        val es = cam.cameraInfo.exposureState
        val step = es.exposureCompensationStep.toFloat()
        val range = es.exposureCompensationRange
        val info = if (es.isExposureCompensationSupported && step > 0f) {
            ExposureInfo(true, range.lower, range.upper, step)
        } else {
            ExposureInfo.UNSUPPORTED
        }
        exposureInfo = info
        if (!info.supported) {
            exposureIndex = 0
            return
        }
        val index = info.indexFor(desiredEv)
        exposureIndex = index
        try {
            cam.cameraControl.setExposureCompensationIndex(index).swallowCancellation("setExposureCompensationIndex")
        } catch (t: Exception) {
            Log.w(TAG, "setExposureCompensationIndex failed", t)
        }
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun supportsAfAuto(cam: Camera): Boolean = try {
        val modes = Camera2CameraInfo.from(cam.cameraInfo).getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        modes?.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) == true
    } catch (_: Exception) {
        false
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun applyAeLockOptions(cam: Camera, locked: Boolean) {
        try {
            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, locked)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, locked)
            if (locked && supportsAfAuto(cam)) {
                options.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            }
            Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(options.build())
                .swallowCancellation("AE lock")
        } catch (t: Exception) {
            Log.w(TAG, "AE lock failed", t)
            aeLocked = false
        }
    }

    /** Superseded / cancelled camera operations are normal (a newer call replaced this one): ignore them quietly. */
    private fun ListenableFuture<*>.swallowCancellation(what: String) {
        addListener(
            {
                try {
                    get()
                } catch (_: CancellationException) {
                } catch (e: ExecutionException) {
                    if (e.cause !is CameraControl.OperationCanceledException) Log.w(TAG, "$what failed", e.cause ?: e)
                } catch (_: Exception) {
                }
            },
            mainExecutor,
        )
    }

    private fun detachZoomObserver() {
        val live = zoomLive
        val obs = zoomObserver
        if (live != null && obs != null) live.removeObserver(obs)
        zoomLive = null
        zoomObserver = null
    }

    /** Switch lens if the desired zoom needs another one, then set the camera's zoom. */
    private fun applyZoomLocked() {
        if (camera == null) return
        val target = lensFor(desiredZoom)
        if (target != activeLens) {
            try {
                rebindLocked(target)
            } catch (t: Throwable) {
                Log.e(TAG, "lens switch failed, back to main lens", t)
                backLenses = backLenses.filter { it != target }
                desiredZoom = desiredZoom.coerceIn(deviceMin(), allowedMax())
                runCatching { rebindLocked(LensSpec(1f)) }
            }
        }
        val cam = camera ?: return
        val scale = if (activeLens.separate) activeLens.ratio else 1f
        val zs = cam.cameraInfo.zoomState.value
        var z = desiredZoom / scale
        if (zs != null) z = z.coerceIn(zs.minZoomRatio, zs.maxZoomRatio)
        cam.cameraControl.setZoomRatio(z)
    }

    /** Which lens should be active for a UI zoom [ratio]. */
    private fun lensFor(ratio: Float): LensSpec {
        if (facing == Facing.FRONT) return LensSpec(1f)
        val separate = backLenses.filter { it.separate }
        if (separate.isEmpty()) return LensSpec(1f)
        if (ratio < 1f) {
            if (ratio >= mainMin - 0.01f && mainMin < 0.99f) return LensSpec(1f)
            val wides = separate.filter { it.ratio < 0.95f }.sortedByDescending { it.ratio }
            return wides.firstOrNull { it.ratio <= ratio + 0.05f } ?: wides.lastOrNull() ?: LensSpec(1f)
        }
        val tele = separate.filter { it.ratio > 1.05f && it.ratio <= ratio + 0.01f }.maxByOrNull { it.ratio }
        return tele ?: LensSpec(1f)
    }

    // ---- state ------------------------------------------------------------------------------

    private fun deviceMin(): Float {
        val wide = lenses.filter { it.separate && it.ratio < 1f }.minOfOrNull { it.ratio }
        return if (wide != null) minOf(mainMin, wide) else mainMin
    }

    private fun deviceMax(): Float {
        val tele = lenses.filter { it.separate }.maxOfOrNull { lensMax[it] ?: it.ratio }
        return if (tele != null) maxOf(mainMax, tele) else mainMax
    }

    private fun allowedMax(): Float = ZoomStops.maxAllowed(deviceMin(), deviceMax(), opticalRatios, unlocked)

    private fun publishState() {
        val cam = camera ?: return
        val scale = if (activeLens.separate) activeLens.ratio else 1f
        val min = deviceMin()
        val max = allowedMax()
        val ratio = (lastCameraZoom * scale).coerceIn(min, maxOf(min, deviceMax()))
        val stops = if (facing == Facing.FRONT && deviceMax() <= 1.01f) {
            emptyList()
        } else {
            ZoomStops.compute(min, deviceMax(), opticalRatios, unlocked)
        }
        val lensKind = if (facing == Facing.FRONT) ActiveLens.FRONT else ZoomStops.lensKind(ratio, opticalRatios)
        _state.value = CameraUiState(
            facing = facing,
            hasFront = hasFront,
            hasBack = hasBack,
            hasFlashUnit = cam.cameraInfo.hasFlashUnit(),
            zoomRatio = ratio,
            minZoom = min,
            maxZoom = max,
            zoomStops = stops,
            lensKind = lensKind,
            exposure = exposureInfo,
            exposureIndex = exposureIndex,
            aeLocked = aeLocked,
        )
    }

    // ---- helpers ----------------------------------------------------------------------------

    private fun ProcessCameraProvider.has(selector: CameraSelector): Boolean = try {
        hasCamera(selector)
    } catch (_: CameraInfoUnavailableException) {
        false
    }

    private fun FlashMode.toCameraX(): Int = when {
        this == FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        facing == Facing.FRONT -> ImageCapture.FLASH_MODE_SCREEN // front has no unit: AUTO/ON both light the screen
        this == FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        else -> ImageCapture.FLASH_MODE_ON
    }

    companion object {
        private const val TAG = "RetroCamera"
        private const val CAPTURE_WIDTH = 4032
        private const val CAPTURE_HEIGHT = 3024

        internal fun uprightBitmap(source: Bitmap, crop: Rect?, rotationDegrees: Int): Bitmap {
            var bmp = source
            if (crop != null && !crop.isEmpty &&
                (crop.left != 0 || crop.top != 0 || crop.width() != bmp.width || crop.height() != bmp.height) &&
                crop.right <= bmp.width && crop.bottom <= bmp.height
            ) {
                val cropped = Bitmap.createBitmap(bmp, crop.left, crop.top, crop.width(), crop.height())
                if (cropped !== bmp) bmp.recycle()
                bmp = cropped
            }
            val rot = ((rotationDegrees % 360) + 360) % 360
            if (rot != 0) {
                val m = Matrix().apply { postRotate(rot.toFloat()) }
                val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (rotated !== bmp) bmp.recycle()
                bmp = rotated
            }
            return bmp
        }
    }
}

private fun ImageProxy.toUprightBitmap(): Bitmap =
    RetroCameraController.uprightBitmap(toBitmap(), cropRect, imageInfo.rotationDegrees)

private suspend fun <T> ListenableFuture<T>.awaitOn(executor: Executor): T = suspendCancellableCoroutine { c ->
    addListener(
        {
            try {
                c.resume(get())
            } catch (e: ExecutionException) {
                c.resumeWithException(e.cause ?: e)
            } catch (e: CancellationException) {
                c.cancel(e)
            }
        },
        executor,
    )
}
