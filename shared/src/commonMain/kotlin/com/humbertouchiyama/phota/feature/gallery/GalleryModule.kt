package com.humbertouchiyama.phota.feature.gallery

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val galleryModule = module {
    viewModel { GalleryViewModel(get()) }
}
