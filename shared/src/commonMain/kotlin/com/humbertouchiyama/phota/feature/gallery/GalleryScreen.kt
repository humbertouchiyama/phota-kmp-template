package com.humbertouchiyama.phota.feature.gallery

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GalleryScreen(
    onOpenPhoto: (String) -> Unit,
    viewModel: GalleryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onEvent(GalleryEvent.Load) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Gallery", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { viewModel.onEvent(GalleryEvent.Refresh) }) { Text("Refresh") }
        }
        when (val s = state) {
            GalleryUiState.Loading -> Centered { CircularProgressIndicator() }
            GalleryUiState.Empty -> Centered { Text("No photos yet") }
            is GalleryUiState.Error -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Could not load photos")
                    if (s.canRetry) TextButton(onClick = { viewModel.onEvent(GalleryEvent.Retry) }) { Text("Retry") }
                }
            }
            is GalleryUiState.Content -> LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(s.photos, key = { it.id }) { photo ->
                    val border = if (photo.id == s.selectedId) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        Modifier
                    }
                    AsyncImage(
                        model = photo.imageUrl,
                        contentDescription = photo.author,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.aspectRatio(1f).then(border).clickable {
                            viewModel.onEvent(GalleryEvent.Select(photo.id))
                            onOpenPhoto(photo.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
