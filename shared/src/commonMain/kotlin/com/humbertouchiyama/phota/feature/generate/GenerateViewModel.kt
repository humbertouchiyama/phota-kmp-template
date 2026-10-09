package com.humbertouchiyama.phota.feature.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.humbertouchiyama.phota.core.data.PhotoRepository
import com.humbertouchiyama.phota.core.network.GenerateEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

class GenerateViewModel(
    private val sourceId: String,
    private val repository: PhotoRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<GenerateState>(GenerateState.Idle)
    val state: StateFlow<GenerateState> = _state.asStateFlow()

    // Bumped per run and on Cancel; a stale job's writes are dropped (spec: Stale-run guard).
    private var runId = 0

    internal var job: Job? = null
        private set

    fun onEvent(event: GenerateUiEvent) {
        val current = _state.value
        when (event) {
            GenerateUiEvent.Start -> if (current is GenerateState.Idle) startRun()
            GenerateUiEvent.Retry -> if (current is GenerateState.Failed && current.canRetry) startRun()
            GenerateUiEvent.Cancel -> if (current.isRunning) {
                runId++
                job?.cancel()
                _state.value = GenerateState.Idle
            }
            GenerateUiEvent.Reset ->
                if (current is GenerateState.Done || current is GenerateState.Failed) _state.value = GenerateState.Idle
        }
    }

    private fun startRun() {
        val run = ++runId
        _state.value = GenerateState.Uploading(0f)
        job = viewModelScope.launch {
            fun set(s: GenerateState) {
                if (runId == run) _state.value = s
            }
            var phase = Phase.Upload
            try {
                repository.upload().collect { set(GenerateState.Uploading(it)) }
                phase = Phase.Generate
                set(GenerateState.Generating(0f))
                var finished = false
                repository.generate(sourceId)
                    .transformWhile { emit(it); it !is GenerateEvent.Finished }
                    .collect { event ->
                        when (event) {
                            is GenerateEvent.Progress -> set(GenerateState.Generating(event.fraction))
                            is GenerateEvent.Finished -> {
                                finished = true
                                set(GenerateState.Done(event.photo))
                            }
                        }
                    }
                if (!finished) set(GenerateState.Failed(Phase.Generate, canRetry = false))
            } catch (e: CancellationException) {
                set(GenerateState.Idle)
                throw e
            } catch (e: Exception) {
                set(GenerateState.Failed(phase, canRetry = true))
            }
        }
    }
}
