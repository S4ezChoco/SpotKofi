package com.spotkofi.app.data.repository

import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.BrowseCategory
import com.spotkofi.app.data.model.Contributor
import com.spotkofi.app.data.model.Conversation
import com.spotkofi.app.data.model.Credit
import com.spotkofi.app.data.model.EpisodeItem
import com.spotkofi.app.data.model.ExploreCard
import com.spotkofi.app.data.model.ExploreItem
import com.spotkofi.app.data.model.FriendActivity
import com.spotkofi.app.data.model.LyricLine
import com.spotkofi.app.data.model.Lyrics
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.Playlist
import com.spotkofi.app.data.model.ReleaseItem
import com.spotkofi.app.data.model.Station
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.TrackDetails

/**
 * Placeholder catalog for the UI-first phase.
 *
 * Every artist, album and song here is invented. The point is to exercise the
 * layouts with realistic text lengths, including some deliberately long titles
 * that must ellipsize, and to keep the shelf/grid/list densities honest.
 *
 * No `artworkUrl` is set anywhere: artwork falls back to a deterministic
 * gradient derived from the item id, so the whole app renders offline and inside
 * `@Preview`.
 */
internal object MockData {

    // ---------------------------------------------------------------- artists
    val artists: List<Artist> = listOf(
        Artist("ar_mira", "Mira Solano", monthlyListeners = 1_284_902),
        Artist("ar_neon", "Neon Manila", monthlyListeners = 842_115),
        Artist("ar_kalye", "Kalye Kolektib", monthlyListeners = 613_770),
        Artist("ar_bagyo", "Bagyo", monthlyListeners = 2_051_338),
        Artist("ar_halo", "Halohalo Sessions", monthlyListeners = 318_204),
        Artist("ar_lofi", "Lo-Fi Kapatid", monthlyListeners = 1_907_665),
        Artist("ar_alon", "Alon", monthlyListeners = 455_012),
        Artist("ar_tres", "Tres Marias", monthlyListeners = 729_488),
    )

    // ----------------------------------------------------------------- albums
    val albums: List<Album> = listOf(
        Album("al_umaga", "Umaga", "Mira Solano", 2025),
        Album("al_neon", "Neon Manila", "Neon Manila", 2024),
        Album("al_kalye", "Kalye, Vol. 1", "Kalye Kolektib", 2025),
        Album("al_bagyo", "Bagyo", "Bagyo", 2023),
        Album("al_halo", "Halohalo", "Halohalo Sessions", 2026),
        Album("al_kape", "Kape at Ulan", "Lo-Fi Kapatid", 2025),
        Album("al_alon", "Alon", "Alon", 2024),
        Album("al_tres", "Tres", "Tres Marias", 2026),
    )

    // ----------------------------------------------------------------- tracks
    val tracks: List<Track> = listOf(
        // Umaga
        Track("tr_01", "Umaga", "Mira Solano", "Umaga", 214_000),
        Track("tr_02", "Hindi Ko Alam Kung Paano Sabihin", "Mira Solano", "Umaga", 251_000),
        Track("tr_03", "Sinag", "Mira Solano", "Umaga", 188_000),
        Track("tr_04", "Tahimik na Umaga sa Kalsada", "Mira Solano", "Umaga", 232_000),

        // Neon Manila
        Track("tr_05", "Neon Manila", "Neon Manila", "Neon Manila", 201_000),
        Track("tr_06", "EDSA Southbound", "Neon Manila", "Neon Manila", 245_000, isExplicit = true),
        Track("tr_07", "Alas Dos", "Neon Manila", "Neon Manila", 176_000),
        Track("tr_08", "Palengke Lights", "Neon Manila", "Neon Manila", 223_000),

        // Kalye, Vol. 1
        Track("tr_09", "Kalye", "Kalye Kolektib", "Kalye, Vol. 1", 196_000, isExplicit = true),
        Track("tr_10", "Tambay", "Kalye Kolektib", "Kalye, Vol. 1", 208_000, isExplicit = true),
        Track("tr_11", "Bakod", "Kalye Kolektib", "Kalye, Vol. 1", 172_000),

        // Bagyo
        Track("tr_12", "Signal No. 5", "Bagyo", "Bagyo", 289_000),
        Track("tr_13", "Habagat", "Bagyo", "Bagyo", 254_000),
        Track("tr_14", "Walang Tigil", "Bagyo", "Bagyo", 231_000, isExplicit = true),

        // Halohalo
        Track("tr_15", "Halohalo", "Halohalo Sessions", "Halohalo", 183_000),
        Track("tr_16", "Leche Flan", "Halohalo Sessions", "Halohalo", 165_000),
        Track("tr_17", "Sago at Gulaman", "Halohalo Sessions", "Halohalo", 199_000),

        // Kape at Ulan
        Track("tr_18", "Kape at Ulan", "Lo-Fi Kapatid", "Kape at Ulan", 142_000),
        Track("tr_19", "3AM Study Loop", "Lo-Fi Kapatid", "Kape at Ulan", 156_000),
        Track("tr_20", "Tulog Na", "Lo-Fi Kapatid", "Kape at Ulan", 138_000),
        Track("tr_21", "Malamig na Hangin", "Lo-Fi Kapatid", "Kape at Ulan", 171_000),

        // Alon
        Track("tr_22", "Alon", "Alon", "Alon", 227_000),
        Track("tr_23", "Buhangin", "Alon", "Alon", 205_000),
        Track("tr_24", "Dagat sa Gabi", "Alon", "Alon", 262_000),

        // Tres
        Track("tr_25", "Tres", "Tres Marias", "Tres", 194_000),
        Track("tr_26", "Salamat sa Wala", "Tres Marias", "Tres", 218_000),
        Track("tr_27", "Huling Sayaw", "Tres Marias", "Tres", 243_000),
    )

