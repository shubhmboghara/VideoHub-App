package com.videhub.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Manages the user session state required to access private YouTube Watch Later playlists.
 */
object WatchLaterSessionManager {
    private val YOUTUBE_SIGNED_IN_KEY = booleanPreferencesKey("youtube_watch_later_signed_in")
    private val YOUTUBE_USER_NAME_KEY = stringPreferencesKey("youtube_watch_later_user_name")

    fun isUserSignedInFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[YOUTUBE_SIGNED_IN_KEY] ?: true // Default to active session
        }
    }

    suspend fun isSessionValid(context: Context): Boolean {
        return context.dataStore.data.first()[YOUTUBE_SIGNED_IN_KEY] ?: true
    }

    suspend fun signIn(context: Context, userName: String = "Google User") {
        context.dataStore.edit { preferences ->
            preferences[YOUTUBE_SIGNED_IN_KEY] = true
            preferences[YOUTUBE_USER_NAME_KEY] = userName
        }
    }

    suspend fun signOut(context: Context) {
        context.dataStore.edit { preferences ->
            preferences[YOUTUBE_SIGNED_IN_KEY] = false
            preferences.remove(YOUTUBE_USER_NAME_KEY)
        }
    }
}
