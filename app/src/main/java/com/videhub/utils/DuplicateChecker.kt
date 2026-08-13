package com.videhub.utils

import android.content.Context
import com.videhub.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper to verify if a video ID already exists within the specific target playlist or download list,
 * ensuring the check is scoped only to that playlist or specific collection before allowing addition.
 */
object DuplicateChecker {

    /**
     * Verifies if a video already exists within a specific target playlist.
     */
    suspend fun isVideoInPlaylist(context: Context, playlistId: Int, videoId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.playlistDao().isVideoInPlaylist(playlistId, videoId)
        }
    }

    /**
     * Verifies if a video already exists within the download list.
     */
    suspend fun isVideoDownloaded(context: Context, videoId: String, isAudioOnly: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.downloadedVideoDao().isVideoDownloaded(videoId)
        }
    }
}
