package com.retrocam.gallery

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retrocam.R
import com.retrocam.data.PhotoLibrary
import com.retrocam.logic.LibraryPhoto
import com.retrocam.ui.openPhoto
import com.retrocam.ui.sharePhoto
import com.retrocam.ui.theme.RetroColors
import kotlinx.coroutines.launch

/** Full-screen RetroCam gallery: grid of every saved photo, swipeable viewer, share / delete / open in Photos. */
@Composable
fun GalleryScreen(viewModel: GalleryViewModel, library: PhotoLibrary, onClose: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun toast(res: Int) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(resources.getString(res))
        }
    }

    LaunchedEffect(viewModel) { viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.messageRes.collect {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(resources.getString(it))
        }
    }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onConsentResult(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(viewModel) {
        viewModel.consentRequests.collect { consentLauncher.launch(IntentSenderRequest.Builder(it.sender).build()) }
    }

    BackHandler {
        when {
            state.confirmDelete != null -> viewModel.dismissDelete()
            state.viewerStart != null -> viewModel.closeViewer()
            state.selection.active -> viewModel.clearSelection()
            else -> onClose()
        }
    }

    fun open(uri: Uri) {
        if (!openPhoto(context, uri)) toast(R.string.msg_no_gallery)
    }

    Box(Modifier.fillMaxSize().background(RetroColors.Charcoal)) {
        val viewerStart = state.viewerStart
        if (viewerStart != null && state.photos.isNotEmpty()) {
            Viewer(
                photos = state.photos,
                startIndex = viewerStart.coerceIn(0, state.photos.lastIndex),
                library = library,
                onBack = viewModel::closeViewer,
                onShare = { if (!sharePhoto(context, it.uri)) toast(R.string.msg_share_failed) },
                onOpen = { open(it.uri) },
                onDelete = viewModel::askDelete,
            )
        } else {
            Grid(state, library, viewModel, onClose)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp))
    }

    state.confirmDelete?.let { doomed ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            containerColor = RetroColors.CharcoalRaised,
            titleContentColor = RetroColors.Cream,
            textContentColor = RetroColors.Cream,
            title = { Text(resources.getQuantityString(R.plurals.gallery_delete_title, doomed.size, doomed.size)) },
            text = { Text(stringResource(R.string.gallery_delete_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.gallery_delete), color = RetroColors.Orange) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text(stringResource(R.string.gallery_cancel), color = RetroColors.Cream) }
            },
        )
    }
}

