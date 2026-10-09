package com.humbertouchiyama.phota.feature.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.humbertouchiyama.phota.core.data.PhotoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(
    private val photoId: String,
    private val repository: PhotoRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun onEvent(event: DetailUiEvent) {
        val current = _state.value
        when (event) {
            DetailUiEvent.Load -> if (current is DetailUiState.Loading && job?.isActive != true) load()
            DetailUiEvent.Retry -> if (current is DetailUiState.Error && current.canRetry) load()
        }
    }

    // A cancelled photo() escapes and leaves Loading; the next Load re-runs it (spec: Detail machine).
    private fun load() {
        _state.value = DetailUiState.Loading
        job = viewModelScope.launch {
            _state.value = repository.photo(photoId).fold(
                onSuccess = { DetailUiState.Content(it) },
                onFailure = { DetailUiState.Error(canRetry = true) },
            )
        }
    }
}
