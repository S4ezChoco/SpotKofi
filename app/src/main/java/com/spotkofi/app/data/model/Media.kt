package com.spotkofi.app.data.model

/**
 * Domain models.
 *
 * These are intentionally plain Kotlin with no annotations and no knowledge of
 * where the data came from. When a real music API arrives in a later phase it
 * gets its own DTOs and maps into these, so no UI code has to change.
 */

/** Anything that can be rendered as a card in a shelf or a row in a list. */
sealed interface MediaCollection {
    val id: String

    /** Primary line on a card. */
    val title: String

    /** Secondary line on a card. */
    val subtitle: String

    /**
     * Remote cover art, or null when the catalog has none.
     *
     * On the interface rather than only on the subtypes so a shelf can render a
     * mixed list without downcasting. Every renderer needs it, and leaving it off
     * meant call sites silently fell back to a gradient for real records.
     */
    val artworkUrl: String?
}

data class Artist(
    override val id: String,
    val name: String,
    /**
     * Primary genre, when the catalog reports one.
     *
     * Replaced a `monthlyListeners` count that the catalog API does not expose;
     * keeping it would have meant printing an invented number on screen.
     */
    val genre: String? = null,
    override val artworkUrl: String? = null,
) : MediaCollection {
    override val title: String get() = name
    override val subtitle: String get() = genre ?: "Artist"
}

data class Album(
    override val id: String,
    override val title: String,
    val artistName: String,
    /** Null when the catalog omits a release date. */
    val year: Int? = null,
    val genre: String? = null,
    val trackCount: Int = 0,
    override val artworkUrl: String? = null,
) : MediaCollection {
    override val subtitle: String get() = artistName
}

/**
 * A user-created playlist stored in the local library.
 *
 * The remote catalog has no user playlists, so these records are created by the
 * local store and mapped back into the same collection model used by the UI.
 */
data class Playlist(
    override val id: String,
    override val title: String,
    val description: String,
    val ownerName: String,
    val trackIds: List<String> = emptyList(),
    override val artworkUrl: String? = null,
    /** Pinned entries sort to the top of Your Library and show a green pin. */
    val isPinned: Boolean = false,
) : MediaCollection {
    override val subtitle: String get() = description
}

/** A catalog track whose official provider page is opened outside the app. */

data class Track(
    val id: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val durationMs: Long,
    val isExplicit: Boolean = false,
    val artworkUrl: String? = null,
    /** Direct audio preview URL, when the catalog supplies one. */
    val audioUrl: String? = null,
    /** Official provider page/search URL opened outside the app. */
    val externalUrl: String? = null,
    /** YouTube video ID for streaming, when available. */
    val videoId: String? = null,
    /** Album the track belongs to, so a row can navigate to its album. */
    val albumId: String? = null,
    /** Catalog id of the performer, so an artist page can be opened from a track. */
    val artistId: String? = null,
) {
    val isExternallyOpenable: Boolean get() = !externalUrl.isNullOrBlank()
}

/** A titled, horizontally scrolling row on the Home screen. */
data class Shelf(
    val id: String,
    val title: String,
    val items: List<MediaCollection>,
)

/** A coloured tile in the Search browse grid. */
data class BrowseCategory(
    val id: String,
    val name: String,
)

/**
 * A genre radio tile. Rendered as a coloured panel with a RADIO badge and
 * overlapping circular artwork, so it needs its own type rather than reusing
 * [MediaCollection].
 *
 * Built from a genre plus the artists the catalog actually returned for it, so
 * the caption names real artists rather than invented ones.
 */
data class Station(
    val id: String,
    val name: String,
    /** Comma separated artists, shown as a caption under the tile. */
    val seedArtists: String,
    val artworkUrl: String? = null,
)

/** A card in the Following feed's release list. */
data class ReleaseItem(
    val id: String,
    val artistName: String,
    val title: String,
    /** Release year, or empty when the catalog omits it. */
    val releasedLabel: String,
    val songCount: Int,
    val artworkUrl: String? = null,
)

/** One avatar in the friend-activity strip inside the profile drawer. */
data class FriendActivity(
    val id: String,
    val name: String,
    /** What they are listening to, or null when idle. */
    val nowPlaying: String?,
    val isOnline: Boolean = false,
)

