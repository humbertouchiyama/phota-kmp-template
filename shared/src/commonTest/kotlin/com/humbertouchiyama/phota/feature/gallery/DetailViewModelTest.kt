package com.humbertouchiyama.phota.feature.gallery

import app.cash.turbine.test
import com.humbertouchiyama.phota.core.network.Photo
import com.humbertouchiyama.phota.feature.FakeError
import com.humbertouchiyama.phota.feature.ScriptedPhotoRepository
import com.humbertouchiyama.phota.feature.testPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private lateinit var repo: ScriptedPhotoRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = ScriptedPhotoRepository()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun gated(): CompletableDeferred<Result<Photo>> {
        val gate = CompletableDeferred<Result<Photo>>()
        repo.photoResult = { gate.await() }
        return gate
    }

    @Test
    fun loads_content() = runTest {
        val gate = gated()
        val vm = DetailViewModel("7", repo)
        vm.state.test {
            assertEquals(DetailUiState.Loading, awaitItem())
            gate.complete(Result.success(testPhoto("7")))
            assertEquals(DetailUiState.Content(testPhoto("7")), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun missingPhoto_isError() = runTest {
        repo.photoResult = { Result.failure(FakeError()) }
        val vm = DetailViewModel("7", repo)
        vm.state.test {
            assertEquals(DetailUiState.Error(canRetry = true), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun retry_afterError_isContent() = runTest {
        repo.photoResult = { Result.failure(FakeError()) }
        val vm = DetailViewModel("7", repo)
        vm.state.test {
            assertEquals(DetailUiState.Error(canRetry = true), awaitItem())
            val gate = gated()
            vm.onEvent(DetailUiEvent.Retry)
            assertEquals(DetailUiState.Loading, awaitItem())
            gate.complete(Result.success(testPhoto("7")))
            assertEquals(DetailUiState.Content(testPhoto("7")), awaitItem())
            expectNoEvents()
        }
        assertEquals(2, repo.photoRequests.size)
    }

    @Test
    fun cancelledLoad_staysLoading_andLoadRecovers() = runTest {
        repo.photoResult = {
            if (repo.photoRequests.size == 1) throw CancellationException("x")
            Result.success(testPhoto(it))
        }
        val vm = DetailViewModel("7", repo)
        vm.state.test {
            assertEquals(DetailUiState.Loading, awaitItem())
            expectNoEvents()
            vm.onEvent(DetailUiEvent.Load)
            assertEquals(DetailUiState.Content(testPhoto("7")), awaitItem())
            expectNoEvents()
        }
        assertEquals(2, repo.photoRequests.size)
    }

    @Test
    fun requests_ownPhotoId() = runTest {
        DetailViewModel("7", repo)
        assertEquals(listOf("7"), repo.photoRequests)
    }

    @Test
    fun retry_fromContent_isNoOp() = runTest {
        val vm = DetailViewModel("7", repo)
        vm.state.test {
            assertEquals(DetailUiState.Content(testPhoto("7")), awaitItem())
            vm.onEvent(DetailUiEvent.Retry)
            expectNoEvents()
        }
        assertEquals(1, repo.photoRequests.size)
    }

    @Test
    fun retry_whileLoading_isNoOp() = runTest {
        gated()
        val vm = DetailViewModel("7", repo)
        vm.state.test {
            assertEquals(DetailUiState.Loading, awaitItem())
            vm.onEvent(DetailUiEvent.Retry)
            expectNoEvents()
        }
        assertEquals(1, repo.photoRequests.size)
    }

    @Test
    fun load_whileActiveOrContent_isNoOp() = runTest {
        val gate = gated()
        val vm = DetailViewModel("7", repo)
        vm.state.test {
            assertEquals(DetailUiState.Loading, awaitItem())
            vm.onEvent(DetailUiEvent.Load)
            assertEquals(1, repo.photoRequests.size)
            gate.complete(Result.success(testPhoto("7")))
            assertEquals(DetailUiState.Content(testPhoto("7")), awaitItem())
            vm.onEvent(DetailUiEvent.Load)
            expectNoEvents()
        }
        assertEquals(1, repo.photoRequests.size)
    }
}
