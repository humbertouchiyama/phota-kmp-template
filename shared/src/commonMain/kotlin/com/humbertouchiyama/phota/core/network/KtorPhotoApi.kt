package com.humbertouchiyama.phota.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Engine comes from the target's single Ktor engine dependency (spec: Design / Engine).
fun createHttpClient(): HttpClient = HttpClient {
    expectSuccess = true
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

@Serializable
private data class PicsumPhoto(val id: String, val author: String) {
    fun toPhoto() = Photo(id, author, "https://picsum.photos/id/$id/400/400")
}

class KtorPhotoApi(
    private val client: HttpClient,
    private val simulator: PhotoApi = FakePhotoApi(),
) : PhotoApi {
    override suspend fun listPhotos(): List<Photo> =
        client.get("https://picsum.photos/v2/list").body<List<PicsumPhoto>>().map { it.toPhoto() }

    override suspend fun getPhoto(id: String): Photo =
        client.get("https://picsum.photos/id/$id/info").body<PicsumPhoto>().toPhoto()

    // Picsum has no upload or generate (spec: Q4).
    override fun upload(): Flow<Float> = simulator.upload()

    override fun generate(sourceId: String): Flow<GenerateEvent> = simulator.generate(sourceId)
}
