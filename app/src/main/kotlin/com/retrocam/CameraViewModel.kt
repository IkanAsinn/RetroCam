package com.retrocam

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.retrocam.camera.CameraUiState
import com.retrocam.camera.Facing
import com.retrocam.camera.FlashMode
import com.retrocam.capture.CapturePipeline
import com.retrocam.capture.ShotRequest
import com.retrocam.data.Settings
import com.retrocam.data.TimerSetting
import com.retrocam.data.cycleNext
import com.retrocam.filmengine.DateStampFormat
import com.retrocam.filmengine.FilmCameraEffect
import com.retrocam.filmengine.Highlight
import com.retrocam.logic.Countdown
import com.retrocam.logic.LensMapping
import com.retrocam.logic.ShutterGate
import com.retrocam.profiles.CameraProfile
import com.retrocam.profiles.EffectToggles
import com.retrocam.profiles.LensKind
import com.retrocam.profiles.ParamsMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One immutable snapshot for the whole screen. Derived in the ViewModel so composables stay dumb. */
data class CameraScreenState(
    val ready: Boolean = false,
    val profiles: List<CameraProfile> = emptyList(),
    val selectedIndex: Int = 0,
    val settings: Settings = Settings(),
    val camera: CameraUiState? = null,
    val countdown: Int? = null,
    val saving: Int = 0,
    val thumbnail: Uri? = null,
) {
    val selected: CameraProfile? get() = profiles.getOrNull(selectedIndex)
}

/** One-shot things the UI shows and forgets. */
sealed interface UiEvent {
    data object ScreenFlash : UiEvent
    data class Message(@StringRes val res: Int) : UiEvent
}

private data class Transient(val countdown: Int? = null, val saving: Int = 0, val thumbnail: Uri? = null)

private data class PreviewInputs(
    val profile: CameraProfile,
    val toggles: EffectToggles,
    val lens: LensKind,
    val flashActive: Boolean,
    val highlight: Highlight?,
)

class CameraViewModel(private val container: AppContainer) : ViewModel() {
    private val controller = container.controller
    private val settingsRepo = container.settingsRepository
    private val pipeline: CapturePipeline get() = container.capturePipeline

    private val _settings = MutableStateFlow(Settings())
    private val _loaded = MutableStateFlow(false)
    private val _profiles = MutableStateFlow<List<CameraProfile>>(emptyList())
    private val _transient = MutableStateFlow(Transient())
    private val bound = MutableStateFlow(false)
    private val events = Channel<UiEvent>(Channel.BUFFERED)

    private val gate = ShutterGate()
    private var countdownJob: Job? = null
    private var pinchBase: Float? = null

    /** The single preview effect; released in [onCleared]. */
    private val effect = FilmCameraEffect { t ->
        Log.e(TAG, "preview effect error", t)
        message(R.string.msg_preview_error)
    }
    private val highlight: StateFlow<Highlight?> = effect.highlight

    val uiEvents: Flow<UiEvent> = events.receiveAsFlow()
    val surfaceRequest = controller.surfaceRequest
    val uiRotationDegrees: StateFlow<Float> = container.orientationWatcher.uiRotationDegrees

    val state: StateFlow<CameraScreenState> = combine(
        _settings, _profiles, controller.state, _transient, _loaded,
    ) { settings, profiles, camera, t, loaded ->
        val index = profiles.indexOfFirst { it.id == settings.cameraId }.coerceAtLeast(0)
        CameraScreenState(loaded && profiles.isNotEmpty(), profiles, index, settings, camera, t.countdown, t.saving, t.thumbnail)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CameraScreenState())

    init {
        loadProfilesAndSettings()
        observeOrientation()
        observeCameraSettings()
        observePreviewParams()
        viewModelScope.launch {
            val last = safely { container.photoSaver.lastSaved() }
            if (last != null) _transient.update { it.copy(thumbnail = last) }
        }
    }

    // ---------------------------------------------------------------- startup

    private fun loadProfilesAndSettings() = viewModelScope.launch {
        val profiles = withContext(Dispatchers.Default) { safely { container.profileRepository.all() } ?: emptyList() }
        val stored = safely { settingsRepo.settings.first() } ?: Settings()
        val id = stored.cameraId?.takeIf { wanted -> profiles.any { it.id == wanted } } ?: profiles.firstOrNull()?.id
        _profiles.value = profiles
        _settings.value = stored.copy(cameraId = id)
        _loaded.value = true
        // Persist every later change; conflated so a burst of taps writes once.
        launch {
            _settings.drop(1).collectLatest { s -> safely { settingsRepo.save(s) } }
        }
    }

