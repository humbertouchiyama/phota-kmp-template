package com.humbertouchiyama.phota.core.network

import kotlinx.coroutines.flow.Flow

// Suspend funs throw on failure; flows emit progress in (0f, 1f] and throw on failure.
interface PhotoApi {
    suspend fun listPhotos(): List<Photo>
    suspend fun getPhoto(id: String): Photo
    fun upload(): Flow<Float>
    fun generate(sourceId: String): Flow<GenerateEvent>
}
