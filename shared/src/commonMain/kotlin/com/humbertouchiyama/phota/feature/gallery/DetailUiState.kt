package com.humbertouchiyama.phota.feature.gallery

import com.humbertouchiyama.phota.core.network.Photo

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Content(val photo: Photo) : DetailUiState
    data class Error(val canRetry: Boolean) : DetailUiState
}

sealed interface DetailUiEvent {
    data object Load : DetailUiEvent
    data object Retry : DetailUiEvent
}
