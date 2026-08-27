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
}

data class Artist(
    override val id: String,
    val name: String,
    val monthlyListeners: Int = 0,
    val artworkUrl: String? = null,
) : MediaCollection {
    override val title: String get() = name
    override val subtitle: String get() = "Artist"
}

data class Album(
    override val id: String,
    override val title: String,
    val artistName: String,
    val year: Int,
    val trackIds: List<String> = emptyList(),
    val artworkUrl: String? = null,
) : MediaCollection {
    override val subtitle: String get() = artistName
}

data class Playlist(
    override val id: String,
    override val title: String,
    val description: String,
    val ownerName: String,
    val trackIds: List<String> = emptyList(),
    val saves: Int = 0,
    val artworkUrl: String? = null,
    /** Pinned entries sort to the top of Your Library and show a green pin. */
    val isPinned: Boolean = false,
) : MediaCollection {
    override val subtitle: String get() = description
}

data class Track(
    val id: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val durationMs: Long,
    val isExplicit: Boolean = false,
    val artworkUrl: String? = null,
)

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
 * An algorithmic radio card. Rendered as a coloured tile with a RADIO badge and
 * overlapping circular artist images, so it needs its own type rather than
 * reusing [MediaCollection].
 */
data class Station(
    val id: String,
    val name: String,
    /** Comma separated seed artists, shown as a caption under the tile. */
    val seedArtists: String,
)

/** A card in the Following feed's "Latest releases" list. */
data class ReleaseItem(
    val id: String,
    val artistName: String,
    val title: String,
    val postedAgo: String,
    val songCount: Int,
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

/** A card in the Search screen's Explore shelves. */
data class ExploreItem(
    val id: String,
    val title: String,
    val caption: String? = null,
)

/*
 * ---------------------------------------------------------------------------
 * Now Playing page content
 *
 * Now Playing is a scrolling page, not just a transport panel: below the
 * controls sit lyrics, artist context, contributor credits and related content.
 * Those are modelled here so the screen can be built before any real metadata
 * provider exists.
 * ---------------------------------------------------------------------------
 */

/** One line of lyrics. Instrumental breaks render as a note glyph, not text. */
data class LyricLine(
    val text: String,
    val isInstrumental: Boolean = false,
)

data class Lyrics(
    val lines: List<LyricLine>,
    /** Index into [lines] that is currently being sung. */
    val activeIndex: Int,
)

/** A podcast episode row in the "Discover more about" card. */
data class EpisodeItem(
    val id: String,
    val title: String,
    val subtitle: String,
)

/** A person credited on the track, shown in the SongDNA card. */
data class Contributor(
    val id: String,
    val name: String,
    val role: String,
)

/** A tile in the "Explore" row at the bottom of Now Playing. */
data class ExploreCard(
    val id: String,
    val title: String,
)

data class Credit(
    val name: String,
    val role: String,
)

/** Everything the Now Playing page renders below the transport controls. */
data class TrackDetails(
    /** Small label above the artwork, e.g. why this track is playing. */
    val contextLabel: String,
    val lyrics: Lyrics,
    val artistBio: String,
    val episodes: List<EpisodeItem>,
    val contributors: List<Contributor>,
    val exploreCards: List<ExploreCard>,
    val credits: List<Credit>,
)

/** The filter chips across the top of Home. */
enum class HomeTab(val label: String) {
    All("All"),
    Music("Music"),
    Following("Following"),
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
 * player can be built and reviewed. Phase 5 swaps the source for Media3 without
 * changing this shape.
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
     * green device row in the mini player. Becomes a real Spotify-Connect style
     * device in a later phase.
     */
    val deviceName: String? = null,
) {
    val hasTrack: Boolean get() = track != null

    /** 0f..1f, safe when there is no track or a zero-length track. */
    val progress: Float
        get() {
            val duration = track?.durationMs ?: return 0f
            if (duration <= 0L) return 0f
            return (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        }
}

/** Formats a duration as m:ss, the format used in track lists. */
fun Long.asTrackDuration(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
