package com.videhub.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import com.videhub.data.AppDatabase
import com.videhub.utils.DownloadProgressTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadCancelReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_CANCEL_DOWNLOAD = "com.videhub.ACTION_CANCEL_DOWNLOAD"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CANCEL_DOWNLOAD && intent.action?.startsWith("CANCEL_") != true) return

        val notificationId = intent.getIntExtra("notificationId", 0)
        val videoId = intent.getStringExtra("videoId") ?: ""
        val playlistId = intent.getStringExtra("playlistId")

        Log.i("DownloadCancelReceiver", "Cancelling download: videoId=$videoId, notifId=$notificationId, playlistId=$playlistId")

        // 1. Mark in tracker & cancel WorkManager work
        if (videoId.isNotBlank()) {
            DownloadProgressTracker.markCancelledByUser(videoId)
            val uniqueName = DownloadWorker.getUniqueWorkName(videoId, playlistId)
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName)
        }

        // 2. Cancel in DownloadService if running there
        if (notificationId != 0) {
            DownloadService.cancelDownload(context, notificationId)
            DownloadProgressTracker.removeDownload(notificationId)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(notificationId)
        }

        // 3. Cancel playlist if applicable
        if (!playlistId.isNullOrBlank()) {
            DownloadService.cancelPlaylist(context, playlistId)
        }

        // 4. Update database asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                if (videoId.isNotBlank()) {
                    db.downloadedVideoDao().updateVideoStateWithHeartbeat(
                        videoId = videoId,
                        state = "CANCELLED",
                        errorMessage = "Download cancelled by user",
                        lastUpdatedAt = System.currentTimeMillis()
                    )
                    db.downloadedVideoDao().deletePlaceholderByVideoId(videoId)
                }
            } catch (e: Exception) {
                Log.w("DownloadCancelReceiver", "Failed to update DB on cancel", e)
            }
        }
    }
}