    private val tracksByAlbum: Map<String, List<Track>> = tracks.groupBy { it.albumTitle }

    // -------------------------------------------------------------- playlists
    val playlists: List<Playlist> = listOf(
        Playlist(
            id = "pl_liked",
            title = "Liked Songs",
            description = "Your saved tracks",
            ownerName = "You",
            trackIds = listOf("tr_01", "tr_05", "tr_12", "tr_18", "tr_22", "tr_09", "tr_25"),
            saves = 0,
            isPinned = true,
        ),
        Playlist(
            id = "pl_daily1",
            title = "Daily Mix 1",
            description = "Mira Solano, Alon, Tres Marias and more",
            ownerName = "SpotKofi",
            trackIds = listOf("tr_01", "tr_22", "tr_25", "tr_03", "tr_23", "tr_26"),
            saves = 12_403,
            isPinned = true,
        ),
        Playlist(
            id = "pl_daily2",
            title = "Daily Mix 2",
            description = "Bagyo, Kalye Kolektib, Neon Manila and more",
            ownerName = "SpotKofi",
            trackIds = listOf("tr_12", "tr_09", "tr_05", "tr_14", "tr_10", "tr_06"),
            saves = 9_881,
        ),
        Playlist(
            id = "pl_discover",
            title = "Discover Weekly",
            description = "Your weekly mixtape of fresh music",
            ownerName = "SpotKofi",
            trackIds = listOf("tr_15", "tr_04", "tr_11", "tr_24", "tr_17", "tr_27", "tr_08"),
            saves = 45_209,
        ),
        Playlist(
            id = "pl_radar",
            title = "Release Radar",
            description = "Catch all the latest music from artists you follow",
            ownerName = "SpotKofi",
            trackIds = listOf("tr_15", "tr_25", "tr_16", "tr_26", "tr_27"),
            saves = 31_772,
        ),
        Playlist(
            id = "pl_opm",
            title = "OPM Rising",
            description = "The next wave of Original Pinoy Music",
            ownerName = "SpotKofi",
            trackIds = listOf("tr_01", "tr_05", "tr_09", "tr_15", "tr_22", "tr_25", "tr_12"),
            saves = 288_140,
        ),
        Playlist(
            id = "pl_chill",
            title = "Chill Kape",
            description = "Slow mornings and a full cup",
            ownerName = "SpotKofi",
            trackIds = listOf("tr_18", "tr_19", "tr_20", "tr_21", "tr_03", "tr_16"),
            saves = 154_663,
        ),
        Playlist(
            id = "pl_tambay",
            title = "Tambay Beats",
            description = "For the long afternoons",
            ownerName = "SpotKofi",
            trackIds = listOf("tr_10", "tr_11", "tr_07", "tr_09", "tr_06"),
            saves = 77_301,
        ),
        Playlist(
            id = "pl_drive",
            title = "Late Night Drive",
            description = "Windows down, EDSA empty",
            ownerName = "SpotKofi",
            trackIds = listOf("tr_06", "tr_05", "tr_08", "tr_24", "tr_13"),
            saves = 61_995,
        ),
        Playlist(
            id = "pl_ballad",
            title = "Tagalog Ballads",
            description = "Hugot hour, every hour",
            ownerName = "SpotKofi",
            trackIds = listOf("tr_02", "tr_26", "tr_27", "tr_04", "tr_23"),
            saves = 402_118,
        ),
        Playlist(
            id = "pl_focus",
            title = "Deep Focus",
            description = "Beats to keep you in the zone",
            ownerName = "SpotKofi",
            trackIds = listOf("tr_19", "tr_21", "tr_18", "tr_20"),
            saves = 921_004,
        ),
        Playlist(
            id = "pl_throwback",
            title = "Throwback Tambayan",
            description = "The ones that never left the rotation",
            ownerName = "SpotKofi",
            trackIds = listOf("tr_13", "tr_23", "tr_02", "tr_16", "tr_07"),
            saves = 210_557,
        ),
    )

