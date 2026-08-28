package com.spotkofi.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Raw Spotify Web API payloads kept inside the data layer. */
@Serializable
internal data class SpotifySearchResponse(
    val tracks: SpotifyPage<SpotifyTrack> = SpotifyPage(),
)

@Serializable
internal data class SpotifyPage<T>(
    val items: List<T> = emptyList(),
)

@Serializable
internal data class SpotifyTrack(
    val id: String = "",
    val name: String = "",
    @SerialName("duration_ms") val durationMs: Long = 0L,
    val explicit: Boolean = false,
    val artists: List<SpotifySimpleArtist> = emptyList(),
    val album: SpotifyAlbum = SpotifyAlbum(),
)

@Serializable
internal data class SpotifySimpleArtist(
    val id: String = "",
    val name: String = "",
)

@Serializable
internal data class SpotifyAlbum(
    val id: String = "",
    val name: String = "",
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("total_tracks") val totalTracks: Int = 0,
    val artists: List<SpotifySimpleArtist> = emptyList(),
    val images: List<SpotifyImage> = emptyList(),
)

@Serializable
internal data class SpotifyArtist(
    val id: String = "",
    val name: String = "",
    val genres: List<String> = emptyList(),
    val images: List<SpotifyImage> = emptyList(),
)

@Serializable
internal data class SpotifyImage(
    val url: String = "",
)

@Serializable
internal data class SpotifyRecommendationsResponse(
    val tracks: List<SpotifyTrack> = emptyList(),
)
