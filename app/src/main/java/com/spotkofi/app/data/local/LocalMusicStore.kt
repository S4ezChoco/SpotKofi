package com.spotkofi.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.Playlist
import com.spotkofi.app.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Durable local state for the parts of a music app that must survive process death.
 *
 * The catalog remains remote, but the user-owned state is local: track snapshots,
 * saved songs, visited collections, playlists, playback history, queue order, and
 * download metadata. Resolved YouTube URLs are deliberately never persisted because
 * they are signed and expire.
 */
class LocalMusicStore(context: Context) {

    private val helper = StoreHelper(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _visitedCollections = MutableStateFlow<List<MediaCollection>>(emptyList())
    val visitedCollections: StateFlow<List<MediaCollection>> = _visitedCollections.asStateFlow()

    private val _savedCollections = MutableStateFlow<List<MediaCollection>>(emptyList())
    val savedCollections: StateFlow<List<MediaCollection>> = _savedCollections.asStateFlow()

    private val _savedTracks = MutableStateFlow<List<Track>>(emptyList())
    val savedTracks: StateFlow<List<Track>> = _savedTracks.asStateFlow()

    private val _history = MutableStateFlow<List<Track>>(emptyList())
    val history: StateFlow<List<Track>> = _history.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _downloadRecords = MutableStateFlow<List<LocalDownload>>(emptyList())
    val downloadRecords: StateFlow<List<LocalDownload>> = _downloadRecords.asStateFlow()

    init {
        // Hydrate before AppContainer constructs the downloader. This makes
        // persisted completed/queued records available synchronously, so a
        // process restart cannot briefly look like an empty download library.
        refreshAll()
    }

    fun isTrackSaved(trackId: String): Boolean =
        _savedTracks.value.any { it.id == trackId }

    fun saveTrack(track: Track) {
        scope.launch {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                upsertTrack(db, track)
                db.insertWithOnConflict(
                    TABLE_SAVED_TRACKS,
                    null,
                    ContentValues().apply {
                        put("track_id", track.id)
                        put("saved_at", System.currentTimeMillis())
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            refreshAll()
        }
    }

    fun removeTrack(trackId: String) {
        scope.launch {
            helper.writableDatabase.delete(TABLE_SAVED_TRACKS, "track_id = ?", arrayOf(trackId))
            refreshAll()
        }
    }

    fun toggleTrack(track: Track) {
        scope.launch {
            val db = helper.writableDatabase
            if (isTrackSaved(track.id)) {
                db.delete(TABLE_SAVED_TRACKS, "track_id = ?", arrayOf(track.id))
            } else {
                db.beginTransaction()
                try {
                    upsertTrack(db, track)
                    db.insertWithOnConflict(
                        TABLE_SAVED_TRACKS,
                        null,
                        ContentValues().apply {
                            put("track_id", track.id)
                            put("saved_at", System.currentTimeMillis())
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            refreshAll()
        }
    }

    /** Keeps collection recents durable while preserving the repository's old API. */
    fun recordVisited(collection: MediaCollection) {
        scope.launch {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                upsertCollection(db, collection)
                db.delete(TABLE_VISITED, "collection_id = ?", arrayOf(collection.id))
                db.insert(
                    TABLE_VISITED,
                    null,
                    ContentValues().apply {
                        put("collection_id", collection.id)
                        put("visited_at", System.currentTimeMillis())
                    },
                )
                trimVisited(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            refreshAll()
        }
    }

    /** Records a play without storing a signed/expiring stream URL. */
    fun recordPlayed(track: Track) {
        scope.launch {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                upsertTrack(db, track)
                val previousCount = db.query(
                    TABLE_HISTORY,
                    arrayOf("play_count"),
                    "track_id = ?",
                    arrayOf(track.id),
                    null,
                    null,
                    null,
                    "1",
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
                db.insertWithOnConflict(
                    TABLE_HISTORY,
                    null,
                    ContentValues().apply {
                        put("track_id", track.id)
                        put("played_at", System.currentTimeMillis())
                        put("play_count", previousCount + 1)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            refreshAll()
        }
    }

    /**
     * Forgets every play.
     *
     * Only the history table is emptied. Track snapshots are left alone because
     * saved songs, playlists and the queue all reference them, and deleting the
     * snapshot rows would strip titles and artwork out of those lists too.
     */
    fun clearHistory() {
        scope.launch {
            helper.writableDatabase.delete(TABLE_HISTORY, null, null)
            refreshAll()
        }
    }

    fun toggleCollection(collection: MediaCollection) {
        scope.launch {
            val db = helper.writableDatabase
            val exists = db.query(
                TABLE_SAVED_COLLECTIONS,
                arrayOf("collection_id"),
                "collection_id = ?",
                arrayOf(collection.id),
                null,
                null,
                null,
                "1",
            ).use { it.moveToFirst() }
            db.beginTransaction()
            try {
                upsertCollection(db, collection)
                if (exists) {
                    db.delete(TABLE_SAVED_COLLECTIONS, "collection_id = ?", arrayOf(collection.id))
                } else {
                    db.insert(
                        TABLE_SAVED_COLLECTIONS,
                        null,
                        ContentValues().apply {
                            put("collection_id", collection.id)
                            put("saved_at", System.currentTimeMillis())
                        },
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            refreshAll()
        }
    }

    suspend fun createPlaylist(name: String, description: String = ""): Playlist =
        withContext(Dispatchers.IO) {
            val playlist = Playlist(
                id = "$LOCAL_PLAYLIST_PREFIX${UUID.randomUUID()}",
                title = name.trim().ifBlank { "My playlist" },
                description = description.trim(),
                ownerName = "You",
            )
            val db = helper.writableDatabase
            db.insertOrThrow(
                TABLE_COLLECTIONS,
                null,
                collectionValues(playlist).apply { put("created_at", System.currentTimeMillis()) },
            )
            refreshAll()
            playlist
        }

    suspend fun addToPlaylist(playlistId: String, track: Track) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val alreadyPresent = db.query(
                TABLE_PLAYLIST_TRACKS,
                arrayOf("track_id"),
                "playlist_id = ? AND track_id = ?",
                arrayOf(playlistId, track.id),
                null,
                null,
                null,
                "1",
            ).use { cursor -> cursor.moveToFirst() }
            if (alreadyPresent) return@withContext

            upsertTrack(db, track)
            val nextPosition = db.rawQuery(
                "SELECT COALESCE(MAX(position), -1) + 1 FROM $TABLE_PLAYLIST_TRACKS WHERE playlist_id = ?",
                arrayOf(playlistId),
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            db.insert(
                TABLE_PLAYLIST_TRACKS,
                null,
                ContentValues().apply {
                    put("playlist_id", playlistId)
                    put("track_id", track.id)
                    put("position", nextPosition)
                },
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshAll()
    }

    suspend fun removeFromPlaylist(playlistId: String, trackId: String) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.delete(
            TABLE_PLAYLIST_TRACKS,
            "playlist_id = ? AND track_id = ?",
            arrayOf(playlistId, trackId),
        )
        compactPlaylistPositions(db, playlistId)
        refreshAll()
    }

    suspend fun playlist(id: String): Playlist? = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            TABLE_COLLECTIONS,
            null,
            "id = ? AND type = ?",
            arrayOf(id, TYPE_PLAYLIST),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toPlaylist() else null
        }
    }

    suspend fun playlistTracks(id: String): List<Track> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            """
            SELECT t.* FROM $TABLE_TRACKS t
            INNER JOIN $TABLE_PLAYLIST_TRACKS p ON p.track_id = t.id
            WHERE p.playlist_id = ?
            ORDER BY p.position ASC
            """.trimIndent(),
            arrayOf(id),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toTrack())
            }
        }
    }

    suspend fun loadQueue(): List<Track> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            """
            SELECT t.* FROM $TABLE_TRACKS t
            INNER JOIN $TABLE_QUEUE q ON q.track_id = t.id
            ORDER BY q.position ASC
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toTrack())
            }
        }
    }

    suspend fun saveQueue(tracks: List<Track>) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_QUEUE, null, null)
            tracks.forEachIndexed { index, track ->
                upsertTrack(db, track)
                db.insert(
                    TABLE_QUEUE,
                    null,
                    ContentValues().apply {
                        put("position", index)
                        put("track_id", track.id)
                    },
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun upsertDownload(download: LocalDownload) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            upsertTrack(db, download.track)
            db.insertWithOnConflict(
                TABLE_DOWNLOADS,
                null,
                ContentValues().apply {
                    put("track_id", download.track.id)
                    put("status", download.status)
                    put("progress", download.progress)
                    put("downloaded_bytes", download.downloadedBytes)
                    put("total_bytes", download.totalBytes)
                    put("file_path", download.filePath)
                    put("error", download.error)
                    put("priority", download.priority)
                    put("queue_sequence", download.queueSequence)
                    put("updated_at", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshAll()
    }

    suspend fun removeDownload(trackId: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(TABLE_DOWNLOADS, "track_id = ?", arrayOf(trackId))
        refreshAll()
    }

    suspend fun downloadedFile(trackId: String): String? = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            TABLE_DOWNLOADS,
            arrayOf("file_path", "status"),
            "track_id = ? AND status = ?",
            arrayOf(trackId, DOWNLOAD_COMPLETED),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            cursor.getString(0)?.takeIf { path -> java.io.File(path).isFile }
        }
    }

    fun close() {
        scope.cancel()
        helper.close()
    }

    private fun refreshAll() {
        val db = helper.readableDatabase
        _visitedCollections.value = readCollections(db, TABLE_VISITED, "visited_at DESC")
        _savedCollections.value = readCollections(db, TABLE_SAVED_COLLECTIONS, "saved_at DESC")
        _savedTracks.value = readTracks(db, TABLE_SAVED_TRACKS, "saved_at DESC")
        _history.value = readTracks(db, TABLE_HISTORY, "played_at DESC")
        // Only playlists this app created.
        //
        // Saving a provider playlist also writes a collections row of type
        // "playlist", so an unfiltered read listed remote playlists as the user's
        // own - and offered them in "add to playlist", where adding a track locally
        // would have meant nothing. Saved remote playlists still appear in Your
        // Library through savedCollections.
        _playlists.value = db.query(
            TABLE_COLLECTIONS,
            null,
            "type = ? AND id LIKE ?",
            arrayOf(TYPE_PLAYLIST, "$LOCAL_PLAYLIST_PREFIX%"),
            null,
            null,
            "created_at DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toPlaylist())
            }
        }
        _downloadRecords.value = db.query(
            TABLE_DOWNLOADS,
            null,
            null,
            null,
            null,
            null,
            "updated_at DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toDownload())
            }
        }
    }

    private fun readCollections(
        db: SQLiteDatabase,
        relation: String,
        order: String,
    ): List<MediaCollection> = db.rawQuery(
        """
        SELECT c.* FROM $TABLE_COLLECTIONS c
        INNER JOIN $relation r ON r.collection_id = c.id
        ORDER BY r.$order
        """.replace("ORDER BY r.visited_at DESC", "ORDER BY r.visited_at DESC")
            .replace("ORDER BY r.saved_at DESC", "ORDER BY r.saved_at DESC"),
        null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toCollection())
        }
    }

    private fun readTracks(
        db: SQLiteDatabase,
        relation: String,
        order: String,
    ): List<Track> = db.rawQuery(
        """
        SELECT t.* FROM $TABLE_TRACKS t
        INNER JOIN $relation r ON r.track_id = t.id
        ORDER BY r.$order
        """.replace("ORDER BY r.saved_at DESC", "ORDER BY r.saved_at DESC")
            .replace("ORDER BY r.played_at DESC", "ORDER BY r.played_at DESC"),
        null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toTrack())
        }
    }

    private fun trimVisited(db: SQLiteDatabase) {
        db.execSQL(
            "DELETE FROM $TABLE_VISITED WHERE collection_id NOT IN " +
                "(SELECT collection_id FROM $TABLE_VISITED ORDER BY visited_at DESC LIMIT $MAX_VISITED)",
        )
    }

    private fun compactPlaylistPositions(db: SQLiteDatabase, playlistId: String) {
        db.rawQuery(
            "SELECT track_id FROM $TABLE_PLAYLIST_TRACKS WHERE playlist_id = ? ORDER BY position ASC",
            arrayOf(playlistId),
        ).use { cursor ->
            val ids = buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            ids.forEachIndexed { index, trackId ->
                db.update(
                    TABLE_PLAYLIST_TRACKS,
                    ContentValues().apply { put("position", index) },
                    "playlist_id = ? AND track_id = ?",
                    arrayOf(playlistId, trackId),
                )
            }
        }
    }

    private fun upsertTrack(db: SQLiteDatabase, track: Track) {
        db.insertWithOnConflict(
            TABLE_TRACKS,
            null,
            trackValues(track),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun upsertCollection(db: SQLiteDatabase, collection: MediaCollection) {
        db.insertWithOnConflict(
            TABLE_COLLECTIONS,
            null,
            collectionValues(collection),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun trackValues(track: Track) = ContentValues().apply {
        put("id", track.id)
        put("title", track.title)
        put("artist_name", track.artistName)
        put("album_title", track.albumTitle)
        put("duration_ms", track.durationMs)
        put("is_explicit", if (track.isExplicit) 1 else 0)
        put("artwork_url", track.artworkUrl)
        put("audio_url", track.audioUrl)
        put("external_url", track.externalUrl)
        put("video_id", track.videoId)
        put("album_id", track.albumId)
        put("artist_id", track.artistId)
    }

    private fun collectionValues(collection: MediaCollection) = ContentValues().apply {
        put("id", collection.id)
        put("type", collectionType(collection))
        put("title", collection.title)
        put("subtitle", collection.subtitle)
        put("artwork_url", collection.artworkUrl)
        when (collection) {
            is Album -> {
                put("artist_name", collection.artistName)
                put("year", collection.year)
                put("genre", collection.genre)
                put("track_count", collection.trackCount)
            }
            is Artist -> {
                put("artist_name", collection.name)
                put("genre", collection.genre)
            }
            is Playlist -> {
                put("description", collection.description)
                put("owner_name", collection.ownerName)
                put("is_pinned", if (collection.isPinned) 1 else 0)
            }
        }
    }

    private fun collectionType(collection: MediaCollection): String = when (collection) {
        is Album -> TYPE_ALBUM
        is Artist -> TYPE_ARTIST
        is Playlist -> TYPE_PLAYLIST
    }

    private fun android.database.Cursor.toTrack(): Track = Track(
        id = getString(getColumnIndexOrThrow("id")),
        title = getString(getColumnIndexOrThrow("title")),
        artistName = getString(getColumnIndexOrThrow("artist_name")),
        albumTitle = getString(getColumnIndexOrThrow("album_title")),
        durationMs = getLong(getColumnIndexOrThrow("duration_ms")),
        isExplicit = getInt(getColumnIndexOrThrow("is_explicit")) == 1,
        artworkUrl = getNullableString("artwork_url"),
        audioUrl = getNullableString("audio_url"),
        externalUrl = getNullableString("external_url"),
        videoId = getNullableString("video_id"),
        albumId = getNullableString("album_id"),
        artistId = getNullableString("artist_id"),
    )

    private fun android.database.Cursor.toCollection(): MediaCollection {
        return when (getString(getColumnIndexOrThrow("type"))) {
            TYPE_ALBUM -> Album(
                id = getString(getColumnIndexOrThrow("id")),
                title = getString(getColumnIndexOrThrow("title")),
                // Read as nullable: the column is genuinely empty for collections
                // that arrived without a performer, and coercing the column *index*
                // (which is what this used to do) read a different column entirely.
                artistName = getNullableString("artist_name").orEmpty(),
                year = getNullableInt("year"),
                genre = getNullableString("genre"),
                trackCount = getInt(getColumnIndexOrThrow("track_count")),
                artworkUrl = getNullableString("artwork_url"),
            )
            TYPE_ARTIST -> Artist(
                id = getString(getColumnIndexOrThrow("id")),
                name = getString(getColumnIndexOrThrow("artist_name")),
                genre = getNullableString("genre"),
                artworkUrl = getNullableString("artwork_url"),
            )
            else -> toPlaylist()
        }
    }

    private fun android.database.Cursor.toPlaylist(): Playlist = Playlist(
        id = getString(getColumnIndexOrThrow("id")),
        title = getString(getColumnIndexOrThrow("title")),
        description = getString(getColumnIndexOrThrow("description")),
        ownerName = getString(getColumnIndexOrThrow("owner_name")),
        trackIds = emptyList(),
        artworkUrl = getNullableString("artwork_url"),
        isPinned = getInt(getColumnIndexOrThrow("is_pinned")) == 1,
    )

    private fun android.database.Cursor.toDownload(): LocalDownload = LocalDownload(
        track = toTrackFromDownload(),
        status = getString(getColumnIndexOrThrow("status")),
        progress = getInt(getColumnIndexOrThrow("progress")),
        downloadedBytes = getLong(getColumnIndexOrThrow("downloaded_bytes")),
        totalBytes = getLong(getColumnIndexOrThrow("total_bytes")),
        filePath = getNullableString("file_path"),
        error = getNullableString("error"),
        priority = getInt(getColumnIndexOrThrow("priority")),
        queueSequence = getLong(getColumnIndexOrThrow("queue_sequence")),
    )

    private fun android.database.Cursor.toTrackFromDownload(): Track {
        val trackId = getString(getColumnIndexOrThrow("track_id"))
        return helper.readableDatabase.query(
            TABLE_TRACKS,
            null,
            "id = ?",
            arrayOf(trackId),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toTrack() else Track(
                id = trackId,
                title = trackId,
                artistName = "Unknown artist",
                albumTitle = "Unknown album",
                durationMs = 0L,
            )
        }
    }

    private fun android.database.Cursor.getNullableString(column: String): String? =
        getString(getColumnIndexOrThrow(column))?.takeIf { it.isNotBlank() }

    private fun android.database.Cursor.getNullableInt(column: String): Int? =
        if (isNull(getColumnIndexOrThrow(column))) null else getInt(getColumnIndexOrThrow(column))

    data class LocalDownload(
        val track: Track,
        val status: String,
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val filePath: String? = null,
        val error: String? = null,
        val priority: Int = 1,
        val queueSequence: Long = 0L,
    )

    private class StoreHelper(context: Context) : SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION,
    ) {
        override fun onCreate(db: SQLiteDatabase) = createSchema(db)

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN priority INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE downloads ADD COLUMN queue_sequence INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE downloads SET queue_sequence = updated_at WHERE queue_sequence = 0")
            }
            createSchema(db)
        }

        private fun createSchema(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tracks(
                    id TEXT PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL,
                    artist_name TEXT NOT NULL,
                    album_title TEXT NOT NULL,
                    duration_ms INTEGER NOT NULL,
                    is_explicit INTEGER NOT NULL DEFAULT 0,
                    artwork_url TEXT,
                    audio_url TEXT,
                    external_url TEXT,
                    video_id TEXT,
                    album_id TEXT,
                    artist_id TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS collections(
                    id TEXT PRIMARY KEY NOT NULL,
                    type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    subtitle TEXT NOT NULL,
                    artwork_url TEXT,
                    artist_name TEXT NOT NULL DEFAULT '',
                    year INTEGER,
                    genre TEXT,
                    track_count INTEGER NOT NULL DEFAULT 0,
                    description TEXT NOT NULL DEFAULT '',
                    owner_name TEXT NOT NULL DEFAULT '',
                    is_pinned INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS saved_tracks(track_id TEXT PRIMARY KEY NOT NULL, saved_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS visited_collections(collection_id TEXT PRIMARY KEY NOT NULL, visited_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS saved_collections(collection_id TEXT PRIMARY KEY NOT NULL, saved_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS history(track_id TEXT PRIMARY KEY NOT NULL, played_at INTEGER NOT NULL, play_count INTEGER NOT NULL DEFAULT 1)")
            db.execSQL("CREATE TABLE IF NOT EXISTS playlist_tracks(playlist_id TEXT NOT NULL, track_id TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlist_id, track_id))")
            db.execSQL("CREATE TABLE IF NOT EXISTS queue(position INTEGER PRIMARY KEY NOT NULL, track_id TEXT NOT NULL)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS downloads(
                    track_id TEXT PRIMARY KEY NOT NULL,
                    status TEXT NOT NULL,
                    progress INTEGER NOT NULL DEFAULT 0,
                    downloaded_bytes INTEGER NOT NULL DEFAULT 0,
                    total_bytes INTEGER NOT NULL DEFAULT 0,
                    file_path TEXT,
                    error TEXT,
                    priority INTEGER NOT NULL DEFAULT 1,
                    queue_sequence INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }

        private companion object {
            const val DATABASE_NAME = "spotkofi_local.db"
            const val DATABASE_VERSION = 2
        }
    }

    private companion object {
        /**
         * Id prefix for playlists created in this app.
         *
         * It is what separates the user's own playlists from provider playlists
         * they merely saved, both of which live in the same table.
         */
        const val LOCAL_PLAYLIST_PREFIX = "local:playlist:"

        const val TABLE_TRACKS = "tracks"
        const val TABLE_COLLECTIONS = "collections"
        const val TABLE_SAVED_TRACKS = "saved_tracks"
        const val TABLE_VISITED = "visited_collections"
        const val TABLE_SAVED_COLLECTIONS = "saved_collections"
        const val TABLE_HISTORY = "history"
        const val TABLE_PLAYLIST_TRACKS = "playlist_tracks"
        const val TABLE_QUEUE = "queue"
        const val TABLE_DOWNLOADS = "downloads"
        const val TYPE_ALBUM = "album"
        const val TYPE_ARTIST = "artist"
        const val TYPE_PLAYLIST = "playlist"
        const val DOWNLOAD_COMPLETED = "completed"
        const val MAX_VISITED = 40
    }
}
