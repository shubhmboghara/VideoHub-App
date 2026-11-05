package com.videhub.service

import android.content.Context
import com.videhub.data.AppDatabase
import com.videhub.data.WatchLaterSessionManager
import com.videhub.data.entity.DownloadedPlaylistEntity
import com.videhub.data.entity.DownloadedPlaylistVideoCrossRef
import com.videhub.data.entity.DownloadedVideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WatchLaterSyncManager {
    const val WATCH_LATER_ID = "WL"
    const val WATCH_LATER_TITLE = "Watch Later"

    /**
     * Prepares and syncs Watch Later metadata into Room as the single source of truth,
     * then triggers the unique WorkManager worker.
     */
    suspend fun syncWatchLater(
        context: Context,
        isAudioOnly: Boolean = false,
        onSessionInvalid: (() -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)

        // 1. Validate session
        if (!WatchLaterSessionManager.isSessionValid(context)) {
            val existing = db.downloadedPlaylistDao().getPlaylistById(WATCH_LATER_ID)
            db.downloadedPlaylistDao().insertPlaylist(
                DownloadedPlaylistEntity(
                    playlistId = WATCH_LATER_ID,
                    title = WATCH_LATER_TITLE,
                    thumbnailUrl = existing?.thumbnailUrl,
                    totalCount = existing?.totalCount ?: 0,
                    completedCount = existing?.completedCount ?: 0,
                    state = "FAILED",
                    errorMessage = "Sign in to download Watch Later",
                    lastUpdatedAt = System.currentTimeMillis()
                )
            )
            onSessionInvalid?.invoke()
            return@withContext false
        }

        // 2. Fetch current list from local Watch Later records
        val currentSavedList = db.watchLaterDao().getAllOnce()
        val totalCount = currentSavedList.size
        val firstCover = currentSavedList.firstOrNull()?.thumbnailUrl ?: ""

        val existingPlaylist = db.downloadedPlaylistDao().getPlaylistById(WATCH_LATER_ID)
        val coverUrl = firstCover.ifBlank { existingPlaylist?.thumbnailUrl ?: "" }

        val existingVideos = db.downloadedVideoDao().getPlaylistVideosSync(WATCH_LATER_ID)
        val existingByVideoId = existingVideos.associateBy { it.videoId }
        val currentVideoIds = currentSavedList.map { it.videoId }.toSet()

        // 3. Mark videos removed from Watch Later as noLongerInWatchLater (Do NOT auto-delete)
        existingVideos.forEach { existing ->
            if (existing.videoId !in currentVideoIds) {
                if (!existing.noLongerInWatchLater) {
                    db.downloadedVideoDao().updateNoLongerInWatchLater(existing.videoId, true)
                }
            } else {
                if (existing.noLongerInWatchLater) {
                    db.downloadedVideoDao().updateNoLongerInWatchLater(existing.videoId, false)
                }
            }
        }

        // Calculate ready count among existing videos
        val readyCount = existingVideos.count { it.state == "DONE" && it.videoId in currentVideoIds }

        // 4. Immediately insert/update Watch Later playlist in Room (single source of truth)
        val initialPlaylistState = if (totalCount > 0 && readyCount == totalCount) "DONE" else "DOWNLOADING"
        db.downloadedPlaylistDao().insertPlaylist(
            DownloadedPlaylistEntity(
                playlistId = WATCH_LATER_ID,
                title = WATCH_LATER_TITLE,
                thumbnailUrl = coverUrl,
                totalCount = totalCount,
                completedCount = readyCount,
                state = initialPlaylistState,
                progress = if (totalCount > 0) ((readyCount * 100) / totalCount) else 0,
                currentVideoTitle = currentSavedList.firstOrNull()?.title,
                currentVideoIndex = 1,
                currentVideoProgress = 0,
                errorMessage = null,
                lastUpdatedAt = System.currentTimeMillis()
            )
        )

        // 5. Insert one row per video with state QUEUED (or preserve DONE) before any file download
        currentSavedList.forEachIndexed { index, video ->
            val existing = existingByVideoId[video.videoId]
            if (existing != null && existing.state == "DONE") {
                // Keep DONE
                db.downloadedPlaylistDao().insertPlaylistVideoCrossRef(
                    DownloadedPlaylistVideoCrossRef(WATCH_LATER_ID, video.videoId)
                )
            } else {
                // New or uncompleted video: insert as QUEUED
                val pendingFileName = existing?.fileName?.takeIf { !it.startsWith("PENDING_") }
                    ?: "PENDING_${WATCH_LATER_ID}_${index}_${video.videoId.hashCode()}"

                db.downloadedVideoDao().insertDownload(
                    DownloadedVideoEntity(
                        fileName = pendingFileName,
                        videoId = video.videoId,
                        title = video.title,
                        thumbnailUrl = video.thumbnailUrl,
                        channelName = video.channelName,
                        viewCount = existing?.viewCount ?: 0L,
                        uploadDate = existing?.uploadDate ?: "",
                        isAudioOnly = isAudioOnly,
                        downloadedAt = System.currentTimeMillis() + index,
                        playlistId = WATCH_LATER_ID,
                        playlistName = WATCH_LATER_TITLE,
                        playlistIndex = index,
                        state = "QUEUED",
                        progress = 0,
                        bytesDownloaded = existing?.bytesDownloaded ?: 0L,
                        totalBytes = existing?.totalBytes ?: 0L,
                        duration = existing?.duration ?: "",
                        noLongerInWatchLater = false,
                        errorMessage = null,
                        lastUpdatedAt = System.currentTimeMillis()
                    )
                )
                db.downloadedPlaylistDao().insertPlaylistVideoCrossRef(
                    DownloadedPlaylistVideoCrossRef(WATCH_LATER_ID, video.videoId)
                )
            }
        }

        // 6. Trigger Unique WorkManager work
        if (initialPlaylistState == "DOWNLOADING") {
            WatchLaterDownloadWorker.enqueue(context, replace = true)
        }

        true
    }
}
