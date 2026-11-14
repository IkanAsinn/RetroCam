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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
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

class ViewfinderActions(
    val onTapToFocus: (x: Float, y: Float, width: Float, height: Float) -> Unit,
    val onPinch: (scale: Float) -> Unit,
    val onPinchEnd: () -> Unit,
    val onSwipe: (delta: Int) -> Unit,
    val onCancelTimer: () -> Unit,
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
) {
    val description = stringResource(R.string.cd_viewfinder)
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val focusAnim = remember { Animatable(1f) }
    val currentActions by rememberUpdatedState(actions)
    val touchSlop = LocalViewConfiguration.current.touchSlop

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            focusAnim.snapTo(0f)
            focusAnim.animateTo(1f, tween(800))
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .background(Color.Black)
            .semantics { contentDescription = description }
            .pointerInput(touchSlop) {
                // One gesture detector for tap, pinch and swipe so they never fight each other.
                val swipeThreshold = 72.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var zooming = false
                    var moved = false
                    var consumedByChild = false
                    var totalDx = 0f
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            zooming = true
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) currentActions.onPinch(zoom)
                            event.changes.forEach { it.consume() }
                        } else if (!zooming) {
                            val change = event.changes.firstOrNull() ?: break
                            if (change.isConsumed) consumedByChild = true // e.g. the AE LOCK pill took this tap
                            totalDx += change.positionChange().x
                            val from = change.position - down.position
                            if (abs(from.x) > touchSlop || abs(from.y) > touchSlop) moved = true
                        }
                    } while (event.changes.any { it.pressed })
                    when {
                        zooming -> currentActions.onPinchEnd()
                        consumedByChild -> Unit
                        !moved -> {
                            focusPoint = down.position
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
            val t = focusAnim.value
            if (p != null && t < 1f) {
                val radius = (52.dp.toPx() * (1f - 0.35f * t))
                val alpha = if (t < 0.6f) 1f else 1f - (t - 0.6f) / 0.4f
                drawCircle(RetroColors.Amber.copy(alpha = alpha), radius, p, style = Stroke(2.dp.toPx()))
            }
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
