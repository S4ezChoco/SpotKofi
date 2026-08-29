package com.spotkofi.app.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Simple audio player using Android MediaPlayer
 */
class AudioPlayer(private val context: Context) {
    
    private var mediaPlayer: MediaPlayer? = null
    private var isCurrentlyPlaying = false
    private var currentStreamUrl: String? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    private var onPlaybackProgressChanged: ((Long, Long) -> Unit)? = null
    private var onPlaybackError: ((String) -> Unit)? = null
    
    fun play(streamUrl: String) {
        stop()
        currentStreamUrl = streamUrl
        Log.d(TAG, "Preparing audio URL")
        
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.parse(streamUrl))
                prepareAsync()
                
                setOnPreparedListener {
                    Log.d(TAG, "Audio prepared durationMs=${duration}")
                    isCurrentlyPlaying = true
                    start()
                    onPlaybackStateChanged?.invoke(true)
                    startProgressUpdates()
                }
                
                setOnCompletionListener {
                    isCurrentlyPlaying = false
                    onPlaybackStateChanged?.invoke(false)
                    progressJob?.cancel()
                }
                
                setOnErrorListener { _, what, extra ->
                    isCurrentlyPlaying = false
                    onPlaybackStateChanged?.invoke(false)
                    val errorMsg = "MediaPlayer error: $what, extra: $extra"
                    Log.e(TAG, errorMsg)
                    onPlaybackError?.invoke(errorMsg)
                    progressJob?.cancel()
                    true
                }
            }
        } catch (e: Exception) {
            isCurrentlyPlaying = false
            Log.e(TAG, "Failed to prepare audio", e)
            onPlaybackError?.invoke("Failed to play audio: ${e.message}")
        }
    }
    
    fun pause() {
        mediaPlayer?.pause()
        isCurrentlyPlaying = false
        onPlaybackStateChanged?.invoke(false)
        progressJob?.cancel()
    }
    
    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                isCurrentlyPlaying = true
                onPlaybackStateChanged?.invoke(true)
                startProgressUpdates()
            }
        }
    }
    
    fun stop() {
        progressJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        isCurrentlyPlaying = false
        onPlaybackStateChanged?.invoke(false)
    }
    
    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
    }
    
    fun getCurrentPosition(): Long {
        return mediaPlayer?.currentPosition?.toLong() ?: 0L
    }
    
    fun getDuration(): Long {
        return mediaPlayer?.duration?.toLong() ?: 0L
    }
    
    fun isPlaying(): Boolean {
        return isCurrentlyPlaying
    }
    
    fun setOnPlaybackStateChanged(callback: (Boolean) -> Unit) {
        onPlaybackStateChanged = callback
    }
    
    fun setOnPlaybackProgressChanged(callback: (Long, Long) -> Unit) {
        onPlaybackProgressChanged = callback
    }
    
    fun setOnPlaybackError(callback: (String) -> Unit) {
        onPlaybackError = callback
    }
    
    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isCurrentlyPlaying) {
                val position = mediaPlayer?.currentPosition?.toLong() ?: 0L
                val duration = mediaPlayer?.duration?.toLong() ?: 0L
                onPlaybackProgressChanged?.invoke(position, duration)
                delay(1000)
            }
        }
    }
    
    fun release() {
        stop()
        progressJob?.cancel()
    }

    private companion object {
        const val TAG = "SpotKofiAudio"
    }
}