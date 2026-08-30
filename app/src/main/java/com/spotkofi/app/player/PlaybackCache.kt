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
import com.spotkofi.app.core.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /** Bytes currently held, for the Settings storage row. */
    val sizeBytes: Long get() = runCatching { cache.cacheSpace }.getOrDefault(0L)

    /**
     * Drops every cached span.
     *
     * Keys are removed one at a time rather than deleting the directory: the cache
     * index is still open, and wiping files behind its back leaves it claiming to
     * hold data that is gone.
     *
     * Suspending because this is a file delete per cached span, which is far too
     * much to do on the thread that drew the button.
     */
    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching {
            cache.keys.toSet().forEach { key ->
                cache.removeResource(key)
            }
        }
        Unit
    }

    fun release() {
        cache.release()
    }

    private companion object {
        const val MAX_CACHE_BYTES = 128L * 1024L * 1024L
        val USER_AGENT: String = AppConstants.USER_AGENT
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
