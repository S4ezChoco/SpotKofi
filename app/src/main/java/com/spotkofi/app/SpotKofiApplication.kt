package com.spotkofi.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.spotkofi.app.core.AppContainer
import okhttp3.OkHttpClient
import okio.FileSystem

class SpotKofiApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(context = this)
        container.downloadManager.onApplicationReady()
    }

    override fun onTerminate() {
        // Nothing in the app owns an in-process media decoder. This still clears
        // the external-link player's current UI state for instrumentation.
        container.release()
        super.onTerminate()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { OkHttpClient() },
                    ),
                )
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .diskCache(
                DiskCache
                    .Builder()
                    .directory(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "spotkofi_image_cache")
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build(),
            )
            .crossfade(true)
            .build()
}