/**
 * A direct-message thread in the profile drawer.
 *
 * Phase 1 renders these from mock data. Phase 4 replaces the source with a
 * Supabase Realtime subscription, gated by RLS so a thread only exists between
 * accounts that follow each other.
 */
data class Conversation(
    val id: String,
    val personName: String,
    val preview: String,
    val timestamp: String,
    /** True when the preview is a shared track rather than plain text. */
    val isSharedTrack: Boolean = false,
)

/*
 * ---------------------------------------------------------------------------
 * Explore
 *
 * Charts, moods and genres come from the provider's own browse feeds, so these
 * types mirror what those feeds actually return. Nothing here is synthesised: a
 * shelf keeps the heading the provider gave it, and a rank is the position in the
 * list the provider ordered.
 * ---------------------------------------------------------------------------
 */

/** A country the provider publishes a chart for. */
data class ChartRegion(
    val code: String,
    val name: String,
)

/**
 * The chart page for one region.
 *
 * Songs and artists are kept apart from the playlist shelves because they are
 * ranked lists, and a rank is only meaningful inside its own list.
 */
data class MusicChart(
    val region: String,
    val topSongs: List<Track> = emptyList(),
    val topArtists: List<Artist> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
) {
    /** One titled carousel of playlists or albums, as the provider grouped it. */
    data class Shelf(
        val title: String,
        val items: List<MediaCollection>,
    )

    val isEmpty: Boolean
        get() = topSongs.isEmpty() && topArtists.isEmpty() && shelves.isEmpty()
}

/**
 * One mood or genre tile.
 *
 * [params] is the opaque token the provider needs to open the category; it is
 * carried through the UI untouched because nothing else can reconstruct it.
 */
data class MoodCategory(
    val title: String,
    val params: String,
    /** Stripe colour supplied by the provider, or null when it sent none. */
    val colorArgb: Long? = null,
)

/** A titled group of mood or genre tiles, e.g. "Moods & moments". */
data class MoodGroup(
    val title: String,
    val items: List<MoodCategory>,
)

/** A card in the Search screen's Explore shelves. */
data class ExploreItem(
    val id: String,
    val title: String,
    val caption: String? = null,
    val artworkUrl: String? = null,
    /** Optional playable track behind an explore tile. */
    val track: Track? = null,
)

/*
 * ---------------------------------------------------------------------------
 * Now Playing page content
 *
 * Now Playing is a scrolling page, not just a transport panel.
 *
 * An earlier version of this modelled lyrics, an artist biography, contributor
 * credits and podcast tie-ins. None of that exists in the catalog API, and the
 * only way to render those sections was to fabricate the content. They are gone.
 * What remains is what can actually be fetched: the rest of the album, and other
 * work by the same artist.
 * ---------------------------------------------------------------------------
 */
data class TrackDetails(
    /** Small label above the artwork explaining where the track came from. */
    val contextLabel: String,
    /** Primary genre returned by optional Spotify artist enrichment. */
    val artistGenre: String? = null,
    /** Remaining tracks on the same album. */
    val albumTracks: List<Track> = emptyList(),
    /** Other tracks by the same artist. */
    val moreByArtist: List<Track> = emptyList(),
    /** Other albums by the same artist. */
    val artistAlbums: List<Album> = emptyList(),
    /** Spotify recommendations resolved back to iTunes metadata. */
    val recommendations: List<Track> = emptyList(),
    /**
     * Lyrics for this recording, when a provider has them.
     *
     * Null means "not available", and the UI says exactly that. Nothing is
     * generated locally to fill the gap.
     */
    val lyrics: TrackLyrics? = null,
    /**
     * Publisher information for this recording, when the provider had any.
     *
     * Best-effort: it is a second request that is allowed to fail without costing
     * the rest of the page.
     */
    val credits: TrackCredits? = null,
)

/**
 * What the provider publishes about a recording.
 *
 * Deliberately short. Songwriter, producer and label are not here because no
 * source in this app supplies them, and a credits panel that fills those rows with
 * guesses would be stating things about real people that nobody verified.
 */
data class TrackCredits(
    /** The channel that published the recording. */
    val channelName: String? = null,
    val plays: Long? = null,
    /** ISO date, as published. */
    val publishedOn: String? = null,
    val description: String? = null,
) {
    val hasAny: Boolean
        get() = channelName != null || plays != null ||
            publishedOn != null || !description.isNullOrBlank()
}

