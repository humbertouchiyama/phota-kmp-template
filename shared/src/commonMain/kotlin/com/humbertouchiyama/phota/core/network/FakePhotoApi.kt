package com.humbertouchiyama.phota.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeApiException(message: String = "Simulated failure") : Exception(message)

class FakePhotoApi(var delayMillis: Long = 500, var progressSteps: Int = 10) : PhotoApi {
    var listCalls: Int = 0
        private set
    var getCalls: Int = 0
        private set

    private val photos = (0..29).map { Photo("$it", "Author $it", "https://picsum.photos/id/$it/400/400") }
    private var pendingFailures = 0
    private var pendingError: Throwable = FakeApiException()
    private var generatedCount = 0

    // Replaces any pending arming (spec: failNext).
    fun failNext(times: Int = 1, error: Throwable = FakeApiException()) {
        pendingFailures = times.coerceAtLeast(0)
        pendingError = error
    }

    private fun takeFailure(): Throwable? {
        if (pendingFailures <= 0) return null
        pendingFailures--
        return pendingError
    }

    override suspend fun listPhotos(): List<Photo> {
        listCalls++
        delay(delayMillis)
        takeFailure()?.let { throw it }
        return photos
    }

    override suspend fun getPhoto(id: String): Photo {
        getCalls++
        delay(delayMillis)
        takeFailure()?.let { throw it }
        return photos.firstOrNull { it.id == id } ?: throw FakeApiException("Photo $id not found")
    }

    override fun upload(): Flow<Float> = progress()

    override fun generate(sourceId: String): Flow<GenerateEvent> = flow {
        progress().collect { emit(GenerateEvent.Progress(it)) }
        val k = ++generatedCount
        emit(GenerateEvent.Finished(Photo("gen-$k", "Phota AI", "https://picsum.photos/seed/phota-gen-$k/400/400")))
    }

    private fun progress(): Flow<Float> = flow {
        // Failure is taken per collection (spec: failNext).
        val pending = takeFailure()
        val steps = progressSteps.coerceAtLeast(1)
        for (i in 1..steps) {
            delay(delayMillis / steps)
            emit(i.toFloat() / steps)
            if (pending != null) throw pending
        }
    }
}
