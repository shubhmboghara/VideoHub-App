package com.videhub.audio

import androidx.media3.common.Player
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SleepTimerManager {
    private var timerJob: Job? = null
    private var endOfTrackListener: Player.Listener? = null
    private var activePlayer: Player? = null

    private val _remainingSeconds = MutableStateFlow<Int?>(null)
    val remainingSeconds: StateFlow<Int?> = _remainingSeconds.asStateFlow()

    private val _isEndOfTrackMode = MutableStateFlow(false)
    val isEndOfTrackMode: StateFlow<Boolean> = _isEndOfTrackMode.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun startTimer(minutes: Int, player: Player?) {
        cancelTimer()
        if (minutes <= 0 || player == null) return

        activePlayer = player
        _isEndOfTrackMode.value = false
        val totalSeconds = minutes * 60
        _remainingSeconds.value = totalSeconds

        timerJob = scope.launch {
            var currentSec = totalSeconds
            while (currentSec > 0) {
                delay(1000L)
                currentSec--
                _remainingSeconds.value = currentSec

                // Gentle fade out in the last 30 seconds
                if (currentSec in 1..30) {
                    val fadeVol = (currentSec / 30f).coerceIn(0.05f, 1f)
                    activePlayer?.volume = fadeVol
                }
            }

            // Time expired
            onTimerExpired()
        }
    }

    fun startEndOfTrackTimer(player: Player?) {
        cancelTimer()
        if (player == null) return

        activePlayer = player
        _isEndOfTrackMode.value = true
        _remainingSeconds.value = null

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    onTimerExpired()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onTimerExpired()
                }
            }
        }
        endOfTrackListener = listener
        player.addListener(listener)
    }

    fun addFiveMinutes() {
        val current = _remainingSeconds.value ?: 0
        val newTime = current + 300
        _remainingSeconds.value = newTime
        activePlayer?.volume = 1.0f // Restore volume if it was fading out

        // If timer job was already finished or null, restart countdown
        if (timerJob == null || timerJob?.isActive != true) {
            timerJob = scope.launch {
                var currentSec = newTime
                while (currentSec > 0) {
                    delay(1000L)
                    currentSec--
                    _remainingSeconds.value = currentSec
                    if (currentSec in 1..30) {
                        val fadeVol = (currentSec / 30f).coerceIn(0.05f, 1f)
                        activePlayer?.volume = fadeVol
                    }
                }
                onTimerExpired()
            }
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null

        endOfTrackListener?.let { listener ->
            activePlayer?.removeListener(listener)
        }
        endOfTrackListener = null

        activePlayer?.volume = 1.0f
        activePlayer = null

        _remainingSeconds.value = null
        _isEndOfTrackMode.value = false
    }

    private fun onTimerExpired() {
        try {
            activePlayer?.pause()
            activePlayer?.volume = 1.0f
        } catch (e: Exception) {
            // Ignore
        }
        cancelTimer()
    }

    fun formatRemainingTime(): String {
        val secs = _remainingSeconds.value ?: return if (_isEndOfTrackMode.value) "End of track" else ""
        val mins = secs / 60
        val remainingSecs = secs % 60
        return String.format("%02d:%02d", mins, remainingSecs)
    }
}
