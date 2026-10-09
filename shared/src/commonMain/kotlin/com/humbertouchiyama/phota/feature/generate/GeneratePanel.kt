package com.humbertouchiyama.phota.feature.generate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.humbertouchiyama.phota.feature.PhotoImage

@Composable
fun GeneratePanel(state: GenerateState, onEvent: (GenerateUiEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            GenerateState.Idle -> TextButton(onClick = { onEvent(GenerateUiEvent.Start) }) { Text("Generate") }
            is GenerateState.Uploading -> Running("Uploading", state.progress, onEvent)
            is GenerateState.Generating -> Running("Generating", state.progress, onEvent)
            is GenerateState.Done -> {
                PhotoImage(state.photo, Modifier.fillMaxWidth().aspectRatio(1f))
                TextButton(onClick = { onEvent(GenerateUiEvent.Reset) }) { Text("Generate again") }
            }
            is GenerateState.Failed -> {
                Text(
                    when (state.phase) {
                        Phase.Upload -> "Upload failed"
                        Phase.Generate -> "Generation failed"
                    },
                )
                Row {
                    if (state.canRetry) TextButton(onClick = { onEvent(GenerateUiEvent.Retry) }) { Text("Retry") }
                    TextButton(onClick = { onEvent(GenerateUiEvent.Reset) }) { Text("Dismiss") }
                }
            }
        }
    }
}

@Composable
private fun Running(label: String, progress: Float, onEvent: (GenerateUiEvent) -> Unit) {
    Text(label)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f))
        Text("${(progress * 100).toInt()}%")
        TextButton(onClick = { onEvent(GenerateUiEvent.Cancel) }) { Text("Cancel") }
    }
}
