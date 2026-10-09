package com.humbertouchiyama.phota.feature.generate

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

// Generate flow ViewModel, keyed by source photo id (u3 spec).
val generateModule = module {
    viewModel { (id: String) -> GenerateViewModel(id, get()) }
}
