package com.videhub.audio

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.videhub.data.dataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioPreset(val displayName: String, val speed: Float, val pitch: Float) {
    STANDARD("Normal (1.0x)", 1.0f, 1.0f),
    NIGHTCORE("✨ Nightcore", 1.25f, 1.25f),
    SLOWED_REVERB("🌌 Slowed & Chill", 0.85f, 0.85f),
    LOFI_WARM("☕ Lo-Fi Relax", 0.90f, 0.95f),
    SPEED_READ("🎙️ Podcast (1.25x)", 1.25f, 1.0f),
    DOUBLE_TIME("⚡ Hyper (1.5x)", 1.5f, 1.15f)
}

object AudioSpeedPitchManager {
    private val SPEED_KEY = floatPreferencesKey("audio_speed_multiplier")
    private val PITCH_KEY = floatPreferencesKey("audio_pitch_multiplier")

    private val _speed = MutableStateFlow(1.0f)
    val speed = _speed.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch = _pitch.asStateFlow()

    private val _currentPreset = MutableStateFlow(AudioPreset.STANDARD)
    val currentPreset = _currentPreset.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun init(context: Context, player: Player?) {
        scope.launch {
            context.dataStore.data.collect { prefs ->
                val s = prefs[SPEED_KEY] ?: 1.0f
                val p = prefs[PITCH_KEY] ?: 1.0f
                _speed.value = s
                _pitch.value = p
                player?.playbackParameters = PlaybackParameters(s, p)
            }
        }
    }

    fun applyPreset(preset: AudioPreset, context: Context, player: Player?) {
        _currentPreset.value = preset
        setSpeedAndPitch(context, player, preset.speed, preset.pitch)
    }

    fun setSpeed(context: Context, player: Player?, newSpeed: Float) {
        setSpeedAndPitch(context, player, newSpeed, _pitch.value)
    }

    fun setPitch(context: Context, player: Player?, newPitch: Float) {
        setSpeedAndPitch(context, player, _speed.value, newPitch)
    }

    fun setSpeedAndPitch(context: Context, player: Player?, newSpeed: Float, newPitch: Float) {
        val safeSpeed = newSpeed.coerceIn(0.25f, 2.5f)
        val safePitch = newPitch.coerceIn(0.25f, 2.5f)
        _speed.value = safeSpeed
        _pitch.value = safePitch

        player?.playbackParameters = PlaybackParameters(safeSpeed, safePitch)

        scope.launch(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[SPEED_KEY] = safeSpeed
                prefs[PITCH_KEY] = safePitch
            }
        }
    }

    fun reset(context: Context, player: Player?) {
        applyPreset(AudioPreset.STANDARD, context, player)
    }
}
