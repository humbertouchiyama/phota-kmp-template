package com.humbertouchiyama.phota.core.data

import org.koin.dsl.module

// Repository binding (spec: u1 Change).
val dataModule = module { single<PhotoRepository> { PhotoRepositoryImpl(get()) } }
