package com.videhub.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EqualizerManager {
    private const val TAG = "EqualizerManager"
    private const val PREFS_NAME = "videhub_equalizer_prefs"
    private const val KEY_ENABLED = "eq_enabled"
    private const val KEY_PRESET = "eq_preset"
    private const val KEY_BASS_STRENGTH = "eq_bass_strength"
    private const val KEY_BAND_PREFIX = "eq_band_"

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var currentSessionId: Int = 0
    private var prefs: SharedPreferences? = null

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _currentPreset = MutableStateFlow("Flat")
    val currentPreset: StateFlow<String> = _currentPreset.asStateFlow()

    private val _bassStrength = MutableStateFlow<Short>(0)
    val bassStrength: StateFlow<Short> = _bassStrength.asStateFlow()

    private val _bandLevels = MutableStateFlow<Map<Short, Short>>(emptyMap())
    val bandLevels: StateFlow<Map<Short, Short>> = _bandLevels.asStateFlow()

    private val _bandFrequencies = MutableStateFlow<List<Pair<Short, Int>>>(emptyList())
    val bandFrequencies: StateFlow<List<Pair<Short, Int>>> = _bandFrequencies.asStateFlow()

    private val _minBandLevel = MutableStateFlow<Short>(-1500)
    val minBandLevel: StateFlow<Short> = _minBandLevel.asStateFlow()

    private val _maxBandLevel = MutableStateFlow<Short>(1500)
    val maxBandLevel: StateFlow<Short> = _maxBandLevel.asStateFlow()

    val presets = listOf(
        "Flat",
        "Bass Boost",
        "Vocal Boost",
        "Rock",
        "Pop",
        "EDM",
        "Hip-Hop",
        "Acoustic",
        "Custom"
    )

    fun init(context: Context, audioSessionId: Int) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        val savedEnabled = prefs?.getBoolean(KEY_ENABLED, false) ?: false
        val savedPreset = prefs?.getString(KEY_PRESET, "Flat") ?: "Flat"
        val savedBass = prefs?.getInt(KEY_BASS_STRENGTH, 0)?.toShort() ?: 0

        _isEnabled.value = savedEnabled
        _currentPreset.value = savedPreset
        _bassStrength.value = savedBass

        if (audioSessionId > 0 && audioSessionId != currentSessionId) {
            release()
            currentSessionId = audioSessionId
            setupEffects(audioSessionId)
        }
    }

    private fun setupEffects(audioSessionId: Int) {
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                val numBands = numberOfBands
                val minLevel = bandLevelRange[0]
                val maxLevel = bandLevelRange[1]
                _minBandLevel.value = minLevel
                _maxBandLevel.value = maxLevel

                val freqs = mutableListOf<Pair<Short, Int>>()
                val levels = mutableMapOf<Short, Short>()

                for (band in 0 until numBands) {
                    val b = band.toShort()
                    val freq = getCenterFreq(b)
                    freqs.add(Pair(b, freq))

                    val savedBandLevel = prefs?.getInt("$KEY_BAND_PREFIX$band", 0)?.toShort() ?: 0
                    levels[b] = savedBandLevel.coerceIn(minLevel, maxLevel)
                    try {
                        setBandLevel(b, levels[b] ?: 0)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting band level: ${e.message}")
                    }
                }

                _bandFrequencies.value = freqs
                _bandLevels.value = levels
                enabled = _isEnabled.value
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Equalizer: ${e.message}")
        }

        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                if (strengthSupported) {
                    setStrength(_bassStrength.value)
                }
                enabled = _isEnabled.value && _bassStrength.value > 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init BassBoost: ${e.message}")
        }

        applyCurrentPresetState()
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled && _bassStrength.value > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling effects: ${e.message}")
        }
    }

    fun setBassStrength(strength: Short) {
        _bassStrength.value = strength
        prefs?.edit()?.putInt(KEY_BASS_STRENGTH, strength.toInt())?.apply()
        try {
            bassBoost?.let {
                if (it.strengthSupported) {
                    it.setStrength(strength)
                    it.enabled = _isEnabled.value && strength > 0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting bass strength: ${e.message}")
        }
    }

    fun setBandLevel(band: Short, level: Short) {
        _currentPreset.value = "Custom"
        prefs?.edit()?.putString(KEY_PRESET, "Custom")?.apply()

        val updated = _bandLevels.value.toMutableMap()
        val clamped = level.coerceIn(_minBandLevel.value, _maxBandLevel.value)
        updated[band] = clamped
        _bandLevels.value = updated

        prefs?.edit()?.putInt("$KEY_BAND_PREFIX$band", clamped.toInt())?.apply()

        try {
            equalizer?.setBandLevel(band, clamped)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting band $band to $level: ${e.message}")
        }
    }

    fun selectPreset(presetName: String) {
        _currentPreset.value = presetName
        prefs?.edit()?.putString(KEY_PRESET, presetName)?.apply()

        val freqs = _bandFrequencies.value
        if (freqs.isEmpty()) return

        val min = _minBandLevel.value.toFloat()
        val max = _maxBandLevel.value.toFloat()

        // Helper to normalize percentage (-1.0 to 1.0) to device band range
        fun calcLevel(pct: Float): Short = (pct * max).toInt().coerceIn(min.toInt(), max.toInt()).toShort()

        val newLevels = mutableMapOf<Short, Short>()
        val count = freqs.size

        when (presetName) {
            "Flat" -> {
                for (i in 0 until count) newLevels[i.toShort()] = 0
                setBassStrength(0)
            }
            "Bass Boost" -> {
                for (i in 0 until count) {
                    val pct = when (i) {
                        0 -> 0.75f
                        1 -> 0.50f
                        2 -> 0.15f
                        3 -> 0.0f
                        else -> -0.1f
                    }
                    newLevels[i.toShort()] = calcLevel(pct)
                }
                setBassStrength(700)
            }
            "Vocal Boost" -> {
                for (i in 0 until count) {
                    val pct = when (i) {
                        0 -> -0.25f
                        1 -> 0.10f
                        2 -> 0.65f
                        3 -> 0.55f
                        else -> 0.20f
                    }
                    newLevels[i.toShort()] = calcLevel(pct)
                }
                setBassStrength(0)
            }
            "Rock" -> {
                for (i in 0 until count) {
                    val pct = when (i) {
                        0 -> 0.60f
                        1 -> 0.25f
                        2 -> -0.20f
                        3 -> 0.35f
                        else -> 0.65f
                    }
                    newLevels[i.toShort()] = calcLevel(pct)
                }
                setBassStrength(400)
            }
            "Pop" -> {
                for (i in 0 until count) {
                    val pct = when (i) {
                        0 -> 0.20f
                        1 -> 0.45f
                        2 -> 0.50f
                        3 -> 0.25f
                        else -> 0.10f
                    }
                    newLevels[i.toShort()] = calcLevel(pct)
                }
                setBassStrength(300)
            }
            "EDM" -> {
                for (i in 0 until count) {
                    val pct = when (i) {
                        0 -> 0.80f
                        1 -> 0.40f
                        2 -> 0.0f
                        3 -> 0.50f
                        else -> 0.70f
                    }
                    newLevels[i.toShort()] = calcLevel(pct)
                }
                setBassStrength(800)
            }
            "Hip-Hop" -> {
                for (i in 0 until count) {
                    val pct = when (i) {
                        0 -> 0.70f
                        1 -> 0.45f
                        2 -> 0.0f
                        3 -> 0.30f
                        else -> 0.40f
                    }
                    newLevels[i.toShort()] = calcLevel(pct)
                }
                setBassStrength(600)
            }
            "Acoustic" -> {
                for (i in 0 until count) {
                    val pct = when (i) {
                        0 -> 0.35f
                        1 -> 0.20f
                        2 -> 0.30f
                        3 -> 0.40f
                        else -> 0.25f
                    }
                    newLevels[i.toShort()] = calcLevel(pct)
                }
                setBassStrength(150)
            }
            else -> return // Custom: maintain current levels
        }

        _bandLevels.value = newLevels
        newLevels.forEach { (band, level) ->
            prefs?.edit()?.putInt("$KEY_BAND_PREFIX$band", level.toInt())?.apply()
            try {
                equalizer?.setBandLevel(band, level)
            } catch (e: Exception) {
                Log.e(TAG, "Error applying preset band $band: ${e.message}")
            }
        }
    }

    private fun applyCurrentPresetState() {
        if (_currentPreset.value != "Custom") {
            selectPreset(_currentPreset.value)
        }
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio effects: ${e.message}")
        }
        equalizer = null
        bassBoost = null
    }
}
