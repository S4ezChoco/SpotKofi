package com.spotkofi.app.data.remote

import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.Track
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Converts iTunes metadata into provider-neutral app models. */
internal object ItunesMapper {

    fun toTrack(result: ItunesResult): Track? {
        val rawTrackId = result.trackId ?: return null
        val title = result.trackName?.trim().orEmpty().takeIf { it.isNotEmpty() } ?: return null
        val artist = result.artistName?.trim().orEmpty().ifBlank { "Unknown artist" }

        return Track(
            id = trackId(rawTrackId),
            title = title,
            artistName = artist,
            albumTitle = result.collectionName?.trim().orEmpty(),
            durationMs = result.trackTimeMillis?.coerceAtLeast(0L) ?: 0L,
            isExplicit = result.trackExplicitness.equals("explicit", ignoreCase = true),
            artworkUrl = artwork(result),
            externalUrl = youtubeSearchUrl(artist, title),
            albumId = result.collectionId?.let(::albumId),
            artistId = result.artistId?.let(::artistId),
        )
    }

    fun toTracks(results: List<ItunesResult>): List<Track> = results
        .asSequence()
        .mapNotNull(::toTrack)
        .distinctBy { it.id }
        .toList()

    fun toAlbum(result: ItunesResult): Album? {
        val collectionId = result.collectionId ?: return null
        val title = result.collectionName?.trim().orEmpty().takeIf { it.isNotEmpty() }
            ?: return null
        val artistName = result.artistName?.trim().orEmpty().ifBlank { "Unknown artist" }

        return Album(
            id = albumId(collectionId),
            title = title,
            artistName = artistName,
            year = result.releaseDate?.take(4)?.toIntOrNull(),
            genre = result.primaryGenreName?.trim()?.takeIf { it.isNotEmpty() },
            trackCount = result.trackCount?.coerceAtLeast(0) ?: 0,
            artworkUrl = artwork(result),
        )
    }

    fun toAlbums(results: List<ItunesResult>): List<Album> = results
        .asSequence()
        .mapNotNull(::toAlbum)
        .distinctBy { it.id }
        .toList()

    fun toArtist(result: ItunesResult): Artist? {
        val id = result.artistId ?: return null
        val name = result.artistName?.trim().orEmpty().takeIf { it.isNotEmpty() } ?: return null
        return Artist(
            id = artistId(id),
            name = name,
            genre = result.primaryGenreName?.trim()?.takeIf { it.isNotEmpty() },
            artworkUrl = artwork(result),
        )
    }

    fun toArtists(results: List<ItunesResult>): List<Artist> = results
        .asSequence()
        .mapNotNull(::toArtist)
        .distinctBy { it.id }
        .toList()

    fun rawId(value: String, prefix: String): Long? = value
        .takeIf { it.startsWith(prefix) }
        ?.removePrefix(prefix)
        ?.toLongOrNull()

    fun trackId(value: Long): String = TRACK_PREFIX + value
    fun albumId(value: Long): String = ALBUM_PREFIX + value
    fun artistId(value: Long): String = ARTIST_PREFIX + value

    const val TRACK_PREFIX = "itunes:track:"
    const val ALBUM_PREFIX = "itunes:album:"
    const val ARTIST_PREFIX = "itunes:artist:"

    private fun youtubeSearchUrl(artist: String, title: String): String =
        "https://www.youtube.com/results?search_query=" +
            URLEncoder.encode("$artist $title", StandardCharsets.UTF_8.name())
                .replace("+", "%20")

    private fun artwork(result: ItunesResult): String? = listOf(
        result.artworkUrl100,
        result.artworkUrl60,
        result.artworkUrl30,
    ).firstOrNull { !it.isNullOrBlank() }?.replace("100x100", "600x600")
}
