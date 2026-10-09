package com.humbertouchiyama.phota.feature.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.humbertouchiyama.phota.feature.PhotoImage
import com.humbertouchiyama.phota.feature.generate.GeneratePanel
import com.humbertouchiyama.phota.feature.generate.GenerateViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DetailScreen(photoId: String, onBack: () -> Unit) {
    // Keyed per photo: the store owner is the Activity/ViewController (spec: Per-photo keys).
    val detail = koinViewModel<DetailViewModel>(key = "detail:$photoId") { parametersOf(photoId) }
    val generate = koinViewModel<GenerateViewModel>(key = "generate:$photoId") { parametersOf(photoId) }
    val state by detail.state.collectAsStateWithLifecycle()
    val generateState by generate.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { detail.onEvent(DetailUiEvent.Load) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        when (val s = state) {
            DetailUiState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is DetailUiState.Error -> {
                Text("Could not load photo")
                if (s.canRetry) TextButton(onClick = { detail.onEvent(DetailUiEvent.Retry) }) { Text("Retry") }
            }
            is DetailUiState.Content -> {
                PhotoImage(s.photo, Modifier.fillMaxWidth().aspectRatio(1f))
                Text(s.photo.author)
                GeneratePanel(generateState, generate::onEvent)
            }
        }
    }
}
