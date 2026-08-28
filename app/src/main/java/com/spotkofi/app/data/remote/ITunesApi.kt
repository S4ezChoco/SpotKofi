package com.spotkofi.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Public iTunes Search API client. It requires no client credential. */
internal class ItunesApi(
    private val client: OkHttpClient = defaultClient(),
    private val countryCode: String = defaultCountryCode(),
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun search(
        term: String,
        entity: String,
        limit: Int = 25,
    ): List<ItunesResult> {
        if (term.isBlank()) return emptyList()
        return get<ItunesResponse>(
            url(
                "search",
                "term" to term,
                "media" to "music",
                "entity" to entity,
                "limit" to limit.coerceIn(1, 200).toString(),
                "country" to countryCode,
            ),
        ).results
    }

    suspend fun lookup(
        id: Long,
        entity: String,
        limit: Int = 50,
    ): List<ItunesResult> = get<ItunesResponse>(
        url(
            "lookup",
            "id" to id.toString(),
            "entity" to entity,
            "limit" to limit.coerceIn(1, 200).toString(),
            "country" to countryCode,
        ),
    ).results

    private fun url(path: String, vararg parameters: Pair<String, String>): String =
        ("$BASE_URL/$path").toHttpUrl().newBuilder()
            .apply { parameters.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
            .toString()

    private suspend inline fun <reified T> get(requestUrl: String): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(requestUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "SpotKofi/1.0")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw CatalogException("iTunes request failed (${response.code})")
                    }
                    json.decodeFromString<T>(response.body.string())
                }
            } catch (io: IOException) {
                throw CatalogException("Could not reach iTunes", io)
            } catch (catalog: CatalogException) {
                throw catalog
            } catch (other: Exception) {
                throw CatalogException("Could not read the iTunes response", other)
            }
        }

    private companion object {
        const val BASE_URL = "https://itunes.apple.com"

        fun defaultCountryCode(): String = Locale.getDefault().country
            .uppercase(Locale.ROOT)
            .takeIf { it.length == 2 && it.all(Char::isLetter) }
            ?: "US"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
