package com.retrocam.ui

import androidx.camera.core.SurfaceRequest
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.retrocam.camera.ExposureInfo
import com.retrocam.logic.DragKind
import com.retrocam.logic.FocusRingLogic
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrocam.R
import com.retrocam.ui.theme.RetroColors
import kotlin.math.abs

private const val BOX_ASPECT = 3f / 4f

/** A viewfinder drag of this length (user vertical) sweeps the whole exposure range. */
private const val EV_DRAG_RANGE_DP = 220f

class ViewfinderActions(
    val onTapToFocus: (x: Float, y: Float, width: Float, height: Float) -> Unit,
    val onPinch: (scale: Float) -> Unit,
    val onPinchEnd: () -> Unit,
    val onSwipe: (delta: Int) -> Unit,
    val onCancelTimer: () -> Unit,
    /** Exposure index chosen by a vertical drag on the viewfinder (same state as the EV chip's slider). */
    val onExposureIndex: (index: Int) -> Unit = {},
    /** Long press: focus and lock AE/AF at the point. */
    val onLongPressFocus: (x: Float, y: Float, width: Float, height: Float) -> Unit = { _, _, _, _ -> },
)

/**
 * The 3:4 live preview. The Preview use case already carries the film look (GL effect), so this
 * only places it and draws overlays: crop mask (1:1 bars above/below; 3:2 and 16:9 bars left/right; [maskAspect] is portrait width/height, null = none), half-frame guide, date stamp, focus ring,
 * camera-type name and the timer countdown. The 3:4 box is fixed so tap-to-focus maps 1:1 onto the sensor frame.
 */
