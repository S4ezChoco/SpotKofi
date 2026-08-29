package com.spotkofi.app.player

import android.content.Context
import android.util.Log
import com.spotkofi.app.data.model.PlaybackState
import com.spotkofi.app.data.model.RepeatMode
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.service.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MusicPlayerController(
    private val context: Context,
    private val musicService: MusicService,
) : PlayerController {
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val audioPlayer = AudioPlayer(context)
    private var currentTrackIndex = 0
    
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()
    
    init {
        audioPlayer.setOnPlaybackStateChanged { isPlaying ->
            _state.update { it.copy(isPlaying = isPlaying) }
        }
        
        audioPlayer.setOnPlaybackProgressChanged { positionMs, durationMs ->
            _state.update { 
                it.copy(
                    positionMs = positionMs,
                    streamDurationMs = durationMs.takeIf { d -> d > 0 }
                )
            }
        }
        
        audioPlayer.setOnPlaybackError { error ->
            _state.update { it.copy(error = error) }
        }
    }
    
    override fun play(track: Track, queue: List<Track>) {
        coroutineScope.launch {
            if (queue.isNotEmpty()) {
                _queue.value = queue
                currentTrackIndex = queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
            } else {
                _queue.value = listOf(track)
                currentTrackIndex = 0
            }
            
            startPlayback(track)
        }
    }
    
    override fun togglePlayPause() {
        if (audioPlayer.isPlaying()) {
            audioPlayer.pause()
        } else {
            audioPlayer.resume()
        }
    }
    
    override fun next() {
        coroutineScope.launch {
            val queue = _queue.value
            if (queue.isEmpty()) return@launch
            
            val currentMode = _state.value.repeatMode
            when (currentMode) {
                RepeatMode.One -> {
                    val currentTrack = queue.getOrNull(currentTrackIndex)
                    currentTrack?.let { startPlayback(it) }
                }
                RepeatMode.All -> {
                    currentTrackIndex = (currentTrackIndex + 1) % queue.size
                    val nextTrack = queue[currentTrackIndex]
                    startPlayback(nextTrack)
                }
                RepeatMode.Off -> {
                    if (currentTrackIndex < queue.size - 1) {
                        currentTrackIndex++
                        val nextTrack = queue[currentTrackIndex]
                        startPlayback(nextTrack)
                    } else {
                        stop()
                    }
                }
            }
        }
    }
    
    override fun previous() {
        coroutineScope.launch {
            val queue = _queue.value
            if (queue.isEmpty()) return@launch
            
            if (_state.value.positionMs > 5000) {
                val currentTrack = queue.getOrNull(currentTrackIndex)
                currentTrack?.let { startPlayback(it) }
            } else {
                if (currentTrackIndex > 0) {
                    currentTrackIndex--
                    val prevTrack = queue[currentTrackIndex]
                    startPlayback(prevTrack)
                } else {
                    val firstTrack = queue[0]
                    startPlayback(firstTrack)
                }
            }
        }
    }
    
    override fun seekToFraction(fraction: Float) {
        val currentTrack = _state.value.track ?: return
        val newPositionMs = (currentTrack.durationMs * fraction.coerceIn(0f, 1f)).toLong()
        audioPlayer.seekTo(newPositionMs)
    }
    
    override fun toggleShuffle() {
        val newShuffled = !_state.value.isShuffled
        
        if (newShuffled && _queue.value.size > 1) {
            val shuffledQueue = _queue.value.shuffled()
            val currentTrack = _state.value.track
            
            // Ensure current track stays at position 0
            val currentIndex = shuffledQueue.indexOfFirst { it.id == currentTrack?.id }
            if (currentIndex > 0 && currentTrack != null) {
                val mutableQueue = shuffledQueue.toMutableList()
                mutableQueue.removeAt(currentIndex)
                mutableQueue.add(0, currentTrack)
                _queue.value = mutableQueue
                currentTrackIndex = 0
            } else {
                _queue.value = shuffledQueue
                currentTrackIndex = 0
            }
        }
        
        _state.update { it.copy(isShuffled = newShuffled) }
    }
    
    override fun cycleRepeatMode() {
        val currentMode = _state.value.repeatMode
        val nextMode = when (currentMode) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        }
        _state.update { it.copy(repeatMode = nextMode) }
    }
    
    override fun toggleSaved() {
        _state.update { it.copy(isSaved = !it.isSaved) }
    }
    
    override fun stop() {
        audioPlayer.stop()
        _state.update { 
            it.copy(
                track = null,
                isPlaying = false,
                positionMs = 0L,
                error = null
            )
        }
    }
    
    private suspend fun startPlayback(track: Track) {
        _state.update {
            it.copy(
                track = track,
                isPlaying = false,
                positionMs = 0L,
                error = null,
                streamDurationMs = track.durationMs
            )
        }

        val audioUrl = track.audioUrl
        val videoId = track.videoId
        Log.d(
            TAG,
            "startPlayback title=${track.title} audioUrlPresent=${!audioUrl.isNullOrBlank()} " +
                "videoIdPresent=${!videoId.isNullOrBlank()}",
        )

        val urlToPlay = if (!videoId.isNullOrBlank()) {
            // A YouTube-backed track must never silently fall back to the iTunes
            // preview. That fallback is what changed a real duration to 30 sec.
            val streamUrl = musicService.getStreamUrl(videoId)
            if (streamUrl.isNullOrBlank()) {
                Log.e(TAG, "No full-length YouTube stream resolved for ${track.title}")
                _state.update {
                    it.copy(error = "Full-length stream unavailable; preview was not used")
                }
                return
            }
            Log.d(TAG, "Using resolved full-length YouTube stream for ${track.title}")
            streamUrl
        } else {
            // Tracks without a YouTube ID can still use the catalog preview.
            audioUrl
        }

        if (urlToPlay.isNullOrBlank()) {
            _state.update {
                it.copy(error = "No playable audio is available for this track")
            }
            return
        }

        audioPlayer.play(urlToPlay)
    }

    private companion object {
        const val TAG = "SpotKofiPlayer"
    }
}