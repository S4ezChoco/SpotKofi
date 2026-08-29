package com.spotkofi.app.data.repository

import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.BrowseCategory
import com.spotkofi.app.data.model.Conversation
import com.spotkofi.app.data.model.ExploreItem
import com.spotkofi.app.data.model.FriendActivity
import com.spotkofi.app.data.model.HomeSection
import com.spotkofi.app.data.model.HomeTab
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.ReleaseItem
import com.spotkofi.app.data.model.SearchResults
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.TrackDetails
import com.spotkofi.app.data.model.TrackLyrics
import com.spotkofi.app.data.local.LocalMusicStore
import com.spotkofi.app.data.remote.ItunesApi
import com.spotkofi.app.data.remote.ItunesMapper
import com.spotkofi.app.data.remote.LyricsApi
import com.spotkofi.app.data.remote.SpotifyApi
import com.spotkofi.app.data.remote.SpotifyEnrichment
import com.spotkofi.app.data.remote.SpotifyTrack
import com.spotkofi.app.data.remote.YouTubeMusicHomeClient
import com.spotkofi.app.data.remote.YouTubeMusicSearchClient
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
) : MusicRepository {

    constructor() : this(ItunesApi(), SpotifyApi(), null)

    constructor(localStore: LocalMusicStore) : this(ItunesApi(), SpotifyApi(), localStore)

    private val searchCache = mutableMapOf<String, SearchResults>()
    private val tracksCache = mutableMapOf<String, List<Track>>()
    private val albumsCache = mutableMapOf<String, List<Album>>()
    private val collectionsById = mutableMapOf<String, MediaCollection>()
    private val tracksByCollection = mutableMapOf<String, List<Track>>()
    private val visited = MutableStateFlow<List<MediaCollection>>(emptyList())
    private val youtubeSearchClient = YouTubeMusicSearchClient()
    private val youtubeHomeClient = YouTubeMusicHomeClient()
    private val lyricsApi = LyricsApi()

    override fun currentUserName(): String = "kofi_listener"

    // ---------------------------------------------------------------- feeds

    override suspend fun quickPicks(): List<MediaCollection> {
        val albums = cachedAlbums("new music", limit = 8)
        return albums.also(::remember)
    }

    override suspend fun homeSections(tab: HomeTab): List<HomeSection> = coroutineScope {
        val youtubeSections = runCatalog { youtubeHomeClient.sections(tab) }.orEmpty()
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

    override suspend fun search(query: String): SearchResults = coroutineScope {
        val term = query.trim()
        if (term.isEmpty()) return@coroutineScope SearchResults()

        val key = term.lowercase()
        searchCache[key]?.let { return@coroutineScope it }

        val tracks = async { cachedTracks(term, limit = 25) }
        val albums = async { cachedAlbums(term, limit = 12) }
        val artists = async { cachedArtists(term, limit = 12) }
        val result = SearchResults(
            tracks = tracks.await(),
            collections = (albums.await() + artists.await())
                .distinctBy { it.id }
                .also(::remember),
        )
        searchCache[key] = result
        result
    }

    // iTunes is a music catalog, not a video or podcast catalog.
    override suspend fun exploreVideos(): List<ExploreItem> = emptyList()
    override suspend fun explorePodcasts(): List<ExploreItem> = emptyList()

    // --------------------------------------------------------------- detail

    override suspend fun collection(id: String): MediaCollection? {
        if (id.startsWith("local:playlist:")) {
            return localStore?.playlist(id)
        }
        collectionsById[id]?.let { return it }

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
            runCatalog {
                lyricsApi.lyrics(
                    title = track.title,
                    artistName = track.artistName,
                    albumTitle = track.albumTitle,
                    durationMs = track.durationMs,
                )
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
                )
            },
        )
    }

    // --------------------------------------------------------------- social

    override fun friendActivity(): List<FriendActivity> = emptyList()
    override fun conversations(): List<Conversation> = emptyList()

    // ---------------------------------------------------------------- internals

    private suspend fun cachedTracks(term: String, limit: Int): List<Track> {
        val key = "tracks:${term.trim().lowercase()}:$limit"
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
        val candidates = runCatalog {
            youtubeSearchClient.searchSongs(term, limit)
        }.orEmpty()
        if (candidates.isEmpty()) return emptyList()

        // Match only for optional album/artist IDs and catalog artwork. Never
        // replace the candidate title, performer, duration, or video identity.
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
            Track(
                id = "youtube:${candidate.videoId}",
                title = candidate.title,
                artistName = candidate.artistName,
                albumTitle = candidate.albumTitle
                    ?.takeIf { it.isNotBlank() }
                    ?: catalogMatch?.albumTitle?.takeIf { it.isNotBlank() }
                    ?: "YouTube Music",
                durationMs = candidate.durationMs.takeIf { it > 0L }
                    ?: catalogMatch?.durationMs?.takeIf { it > 0L }
                    ?: 0L,
                isExplicit = candidate.isExplicit || catalogMatch?.isExplicit == true,
                artworkUrl = candidate.artworkUrl ?: catalogMatch?.artworkUrl,
                externalUrl = "https://music.youtube.com/watch?v=${candidate.videoId}",
                videoId = candidate.videoId,
                albumId = catalogMatch?.albumId,
                artistId = catalogMatch?.artistId,
            )
        }
    }

    private suspend fun cachedAlbums(term: String, limit: Int): List<Album> {
        val key = "albums:${term.trim().lowercase()}:$limit"
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

        /** Rows per song shelf on Home. Long shelves push the albums off-screen. */
        const val SONG_SHELF_LIMIT = 6

        val BrowseGenres = listOf(
            BrowseCategory("tc_music", "Music"),
            BrowseCategory("tc_podcasts", "Podcasts"),
            BrowseCategory("tc_opm", "OPM"),
            BrowseCategory("tc_kpop", "K-Pop"),
            BrowseCategory("tc_pop", "Pop"),
            BrowseCategory("tc_rock", "Rock"),
            BrowseCategory("tc_hiphop", "Hip-Hop"),
            BrowseCategory("tc_jazz", "Jazz"),
        )
    }
}
