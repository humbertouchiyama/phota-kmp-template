package com.humbertouchiyama.phota.core.network

import kotlinx.serialization.Serializable

@Serializable
data class Photo(val id: String, val author: String, val imageUrl: String)

// Closed at Progress + Finished (spec: Additive).
sealed interface GenerateEvent {
    data class Progress(val fraction: Float) : GenerateEvent
    data class Finished(val photo: Photo) : GenerateEvent
}
