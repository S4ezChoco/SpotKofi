package com.spotkofi.app.data.remote

import kotlinx.serialization.Serializable

/** Raw iTunes Search API payloads kept inside the data layer. */
@Serializable
internal data class ItunesResponse(
    val resultCount: Int = 0,
    val results: List<ItunesResult> = emptyList(),
)

@Serializable
internal data class ItunesResult(
    val wrapperType: String? = null,
    val kind: String? = null,
    val artistId: Long? = null,
    val collectionId: Long? = null,
    val trackId: Long? = null,
    val artistName: String? = null,
    val collectionName: String? = null,
    val trackName: String? = null,
    val trackTimeMillis: Long? = null,
    val artworkUrl30: String? = null,
    val artworkUrl60: String? = null,
    val artworkUrl100: String? = null,
    val previewUrl: String? = null,
    val primaryGenreName: String? = null,
    val releaseDate: String? = null,
    val trackCount: Int? = null,
    val trackExplicitness: String? = null,
    val collectionExplicitness: String? = null,
)
