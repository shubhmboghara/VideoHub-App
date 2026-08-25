package com.videhub.audio

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.media3.common.Player
import com.videhub.data.dataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

object CrossfadeManager {
    private val CROSSFADE_SECONDS_KEY = intPreferencesKey("audio_crossfade_seconds")

    private val _crossfadeDurationSec = MutableStateFlow(0) // 0 = Off
    val crossfadeDurationSec = _crossfadeDurationSec.asStateFlow()

    private var fadeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun init(context: Context) {
        scope.launch {
            getCrossfadeSecondsFlow(context).collect { seconds ->
                _crossfadeDurationSec.value = seconds
            }
        }
    }

    private fun getCrossfadeSecondsFlow(context: Context): Flow<Int> {
        return context.dataStore.data.map { preferences ->
            preferences[CROSSFADE_SECONDS_KEY] ?: 0
        }
    }

    fun setCrossfadeDuration(context: Context, seconds: Int) {
        _crossfadeDurationSec.value = seconds
        scope.launch(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                preferences[CROSSFADE_SECONDS_KEY] = seconds
            }
        }
    }

    /**
     * Checks if current track is near the end and applies gentle fade-out if crossfade is enabled.
     */
    fun checkAndApplyFadeOut(player: Player) {
        val crossfade = _crossfadeDurationSec.value
        if (crossfade <= 0) return

        val duration = player.duration
        val position = player.currentPosition
        if (duration <= 0) return

        val remainingMs = duration - position
        val fadeMs = crossfade * 1000L

        if (remainingMs in 1..fadeMs && fadeJob == null) {
            val startVol = player.volume
            fadeJob = scope.launch {
                val steps = 20
                val stepInterval = (remainingMs / steps).coerceAtLeast(50L)
                for (i in 1..steps) {
                    val factor = 1f - (i.toFloat() / steps.toFloat())
                    player.volume = (startVol * factor).coerceIn(0.05f, 1f)
                    delay(stepInterval)
                }
            }
        }
    }

    /**
     * Smoothly fades in track at the start of playback.
     */
    fun applyFadeIn(player: Player) {
        val crossfade = _crossfadeDurationSec.value
        if (crossfade <= 0) {
            player.volume = 1f
            return
        }

        fadeJob?.cancel()
        fadeJob = scope.launch {
            player.volume = 0.05f
            val fadeDurationMs = (crossfade * 500L).coerceIn(800L, 3000L)
            val steps = 20
            val interval = fadeDurationMs / steps
            for (i in 1..steps) {
                val vol = (i.toFloat() / steps.toFloat())
                player.volume = vol.coerceIn(0f, 1f)
                delay(interval)
            }
            player.volume = 1f
            fadeJob = null
        }
    }

    fun resetFade(player: Player) {
        fadeJob?.cancel()
        fadeJob = null
        player.volume = 1f
    }
}
