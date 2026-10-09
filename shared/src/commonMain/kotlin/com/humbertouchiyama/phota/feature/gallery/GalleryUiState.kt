package com.humbertouchiyama.phota.feature.gallery

import com.humbertouchiyama.phota.core.network.Photo

sealed interface GalleryUiState {
    data object Loading : GalleryUiState
    data class Content(val photos: List<Photo>, val selectedId: String? = null) : GalleryUiState
    data object Empty : GalleryUiState
    data class Error(val canRetry: Boolean) : GalleryUiState
}

sealed interface GalleryEvent {
    data object Load : GalleryEvent
    data object Retry : GalleryEvent
    data object Refresh : GalleryEvent
    data class Select(val photoId: String) : GalleryEvent
}
