package com.humbertouchiyama.phota.feature.generate

import com.humbertouchiyama.phota.core.network.Photo

enum class Phase { Upload, Generate }

sealed interface GenerateState {
    data object Idle : GenerateState
    data class Uploading(val progress: Float) : GenerateState
    data class Generating(val progress: Float) : GenerateState
    data class Done(val photo: Photo) : GenerateState
    data class Failed(val phase: Phase, val canRetry: Boolean) : GenerateState
}

val GenerateState.isRunning: Boolean
    get() = this is GenerateState.Uploading || this is GenerateState.Generating

sealed interface GenerateUiEvent {
    data object Start : GenerateUiEvent
    data object Cancel : GenerateUiEvent
    data object Retry : GenerateUiEvent
    data object Reset : GenerateUiEvent
}
