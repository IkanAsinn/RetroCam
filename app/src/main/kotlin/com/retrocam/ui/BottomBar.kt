package com.retrocam.ui

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlipCameraAndroid
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.retrocam.R
import com.retrocam.ui.theme.RetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Thumbnail (left), shutter (centre), front/back switch (right). */
@Composable
fun BottomBar(
    thumbnail: Uri?,
    saving: Boolean,
    counting: Boolean,
    canSwitch: Boolean,
    rotation: () -> Float,
    onThumbnail: (Uri) -> Unit,
    onShutter: () -> Unit,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Thumbnail(thumbnail, saving, rotation, onThumbnail)
        }
        ShutterButton(counting, onShutter)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            val label = stringResource(R.string.cd_switch_camera)
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canSwitch, role = Role.Button, onClick = onSwitch)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.FlipCameraAndroid, null,
                    tint = RetroColors.Cream.copy(alpha = if (canSwitch) 1f else 0.35f),
                    modifier = Modifier.size(30.dp).uiRotate(rotation),
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(counting: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, tween(80), label = "shutterScale")
    val label = stringResource(if (counting) R.string.cd_cancel_timer_short else R.string.cd_shutter)
    Box(
        modifier = Modifier
            .size(84.dp)
            .scale(scale)
            .border(4.dp, RetroColors.Cream, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (counting) RetroColors.Orange else RetroColors.Cream)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (counting) Icon(Icons.Outlined.Close, null, tint = RetroColors.Charcoal, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun Thumbnail(uri: Uri?, saving: Boolean, rotation: () -> Float, onOpen: (Uri) -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) { loadThumb(context, uri) }
    }
    val label = stringResource(if (uri != null) R.string.cd_thumbnail else R.string.cd_thumbnail_none)
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.size(56.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .uiRotate(rotation)
                .clip(shape)
                .border(2.dp, RetroColors.Cream.copy(alpha = 0.8f), shape)
                .clickable(enabled = uri != null, role = Role.Button) { uri?.let(onOpen) }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = bitmap,
                transitionSpec = { (fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.6f)) togetherWith fadeOut(tween(120)) },
                label = "thumbnail",
            ) { bmp ->
                if (bmp != null) {
                    Image(bmp, null, Modifier.size(56.dp), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Outlined.PhotoLibrary, null, tint = RetroColors.Cream.copy(alpha = 0.5f))
                }
            }
        }
        if (saving) {
            val savingLabel = stringResource(R.string.cd_saving)
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .semantics { contentDescription = savingLabel },
                strokeWidth = 2.dp,
                color = RetroColors.Orange,
            )
        }
    }
}

private fun loadThumb(context: android.content.Context, uri: Uri): ImageBitmap? = try {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val scale = 256f / max(info.size.width, info.size.height).coerceAtLeast(1)
        if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }.asImageBitmap()
} catch (_: Throwable) {
    null
}
