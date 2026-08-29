package com.spotkofi.app.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Bounded streaming cache. It is intentionally separate from the download
 * directory so LRU eviction can never remove a user's offline songs.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackCache(context: Context) {

    private val cache = SimpleCache(
        File(context.applicationContext.cacheDir, "audio_stream_cache"),
        LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
        StandaloneDatabaseProvider(context.applicationContext),
    )

    private val upstream = DefaultDataSource.Factory(
        context.applicationContext,
        DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true),
    )

    val dataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun release() {
        cache.release()
    }

    private companion object {
        const val MAX_CACHE_BYTES = 128L * 1024L * 1024L
        const val USER_AGENT = "SpotKofi/1.0 (Android)"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
