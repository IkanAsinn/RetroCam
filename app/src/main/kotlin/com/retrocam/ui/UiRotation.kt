package com.retrocam.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Icons and labels turn in place when the phone turns; the layout never does.
 * [degrees] is OrientationWatcher's continuous angle (may be 270, -180, 360), fed straight to the
 * animation: it is already continuous, so no modulo here or the icons would spin a full turn.
 * Read the result only inside [uiRotate] so rotation never triggers recomposition.
 */
@Composable
fun rememberUiRotation(degrees: Float): State<Float> =
    animateFloatAsState(targetValue = degrees, animationSpec = tween(200), label = "uiRotation")

fun Modifier.uiRotate(rotation: () -> Float): Modifier = graphicsLayer { rotationZ = rotation() }
