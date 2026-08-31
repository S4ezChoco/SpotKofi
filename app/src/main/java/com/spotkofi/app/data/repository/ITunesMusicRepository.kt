package com.spotkofi.app.data.repository

import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.BrowseCategory
import com.spotkofi.app.data.model.Conversation
import com.spotkofi.app.data.model.ExploreItem
import com.spotkofi.app.data.model.FriendActivity
import com.spotkofi.app.data.model.HomeSection
import com.spotkofi.app.data.model.HomeTab
import com.spotkofi.app.data.model.ChartRegion
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.MoodCategory
import com.spotkofi.app.data.model.MoodGroup
import com.spotkofi.app.data.model.MusicChart
import com.spotkofi.app.data.model.SearchResults
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.TrackDetails
import com.spotkofi.app.data.model.TrackLyrics
import com.spotkofi.app.data.local.AppSettings
import com.spotkofi.app.data.local.LocalMusicStore
import com.spotkofi.app.data.remote.ItunesApi
import com.spotkofi.app.data.remote.ItunesMapper
import com.spotkofi.app.data.remote.ChartRegions
import com.spotkofi.app.data.remote.LyricsApi
import com.spotkofi.app.data.remote.SpotifyApi
import com.spotkofi.app.data.remote.SpotifyEnrichment
import com.spotkofi.app.data.remote.SpotifyTrack
import com.spotkofi.app.data.remote.YouTubeIds
import com.spotkofi.app.data.remote.YouTubeMusicBrowseClient
import com.spotkofi.app.data.remote.YouTubeMusicHomeClient
import com.spotkofi.app.data.remote.YouTubeMusicSearchClient
import com.spotkofi.app.data.remote.YouTubeSongInfoClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * MusicRepository backed by iTunes metadata, with optional Spotify enrichment.
 *
 * iTunes owns search results, artwork, album/artist ids, and the metadata used by
 * navigation. Spotify is only a best-effort source for artist genre and related
 * recommendations; recommendation candidates are resolved back through iTunes
 * before they are exposed to the UI.
 */
