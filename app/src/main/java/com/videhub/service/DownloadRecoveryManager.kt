package com.videhub.service

import android.content.Context
import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.videhub.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DownloadRecoveryManager {
    private const val TAG = "DownloadRecovery"
    private const val STALE_HEARTBEAT_THRESHOLD_MS = 60_000L

    suspend fun recoverDownloads(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val workManager = WorkManager.getInstance(context)
            val downloadingVideos = db.downloadedVideoDao().getDownloadingVideosSync()
            val now = System.currentTimeMillis()

            // Check Watch Later playlist recovery
            val wlPlaylist = db.downloadedPlaylistDao().getPlaylistById(WatchLaterSyncManager.WATCH_LATER_ID)
            if (wlPlaylist != null && wlPlaylist.state == "DOWNLOADING") {
                val wlWorkInfos = try {
                    workManager.getWorkInfosForUniqueWork(WatchLaterDownloadWorker.UNIQUE_WORK_NAME).get()
                } catch (e: Exception) {
                    emptyList<WorkInfo>()
                }
                val isWlActive = wlWorkInfos.any {
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                }
                val isWlStale = (now - wlPlaylist.lastUpdatedAt) > STALE_HEARTBEAT_THRESHOLD_MS
                if (!isWlActive || isWlStale) {
                    Log.i(TAG, "Recovering stalled Watch Later download. Active: $isWlActive, Stale: $isWlStale")
                    WatchLaterDownloadWorker.enqueue(context, replace = true)
                }
            }

            for (video in downloadingVideos) {
                if (video.playlistId == WatchLaterSyncManager.WATCH_LATER_ID) {
                    // Handled by WatchLaterDownloadWorker
                    continue
                }

                val uniqueWorkName = DownloadWorker.getUniqueWorkName(video.videoId, video.playlistId)
                val workInfos = try {
                    workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
                } catch (e: Exception) {
                    emptyList<WorkInfo>()
                }

                val isWorkActive = workInfos.any {
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                }
                val isHeartbeatStale = (now - video.lastUpdatedAt) > STALE_HEARTBEAT_THRESHOLD_MS

                if (!isWorkActive || isHeartbeatStale) {
                    Log.i(TAG, "Recovering stalled download for ${video.videoId} (${video.title}). Active: $isWorkActive, Stale: $isHeartbeatStale")

                    // Re-enqueue the worker to resume using Range request
                    DownloadWorker.enqueueDownload(
                        context = context,
                        videoId = video.videoId,
                        title = video.title,
                        thumbnailUrl = video.thumbnailUrl,
                        channelName = video.channelName,
                        isAudioOnly = video.isAudioOnly,
                        playlistId = video.playlistId,
                        playlistName = video.playlistName,
                        playlistIndex = video.playlistIndex,
                        replace = true
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error recovering downloads on app start", e)
        }
    }
}
