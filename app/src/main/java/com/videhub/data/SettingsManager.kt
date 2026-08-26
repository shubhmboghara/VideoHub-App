package com.videhub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "videhub_settings")

object SettingsManager {
    private val AUTOPLAY_KEY = booleanPreferencesKey("autoplay_enabled")
    private val PLAYBACK_SPEED_KEY = floatPreferencesKey("playback_speed")
    private val CUSTOM_TABS_KEY = androidx.datastore.preferences.core.stringPreferencesKey("custom_tabs_list")
    private val THEME_MODE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("theme_mode")
    private val IS_DARK_MODE_KEY = booleanPreferencesKey("is_dark_mode")
    private val IS_AMOLED_MODE_KEY = booleanPreferencesKey("is_amoled_mode")
    private val SHOW_CAPTIONS_KEY = booleanPreferencesKey("show_captions")
    private val SPONSOR_BLOCK_KEY = booleanPreferencesKey("sponsor_block_enabled")
    private val VOLUME_BOOST_KEY = booleanPreferencesKey("volume_boost")
    private val LOOP_VIDEO_KEY = booleanPreferencesKey("loop_video")
    private val USER_INTERESTS_KEY = androidx.datastore.preferences.core.stringPreferencesKey("user_interests_list")
    private val HAS_CONFIGURED_INTERESTS_KEY = booleanPreferencesKey("has_configured_interests")

    fun getUserInterests(context: Context): Flow<List<String>> {
        return context.dataStore.data.map { preferences ->
            val raw = preferences[USER_INTERESTS_KEY] ?: ""
            if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    suspend fun setUserInterests(context: Context, interests: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[USER_INTERESTS_KEY] = interests.distinct().joinToString(",")
            preferences[HAS_CONFIGURED_INTERESTS_KEY] = true
        }
    }

    fun getHasConfiguredInterests(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[HAS_CONFIGURED_INTERESTS_KEY] ?: false
        }
    }

    suspend fun setHasConfiguredInterests(context: Context, configured: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_CONFIGURED_INTERESTS_KEY] = configured
        }
    }

    fun getLoopVideo(context: Context): Flow<Boolean> = context.dataStore.data.map { it[LOOP_VIDEO_KEY] ?: false }
    suspend fun setLoopVideo(context: Context, loop: Boolean) = context.dataStore.edit { it[LOOP_VIDEO_KEY] = loop }

    fun getVolumeBoost(context: Context): Flow<Boolean> = context.dataStore.data.map { it[VOLUME_BOOST_KEY] ?: false }
    suspend fun setVolumeBoost(context: Context, boost: Boolean) = context.dataStore.edit { it[VOLUME_BOOST_KEY] = boost }

    fun getSponsorBlockEnabled(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[SPONSOR_BLOCK_KEY] ?: true
        }
    }

    suspend fun setSponsorBlockEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SPONSOR_BLOCK_KEY] = enabled
        }
    }

    fun getShowCaptions(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[SHOW_CAPTIONS_KEY] ?: true
        }
    }

    suspend fun setShowCaptions(context: Context, show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_CAPTIONS_KEY] = show
        }
    }

    fun getThemeMode(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[THEME_MODE_KEY] ?: "SYSTEM"
        }
    }

    suspend fun setThemeMode(context: Context, mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
            if (mode == "DARK") {
                preferences[IS_DARK_MODE_KEY] = true
            } else if (mode == "LIGHT") {
                preferences[IS_DARK_MODE_KEY] = false
            }
        }
    }

    fun getIsDarkMode(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            val mode = preferences[THEME_MODE_KEY]
            if (mode != null) {
                mode == "DARK"
            } else {
                preferences[IS_DARK_MODE_KEY] ?: false
            }
        }
    }

    suspend fun setIsDarkMode(context: Context, isDark: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_DARK_MODE_KEY] = isDark
            preferences[THEME_MODE_KEY] = if (isDark) "DARK" else "LIGHT"
        }
    }

    fun getIsAmoledMode(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[IS_AMOLED_MODE_KEY] ?: false
        }
    }

    suspend fun setIsAmoledMode(context: Context, isAmoled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_AMOLED_MODE_KEY] = isAmoled
        }
    }

    fun getCustomTabs(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[CUSTOM_TABS_KEY] ?: "Music,Gaming,News,Sports"
        }
    }

    suspend fun setCustomTabs(context: Context, tabs: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_TABS_KEY] = tabs
        }
    }

    fun getAutoplay(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[AUTOPLAY_KEY] ?: true
        }
    }

    suspend fun setAutoplay(context: Context, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTOPLAY_KEY] = enabled
        }
    }

    fun getPlaybackSpeed(context: Context): Flow<Float> {
        return context.dataStore.data.map { preferences ->
            preferences[PLAYBACK_SPEED_KEY] ?: 1f
        }
    }

    suspend fun setPlaybackSpeed(context: Context, speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[PLAYBACK_SPEED_KEY] = speed
        }
    }
}