class ItunesMusicRepository internal constructor(
    private val itunes: ItunesApi,
    private val spotify: SpotifyApi,
    private val localStore: LocalMusicStore? = null,
    /**
     * Read per request rather than captured once, so changing the content region or
     * the explicit filter in Settings applies to the next search instead of the
     * next launch.
     */
    private val settingsProvider: () -> AppSettings = { AppSettings() },
) : MusicRepository {

    constructor() : this(ItunesApi(), SpotifyApi(), null)

    constructor(localStore: LocalMusicStore) : this(ItunesApi(), SpotifyApi(), localStore)

    constructor(
        localStore: LocalMusicStore,
        settingsProvider: () -> AppSettings,
    ) : this(ItunesApi(), SpotifyApi(), localStore, settingsProvider)

    private val searchCache = mutableMapOf<String, SearchResults>()
    private val tracksCache = mutableMapOf<String, List<Track>>()
    private val albumsCache = mutableMapOf<String, List<Album>>()
    private val collectionsById = mutableMapOf<String, MediaCollection>()
    private val tracksByCollection = mutableMapOf<String, List<Track>>()
    private val visited = MutableStateFlow<List<MediaCollection>>(emptyList())
    private val youtubeSearchClient = YouTubeMusicSearchClient()
    private val youtubeHomeClient = YouTubeMusicHomeClient()
    private val youtubeBrowseClient = YouTubeMusicBrowseClient()
    private val lyricsApi = LyricsApi()
    private val songInfoClient = YouTubeSongInfoClient()
    private val metadataTrackResolver = com.spotkofi.app.data.remote.YouTubeTrackResolver()
    private val moodGroupsCache = MutableStateFlow<List<MoodGroup>>(emptyList())

    override fun currentUserName(): String = "kofi_listener"

    override fun invalidateHomeCache() {
        // Pull-to-refresh is an explicit request for new shelves, not merely a
        // recomposition. Clear only short-lived remote caches; local library and
        // playback state remain durable and untouched.
        searchCache.clear()
        tracksCache.clear()
        albumsCache.clear()
        moodGroupsCache.value = emptyList()
    }

    // ---------------------------------------------------------------- feeds

    override suspend fun quickPicks(): List<MediaCollection> {
        val albums = cachedAlbums("new music", limit = 8)
        return albums.also(::remember)
    }

    override suspend fun homeSections(tab: HomeTab): List<HomeSection> = coroutineScope {
        val region = settingsProvider().contentRegion
        val youtubeSections = runCatalog {
            youtubeHomeClient.sections(tab, country = region)
        }.orEmpty()
        if (youtubeSections.isNotEmpty()) return@coroutineScope youtubeSections

        when (tab) {
            HomeTab.All -> {
                val albums = async { cachedAlbums("new music", limit = 12) }
                // Song shelves come first in the request order because they are
                // the only Home content that can be played without a detour
                // through a detail screen.
                val picks = async { cachedTracks("popular music", limit = 20) }
                val fresh = async { cachedTracks("new songs this week", limit = 20) }
                buildList {
                    picks.await().take(SONG_SHELF_LIMIT).takeIf { it.isNotEmpty() }?.let { values ->
                        add(HomeSection.Songs("itunes_quick_songs", "Quick picks", values))
                    }
                    albums.await().firstOrNull()?.let { spotlight ->
                        add(HomeSection.Spotlight("itunes_spotlight", "New releases", spotlight))
                    }
                    fresh.await().take(SONG_SHELF_LIMIT).takeIf { it.isNotEmpty() }?.let { values ->
                        add(HomeSection.Songs("itunes_fresh_songs", "Fresh finds", values))
                    }
                    albums.await().takeIf { it.isNotEmpty() }?.let { values ->
                        add(HomeSection.Cards("itunes_new_albums", "New albums", values))
                    }
                    picks.await()
                        .drop(SONG_SHELF_LIMIT)
                        .take(SONG_SHELF_LIMIT)
                        .takeIf { it.isNotEmpty() }
                        ?.let { values ->
                            add(HomeSection.Songs("itunes_more_songs", "More of what you like", values))
                        }
                    artistsOf(picks.await()).takeIf { it.isNotEmpty() }?.let { values ->
                        add(HomeSection.Cards("itunes_artists", "Artists to explore", values))
                    }
                }
            }

            HomeTab.Music -> {
                val pop = async { cachedAlbums("pop music", limit = 12) }
                val rock = async { cachedAlbums("rock music", limit = 12) }
                val tracks = async { cachedTracks("top music", limit = 20) }
                val opm = async { cachedTracks("opm hits", limit = 20) }
                buildList {
                    tracks.await().take(SONG_SHELF_LIMIT).takeIf { it.isNotEmpty() }?.let { values ->
                        add(HomeSection.Songs("itunes_top_songs", "Top songs", values))
                    }
                    pop.await().takeIf { it.isNotEmpty() }?.let { values ->
                        add(HomeSection.Cards("itunes_pop", "Pop albums", values))
                    }
                    opm.await().take(SONG_SHELF_LIMIT).takeIf { it.isNotEmpty() }?.let { values ->
                        add(HomeSection.Songs("itunes_opm_songs", "OPM picks", values))
                    }
                    rock.await().takeIf { it.isNotEmpty() }?.let { values ->
                        add(HomeSection.Cards("itunes_rock", "Rock albums", values))
                    }
                    artistsOf(tracks.await()).takeIf { it.isNotEmpty() }?.let { values ->
                        add(HomeSection.Cards("itunes_music_artists", "Popular artists", values))
                    }
                }
            }

            // iTunes is a music catalog. A podcast feed would have to be invented,
            // so the tab reports nothing and the screen shows an empty state rather
            // than fabricated shows.
            HomeTab.Podcasts -> emptyList()
        }
    }

    override fun library(): Flow<List<MediaCollection>> =
        localStore?.visitedCollections ?: visited.asStateFlow()

    override fun recordVisited(collection: MediaCollection) {
        if (localStore != null) {
            localStore.recordVisited(collection)
            return
        }
        visited.update { current ->
            (listOf(collection) + current.filterNot { it.id == collection.id })
                .take(MAX_VISITED)
        }
    }

    // --------------------------------------------------------------- search

    override fun browseCategories(): List<BrowseCategory> = BrowseGenres

    /**
     * Search across songs, artists, albums and playlists.
     *
     * A single letter is not a query. It used to be treated as one, and because
     * the provider answers anything with something, the results list filled with
     * unrelated rows before the user had finished typing a word.
     *
     * Artists and albums now come from the same provider as the songs. They were
     * previously borrowed from the metadata catalog, which is how a search could
     * list an artist that had nothing to do with the tracks above it - and could
     * not be opened, because that id pointed at a different service.
     */
    override suspend fun search(query: String): SearchResults = coroutineScope {
        val term = query.trim()
        if (term.length < MIN_SEARCH_LENGTH) return@coroutineScope SearchResults()

        val key = "${settingsProvider().contentRegion}:${term.lowercase()}:${settingsProvider().hideExplicitContent}"
        searchCache[key]?.let { return@coroutineScope it }

        val region = settingsProvider().contentRegion
        val tracks = async { cachedTracks(term, limit = 25) }
        val artists = async {
            runCatalog { youtubeSearchClient.searchArtists(term, country = region) }.orEmpty()
        }
        val albums = async {
            runCatalog { youtubeSearchClient.searchAlbums(term, country = region) }.orEmpty()
        }
        val playlists = async {
            runCatalog { youtubeSearchClient.searchPlaylists(term, country = region) }.orEmpty()
        }

        val providerArtists = artists.await()
        val providerAlbums = albums.await()

        // The metadata catalog is only reached for a type the provider returned
        // nothing for, so a working provider result is never diluted by a second
        // source with different ids.
        val fallbackArtists = if (providerArtists.isEmpty()) {
            runCatalog { cachedArtists(term, limit = 12) }.orEmpty()
        } else {
            emptyList()
        }
        val fallbackAlbums = if (providerAlbums.isEmpty()) {
            runCatalog { cachedAlbums(term, limit = 12) }.orEmpty()
        } else {
            emptyList()
        }

        val result = SearchResults(
            tracks = tracks.await().filterExplicit(),
            collections = (
                providerArtists + fallbackArtists +
                    providerAlbums + fallbackAlbums +
                    playlists.await()
                )
                .distinctBy { it.id }
                .also(::remember),
        )
        searchCache[key] = result
        result
    }

    // iTunes is a music catalog, not a video or podcast catalog.
    override suspend fun exploreVideos(): List<ExploreItem> = emptyList()
    override suspend fun explorePodcasts(): List<ExploreItem> = emptyList()

    // -------------------------------------------------------------- explore

    override fun chartRegions(): List<ChartRegion> = ChartRegions.all

    override suspend fun chart(regionCode: String): MusicChart? =
        runCatalog { youtubeBrowseClient.chart(regionCode) }
            ?.also { chart ->
                remember(chart.topArtists)
                chart.shelves.forEach { shelf -> remember(shelf.items) }
            }

    override suspend fun moodsAndGenres(): List<MoodGroup> {
        // The grid is stable for a session and costs a full browse, so the first
        // successful answer is reused rather than refetched every time the screen
        // is opened.
        moodGroupsCache.value.takeIf { it.isNotEmpty() }?.let { return it }
        val groups = runCatalog { youtubeBrowseClient.moodsAndGenres() }.orEmpty()
        if (groups.isNotEmpty()) moodGroupsCache.value = groups
        return groups
    }

    override suspend fun moodCategory(category: MoodCategory): MoodCategoryContents? =
        runCatalog { youtubeBrowseClient.moodCategory(category.params) }
            ?.let { page ->
                remember(page.playlists)
                MoodCategoryContents(
                    // The provider's own heading is preferred; the tile's label is
                    // the fallback when the page returns none.
                    title = page.title.ifBlank { category.title },
                    songs = page.songs,
                    playlists = page.playlists,
                )
            }

    override suspend fun newReleases(): List<MediaCollection> =
        runCatalog { youtubeBrowseClient.newReleases() }.orEmpty().also(::remember)

    override suspend fun trendingPlaylists(regionCode: String): List<MediaCollection> =
        runCatalog { youtubeBrowseClient.trendingPlaylists(regionCode) }
            .orEmpty()
            .also(::remember)

    // --------------------------------------------------------------- detail

    override suspend fun collection(id: String): MediaCollection? {
        if (id.startsWith("local:playlist:")) {
            return localStore?.playlist(id)
        }
        collectionsById[id]?.let { return it }

        // Provider-sourced ids resolve against the provider. Without this branch a
        // search hit or a chart card opened onto an empty screen, because the id
        // was being parsed as a metadata-catalog id and never matched.
        if (YouTubeIds.isYouTube(id)) {
            return runCatalog { youtubeBrowseClient.collection(id) }
                ?.also { collectionsById[id] = it }
        }

        val resolved = when {
            id.startsWith(ItunesMapper.ALBUM_PREFIX) -> {
                val rawId = ItunesMapper.rawId(id, ItunesMapper.ALBUM_PREFIX) ?: return null
                itunes.lookup(rawId, entity = "song", limit = 1)
                    .asSequence()
                    .mapNotNull(ItunesMapper::toAlbum)
                    .firstOrNull()
            }

            id.startsWith(ItunesMapper.ARTIST_PREFIX) -> {
                val rawId = ItunesMapper.rawId(id, ItunesMapper.ARTIST_PREFIX) ?: return null
                itunes.lookup(rawId, entity = "musicArtist", limit = 1)
                    .asSequence()
                    .mapNotNull(ItunesMapper::toArtist)
                    .firstOrNull()
                    ?: itunes.lookup(rawId, entity = "song", limit = 1)
                        .asSequence()
                        .mapNotNull(ItunesMapper::toArtist)
                        .firstOrNull()
            }

            else -> null
        }

        return resolved?.also { collectionsById[id] = it }
    }

    override suspend fun tracks(collectionId: String): List<Track> {
        if (collectionId.startsWith("local:playlist:")) {
            return localStore?.playlistTracks(collectionId).orEmpty()
        }
        tracksByCollection[collectionId]?.let { return it }

        if (YouTubeIds.isYouTube(collectionId)) {
            val provided = runCatalog {
                youtubeBrowseClient.collectionTracks(collectionId)
            }.orEmpty()
            if (provided.isNotEmpty()) tracksByCollection[collectionId] = provided
            return provided
        }

        val rawId = when {
            collectionId.startsWith(ItunesMapper.ALBUM_PREFIX) ->
                ItunesMapper.rawId(collectionId, ItunesMapper.ALBUM_PREFIX)
            ItunesMapper.ARTIST_PREFIX.let(collectionId::startsWith) ->
                ItunesMapper.rawId(collectionId, ItunesMapper.ARTIST_PREFIX)
            else -> null
        } ?: return emptyList()

        val tracks = ItunesMapper.toTracks(
            itunes.lookup(rawId, entity = "song", limit = TRACK_LIMIT),
        )
        tracksByCollection[collectionId] = tracks
        return tracks
    }

    override suspend fun trackDetails(track: Track): TrackDetails = coroutineScope {
        val settings = settingsProvider()
        val region = settings.contentRegion
        // Catalog rows do not carry a YouTube id. Resolve one once and share it
        // between credits and lyrics so the detail page describes the recording
        // that playback will use instead of silently giving up on provider data.
        val resolvedVideoId = async {
            track.videoId
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: runCatalog {
                    metadataTrackResolver.resolveVideoId(track, country = region)
                }
        }
        val albumTracks = async {
            track.albumId?.let { runCatalog { tracks(it) } }.orEmpty()
        }
        val artistTracks = async {
            track.artistId?.let { runCatalog { tracks(it) } }
                ?: runCatalog { cachedTracks(track.artistName, limit = 20) }
        }
        val artistAlbums = async {
            track.artistId?.let { runCatalog { albumsForArtist(it) } }
                ?: runCatalog { cachedAlbums(track.artistName, limit = 10) }
        }
        // Lyrics are a separate provider and the slowest of the four, so they are
        // fetched alongside the rest and allowed to fail on their own. A missing
        // lyric sheet must never cost the user the album and artist rows.
        val lyrics = async {
            if (!settings.lyricsEnabled) return@async null
            runCatalog {
                lyricsApi.lyrics(
                    title = track.title,
                    artistName = track.artistName,
                    albumTitle = track.albumTitle,
                    durationMs = track.durationMs,
                    videoId = resolvedVideoId.await(),
                    provider = settings.lyricsProvider,
                )
            }
        }
        // Publisher details for the credits panel. Its own request, allowed to fail
        // on its own, and only attempted when the track has a provider id to ask
        // about. The player context uses the selected region, just like search.
        val credits = async {
            resolvedVideoId.await()?.let { videoId ->
                runCatalog { songInfoClient.credits(videoId, country = region) }
            }
        }
        val spotifyEnrichment = async {
            runCatalog { spotify.enrich(track.title, track.artistName) }
        }

        val enrichment = spotifyEnrichment.await()
        val recommendations = enrichment
            ?.let { resolveRecommendations(it) }
            .orEmpty()

        TrackDetails(
            contextLabel = track.albumTitle.ifBlank { track.artistName },
            artistGenre = enrichment?.artistGenre,
            albumTracks = albumTracks.await().orEmpty().filterNot { it.id == track.id },
            moreByArtist = artistTracks.await().orEmpty().filterNot { it.id == track.id },
            artistAlbums = artistAlbums.await().orEmpty(),
            recommendations = recommendations.filterNot { it.id == track.id },
            lyrics = lyrics.await()?.let { result ->
                TrackLyrics(
                    plain = result.plain,
                    synced = result.synced,
                    instrumental = result.instrumental,
                    providerName = result.providerName,
                )
            },
            credits = credits.await()?.takeIf { it.hasAny },
        )
    }

    // --------------------------------------------------------------- social

    override fun friendActivity(): List<FriendActivity> = emptyList()
    override fun conversations(): List<Conversation> = emptyList()

    // ---------------------------------------------------------------- internals

    private suspend fun cachedTracks(term: String, limit: Int): List<Track> {
        val region = settingsProvider().contentRegion
        val key = "tracks:$region:${term.trim().lowercase()}:$limit"
        tracksCache[key]?.let { return it }

        // YouTube Music owns the result identity and metadata so every visible
        // row points at the song that produced its own video ID. iTunes remains
        // an optional enrichment source and a fallback when YouTube is down.
        val youtubeTracks = searchYouTubeTracks(term, limit)
        val tracks = if (youtubeTracks.isNotEmpty()) {
            youtubeTracks
        } else {
            runCatalog {
                ItunesMapper.toTracks(
                    itunes.search(term = term, entity = "musicTrack", limit = limit),
                )
            }.orEmpty()
        }

        tracksCache[key] = tracks
        return tracks
    }

    private suspend fun searchYouTubeTracks(term: String, limit: Int): List<Track> {
        val region = settingsProvider().contentRegion
        val candidates = runCatalog {
            youtubeSearchClient.searchSongs(term, limit, country = region)
        }.orEmpty()
        if (candidates.isEmpty()) return emptyList()

        // Match only to fill gaps: album/artist ids for navigation, and artwork or
        // a duration the provider omitted. The provider's title, performer and
        // video identity are never replaced, or a row would stop describing the
        // audio it actually plays.
        val catalogTracks = runCatalog {
            ItunesMapper.toTracks(
                itunes.search(
                    term = term,
                    entity = "musicTrack",
                    limit = limit.coerceAtLeast(25),
                ),
            )
        }.orEmpty()

        return candidates.map { candidate ->
            val catalogMatch = catalogTracks.firstOrNull { catalog ->
                normalize(catalog.title) == normalize(candidate.title) &&
                    normalize(catalog.artistName) == normalize(candidate.artistName)
            }
            candidate.copy(
                // Left blank when neither source knows the album. A filler value
                // such as the service's own name looked like real metadata.
                albumTitle = candidate.albumTitle
                    .ifBlank { catalogMatch?.albumTitle.orEmpty() },
                durationMs = candidate.durationMs.takeIf { it > 0L }
                    ?: catalogMatch?.durationMs
                    ?: 0L,
                isExplicit = candidate.isExplicit || catalogMatch?.isExplicit == true,
                artworkUrl = candidate.artworkUrl ?: catalogMatch?.artworkUrl,
                albumId = catalogMatch?.albumId,
                artistId = catalogMatch?.artistId,
            )
        }
    }

    private suspend fun cachedAlbums(term: String, limit: Int): List<Album> {
        val region = settingsProvider().contentRegion
        val key = "albums:$region:${term.trim().lowercase()}:$limit"
        albumsCache[key]?.let { return it }
        val albums = ItunesMapper.toAlbums(
            itunes.search(term = term, entity = "album", limit = limit),
        )
        albumsCache[key] = albums
        return albums.also(::remember)
    }

    private suspend fun cachedArtists(term: String, limit: Int): List<Artist> =
        ItunesMapper.toArtists(
            itunes.search(term = term, entity = "musicArtist", limit = limit),
        ).also(::remember)

    private suspend fun albumsForArtist(artistId: String): List<Album> {
        val rawId = ItunesMapper.rawId(artistId, ItunesMapper.ARTIST_PREFIX)
            ?: return emptyList()
        return ItunesMapper.toAlbums(
            itunes.lookup(rawId, entity = "album", limit = 20),
        ).also(::remember)
    }

    private fun artistsOf(tracks: List<Track>, limit: Int = 10): List<Artist> = tracks
        .asSequence()
        .filter { it.artistName.isNotBlank() && it.artistId != null }
        .distinctBy { it.artistId }
        .take(limit)
        .map { track ->
            Artist(
                id = track.artistId!!,
                name = track.artistName,
                artworkUrl = track.artworkUrl,
            )
        }
        .toList()
        .also(::remember)

    private suspend fun resolveRecommendations(enrichment: SpotifyEnrichment): List<Track> =
        coroutineScope {
            enrichment.recommendations
                .asSequence()
                .filter { it.name.isNotBlank() && it.artists.isNotEmpty() }
                .take(RECOMMENDATION_LIMIT)
                .map { candidate ->
                    async {
                        try {
                            val artist = candidate.artists.first().name
                            val matches = cachedTracks(
                                term = "$artist ${candidate.name}",
                                limit = 5,
                            )
                            val title = normalize(candidate.name)
                            val performer = normalize(artist)
                            matches.firstOrNull { match ->
                                normalize(match.title) == title &&
                                    normalize(match.artistName) == performer
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                .toList()
                .awaitAll()
                .filterNotNull()
                .distinctBy { it.id }
        }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    /**
     * Drops explicit tracks when the user asked for them to be hidden.
     *
     * Filtered at the repository rather than in each screen: a track that should be
     * hidden must not reach a list, a queue or a download in the first place.
     */
    private fun List<Track>.filterExplicit(): List<Track> =
        if (settingsProvider().hideExplicitContent) filterNot { it.isExplicit } else this

    private fun remember(items: List<MediaCollection>) {
        items.forEach { collectionsById[it.id] = it }
    }

    private suspend fun <T> runCatalog(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val MAX_VISITED = 40
        const val TRACK_LIMIT = 50
        const val RECOMMENDATION_LIMIT = 8

        /**
         * Shortest query worth sending.
         *
         * One character matches almost everything, so the provider answers with a
         * broad, unrelated list. Waiting for a second character costs nothing and
         * is the difference between suggestions and noise.
         */
        const val MIN_SEARCH_LENGTH = 2

        /** Rows per song shelf on Home. Long shelves push the albums off-screen. */
        const val SONG_SHELF_LIMIT = 6

        val BrowseGenres = listOf(
            BrowseCategory(
                id = "tc_music",
                name = "Music",
                subtitle = "Songs, albums and artists",
                artworkUrl = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=900&q=85",
                targetQuery = "music",
            ),
            BrowseCategory(
                id = "tc_podcasts",
                name = "Podcasts",
                subtitle = "Shows and episodes",
                artworkUrl = "https://images.unsplash.com/photo-1478737270239-2f02b77fc618?w=900&q=85",
                targetQuery = "podcasts",
            ),
            BrowseCategory(
                id = "tc_opm",
                name = "OPM",
                subtitle = "Filipino music",
                artworkUrl = "https://images.unsplash.com/photo-1516280440614-37939bbacd81?w=900&q=85",
                targetQuery = "OPM",
            ),
            BrowseCategory(
                id = "tc_kpop",
                name = "K-Pop",
                subtitle = "K-Pop ON!",
                artworkUrl = "https://images.unsplash.com/photo-1524368535928-5b5e00ddc76b?w=900&q=85",
                targetQuery = "K-Pop",
            ),
            BrowseCategory(
                id = "tc_pop",
                name = "Pop",
                subtitle = "Popular right now",
                artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=900&q=85",
                targetQuery = "pop music",
            ),
            BrowseCategory(
                id = "tc_rock",
                name = "Rock",
                subtitle = "Guitars and anthems",
                artworkUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=900&q=85",
                targetQuery = "rock music",
            ),
            BrowseCategory(
                id = "tc_hiphop",
                name = "Hip-Hop",
                subtitle = "Rap and hip-hop",
                artworkUrl = "https://images.unsplash.com/photo-1571609803939-54f463c1752f?w=900&q=85",
                targetQuery = "hip hop",
            ),
            BrowseCategory(
                id = "tc_jazz",
                name = "Jazz",
                subtitle = "Smooth and classic",
                artworkUrl = "https://images.unsplash.com/photo-1415201364774-f6f0bb35f28f?w=900&q=85",
                targetQuery = "jazz music",
            ),
            BrowseCategory(
                id = "tc_rnb",
                name = "R&B",
                subtitle = "Rhythm and soul",
                artworkUrl = "https://images.unsplash.com/photo-1504898770365-14faca6a7320?w=900&q=85",
                targetQuery = "R&B",
            ),
            BrowseCategory(
                id = "tc_lofi",
                name = "Lo-Fi",
                subtitle = "Chill beats to relax",
                artworkUrl = "https://images.unsplash.com/photo-1494232410401-ad00d5433cfa?w=900&q=85",
                targetQuery = "lo-fi",
            ),
            BrowseCategory(
                id = "tc_edm",
                name = "EDM",
                subtitle = "Electronic dance music",
                artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=900&q=85",
                targetQuery = "EDM",
            ),
            BrowseCategory(
                id = "tc_country",
                name = "Country",
                subtitle = "Country hits",
                artworkUrl = "https://images.unsplash.com/photo-1510915361894-db8b60106cb1?w=900&q=85",
                targetQuery = "country music",
            ),
        )
    }
}
