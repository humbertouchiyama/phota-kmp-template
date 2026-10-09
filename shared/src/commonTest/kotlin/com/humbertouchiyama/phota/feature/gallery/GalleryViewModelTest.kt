package com.humbertouchiyama.phota.feature.gallery

import app.cash.turbine.test
import com.humbertouchiyama.phota.core.data.PhotoRepository
import com.humbertouchiyama.phota.core.network.GenerateEvent
import com.humbertouchiyama.phota.core.network.Photo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun photo(id: String) = Photo(id, "author-$id", "https://example.test/$id.jpg")

private class FakePhotoRepository : PhotoRepository {
    var photosResult: Result<List<Photo>> = Result.success(emptyList())
    var gate: CompletableDeferred<Unit>? = null
    var cancelOnCall = false
    var photosCalls = 0
    var lastForceRefresh: Boolean? = null

    override suspend fun photos(forceRefresh: Boolean): Result<List<Photo>> {
        photosCalls++
        lastForceRefresh = forceRefresh
        if (cancelOnCall) throw CancellationException("cancelled")
        val result = photosResult
        gate?.let { withContext(NonCancellable) { it.await() } }
        return result
    }

    override suspend fun photo(id: String): Result<Photo> = Result.failure(NotImplementedError())
    override fun upload(): Flow<Float> = emptyFlow()
    override fun generate(sourceId: String): Flow<GenerateEvent> = emptyFlow()
}

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {

    private val repo = FakePhotoRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun success() = runTest {
        repo.photosResult = Result.success(listOf(photo("1"), photo("2")))
        val vm = GalleryViewModel(repo)
        vm.state.test {
            assertEquals(GalleryUiState.Loading, awaitItem())
            vm.onEvent(GalleryEvent.Load)
            assertEquals(GalleryUiState.Content(listOf(photo("1"), photo("2"))), awaitItem())
        }
        assertEquals(false, repo.lastForceRefresh)
    }

    @Test
    fun emptyList() = runTest {
        val vm = GalleryViewModel(repo)
        vm.state.test {
            assertEquals(GalleryUiState.Loading, awaitItem())
            vm.onEvent(GalleryEvent.Load)
            assertEquals(GalleryUiState.Empty, awaitItem())
        }
    }

    @Test
    fun errorCanRetry() = runTest {
        repo.photosResult = Result.failure(IllegalStateException("boom"))
        val vm = GalleryViewModel(repo)
        vm.state.test {
            assertEquals(GalleryUiState.Loading, awaitItem())
            vm.onEvent(GalleryEvent.Load)
            assertEquals(GalleryUiState.Error(canRetry = true), awaitItem())
        }
    }

    @Test
    fun retryAfterError() = runTest {
        repo.photosResult = Result.failure(IllegalStateException("boom"))
        val vm = GalleryViewModel(repo)
        vm.onEvent(GalleryEvent.Load)
        repo.photosResult = Result.success(listOf(photo("1")))
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        vm.state.test {
            assertEquals(GalleryUiState.Error(canRetry = true), awaitItem())
            vm.onEvent(GalleryEvent.Retry)
            assertEquals(GalleryUiState.Loading, awaitItem())
            gate.complete(Unit)
            assertEquals(GalleryUiState.Content(listOf(photo("1"))), awaitItem())
        }
        assertEquals(false, repo.lastForceRefresh)
    }

    @Test
    fun cancelledLoadIsNotError() = runTest {
        repo.cancelOnCall = true
        val vm = GalleryViewModel(repo)
        vm.state.test {
            assertEquals(GalleryUiState.Loading, awaitItem())
            vm.onEvent(GalleryEvent.Load)
            expectNoEvents()
        }
        assertEquals(1, repo.photosCalls)
        assertEquals(GalleryUiState.Loading, vm.state.value)
    }

    @Test
    fun supersededLoadIgnoresLateResult() = runTest {
        val gate = CompletableDeferred<Unit>()
        repo.photosResult = Result.success(listOf(photo("old")))
        repo.gate = gate
        val vm = GalleryViewModel(repo)
        vm.onEvent(GalleryEvent.Load)
        repo.gate = null
        repo.photosResult = Result.success(listOf(photo("new")))
        vm.onEvent(GalleryEvent.Refresh)
        assertEquals(GalleryUiState.Content(listOf(photo("new"))), vm.state.value)
        gate.complete(Unit)
        assertEquals(GalleryUiState.Content(listOf(photo("new"))), vm.state.value)
    }

    @Test
    fun reloadFromContentKeepsGrid() = runTest {
        repo.photosResult = Result.success(listOf(photo("1")))
        val vm = GalleryViewModel(repo)
        vm.onEvent(GalleryEvent.Load)
        repo.photosResult = Result.success(listOf(photo("1"), photo("2")))
        vm.state.test {
            assertEquals(GalleryUiState.Content(listOf(photo("1"))), awaitItem())
            vm.onEvent(GalleryEvent.Load)
            assertEquals(GalleryUiState.Content(listOf(photo("1"), photo("2"))), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun failedReloadKeepsGrid() = runTest {
        repo.photosResult = Result.success(listOf(photo("1")))
        val vm = GalleryViewModel(repo)
        vm.onEvent(GalleryEvent.Load)
        repo.photosResult = Result.failure(IllegalStateException("boom"))
        vm.onEvent(GalleryEvent.Load)
        assertEquals(GalleryUiState.Content(listOf(photo("1"))), vm.state.value)
        vm.onEvent(GalleryEvent.Refresh)
        assertIs<GalleryUiState.Content>(vm.state.value)
        assertEquals(3, repo.photosCalls)
    }

    @Test
    fun refreshUsesForceRefresh() = runTest {
        val vm = GalleryViewModel(repo)
        vm.onEvent(GalleryEvent.Refresh)
        assertEquals(true, repo.lastForceRefresh)
        assertTrue(repo.photosCalls == 1)
    }
}