    // ------------------------------------------------------------- categories
    /** The four large tiles at the very top of Search. */
    val topCategories: List<BrowseCategory> = listOf(
        BrowseCategory("tc_music", "Music"),
        BrowseCategory("tc_podcasts", "Podcasts"),
        BrowseCategory("tc_live", "Live Events"),
        BrowseCategory("tc_kpop", "K-Pop ON! (\uC624\uB098) Hub"),
    )

    /** The longer genre grid further down Search. */
    val browseCategories: List<BrowseCategory> = listOf(
        BrowseCategory("ca_opm", "OPM"),
        BrowseCategory("ca_pop", "Pop"),
        BrowseCategory("ca_hiphop", "Hip-Hop"),
        BrowseCategory("ca_rock", "Rock"),
        BrowseCategory("ca_indie", "Indie"),
        BrowseCategory("ca_chill", "Chill"),
        BrowseCategory("ca_focus", "Focus"),
        BrowseCategory("ca_sleep", "Sleep"),
        BrowseCategory("ca_workout", "Workout"),
        BrowseCategory("ca_kpop", "K-Pop"),
        BrowseCategory("ca_jazz", "Jazz"),
        BrowseCategory("ca_rnb", "R&B"),
        BrowseCategory("ca_podcast", "Podcasts"),
        BrowseCategory("ca_live", "Live Events"),
        BrowseCategory("ca_new", "New Releases"),
        BrowseCategory("ca_charts", "Charts"),
    )

    // ---------------------------------------------------------------- stations
    val stations: List<Station> = listOf(
        Station("st_mira", "Mira Solano", "Alon, Tres Marias, Halohalo Sessions, Adie"),
        Station("st_bagyo", "Bagyo", "Kalye Kolektib, Neon Manila, Bagyo, Sampaguita"),
        Station("st_lofi", "Lo-Fi Kapatid", "Kape at Ulan, Tulog Na, 3AM Study Loop"),
        Station("st_kalye", "Kalye Kolektib", "Tambay, Bakod, EDSA Southbound"),
    )

    // ---------------------------------------------------------- latest releases
    val latestReleases: List<ReleaseItem> = listOf(
        ReleaseItem("rl_01", "Halohalo Sessions", "Halohalo (Reimagined)", "15 hours ago", 1),
        ReleaseItem("rl_02", "Tres Marias", "Huling Sayaw", "1 day ago", 1),
        ReleaseItem("rl_03", "Neon Manila, Bagyo", "Signal No. 5 (Live)", "1 day ago", 1),
        ReleaseItem("rl_04", "Mira Solano", "Sinag \u2014 slowed", "3 days ago", 4),
        ReleaseItem("rl_05", "Lo-Fi Kapatid", "Malamig na Hangin", "5 days ago", 10),
    )

    // ------------------------------------------------------------ social (mock)
    // Placeholder handles only. Real profiles arrive with Supabase auth in Phase 3.
    val friendActivity: List<FriendActivity> = listOf(
        FriendActivity("fa_me", "Activity", null, isOnline = true),
        FriendActivity("fa_01", "user_one", "Alon \u2014 Alon"),
        FriendActivity("fa_02", "user_two", "Sinag \u2014 Mira\u2026"),
    )

