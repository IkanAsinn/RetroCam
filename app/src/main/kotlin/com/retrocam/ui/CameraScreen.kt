package com.retrocam.ui

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.retrocam.CameraViewModel
import com.retrocam.CrashLogger
import com.retrocam.UiEvent
import com.retrocam.R
import com.retrocam.camera.Facing
import com.retrocam.data.PhotoLibrary
import com.retrocam.gallery.GalleryScreen
import com.retrocam.gallery.GalleryViewModel
import com.retrocam.filmengine.DateStampFormat
import com.retrocam.filmengine.DateStampRenderer
import com.retrocam.logic.FrameSelection
import com.retrocam.logic.UiMath
import com.retrocam.filmengine.FrameFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    galleryViewModel: GalleryViewModel,
    photoLibrary: PhotoLibrary,
    crashLogger: CrashLogger,
    hasLocationPermission: () -> Boolean,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val owner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val uiRotation by viewModel.uiRotationDegrees.collectAsStateWithLifecycle()
    val rotationState = rememberUiRotation(uiRotation)
    val rotation: () -> Float = { rotationState.value }

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val flashAlpha = remember { Animatable(0f) }
    var showSettings by remember { mutableStateOf(false) }
    var showGallery by rememberSaveable { mutableStateOf(false) }

    // Strength row: an overlay on the bottom edge of the viewfinder area (so the viewfinder never resizes).
    // Hides on camera change and after 4 s without interaction; [strengthTouch] restarts that timer.
    var strengthOpen by remember { mutableStateOf(false) }
    var strengthTouch by remember { mutableIntStateOf(0) }

    // Camera follows the lifecycle: (re)bind every time the screen starts; CameraX releases it on stop.
    // While the gallery is open the camera is released; closing it re-runs this effect and binds again.
    LaunchedEffect(owner, showGallery) {
        if (showGallery) {
            viewModel.releaseCamera()
        } else {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.bindCamera(owner) }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenStopped() }

    // Screen stays on while the viewfinder is up.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                UiEvent.ScreenFlash -> scope.launch {
                    flashAlpha.snapTo(0.85f)
                    flashAlpha.animateTo(0f, tween(260))
                }
                is UiEvent.Message -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(resources.getString(event.res))
                }
            }
        }
    }

    // Location: ask for the permission at the moment the user turns EXIF location on.
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) viewModel.setExifLocation(true) else viewModel.message(R.string.msg_location_denied)
    }

    // Camera-type name flashes briefly when the selection changes (not at startup).
    val selected = state.selected
    LaunchedEffect(selected?.id) { strengthOpen = false }
    LaunchedEffect(strengthOpen, strengthTouch) {
        if (strengthOpen) {
            delay(STRENGTH_AUTO_HIDE_MS)
            strengthOpen = false
        }
    }
    val strengthPercent = state.settings.strengthFor(selected?.id)
    var seenName by remember { mutableStateOf<String?>(null) }
    var nameVisible by remember { mutableStateOf(false) }
    LaunchedEffect(selected?.name) {
        val name = selected?.name ?: return@LaunchedEffect
        val previous = seenName
        seenName = name
        if (previous != null && previous != name) {
            nameVisible = true
            delay(1000)
            nameVisible = false
        }
    }

    val frame = selected?.frame ?: FrameFormat.FULL
    val frameOption = state.settings.frameOption
    val squareMask = FrameSelection.isSquarePreview(frame, frameOption)
    val stamp by produceDateStamp(
        enabled = state.settings.dateStamp,
        format = state.settings.dateFormat,
        colorHex = selected?.dateStampColor,
        square = squareMask,
    )

    val camera = state.camera
    val selectedStop = remember(camera?.zoomStops, camera?.zoomRatio) {
        camera?.let { UiMath.selectedStop(it.zoomStops, it.zoomRatio) } ?: -1
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (showGallery) {
            GalleryScreen(galleryViewModel, photoLibrary, onClose = { showGallery = false })
        } else Column(Modifier.fillMaxSize()) {
            TopBar(
                state = state,
                rotation = rotation,
                actions = TopBarActions(
                    onFlash = viewModel::cycleFlash,
                    onGrain = viewModel::cycleGrain,
                    onFlare = viewModel::cycleFlare,
                    onLeaks = viewModel::cycleLeaks,
                    onDate = viewModel::toggleDateStamp,
                    onTimer = viewModel::cycleTimer,
                    onStrength = { strengthOpen = !strengthOpen; strengthTouch++ },
                    onSettings = { showSettings = true },
                ),
                strengthOpen = strengthOpen,
                modifier = Modifier.statusBarsPadding().padding(top = 4.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Viewfinder(
                    surfaceRequest = surfaceRequest,
                    squareMask = squareMask,
                    halfFrameGuide = frame == FrameFormat.HALF_FRAME,
                    dateStamp = stamp,
                    cameraName = selected?.name,
                    nameVisible = nameVisible,
                    countdown = state.countdown,
                    rotation = rotation,
                    actions = ViewfinderActions(
                        onTapToFocus = viewModel::focusAt,
                        onPinch = viewModel::onPinch,
                        onPinchEnd = viewModel::onPinchEnd,
                        onSwipe = viewModel::selectRelative,
                        onCancelTimer = viewModel::cancelTimer,
                    ),
                )
                if (strengthOpen && state.ready) {
                    StrengthRow(
                        percent = strengthPercent,
                        onChange = viewModel::setStrength,
                        onInteraction = { strengthTouch++ },
                        rotation = rotation,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            ZoomChips(
                stops = camera?.zoomStops.orEmpty(),
                selectedIndex = selectedStop,
                rotation = rotation,
                onSelect = { viewModel.setZoom(it.ratio) },
            )
            if (state.ready) {
                CameraStrip(
                    profiles = state.profiles,
                    selectedIndex = state.selectedIndex,
                    onSelect = { viewModel.selectCamera(state.profiles[it].id) },
                    rotation = rotation,
                    selectedStrengthPercent = strengthPercent,
                    onSelectedTap = { strengthOpen = !strengthOpen; strengthTouch++ },
                )
            } else {
                Box(Modifier.fillMaxWidth().padding(top = 100.dp))
            }
            BottomBar(
                thumbnail = state.thumbnail,
                saving = state.saving > 0,
                counting = state.countdown != null,
                canSwitch = camera != null && (if (camera.facing == Facing.BACK) camera.hasFront else camera.hasBack),
                rotation = rotation,
                onThumbnail = { showGallery = true },
                onShutter = viewModel::onShutter,
                onSwitch = viewModel::switchFacing,
                modifier = Modifier.navigationBarsPadding(),
            )
        }

        // Brief white flash when the flash fires.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = flashAlpha.value }
                .background(Color.White),
        )

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 200.dp),
        )
    }

    if (showSettings) {
        SettingsSheet(
            settings = state.settings,
            versionName = remember { appVersionName(context) },
            actions = SettingsActions(
                onShutterSound = viewModel::setShutterSound,
                onDateFormat = viewModel::setDateFormat,
                onFrameOption = viewModel::setFrameOption,
                onExifLocation = { on ->
                    when {
                        !on -> viewModel.setExifLocation(false)
                        hasLocationPermission() -> viewModel.setExifLocation(true)
                        else -> locationLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    }
                },
                onZoomUnlocked = viewModel::setZoomUnlocked,
                onSendCrashLog = { shareCrashLog(context, crashLogger)?.let(viewModel::message) },
                onDismiss = { showSettings = false },
            ),
        )
    }
}

/** Date-stamp preview drawn by the real [DateStampRenderer] on a transparent bitmap (same look as the saved photo). */
@Composable
private fun produceDateStamp(enabled: Boolean, format: DateStampFormat, colorHex: String?, square: Boolean) =
    produceState<ImageBitmap?>(null, enabled, format, colorHex, square) {
        value = if (!enabled) null else withContext(Dispatchers.Default) {
            try {
                val bmp = Bitmap.createBitmap(600, if (square) 600 else 800, Bitmap.Config.ARGB_8888)
                val text = DateStampRenderer.text(LocalDate.now(), format)
                DateStampRenderer.draw(bmp, text, UiMath.hexToRgb(colorHex ?: ""))
                bmp.asImageBitmap()
            } catch (_: Throwable) {
                null
            }
        }
    }

private fun appVersionName(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
} catch (_: Throwable) {
    ""
}

private const val STRENGTH_AUTO_HIDE_MS = 4_000L