// ------------------------------------------------------------------------------ grid

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Grid(state: GalleryState, library: PhotoLibrary, viewModel: GalleryViewModel, onClose: () -> Unit) {
    val selecting = state.selection.active
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                IconButton(onClick = viewModel::clearSelection) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.gallery_cd_cancel_selection), tint = RetroColors.Cream)
                }
                Text(
                    LocalResources.current.getQuantityString(R.plurals.gallery_selected, state.selection.count, state.selection.count),
                    color = RetroColors.Cream,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::selectAll) {
                    Icon(Icons.Outlined.SelectAll, stringResource(R.string.gallery_cd_select_all), tint = RetroColors.Cream)
                }
                IconButton(onClick = viewModel::askDeleteSelection) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.gallery_cd_delete_selected), tint = RetroColors.Orange)
                }
            } else {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.gallery_cd_back), tint = RetroColors.Cream)
                }
                Text(
                    stringResource(R.string.gallery_title),
                    color = RetroColors.Cream,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                state.loading -> {
                    val label = stringResource(R.string.gallery_loading)
                    CircularProgressIndicator(
                        color = RetroColors.Orange,
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
                state.photos.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.PhotoLibrary, null, tint = RetroColors.Cream.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.gallery_empty), color = RetroColors.Cream, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.gallery_empty_hint),
                        color = RetroColors.Cream.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                    )
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(state.photos, key = { _, p -> p.id }) { index, photo ->
                        val selected = photo.id in state.selection.ids
                        val desc = stringResource(
                            if (selected) R.string.gallery_cd_photo_selected else R.string.gallery_cd_photo,
                            index + 1, state.photos.size,
                        )
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .background(RetroColors.CharcoalRaised)
                                .combinedClickable(
                                    role = Role.Button,
                                    onClick = { viewModel.onGridTap(index) },
                                    onLongClick = { viewModel.onGridLongPress(photo) },
                                )
                                .semantics { contentDescription = desc },
                        ) {
                            Thumb(library, photo.uri, 256)
                            if (selected) {
                                Box(Modifier.fillMaxSize().background(RetroColors.Orange.copy(alpha = 0.28f)))
                                Icon(
                                    Icons.Outlined.CheckCircle, null,
                                    tint = RetroColors.Cream,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(22.dp)
                                        .background(RetroColors.Orange, CircleShape),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumb(library: PhotoLibrary, uri: Uri, sizePx: Int) {
    val bitmap by produceState<ImageBitmap?>(null, uri) { value = library.thumbnail(uri, sizePx)?.asImageBitmap() }
    bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
}

// ------------------------------------------------------------------------------ viewer

@Composable
private fun Viewer(
    photos: List<LibraryPhoto>,
    startIndex: Int,
    library: PhotoLibrary,
    onBack: () -> Unit,
    onShare: (LibraryPhoto) -> Unit,
    onOpen: (LibraryPhoto) -> Unit,
    onDelete: (LibraryPhoto) -> Unit,
) {
    val pager = rememberPagerState(initialPage = startIndex) { photos.size }
    val current = photos.getOrNull(pager.currentPage)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { photos.getOrNull(it)?.id ?: it },
        ) { page ->
            val photo = photos.getOrNull(page)
            if (photo != null) ViewerPage(library, photo, page, photos.size)
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.gallery_cd_back_to_grid), tint = RetroColors.Cream)
            }
            Text(
                stringResource(R.string.gallery_position, pager.currentPage + 1, photos.size),
                color = RetroColors.Cream,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (current != null) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .navigationBarsPadding()
                    .padding(vertical = 4.dp),
            ) {
                ViewerAction(Modifier.weight(1f), Icons.Outlined.Share, R.string.gallery_share) { onShare(current) }
                ViewerAction(Modifier.weight(1f), Icons.AutoMirrored.Outlined.OpenInNew, R.string.gallery_open_in_photos) { onOpen(current) }
                ViewerAction(Modifier.weight(1f), Icons.Outlined.Delete, R.string.gallery_delete, RetroColors.Orange) { onDelete(current) }
            }
        }
    }
}

@Composable
private fun ViewerAction(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    tint: Color = RetroColors.Cream,
    onClick: () -> Unit,
) {
    val text = stringResource(label)
    Column(
        modifier
            .combinedClickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics { contentDescription = text },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Text(text, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ViewerPage(library: PhotoLibrary, photo: LibraryPhoto, index: Int, total: Int) {
    val bitmap by produceState<ImageBitmap?>(null, photo.uri) { value = library.viewerBitmap(photo.uri, VIEWER_MAX_SIDE)?.asImageBitmap() }
    val desc = stringResource(R.string.gallery_cd_photo, index + 1, total)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp == null) {
            CircularProgressIndicator(color = RetroColors.Orange)
        } else {
            ZoomableImage(bmp, desc)
        }
    }
}

/**
 * Pinch to zoom (1x to 5x), drag to pan while zoomed, double-tap to toggle. While at 1x single-finger drags are
 * left alone so the pager can swipe.
 */
@Composable
private fun ZoomableImage(bitmap: ImageBitmap, description: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else 2.5f
                    offset = Offset.Zero
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.changes.size > 1 || scale > 1f) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            val limitX = size.width * (newScale - 1f) / 2f
                            val limitY = size.height * (newScale - 1f) / 2f
                            val pan = event.calculatePan()
                            offset = if (newScale <= 1f) {
                                Offset.Zero
                            } else {
                                Offset((offset.x + pan.x).coerceIn(-limitX, limitX), (offset.y + pan.y).coerceIn(-limitY, limitY))
                            }
                            scale = newScale
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

private const val VIEWER_MAX_SIDE = 2048
private const val MAX_ZOOM = 5f
