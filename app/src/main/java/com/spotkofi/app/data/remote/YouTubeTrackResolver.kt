package com.spotkofi.app.data.remote

import android.util.Log
import com.spotkofi.app.data.model.Track
import kotlinx.coroutines.CancellationException

/**
 * Finds the YouTube video that corresponds to a catalog track.
 *
 * Search results already arrive with their own video ID, but album, artist and
 * recommendation rows come from the iTunes catalog, which has no notion of a
 * YouTube video. Resolving lazily here, at the moment a track is actually
 * played, is what lets those rows play full-length audio without paying for
 * fifty network lookups every time an album screen opens.
 *
 * The match is scored rather than taken from the top hit: a bare title search
 * happily returns live versions, covers and reactions, and picking the first of
 * those is how a track ends up playing something the user did not choose.
 */
internal class YouTubeTrackResolver(
    private val searchClient: YouTubeMusicSearchClient = YouTubeMusicSearchClient(),
) {

    /** Resolved ids are stable, so they are worth keeping for the session. */
    private val cache = linkedMapOf<String, String>()

    /**
     * Returns [Track.videoId] when the track already carries one, otherwise the
     * best-matching YouTube video ID, or null when nothing matches well enough.
     */
    suspend fun resolveVideoId(track: Track): String? {
        track.videoId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        val key = cacheKey(track)
        cache[key]?.let { return it }

        val query = listOf(track.artistName, track.title)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (query.isEmpty()) return null

        val candidates = try {
            searchClient.searchSongs(query, limit = CANDIDATE_LIMIT)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "YouTube lookup failed for \"$query\"", error)
            return null
        }
        if (candidates.isEmpty()) return null

        val scored = candidates
            .mapNotNull { candidate ->
                val videoId = candidate.videoId?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                Triple(candidate, videoId, score(candidate, track))
            }
            .filter { (_, _, value) -> value >= MIN_SCORE }
            .maxByOrNull { (_, _, value) -> value }
            ?: return null

        return scored.second.also { videoId -> store(key, videoId) }
    }

    /**
     * Higher is better. Title agreement dominates, because a wrong title is a
     * wrong song, while a missing album or an off-by-a-second duration is not.
     */
    private fun score(candidate: Track, track: Track): Int {
        val candidateTitle = normalize(candidate.title)
        val wantedTitle = normalize(track.title)
        val candidateArtist = normalize(candidate.artistName)
        val wantedArtist = normalize(track.artistName)

        var total = when {
            candidateTitle == wantedTitle -> 60
            candidateTitle.contains(wantedTitle) || wantedTitle.contains(candidateTitle) -> 34
            else -> 0
        }

        total += when {
            candidateArtist == wantedArtist -> 30
            candidateArtist.contains(wantedArtist) || wantedArtist.contains(candidateArtist) -> 18
            else -> 0
        }

        // The catalog duration is a strong tiebreaker: a studio recording and its
        // extended live rendition rarely land within a few seconds of each other.
        if (track.durationMs > 0L && candidate.durationMs > 0L) {
            val deltaSeconds = Math.abs(track.durationMs - candidate.durationMs) / 1000L
            total += when {
                deltaSeconds <= 2L -> 24
                deltaSeconds <= 5L -> 16
                deltaSeconds <= 12L -> 6
                deltaSeconds >= 45L -> -20
                else -> 0
            }
        }

        // Explicit penalties for the variants that are technically the same song
        // but are not what a listener tapping an album track expects.
        if (UNWANTED_VARIANTS.any { marker -> candidateTitle.contains(marker) } &&
            !wantedTitle.let { wanted -> UNWANTED_VARIANTS.any(wanted::contains) }
        ) {
            total -= 26
        }

        return total
    }

    private fun store(key: String, videoId: String) {
        cache.remove(key)
        cache[key] = videoId
        while (cache.size > MAX_CACHED) {
            cache.remove(cache.keys.first())
        }
    }

    private fun cacheKey(track: Track): String =
        normalize(track.artistName) + "|" + normalize(track.title)

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private companion object {
        const val TAG = "SpotKofiResolver"
        const val CANDIDATE_LIMIT = 8
        const val MAX_CACHED = 128

        /** Below this, the best hit is too weak to call it the same recording. */
        const val MIN_SCORE = 34

        val UNWANTED_VARIANTS = listOf(
            "live", "cover", "remix", "karaoke", "instrumental",
            "reaction", "sped up", "slowed", "nightcore", "8d",
        )
    }
}