@Composable
fun Viewfinder(
    surfaceRequest: SurfaceRequest?,
    maskAspect: Float?,
    halfFrameGuide: Boolean,
    dateStamp: ImageBitmap?,
    cameraName: String?,
    nameVisible: Boolean,
    countdown: Int?,
    rotation: () -> Float,
    actions: ViewfinderActions,
    modifier: Modifier = Modifier,
    aeLocked: Boolean = false,
    onAeLockTap: () -> Unit = {},
    exposure: ExposureInfo = ExposureInfo.UNSUPPORTED,
    exposureIndex: Int = 0,
    lockEnabled: Boolean = true,
    /** Changing this (camera type, facing) forgets the focus point. */
    ringResetKey: Any? = null,
) {
    val description = stringResource(R.string.cd_viewfinder)
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var ringShown by remember { mutableStateOf(false) }
    var focusToken by remember { mutableIntStateOf(0) } // a new focus point: restart the ring animation
    var touchToken by remember { mutableIntStateOf(0) } // any ring interaction: restart the fade timer
    var evDragging by remember { mutableStateOf(false) }
    var labelVisible by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val focusAnim = remember { Animatable(1f) }
    val currentActions by rememberUpdatedState(actions)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val haptic = LocalHapticFeedback.current

    val ringVisible = focusPoint != null && (aeLocked || ringShown)
    val ringVisibleState = rememberUpdatedState(ringVisible)
    val exposureState = rememberUpdatedState(exposure)
    val indexState = rememberUpdatedState(exposureIndex)
    val ringAlpha by animateFloatAsState(
        targetValue = if (!ringVisible) 0f else if (aeLocked) 0.6f else 1f,
        animationSpec = tween(250),
        label = "ringAlpha",
    )
    val quadrant by remember { derivedStateOf { FocusRingLogic.quadrant(rotation()) } }

    LaunchedEffect(focusToken) {
        if (focusToken > 0) {
            focusAnim.snapTo(0f)
            focusAnim.animateTo(1f, tween(800))
        }
    }
    // Ring and slider fade FADE_MS after the last interaction, never while locked.
    LaunchedEffect(touchToken, aeLocked, focusPoint) {
        if (focusPoint != null && !aeLocked) {
            delay(FocusRingLogic.FADE_MS)
            ringShown = false
        }
    }
    LaunchedEffect(evDragging) {
        while (evDragging) {
            touchToken++
            delay(1_000)
        }
    }
    var seenResetKey by remember { mutableStateOf(ringResetKey) }
    LaunchedEffect(ringResetKey) {
        if (seenResetKey != ringResetKey) {
            seenResetKey = ringResetKey
            focusPoint = null
            ringShown = false
        }
    }
    // The EV value shows next to the sun briefly after every change (ring slider, EV row or viewfinder drag).
    var seenIndex by remember { mutableIntStateOf(exposureIndex) }
    LaunchedEffect(exposureIndex) {
        if (seenIndex != exposureIndex) {
            seenIndex = exposureIndex
            labelVisible = true
            delay(FocusRingLogic.LABEL_MS)
            labelVisible = false
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .background(Color.Black)
            .semantics { contentDescription = description }
            .onSizeChanged { boxSize = it }
            .pointerInput(touchSlop) {
                // One gesture detector for tap, long press, pinch, exposure drag and swipe so they never fight each other:
                // two fingers = zoom; one finger that moves mostly along the user's vertical while the ring is visible =
                // exposure; any other one-finger drag = camera swipe; no movement = tap (focus) or long press (focus + lock).
                val swipeThreshold = 72.dp.toPx()
                val dragRange = EV_DRAG_RANGE_DP.dp.toPx()
                val longPressMs = viewConfiguration.longPressTimeoutMillis
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var zooming = false
                    var consumedByChild = down.isConsumed
                    var kind = DragKind.UNDECIDED
                    var longPressed = false
                    var totalDx = 0f
                    var startIndex = 0
                    var baseUp = 0f
                    var lastUptime = down.uptimeMillis
                    while (true) {
                        val canLongPress = !zooming && !consumedByChild && !longPressed && kind == DragKind.UNDECIDED
                        val event = if (canLongPress) {
                            val remaining = (longPressMs - (lastUptime - down.uptimeMillis)).coerceAtLeast(1L)
                            withTimeoutOrNull(remaining) { awaitPointerEvent(PointerEventPass.Main) }
                        } else {
                            awaitPointerEvent(PointerEventPass.Main)
                        }
                        if (event == null) {
                            longPressed = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            focusPoint = down.position
                            ringShown = true
                            focusToken++
                            touchToken++
                            currentActions.onLongPressFocus(down.position.x, down.position.y, size.width.toFloat(), size.height.toFloat())
                            continue
                        }
                        lastUptime = event.changes.firstOrNull()?.uptimeMillis ?: lastUptime
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            zooming = true
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) currentActions.onPinch(zoom)
                            event.changes.forEach { it.consume() }
                        } else if (!zooming) {
                            val change = event.changes.firstOrNull() ?: break
                            if (change.isConsumed) consumedByChild = true // e.g. the lock button or pill took this tap
                            totalDx += change.positionChange().x
                            if (!consumedByChild && !longPressed) {
                                val from = change.position - down.position
                                val q = FocusRingLogic.quadrant(rotation())
                                if (kind == DragKind.UNDECIDED) {
                                    kind = FocusRingLogic.classifyDrag(from.x, from.y, q, ringVisibleState.value, touchSlop)
                                    if (kind == DragKind.EXPOSURE) {
                                        startIndex = indexState.value
                                        baseUp = FocusRingLogic.upDelta(from.x, from.y, q)
                                        evDragging = true
                                    }
                                }
                                if (kind == DragKind.EXPOSURE) {
                                    val up = FocusRingLogic.upDelta(from.x, from.y, q) - baseUp
                                    val index = FocusRingLogic.indexForDrag(startIndex, up, exposureState.value, dragRange)
                                    if (index != indexState.value) {
                                        currentActions.onExposureIndex(index)
                                        touchToken++
                                    }
                                    change.consume()
                                }
                            }
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    evDragging = false
                    when {
                        zooming -> currentActions.onPinchEnd()
                        consumedByChild || longPressed -> Unit
                        kind == DragKind.EXPOSURE -> Unit
                        kind == DragKind.UNDECIDED -> {
                            focusPoint = down.position
                            ringShown = true
                            focusToken++
                            touchToken++
                            currentActions.onTapToFocus(down.position.x, down.position.y, size.width.toFloat(), size.height.toFloat())
                        }
                        abs(totalDx) > swipeThreshold -> currentActions.onSwipe(if (totalDx < 0) +1 else -1)
                    }
                }
            },
    ) {
        if (surfaceRequest != null) {
            CameraXViewfinder(
                surfaceRequest = surfaceRequest,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        // The visible (unmasked) photo area, centred: bars above/below for 1:1, left/right for the taller 3:2 and 9:16.
        val visibleModifier = if (maskAspect != null) {
            (if (maskAspect < BOX_ASPECT) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())
                .aspectRatio(maskAspect).align(Alignment.Center)
        } else {
            Modifier.fillMaxSize()
        }

        // Date stamp preview, bottom-right of the (possibly masked) photo area.
        if (dateStamp != null) {
            val stampModifier = visibleModifier
            Image(
                bitmap = dateStamp,
                contentDescription = stringResource(R.string.cd_date_stamp_preview),
                contentScale = ContentScale.FillBounds,
                modifier = stampModifier,
            )
        }

        // Crop mask and half-frame guide.
        Canvas(Modifier.fillMaxSize()) {
            if (maskAspect != null) {
                val shade = Color.Black.copy(alpha = 0.72f)
                if (maskAspect < BOX_ASPECT) {
                    val bar = (size.width - size.height * maskAspect) / 2f
                    if (bar > 0f) {
                        drawRect(shade, Offset.Zero, Size(bar, size.height))
                        drawRect(shade, Offset(size.width - bar, 0f), Size(bar, size.height))
                    }
                } else {
                    val bar = (size.height - size.width / maskAspect) / 2f
                    if (bar > 0f) {
                        drawRect(shade, Offset.Zero, Size(size.width, bar))
                        drawRect(shade, Offset(0f, size.height - bar), Size(size.width, bar))
                    }
                }
            }
            if (halfFrameGuide) {
                drawLine(
                    color = RetroColors.Cream.copy(alpha = 0.75f),
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 10.dp.toPx())),
                )
            }
            val p = focusPoint
            if (p != null && ringAlpha > 0.01f) {
                val radius = FOCUS_RING_RADIUS_DP.dp.toPx() * (1f + 0.3f * (1f - focusAnim.value))
                drawCircle(RetroColors.Amber.copy(alpha = ringAlpha), radius, p, style = Stroke(2.dp.toPx()))
            }
        }

        // Exposure slider beside the ring and the AE/AF lock button attached to it.
        val ringPoint = focusPoint
        if (ringPoint != null && ringAlpha > 0.01f && boxSize.width > 0) {
            val density = LocalDensity.current
            val placement = remember(ringPoint, boxSize, quadrant, density) {
                with(density) {
                    FocusRingLogic.place(
                        ringPoint.x, ringPoint.y, boxSize.width.toFloat(), boxSize.height.toFloat(), quadrant,
                        FOCUS_RING_RADIUS_DP.dp.toPx(), 12.dp.toPx(), SLIDER_LENGTH_DP.dp.toPx(), SLIDER_THICKNESS_DP.dp.toPx(),
                        LOCK_BUTTON_DP.dp.toPx(), 8.dp.toPx(),
                    )
                }
            }
            if (exposure.supported) {
                FocusExposureSlider(
                    info = exposure,
                    index = exposureIndex,
                    side = placement.side,
                    labelVisible = labelVisible,
                    onIndexChange = currentActions.onExposureIndex,
                    onInteraction = { touchToken++ },
                    rotation = rotation,
                    alpha = { ringAlpha },
                    modifier = Modifier.offset {
                        IntOffset(
                            (placement.sliderCx - SLIDER_THICKNESS_DP.dp.toPx() / 2f).roundToInt(),
                            (placement.sliderCy - SLIDER_LENGTH_DP.dp.toPx() / 2f).roundToInt(),
                        )
                    },
                )
            }
            FocusLockButton(
                locked = aeLocked,
                enabled = lockEnabled,
                onClick = {
                    onAeLockTap()
                    touchToken++
                },
                rotation = rotation,
                alpha = { ringAlpha },
                modifier = Modifier.offset {
                    IntOffset(
                        (placement.lockCx - LOCK_BUTTON_DP.dp.toPx() / 2f).roundToInt(),
                        (placement.lockCy - LOCK_BUTTON_DP.dp.toPx() / 2f).roundToInt(),
                    )
                },
            )
        }

        // Camera-type name, fades in after a swipe or tap on the strip.
        val nameAlpha by animateFloatAsState(if (nameVisible) 1f else 0f, tween(250), label = "nameAlpha")
        if (cameraName != null) {
            Box(visibleModifier) {
                Text(
                    text = cameraName,
                    color = RetroColors.Cream,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .graphicsLayer { alpha = nameAlpha }
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .uiRotate(rotation),
                )
            }
        }

        // AE LOCK indicator, top-left of the visible area; tapping it releases the lock.
        if (aeLocked) {
            val lockDescription = stringResource(R.string.cd_ae_lock_indicator)
            Box(visibleModifier) {
                Text(
                    text = stringResource(R.string.ae_lock_indicator),
                    color = RetroColors.Amber,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button, onClick = onAeLockTap)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .uiRotate(rotation)
                        .semantics { contentDescription = lockDescription },
                )
            }
        }

        // Self-timer countdown; tapping it cancels.
        if (countdown != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClickLabel = stringResource(R.string.cd_cancel_timer, countdown), onClick = actions.onCancelTimer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = countdown.toString(),
                    color = RetroColors.Cream,
                    fontSize = 120.sp,
                    modifier = Modifier
                        .uiRotate(rotation)
                        .semantics { contentDescription = "" },
                )
            }
        }
    }
}
