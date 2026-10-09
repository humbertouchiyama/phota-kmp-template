package com.humbertouchiyama.phota.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.humbertouchiyama.phota.core.data.PhotoRepository
import com.humbertouchiyama.phota.core.network.FakePhotoApi
import com.humbertouchiyama.phota.core.network.PhotoApi
import com.humbertouchiyama.phota.feature.gallery.DetailViewModel
import com.humbertouchiyama.phota.feature.gallery.GalleryViewModel
import com.humbertouchiyama.phota.feature.generate.GenerateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.Koin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class KoinGraphTest {

    private lateinit var koin: Koin

    // ViewModels go through a store so teardown can clear them and cancel jobs launched in init.
    private val store = ViewModelStore()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        initKoin()
        koin = KoinPlatform.getKoin()
    }

    @AfterTest
    fun tearDown() {
        try {
            store.clear()
        } finally {
            try {
                stopKoin()
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    private fun <T : ViewModel> viewModel(cls: KClass<T>, parameters: ParametersDefinition? = null): T {
        val factory = object : ViewModelProvider.Factory {
            override fun <VM : ViewModel> create(modelClass: KClass<VM>, extras: CreationExtras): VM =
                koin.get(modelClass, null, parameters)
        }
        return ViewModelProvider.create(store, factory)[cls]
    }

    @Test
    fun repositoryResolves() {
        assertNotNull(koin.get<PhotoRepository>())
    }

    @Test
    fun repositoryIsSingleton() {
        assertSame(koin.get<PhotoRepository>(), koin.get<PhotoRepository>())
    }

    @Test
    fun defaultPhotoApiIsFake() {
        assertIs<FakePhotoApi>(koin.get<PhotoApi>())
    }

    @Test
    fun galleryViewModelResolves() {
        assertIs<GalleryViewModel>(viewModel(GalleryViewModel::class))
    }

    @Test
    fun photoViewModelsResolveWithId() {
        assertIs<DetailViewModel>(viewModel(DetailViewModel::class) { parametersOf("1") })
        assertIs<GenerateViewModel>(viewModel(GenerateViewModel::class) { parametersOf("1") })
    }
}
