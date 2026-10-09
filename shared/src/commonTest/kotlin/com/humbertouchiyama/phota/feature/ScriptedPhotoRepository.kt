package com.humbertouchiyama.phota.feature

import com.humbertouchiyama.phota.core.data.PhotoRepository
import com.humbertouchiyama.phota.core.network.GenerateEvent
import com.humbertouchiyama.phota.core.network.Photo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion

fun testPhoto(id: String) = Photo(id, "author-$id", "https://example.test/$id.jpg")

class FakeError : Exception("boom")

// Each upload/generate call gets a channel the test drives: trySend emits, close completes or fails.
class ScriptedPhotoRepository : PhotoRepository {
    var photoResult: suspend (String) -> Result<Photo> = { Result.success(testPhoto(it)) }
    val photoRequests = mutableListOf<String>()
    val uploads = mutableListOf<Channel<Float>>()
    val generates = mutableListOf<Pair<String, Channel<GenerateEvent>>>()
    val completions = mutableListOf<Throwable?>()

    override suspend fun photos(forceRefresh: Boolean): Result<List<Photo>> = Result.success(emptyList())

    override suspend fun photo(id: String): Result<Photo> {
        photoRequests += id
        return photoResult(id)
    }

    override fun upload(): Flow<Float> {
        val channel = Channel<Float>(Channel.UNLIMITED)
        uploads += channel
        return channel.consumeAsFlow().onCompletion { completions += it }
    }

    override fun generate(sourceId: String): Flow<GenerateEvent> {
        val channel = Channel<GenerateEvent>(Channel.UNLIMITED)
        generates += sourceId to channel
        return channel.consumeAsFlow().onCompletion { completions += it }
    }
}
