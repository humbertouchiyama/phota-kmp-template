package com.humbertouchiyama.phota

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory

// Without the Ktor fetcher Coil 3 renders blank images on iOS (u2 spec).
@Composable
fun SetUpImageLoader() {
    setSingletonImageLoaderFactory { ctx ->
        ImageLoader.Builder(ctx).components { add(KtorNetworkFetcherFactory()) }.build()
    }
}
