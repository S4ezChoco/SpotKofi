package com.spotkofi.app.data.remote

import android.util.Log
import dev.maxrave.pipepipe.extractor.NewPipe
import dev.maxrave.pipepipe.extractor.ServiceList
import dev.maxrave.pipepipe.extractor.downloader.CancellableCall
import dev.maxrave.pipepipe.extractor.downloader.Downloader
import dev.maxrave.pipepipe.extractor.downloader.Request
import dev.maxrave.pipepipe.extractor.downloader.Response
import dev.maxrave.pipepipe.extractor.exceptions.ReCaptchaException
import dev.maxrave.pipepipe.extractor.search.SearchInfo
import dev.maxrave.pipepipe.extractor.stream.StreamInfo
import dev.maxrave.pipepipe.extractor.stream.StreamInfoItem
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Resolves a YouTube video's actual audio stream before playback.
 *
 * The iTunes URL is intentionally not used here: it is a 30-second preview.
 * PipePipe follows YouTube's current player/signature flow and returns one of
 * the audio-only stream URLs that the reference app uses.
 */
internal class YouTubeStreamExtractor {

    fun searchVideoId(query: String): String? {
        if (query.isBlank()) return null

        val videoId = searchVideoIdFromHtml(query) ?: runCatching {
            ensureInitialized()
            val searchInfo = SearchInfo.getInfo(
                ServiceList.YouTube.getSearchExtractor(query),
            )
            searchInfo.relatedItems
                .asSequence()
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { videoIdRegex.find(it.url)?.groupValues?.getOrNull(1) }
                .firstOrNull()
        }.onFailure { error ->
            Log.e(TAG, "PipePipe search failed for $query", error)
        }.getOrNull()

        if (videoId == null) {
            Log.w(TAG, "YouTube search returned no video for $query")
        } else {
            Log.d(TAG, "Found YouTube video $videoId for $query")
        }
        return videoId
    }

    private fun searchVideoIdFromHtml(query: String): String? = runCatching {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("www.youtube.com")
            .addPathSegment("results")
            .addQueryParameter("search_query", query)
            .build()
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "CONSENT=YES+cb")
            .build()

        OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "YouTube HTML search returned HTTP ${response.code} for $query")
                return@use null
            }
            val html = response.body.string()
            val searchableHtml = html
                .replace("\\x22", "\"")
                .replace("\\u0022", "\"")
            val rendererPattern =
                Regex("\\\"videoRenderer\\\"\\s*:\\s*\\{[^}]*?\\\"videoId\\\"\\s*:\\s*\\\"([A-Za-z0-9_-]{11})\\\"")
            val videoIdPattern =
                Regex("\\\"videoId\\\"\\s*:\\s*\\\"([A-Za-z0-9_-]{11})\\\"")
            val videoId = rendererPattern.find(searchableHtml)?.groupValues?.getOrNull(1)
                ?: videoIdPattern.find(searchableHtml)?.groupValues?.getOrNull(1)
                ?: videoIdRegex.find(searchableHtml)?.groupValues?.getOrNull(1)
            val markerIndex = sequenceOf(
                searchableHtml.indexOf("videoRenderer"),
                searchableHtml.indexOf("videoId"),
                searchableHtml.indexOf("watch?v="),
                searchableHtml.indexOf("ytInitialData"),
            ).filter { it >= 0 }.firstOrNull()
            val markerSnippet = markerIndex?.let { index ->
                searchableHtml.substring(index, minOf(searchableHtml.length, index + 240))
                    .replace(Regex("\\s+"), " ")
            }
            Log.d(
                TAG,
                "YouTube HTML search code=${response.code} bytes=${html.length} " +
                    "videoId=$videoId markerIndex=$markerIndex snippet=$markerSnippet for $query",
            )
            videoId
        }
    }.onFailure { error ->
        Log.e(TAG, "YouTube HTML search failed for $query", error)
    }.getOrNull()

    fun getAudioUrl(videoId: String): String? {
        if (videoId.isBlank()) return null

        return runCatching {
            ensureInitialized()
            val info = StreamInfo.getInfo(
                ServiceList.YouTube,
                "https://music.youtube.com/watch?v=$videoId",
            )
            info.audioStreams
                .asSequence()
                .map { it.content }
                .firstOrNull { it.isNotBlank() }
        }.onFailure { error ->
            Log.e(TAG, "Unable to resolve YouTube audio for $videoId", error)
        }.getOrNull()
    }

    private companion object {
        const val TAG = "SpotKofiStream"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36 " +
                "Chrome/140.0.0.0 Mobile Safari/537.36"

        private val videoIdRegex = Regex("(?:v=|youtu\\.be/|/shorts/)([A-Za-z0-9_-]{11})")

        private val initLock = Any()
        @Volatile
        private var initialized = false

        fun ensureInitialized() {
            if (initialized) return
            synchronized(initLock) {
                if (initialized) return
                NewPipe.init(YouTubeDownloader())
                initialized = true
            }
        }
    }

    private class YouTubeDownloader : Downloader() {
        private val client = OkHttpClient()

        @Throws(IOException::class, ReCaptchaException::class)
        override fun execute(request: Request): Response {
            val requestBuilder = okhttp3.Request.Builder()
                .url(request.url())
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .header("User-Agent", USER_AGENT)

            request.headers().forEach { (name, values) ->
                requestBuilder.removeHeader(name)
                values.forEach { value -> requestBuilder.addHeader(name, value) }
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 429) {
                    throw ReCaptchaException("YouTube requested a reCAPTCHA challenge", request.url())
                }

                val responseBytes = response.body.bytes()
                val responseBody = responseBytes.toString(Charsets.UTF_8)
                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    responseBody,
                    responseBytes,
                    response.request.url.toString(),
                )
            }
        }

        override fun executeAsync(
            request: Request,
            callback: Downloader.AsyncCallback?,
        ): CancellableCall {
            val call = client.newCall(buildRequest(request))
            val cancellableCall = CancellableCall(call)
            call.enqueue(
                object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: IOException) {
                        cancellableCall.setFinished()
                        callback?.onError(e)
                    }

                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        try {
                            if (response.code == 429) {
                                callback?.onError(
                                    ReCaptchaException(
                                        "YouTube requested a reCAPTCHA challenge",
                                        request.url(),
                                    ),
                                )
                                return
                            }
                            response.use { callback?.onSuccess(it.toPipeResponse()) }
                        } catch (error: Exception) {
                            callback?.onError(error)
                        } finally {
                            cancellableCall.setFinished()
                        }
                    }
                },
            )
            return cancellableCall
        }

        private fun buildRequest(request: Request): okhttp3.Request =
            okhttp3.Request.Builder()
                .url(request.url())
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .header("User-Agent", USER_AGENT)
                .apply {
                    request.headers().forEach { (name, values) ->
                        removeHeader(name)
                        values.forEach { value -> addHeader(name, value) }
                    }
                }
                .build()

        private fun okhttp3.Response.toPipeResponse(): Response {
            val responseBytes = body.bytes()
            return Response(
                code,
                message,
                headers.toMultimap(),
                responseBytes.toString(Charsets.UTF_8),
                responseBytes,
                request.url.toString(),
            )
        }
    }
}
