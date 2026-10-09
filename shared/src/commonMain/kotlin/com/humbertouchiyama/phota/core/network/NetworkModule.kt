package com.humbertouchiyama.phota.core.network

import org.koin.dsl.module

// Fake bound by default; the real client is one line away (spec: Q1).
val networkModule = module {
    single { createHttpClient() }
    single<PhotoApi> { FakePhotoApi() }
}
