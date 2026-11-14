package com.retrocam.gallery

import android.content.IntentSender
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.retrocam.AppContainer
import com.retrocam.R
import com.retrocam.data.DeleteOutcome
import com.retrocam.data.PhotoLibrary
import com.retrocam.logic.LibraryPhoto
import com.retrocam.logic.Selection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryState(
    val loading: Boolean = true,
    val photos: List<LibraryPhoto> = emptyList(),
    val selection: Selection = Selection(),
    /** Page the full-screen viewer opens on; null while the grid is showing. */
    val viewerStart: Int? = null,
    /** Photos waiting for the user to confirm deleting them. */
    val confirmDelete: List<LibraryPhoto>? = null,
)

/** A system confirmation (MediaStore delete request) for the UI to launch. */
class ConsentRequest(val sender: IntentSender)

class GalleryViewModel(private val library: PhotoLibrary) : ViewModel() {
    private val _state = MutableStateFlow(GalleryState())
    val state: StateFlow<GalleryState> = _state

    private val consents = Channel<ConsentRequest>(Channel.BUFFERED)
    val consentRequests: Flow<ConsentRequest> = consents.receiveAsFlow()

    private val messages = Channel<Int>(Channel.BUFFERED)
    val messageRes: Flow<Int> = messages.receiveAsFlow()

    private var pendingConsent: List<Uri> = emptyList()

    init {
        viewModelScope.launch {
            library.changes.collect { reload() }
        }
    }

    private suspend fun reload() {
        try {
            val photos = library.list()
            _state.update { s ->
                s.copy(
                    loading = false,
                    photos = photos,
                    selection = s.selection.prune(photos.map { it.id }),
                    viewerStart = if (photos.isEmpty()) null else s.viewerStart,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "listing failed", t)
            _state.update { it.copy(loading = false) }
            messages.trySend(R.string.gallery_load_failed)
        }
    }

    /** Called each time the gallery opens, in case photos changed while it was closed. */
    fun refresh() {
        viewModelScope.launch { reload() }
    }

    // ---------------------------------------------------------------- selection

    fun onGridLongPress(photo: LibraryPhoto) = _state.update { it.copy(selection = it.selection.start(photo.id)) }

    fun onGridTap(index: Int) {
        val s = _state.value
        val photo = s.photos.getOrNull(index) ?: return
        if (s.selection.active) {
            _state.update { it.copy(selection = it.selection.toggle(photo.id)) }
        } else {
            _state.update { it.copy(viewerStart = index) }
        }
    }

    fun selectAll() = _state.update { it.copy(selection = it.selection.selectAll(it.photos.map { p -> p.id })) }
    fun clearSelection() = _state.update { it.copy(selection = it.selection.clear()) }
    fun closeViewer() = _state.update { it.copy(viewerStart = null) }

    // ---------------------------------------------------------------- delete

    fun askDeleteSelection() {
        val s = _state.value
        val chosen = s.photos.filter { it.id in s.selection.ids }
        if (chosen.isNotEmpty()) _state.update { it.copy(confirmDelete = chosen) }
    }

    fun askDelete(photo: LibraryPhoto) = _state.update { it.copy(confirmDelete = listOf(photo)) }

    fun dismissDelete() = _state.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val doomed = _state.value.confirmDelete ?: return
        _state.update { it.copy(confirmDelete = null) }
        viewModelScope.launch { handle(library.delete(doomed.map { it.uri })) }
    }

    /** The system dialog finished: [approved] is RESULT_OK. */
    fun onConsentResult(approved: Boolean) {
        val remaining = pendingConsent
        pendingConsent = emptyList()
        if (!approved) {
            library.notifyChanged() // refresh in case part of the batch went through
            return
        }
        viewModelScope.launch { handle(library.deleteAfterConsent(remaining)) }
    }

    private suspend fun handle(outcome: DeleteOutcome) {
        when (outcome) {
            DeleteOutcome.Done -> library.notifyChanged()
            DeleteOutcome.Failed -> {
                library.notifyChanged()
                messages.trySend(R.string.gallery_delete_failed)
            }
            is DeleteOutcome.NeedsConsent -> {
                pendingConsent = outcome.remaining
                consents.trySend(ConsentRequest(outcome.sender))
            }
        }
    }

    companion object {
        private const val TAG = "GalleryViewModel"

        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = GalleryViewModel(container.photoLibrary) as T
        }
    }
}
