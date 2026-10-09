package com.humbertouchiyama.phota.feature.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.humbertouchiyama.phota.core.data.PhotoRepository
import com.humbertouchiyama.phota.core.network.Photo
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(private val repository: PhotoRepository) : ViewModel() {
    private val _state = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun onEvent(event: GalleryEvent) {
        when (event) {
            GalleryEvent.Load -> load(forceRefresh = false, viaLoading = _state.value !is GalleryUiState.Content)
            GalleryEvent.Retry -> load(forceRefresh = false, viaLoading = true)
            GalleryEvent.Refresh -> load(forceRefresh = true, viaLoading = _state.value !is GalleryUiState.Content)
            is GalleryEvent.Select -> {
                val current = _state.value
                if (current is GalleryUiState.Content) _state.value = current.copy(selectedId = event.photoId)
            }
        }
    }

    private fun load(forceRefresh: Boolean, viaLoading: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (viaLoading) _state.value = GalleryUiState.Loading
            val result = repository.photos(forceRefresh)
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = ::onLoaded,
                onFailure = {
                    if (_state.value !is GalleryUiState.Content) _state.value = GalleryUiState.Error(canRetry = true)
                },
            )
        }
    }

    private fun onLoaded(photos: List<Photo>) {
        val selected = (_state.value as? GalleryUiState.Content)?.selectedId
        _state.value = if (photos.isEmpty()) {
            GalleryUiState.Empty
        } else {
            GalleryUiState.Content(photos, selected?.takeIf { id -> photos.any { it.id == id } })
        }
    }
}
