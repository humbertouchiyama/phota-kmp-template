package com.humbertouchiyama.phota.core.data

import app.cash.turbine.test
import com.humbertouchiyama.phota.core.network.FakeApiException
import com.humbertouchiyama.phota.core.network.FakePhotoApi
import com.humbertouchiyama.phota.core.network.GenerateEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoRepositoryTest {

    @Test
    fun photosSuccess() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        val list = repo.photos().getOrThrow()
        assertEquals(30, list.size)
        assertEquals("0", list.first().id)
    }

    @Test
    fun photosFailure() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        api.failNext()
        assertIs<FakeApiException>(repo.photos().exceptionOrNull())
    }

    @Test
    fun failNextTwice() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        api.failNext(times = 2)
        assertTrue(repo.photos().isFailure)
        assertTrue(repo.photos().isFailure)
        assertTrue(repo.photos().isSuccess)
        assertEquals(3, api.listCalls)
    }

    @Test
    fun photosCacheHit() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        val first = repo.photos().getOrThrow()
        val second = repo.photos().getOrThrow()
        assertEquals(1, api.listCalls)
        assertEquals(first, second)
    }

    @Test
    fun forceRefreshRefetches() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        repo.photos()
        repo.photos(forceRefresh = true)
        assertEquals(2, api.listCalls)
    }

    @Test
    fun failedRefreshKeepsCache() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        repo.photos().getOrThrow()
        api.failNext()
        assertTrue(repo.photos(true).isFailure)
        assertEquals(30, repo.photos().getOrThrow().size)
        assertEquals(2, api.listCalls)
    }

    @Test
    fun generatePrependsToCache() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        repo.photos().getOrThrow()
        repo.generate("3").test {
            skipItems(api.progressSteps)
            assertIs<GenerateEvent.Finished>(awaitItem())
            awaitComplete()
        }
        val list = repo.photos().getOrThrow()
        assertEquals("gen-1", list.first().id)
        assertEquals(31, list.size)
        assertEquals(1, api.listCalls)
    }

    @Test
    fun generateBeforeFirstLoadStillFetches() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        repo.generate("3").collect {}
        val list = repo.photos().getOrThrow()
        assertEquals(31, list.size)
        assertEquals("gen-1", list.first().id)
        assertEquals(1, api.listCalls)
    }

    @Test
    fun generateFailureDoesNotTouchCache() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        repo.photos().getOrThrow()
        api.failNext()
        repo.generate("3").test {
            awaitItem()
            assertIs<FakeApiException>(awaitError())
        }
        assertEquals(30, repo.photos().getOrThrow().size)
    }

    @Test
    fun generateCancelledMidwayWritesNothing() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        repo.photos().getOrThrow()
        val j = launch { repo.generate("3").collect {} }
        advanceTimeBy(api.progressMillis / 2)
        j.cancel()
        advanceUntilIdle()
        assertTrue(j.isCancelled)
        assertEquals(30, repo.photos().getOrThrow().size)
    }

    @Test
    fun finishedThenCancelKeepsPhoto() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        repo.generate("3").first { it is GenerateEvent.Finished }
        assertEquals("gen-1", repo.photos().getOrThrow().first().id)
    }

    @Test
    fun photoServesFromCache() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        repo.photos().getOrThrow()
        assertEquals("5", repo.photo("5").getOrThrow().id)
        assertEquals(0, api.getCalls)
    }

    @Test
    fun photoServesGeneratedFromCache() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        repo.generate("3").collect {}
        assertEquals("gen-1", repo.photo("gen-1").getOrThrow().id)
        assertEquals(0, api.getCalls)
    }

    @Test
    fun photoFallsBackToApi() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        assertEquals("7", repo.photo("7").getOrThrow().id)
        assertEquals(1, api.getCalls)
    }

    @Test
    fun photoUnknownIdFails() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        assertIs<FakeApiException>(repo.photo("999").exceptionOrNull())
    }

    @Test
    fun uploadPassesThrough() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        val values = repo.upload().toList()
        assertEquals(10, values.size)
        assertEquals(1f, values.last())
    }

    @Test
    fun uploadCancelledCompletes() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        val j = launch { repo.upload().collect {} }
        advanceTimeBy(api.progressMillis / 2)
        j.cancel()
        advanceUntilIdle()
        assertTrue(j.isCancelled)
        assertTrue(j.isCompleted)
        assertEquals(30, repo.photos().getOrThrow().size)
    }

    @Test
    fun apiCancellationIsRethrown() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        api.failNext(error = CancellationException("x"))
        assertFailsWith<CancellationException> { repo.photos() }
        api.failNext(error = CancellationException("y"))
        assertFailsWith<CancellationException> { repo.photo("1") }
    }

    @Test
    fun cancelledCallerGetsNoResult() = runTest {
        val api = FakePhotoApi(); val repo = PhotoRepositoryImpl(api)
        var r: Result<*>? = null
        val j = launch { r = repo.photos() }
        runCurrent()
        j.cancel()
        advanceUntilIdle()
        assertTrue(j.isCancelled)
        assertNull(r)
    }
}