    /** Called once the camera permission is granted; CameraX ties the camera to [owner]'s lifecycle (released in onStop). */
    suspend fun bindCamera(owner: LifecycleOwner) {
        try {
            controller.bind(owner, effect)
            bound.value = true
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "bind failed", t)
            message(R.string.msg_camera_error)
        }
    }

    private fun observeOrientation() = viewModelScope.launch {
        container.orientationWatcher.surfaceRotation.collect { r -> safely { controller.setTargetRotation(r) } }
    }

    /** Push flash / facing / zoom-unlock settings to the controller whenever they change (and once bound). */
    private fun observeCameraSettings() {
        viewModelScope.launch {
            combine(bound, _settings.map { it.flash }.distinctUntilChanged(), hasFlashUnit()) { b, mode, unit ->
                if (b) (if (unit) mode else FlashMode.OFF) else null
            }.collect { mode -> if (mode != null) safely { controller.setFlashMode(mode) } }
        }
        viewModelScope.launch {
            combine(bound, _settings.map { it.facing }.distinctUntilChanged()) { b, f -> if (b) f else null }
                .collect { f ->
                    if (f != null && controller.state.value?.facing != f) safely { controller.setFacing(f) }
                }
        }
        viewModelScope.launch {
            combine(bound, _settings.map { it.zoomUnlocked }.distinctUntilChanged()) { b, u -> if (b) u else null }
                .collect { u -> if (u != null) safely { controller.setFullZoomUnlocked(u) } }
        }
    }

    private fun hasFlashUnit(): Flow<Boolean> = controller.state.map { it?.hasFlashUnit ?: false }.distinctUntilChanged()

    /** Selected profile + toggles + lens + flash + highlight -> shader params -> preview effect. */
    private fun observePreviewParams() {
        val inputs = combine(
            combine(_profiles, _settings.map { it.cameraId }.distinctUntilChanged()) { list, id ->
                list.firstOrNull { it.id == id } ?: list.firstOrNull()
            },
            _settings.map { it.toggles }.distinctUntilChanged(),
            controller.state.map { it?.lensKind }.distinctUntilChanged(),
            combine(_settings.map { it.flash == FlashMode.ON }, hasFlashUnit()) { on, unit -> on && unit }.distinctUntilChanged(),
            highlight,
        ) { profile, toggles, lens, flash, bright ->
            profile?.let {
                PreviewInputs(it, toggles, lens?.let(LensMapping::toLensKind) ?: LensKind.MAIN, flash, bright)
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            inputs.collectLatest { input ->
                if (input == null) return@collectLatest
                try {
                    val lut = container.profileRepository.lut(input.profile.id)
                    val params = ParamsMapper.toFilmParams(
                        profile = input.profile,
                        toggles = input.toggles,
                        lens = input.lens,
                        flashActive = input.flashActive,
                        seed = PREVIEW_SEED,
                        brightHighlight = input.highlight != null,
                        highlightX = input.highlight?.x,
                        highlightY = input.highlight?.y,
                    )
                    effect.update(params, lut)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.e(TAG, "preview params failed", t)
                }
            }
        }
    }

    // ---------------------------------------------------------------- settings

    private fun update(transform: (Settings) -> Settings) {
        if (_loaded.value) _settings.update(transform)
    }

    fun selectCamera(id: String) = update { if (it.cameraId == id) it else it.copy(cameraId = id) }

    /** Swipe: +1 = next camera type, -1 = previous. Stops at the ends. */
    fun selectRelative(delta: Int) {
        val list = _profiles.value
        val current = list.indexOfFirst { it.id == _settings.value.cameraId }.coerceAtLeast(0)
        list.getOrNull(current + delta)?.let { selectCamera(it.id) }
    }

    fun cycleFlash() {
        if (controller.state.value?.hasFlashUnit == true) update { it.copy(flash = it.flash.cycleNext()) }
    }
    fun cycleGrain() = update { it.copy(grain = it.grain.cycleNext()) }
    fun cycleFlare() = update { it.copy(flare = it.flare.cycleNext()) }
    fun cycleLeaks() = update { it.copy(leaks = it.leaks.cycleNext()) }
    fun toggleDateStamp() = update { it.copy(dateStamp = !it.dateStamp) }
    fun cycleTimer() = update { it.copy(timer = it.timer.cycleNext()) }
    fun setShutterSound(on: Boolean) = update { it.copy(shutterSound = on) }
    fun setDateFormat(format: DateStampFormat) = update { it.copy(dateFormat = format) }
    fun setFrameOption(on: Boolean) = update { it.copy(frameOption = on) }
    fun setExifLocation(on: Boolean) = update { it.copy(exifLocation = on) }
    fun setZoomUnlocked(on: Boolean) = update { it.copy(zoomUnlocked = on) }

    fun switchFacing() {
        val cam = controller.state.value ?: return
        val target = if (cam.facing == Facing.BACK) Facing.FRONT else Facing.BACK
        val available = if (target == Facing.FRONT) cam.hasFront else cam.hasBack
        if (available) update { it.copy(facing = target) }
    }

    // ---------------------------------------------------------------- viewfinder gestures

    fun focusAt(x: Float, y: Float, width: Float, height: Float) {
        safely { controller.focusAt(x, y, width, height) }
    }

    fun onPinch(scale: Float) {
        val cam = controller.state.value ?: return
        val base = pinchBase ?: cam.zoomRatio
        val target = (base * scale).coerceIn(cam.minZoom, cam.maxZoom)
        pinchBase = target
        safely { controller.setZoomRatio(target) }
    }

    fun onPinchEnd() {
        pinchBase = null
    }

    fun setZoom(ratio: Float) {
        pinchBase = null
        safely { controller.setZoomRatio(ratio) }
    }

    // ---------------------------------------------------------------- shutter

    /** Shutter button or volume key. During a countdown a press cancels it. */
    fun onShutter() {
        if (!bound.value || !_loaded.value) return
        if (countdownJob?.isActive == true) {
            cancelTimer()
            return
        }
        val seconds = _settings.value.timer.seconds
        if (_settings.value.timer != TimerSetting.OFF && seconds > 0) {
            countdownJob = viewModelScope.launch {
                try {
                    Countdown.run(seconds) { remaining -> _transient.update { it.copy(countdown = remaining) } }
                } finally {
                    _transient.update { it.copy(countdown = null) }
                }
                takePicture()
            }
        } else {
            takePicture()
        }
    }

    fun cancelTimer() {
        countdownJob?.cancel()
        countdownJob = null
    }

    fun onScreenStopped() = cancelTimer()

    private fun takePicture() {
        when (gate.tryBeginCapture(SystemClock.elapsedRealtime())) {
            ShutterGate.Decision.ACCEPT -> Unit
            ShutterGate.Decision.QUEUE_FULL -> {
                message(R.string.msg_still_saving)
                return
            }
            ShutterGate.Decision.BUSY_CAPTURING, ShutterGate.Decision.TOO_SOON -> return
        }
        val settings = _settings.value
        val camera = controller.state.value
        val profile = _profiles.value.firstOrNull { it.id == settings.cameraId } ?: _profiles.value.firstOrNull()
        if (camera == null || profile == null) {
            gate.captureFailed()
            return
        }
        val flashOn = settings.flash == FlashMode.ON && camera.hasFlashUnit
        val bright = highlight.value
        container.shutterFeedback.click(settings.shutterSound)
        if (flashOn) events.trySend(UiEvent.ScreenFlash)

        viewModelScope.launch {
            val frame = try {
                controller.capture()
            } catch (e: CancellationException) {
                gate.captureFailed()
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "capture failed", t)
                gate.captureFailed()
                message(R.string.msg_capture_failed)
                return@launch
            }
            gate.captureFinished()
            publishSaving()
            if (!flashOn && frame.flashFired) events.trySend(UiEvent.ScreenFlash) // Auto flash decided to fire

            // Processing outlives this screen: a photo already taken must still be saved.
            container.appScope.launch {
                try {
                    val location = if (settings.exifLocation) withContext(Dispatchers.IO) { container.locationProvider.lastKnown() } else null
                    val uri = pipeline.process(ShotRequest(frame, profile, settings, bright, location))
                    _transient.update { it.copy(thumbnail = uri) }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.e(TAG, "save failed", t)
                    message(R.string.msg_save_failed)
                } finally {
                    withContext(NonCancellable + Dispatchers.Main) {
                        gate.processingFinished()
                        publishSaving()
                    }
                }
            }
        }
    }

    private fun publishSaving() = _transient.update { it.copy(saving = gate.processing) }

    // ---------------------------------------------------------------- misc

    fun message(@StringRes res: Int) {
        events.trySend(UiEvent.Message(res))
    }

    override fun onCleared() {
        cancelTimer()
        safely { controller.unbind() }
        safely { controller.close() }
        safely { effect.release() }
        container.renderer.requestRelease() // deferred while a photo is still being saved
        container.shutterFeedback.release()
    }

    private inline fun <T> safely(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Log.w(TAG, "non-fatal failure", t)
        null
    }

    companion object {
        private const val TAG = "CameraViewModel"
        private const val PREVIEW_SEED = 0x5EED

        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CameraViewModel(container) as T
        }
    }
}
