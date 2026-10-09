package com.humbertouchiyama.phota

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.humbertouchiyama.phota.feature.gallery.DetailScreen
import com.humbertouchiyama.phota.feature.gallery.GalleryScreen

sealed interface Screen {
    data object Gallery : Screen
    data class Detail(val photoId: String) : Screen
}

internal fun Screen.encode(): String = when (this) {
    Screen.Gallery -> "gallery"
    is Screen.Detail -> "detail:$photoId"
}

internal fun decodeScreen(s: String): Screen {
    val parts = s.split(":", limit = 2)
    return if (parts[0] == "detail" && parts.size == 2) Screen.Detail(parts[1]) else Screen.Gallery
}

private val ScreenSaver = Saver<Screen, String>(save = { it.encode() }, restore = { decodeScreen(it) })

@Composable
fun App() {
    SetUpImageLoader()
    var screen: Screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf(Screen.Gallery) }
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.safeDrawingPadding()) {
                when (val current = screen) {
                    Screen.Gallery -> GalleryScreen(onOpenPhoto = { screen = Screen.Detail(it) })
                    is Screen.Detail -> DetailScreen(photoId = current.photoId, onBack = { screen = Screen.Gallery })
                }
            }
        }
    }
}