/**
 * Lyrics as returned by the provider, in whichever forms it had.
 *
 * [synced] carries `[mm:ss.xx]` stamps and is what a follow-along view needs;
 * [plain] is the fallback. [instrumental] is a positive statement from the
 * provider that the recording has no vocals, which is different from having no
 * lyrics on file.
 */
data class TrackLyrics(
    val plain: String? = null,
    val synced: String? = null,
    val instrumental: Boolean = false,
) {
    val hasText: Boolean get() = !plain.isNullOrBlank() || !synced.isNullOrBlank()
}

/** The filter chips across the top of Home. */
enum class HomeTab(val label: String) {
    All("All"),
    Music("Music"),
    Podcasts("Podcasts"),
}

/**
 * One block on the Home screen.
 *
 * Home is not a uniform list of card shelves: it mixes radio tiles, a large
 * promo, and full-width release cards. Modelling that as a sealed type keeps the
 * renderer honest, because adding a new block shape forces a new branch instead
 * of being crammed into a generic shelf.
 */
sealed interface HomeSection {
    val id: String
    val title: String

    /** Horizontally scrolling square media cards. */
    data class Cards(
        override val id: String,
        override val title: String,
        val items: List<MediaCollection>,
    ) : HomeSection

    /** Coloured radio tiles with a RADIO badge. */
    data class Stations(
        override val id: String,
        override val title: String,
        val items: List<Station>,
    ) : HomeSection

    /** A single large highlighted item, used for pre-save promos. */
    data class Spotlight(
        override val id: String,
        override val title: String,
        val item: MediaCollection,
    ) : HomeSection

    /** Full-width release cards, as used by the Following feed. */
    data class Releases(
        override val id: String,
        override val title: String,
        val items: List<ReleaseItem>,
    ) : HomeSection

    /**
     * Directly playable song rows, the way the reference app surfaces its
     * recommendations.
     *
     * Home previously held nothing but albums and artists, so every tap was a
     * navigation and the only way to reach a song was through a detail screen.
     * These carry real tracks, so a recommendation can be played from Home.
     */
    data class Songs(
        override val id: String,
        override val title: String,
        val items: List<Track>,
    ) : HomeSection
}

data class SearchResults(
    val tracks: List<Track> = emptyList(),
    val collections: List<MediaCollection> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && collections.isEmpty()
}

enum class RepeatMode { Off, All, One }

/**
 * Everything the player UI needs to render.
 *
 * Phase 1 drives this from an in-memory fake so the Now Playing screen and mini
 * player can be built and reviewed. Runtime catalog data now comes from iTunes,
 * with optional Spotify enrichment and official external playback handoff.
 */
data class PlaybackState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val isShuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val isSaved: Boolean = false,
    /**
     * Remote playback target, or null when playing on this device. Drives the
     * green device row in the mini player.
     */
    val deviceName: String? = null,

    /**
     * Length of the actual full stream, as reported by the player.
     *
     * Distinct from `track.durationMs` when the provider's catalog duration is
     * rounded or otherwise differs from the decoded media duration.
     */
    val streamDurationMs: Long? = null,

    /** Set when playback failed or the track has no stream. Null when fine. */
    val error: String? = null,

    /**
     * Counts explicit user play requests, and nothing else.
     *
     * Increments when someone taps a track; it does NOT change when the queue
     * advances on its own or when next/previous is pressed. That distinction is
     * what lets the host open Now Playing on a real selection while leaving the
     * player collapsed when a finished song simply rolls into the next one.
     */
    val playRequestId: Long = 0L,
) {
    val hasTrack: Boolean get() = track != null

    /** The duration the scrubber and timestamps should use. */
    val effectiveDurationMs: Long
        get() = streamDurationMs ?: track?.durationMs ?: 0L

    /** 0f..1f, safe when there is no track or a zero-length clip. */
    val progress: Float
        get() {
            val duration = effectiveDurationMs
            if (duration <= 0L) return 0f
            return (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        }

    /** Time left in the current track, for the "when does this end" readout. */
    val remainingMs: Long
        get() = (effectiveDurationMs - positionMs).coerceAtLeast(0L)
}

/** Formats a duration as m:ss, the format used in track lists. */
fun Long.asTrackDuration(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
