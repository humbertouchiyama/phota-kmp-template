package com.humbertouchiyama.phota.di

import com.humbertouchiyama.phota.core.data.dataModule
import com.humbertouchiyama.phota.core.network.networkModule
import com.humbertouchiyama.phota.feature.gallery.galleryModule
import com.humbertouchiyama.phota.feature.generate.generateModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

fun initKoin(config: KoinApplication.() -> Unit) {
    startKoin {
        config()
        modules(
            networkModule,
            dataModule,
            galleryModule,
            generateModule,
        )
    }
}

// Swift sees default arguments as required, so iOS gets a no-arg entry point.
fun initKoin() = initKoin {}
