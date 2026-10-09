package com.humbertouchiyama.phota.core.data

import com.humbertouchiyama.phota.core.network.GenerateEvent
import com.humbertouchiyama.phota.core.network.Photo
import com.humbertouchiyama.phota.core.network.PhotoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PhotoRepository {
    suspend fun photos(forceRefresh: Boolean = false): Result<List<Photo>>
    suspend fun photo(id: String): Result<Photo>
    fun upload(): Flow<Float>
    fun generate(sourceId: String): Flow<GenerateEvent>
}

// Rethrows cancellation, wraps other failures (spec: Design / Result helper).
internal suspend inline fun <T> resultOf(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

class PhotoRepositoryImpl(private val api: PhotoApi) : PhotoRepository {
    private val mutex = Mutex()
    private var remote: List<Photo>? = null
    private var generated: List<Photo> = emptyList()

    override suspend fun photos(forceRefresh: Boolean): Result<List<Photo>> = resultOf {
        val cached = mutex.withLock { remote }
        // API call stays outside the lock (spec: Mutex scope).
        val list = if (cached == null || forceRefresh) {
            api.listPhotos().also { fresh -> mutex.withLock { remote = fresh } }
        } else {
            cached
        }
        mutex.withLock { (generated + list).distinctBy { it.id } }
    }

    override suspend fun photo(id: String): Result<Photo> = resultOf {
        val cached = mutex.withLock { (generated + remote.orEmpty()).firstOrNull { it.id == id } }
        cached ?: api.getPhoto(id)
    }

    override fun upload(): Flow<Float> = api.upload()

    // onEach caches Finished before the collector sees it (spec: Design / Cache).
    override fun generate(sourceId: String): Flow<GenerateEvent> = api.generate(sourceId).onEach { event ->
        if (event is GenerateEvent.Finished) mutex.withLock { generated = listOf(event.photo) + generated }
    }
}
