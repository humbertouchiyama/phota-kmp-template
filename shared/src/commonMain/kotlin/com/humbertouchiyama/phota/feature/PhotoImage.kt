package com.humbertouchiyama.phota.feature

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import com.humbertouchiyama.phota.core.network.Photo

@Composable
fun PhotoImage(photo: Photo, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        model = photo.imageUrl,
        contentDescription = photo.author,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        },
        error = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Image unavailable") }
        },
    )
}
