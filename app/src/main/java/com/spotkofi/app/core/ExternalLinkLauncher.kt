package com.spotkofi.app.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.spotkofi.app.data.model.Track

/** Opens only official YouTube HTTPS links in the user's installed handler. */
class ExternalLinkLauncher(context: Context) {

    private val appContext = context.applicationContext

    fun open(track: Track): Boolean {
        val url = track.externalUrl
            ?.takeIf(::isAllowedYouTubeUrl)
            ?: youtubeSearchUrl(track.artistName, track.title)
        if (!isAllowedYouTubeUrl(url)) return false

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            appContext.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isAllowedYouTubeUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.lowercase() in ALLOWED_HOSTS
    }

    private companion object {
        val ALLOWED_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com")

        fun youtubeSearchUrl(artistName: String, title: String): String =
            "https://www.youtube.com/results?search_query=${Uri.encode("$artistName $title")}" 
    }
}