    val conversations: List<Conversation> = listOf(
        Conversation("cv_01", "user_one", "tambay muna ko sa playlist mo", "Fri"),
        Conversation(
            id = "cv_02",
            personName = "user_two",
            preview = "Huling Sayaw \u2014 Tres Marias",
            timestamp = "Jul 10",
            isSharedTrack = true,
        ),
        Conversation(
            id = "cv_03",
            personName = "user_three",
            preview = "Kalye \u2014 Kalye Kolektib",
            timestamp = "Jul 2",
            isSharedTrack = true,
        ),
    )

    // --------------------------------------------------------------- explore
    val exploreVideos: List<ExploreItem> = listOf(
        ExploreItem("ev_01", "VIDEOS FOR YOU"),
        ExploreItem("ev_02", "OPM BINGE"),
        ExploreItem("ev_03", "AI 2010"),
        ExploreItem("ev_04", "LIVE SETS"),
    )

    val exploreEpisodes: List<ExploreItem> = listOf(
        ExploreItem("ee_01", "Tambay Talks", "Episode 42"),
        ExploreItem("ee_02", "Sit Down, Kape", "Episode 9"),
        ExploreItem("ee_03", "Gabi ng Lagim", "Episode 118"),
        ExploreItem("ee_04", "Barbero Sessions", "Episode 3"),
    )

    // --------------------------------------------------- now playing details
    /**
     * Built per track so every song shows plausible, differing content. The
     * lyric text is invented, as is the bio.
     */
    fun detailsFor(track: Track): TrackDetails = TrackDetails(
        contextLabel = "Recommended for you",
        lyrics = Lyrics(
            lines = listOf(
                LyricLine("Dama ang pananabik, ako'y papalapit na"),
                LyricLine("", isInstrumental = true),
                LyricLine("Parating na ang dapithapon"),
                LyricLine("", isInstrumental = true),
                LyricLine("At kay sarap umuwi sa 'yo"),
                LyricLine("Hindi na ako maghihintay pa"),
            ),
            // Middle line highlighted so the active/inactive treatment is visible.
            activeIndex = 2,
        ),
        artistBio = "${track.artistName} is an invented artist used as " +
            "placeholder content while the catalog API is not connected. The bio " +
            "text exists only to exercise the layout, including the point where it " +
            "runs long enough to need truncating behind a see more control.",
        episodes = listOf(
            EpisodeItem("ep_1", "S2 E3: Kwentuhan Sa Bawat Sandali", "Episode \u2022 ${track.artistName}"),
            EpisodeItem("ep_2", "How ${track.artistName} Built A Sound", "Episode \u2022 ${track.artistName}"),
            EpisodeItem("ep_3", "Studio Notes: ${track.albumTitle}", "Episode \u2022 ${track.artistName}"),
        ),
        contributors = listOf(
            Contributor("co_1", track.artistName, "Main Artist"),
            Contributor("co_2", "${track.artistName.take(4)} Sessions", "Composer + 1 more"),
        ),
        exploreCards = listOf(
            ExploreCard("ex_1", "Songs by ${track.artistName}"),
            ExploreCard("ex_2", "Similar to ${track.artistName}"),
            ExploreCard("ex_3", "Similar to ${track.title}"),
        ),
        credits = listOf(
            Credit(track.artistName, "Main Artist, Composer"),
            Credit("Kalye Studio", "Producer"),
            Credit("A. Reyes", "Mixing Engineer"),
        ),
    )

    // ------------------------------------------------------------- lookups
    private val byId: Map<String, MediaCollection> =
        (playlists + albums + artists).associateBy { it.id }

    private val trackById: Map<String, Track> = tracks.associateBy { it.id }

    fun collection(id: String): MediaCollection? = byId[id]

    fun track(id: String): Track? = trackById[id]

    /** Resolves the running order for a playlist, or the album's own tracks. */
    fun tracksFor(collectionId: String): List<Track> =
        when (val item = byId[collectionId]) {
            is Playlist -> item.trackIds.mapNotNull(trackById::get)
            is Album -> tracksByAlbum[item.title].orEmpty()
            is Artist -> tracks.filter { it.artistName == item.name }
            null -> emptyList()
        }
}
