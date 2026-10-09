package com.humbertouchiyama.phota.feature.generate

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.humbertouchiyama.phota.core.network.GenerateEvent
import com.humbertouchiyama.phota.feature.FakeError
import com.humbertouchiyama.phota.feature.ScriptedPhotoRepository
import com.humbertouchiyama.phota.feature.testPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GenerateViewModelTest {

    private lateinit var repo: ScriptedPhotoRepository
    private lateinit var vm: GenerateViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = ScriptedPhotoRepository()
        vm = GenerateViewModel("src-1", repo)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val generated = testPhoto("gen-1")

    // Idle → Start → Uploading(0f).
    private suspend fun ReceiveTurbine<GenerateState>.start() {
        assertEquals(GenerateState.Idle, awaitItem())
        vm.onEvent(GenerateUiEvent.Start)
        assertEquals(GenerateState.Uploading(0f), awaitItem())
    }

    // From Uploading(0f): finishes the latest upload and reaches Generating(0f).
    private suspend fun ReceiveTurbine<GenerateState>.toGenerating() {
        repo.uploads.last().close()
        assertEquals(GenerateState.Generating(0f), awaitItem())
    }

    private suspend fun ReceiveTurbine<GenerateState>.toDone() {
        toGenerating()
        repo.generates.last().second.trySend(GenerateEvent.Finished(generated))
        assertEquals(GenerateState.Done(generated), awaitItem())
    }

    private suspend fun ReceiveTurbine<GenerateState>.failUpload() {
        repo.uploads.last().close(FakeError())
        assertEquals(GenerateState.Failed(Phase.Upload, canRetry = true), awaitItem())
    }

    @Test
    fun success_endsDone_withFinishedPhoto() = runTest {
        vm.state.test {
            start()
            repo.uploads.single().trySend(0.5f)
            assertEquals(GenerateState.Uploading(0.5f), awaitItem())
            repo.uploads.single().close()
            assertEquals(GenerateState.Generating(0f), awaitItem())
            val generate = repo.generates.single().second
            generate.trySend(GenerateEvent.Progress(0.5f))
            assertEquals(GenerateState.Generating(0.5f), awaitItem())
            generate.trySend(GenerateEvent.Finished(generated))
            assertEquals(GenerateState.Done(generated), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun generate_usesSourceId() = runTest {
        vm.state.test {
            start()
            toGenerating()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("src-1", repo.generates.single().first)
    }

    @Test
    fun uploadFailure_isFailedUpload_noLiveJob() = runTest {
        vm.state.test {
            start()
            failUpload()
            expectNoEvents()
        }
        assertFalse(vm.job!!.isActive)
    }

    @Test
    fun generateFailure_isFailedGenerate() = runTest {
        vm.state.test {
            start()
            toGenerating()
            repo.generates.single().second.close(FakeError())
            assertEquals(GenerateState.Failed(Phase.Generate, canRetry = true), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun generateWithoutFinished_isNotRetryable() = runTest {
        vm.state.test {
            start()
            toGenerating()
            val generate = repo.generates.single().second
            generate.trySend(GenerateEvent.Progress(1f))
            assertEquals(GenerateState.Generating(1f), awaitItem())
            generate.close()
            assertEquals(GenerateState.Failed(Phase.Generate, canRetry = false), awaitItem())
            vm.onEvent(GenerateUiEvent.Retry)
            expectNoEvents()
        }
        assertEquals(1, repo.uploads.size)
    }

    @Test
    fun afterFinished_laterSignalsIgnored() = runTest {
        vm.state.test {
            start()
            toGenerating()
            val generate = repo.generates.single().second
            generate.trySend(GenerateEvent.Finished(generated))
            generate.trySend(GenerateEvent.Progress(0.9f))
            generate.close(FakeError())
            assertEquals(GenerateState.Done(generated), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun retry_afterFailure_succeeds() = runTest {
        vm.state.test {
            start()
            failUpload()
            vm.onEvent(GenerateUiEvent.Retry)
            assertEquals(GenerateState.Uploading(0f), awaitItem())
            toDone()
            expectNoEvents()
        }
        assertEquals(2, repo.uploads.size)
    }

    @Test
    fun reset_fromDone_isIdle() = runTest {
        vm.state.test {
            start()
            toDone()
            vm.onEvent(GenerateUiEvent.Reset)
            assertEquals(GenerateState.Idle, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun reset_fromFailed_isIdle() = runTest {
        vm.state.test {
            start()
            failUpload()
            vm.onEvent(GenerateUiEvent.Reset)
            assertEquals(GenerateState.Idle, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun start_whileRunning_isIgnored() = runTest {
        vm.state.test {
            start()
            vm.onEvent(GenerateUiEvent.Start)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repo.uploads.size)
    }

    @Test
    fun retry_whileRunning_isIgnored() = runTest {
        vm.state.test {
            start()
            vm.onEvent(GenerateUiEvent.Retry)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repo.uploads.size)
    }

    @Test
    fun start_fromDoneOrFailed_isIgnored() = runTest {
        vm.state.test {
            start()
            toDone()
            vm.onEvent(GenerateUiEvent.Start)
            expectNoEvents()
            vm.onEvent(GenerateUiEvent.Reset)
            start()
            failUpload()
            vm.onEvent(GenerateUiEvent.Start)
            expectNoEvents()
        }
        assertEquals(2, repo.uploads.size)
    }

    @Test
    fun cancel_fromIdle_isNoOp() = runTest {
        vm.state.test {
            assertEquals(GenerateState.Idle, awaitItem())
            vm.onEvent(GenerateUiEvent.Cancel)
            expectNoEvents()
        }
        assertNull(vm.job)
    }

    @Test
    fun cancel_midUpload_isIdle() = runTest {
        vm.state.test {
            start()
            repo.uploads.single().trySend(0.5f)
            assertEquals(GenerateState.Uploading(0.5f), awaitItem())
            val j = vm.job!!
            vm.onEvent(GenerateUiEvent.Cancel)
            assertEquals(GenerateState.Idle, awaitItem())
            expectNoEvents()
            assertTrue(j.isCancelled)
        }
        assertIs<CancellationException>(repo.completions.single())
    }

    @Test
    fun cancel_midGenerate_isIdle() = runTest {
        vm.state.test {
            start()
            toGenerating()
            repo.generates.single().second.trySend(GenerateEvent.Progress(0.5f))
            assertEquals(GenerateState.Generating(0.5f), awaitItem())
            val j = vm.job!!
            vm.onEvent(GenerateUiEvent.Cancel)
            assertEquals(GenerateState.Idle, awaitItem())
            expectNoEvents()
            assertTrue(j.isCancelled)
        }
        assertIs<CancellationException>(repo.completions.last())
    }

    @Test
    fun foreignCancellation_isRethrown_neverFailed() = runTest {
        vm.state.test {
            start()
            repo.uploads.single().close(CancellationException("x"))
            assertEquals(GenerateState.Idle, awaitItem())
            expectNoEvents()
        }
        assertTrue(vm.job!!.isCancelled)
    }

    @Test
    fun cancelThenStart_staleRunCannotOverwrite() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = GenerateViewModel("src-1", repo)
        vm.onEvent(GenerateUiEvent.Start)
        runCurrent()
        assertEquals(1, repo.uploads.size)
        vm.onEvent(GenerateUiEvent.Cancel)
        vm.onEvent(GenerateUiEvent.Start)
        advanceUntilIdle()
        assertEquals(GenerateState.Uploading(0f), vm.state.value)
        assertEquals(2, repo.uploads.size)
    }
}
