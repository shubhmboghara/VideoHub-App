package com.videhub.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.videhub.MainActivity
import com.videhub.data.AppDatabase
import com.videhub.data.entity.DownloadedVideoEntity
import com.videhub.extractor.ExtractorHelper
import com.videhub.ui.screens.cleanTitle
import com.videhub.ui.screens.formatMB
import com.videhub.utils.DownloadProgressTracker
import com.videhub.utils.ThumbnailDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.TimeUnit

class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "DownloadWorker"
        const val CHANNEL_ID = "DownloadChannel"
        const val MAX_RETRIES = 3
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val STALL_TIMEOUT_MS = 30_000L

        fun getUniqueWorkName(videoId: String, playlistId: String? = null): String {
            return if (!playlistId.isNullOrBlank()) {
                "download_playlist_${playlistId}_video_$videoId"
            } else {
                "download_video_$videoId"
            }
        }

        fun enqueueDownload(
            context: Context,
            videoId: String,
            title: String,
            thumbnailUrl: String,
            channelName: String = "",
            isAudioOnly: Boolean = false,
            quality: String? = null,
            playlistId: String? = null,
            playlistName: String? = null,
            playlistIndex: Int = 0,
            replace: Boolean = false
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val data = Data.Builder()
                .putString("videoId", videoId)
                .putString("title", title)
                .putString("thumbnailUrl", thumbnailUrl)
                .putString("channelName", channelName)
                .putBoolean("isAudioOnly", isAudioOnly)
                .putString("quality", quality ?: "")
                .putString("playlistId", playlistId ?: "")
                .putString("playlistName", playlistName ?: "")
                .putInt("playlistIndex", playlistIndex)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setInputData(data)
                .addTag("download")
                .addTag("video_$videoId")
                .build()

            val uniqueWorkName = getUniqueWorkName(videoId, playlistId)
            val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName,
                policy,
                workRequest
            )
        }
    }

    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var notificationId = 0

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(
        title: String,
        progress: Int,
        bytesDownloaded: Long,
        totalBytes: Long
    ): ForegroundInfo {
        createNotificationChannel()

        val cancelIntent = Intent(applicationContext, DownloadService::class.java).apply {
            action = "CANCEL_$notificationId"
        }
        val cancelPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                applicationContext,
                notificationId,
                cancelIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            PendingIntent.getService(
                applicationContext,
                notificationId,
                cancelIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val contentText = if (totalBytes > 0) {
            "${formatMB(bytesDownloaded)} / ${formatMB(totalBytes)} • $progress%"
        } else if (progress >= 0) {
            "$progress%"
        } else {
            "Connecting..."
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading: ${cleanTitle(title)}")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress.coerceIn(0, 100), progress < 0)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelPending)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getString("videoId") ?: return@withContext Result.failure()
        val title = inputData.getString("title") ?: "Video"
        val thumbnailUrl = inputData.getString("thumbnailUrl") ?: ""
        val channelName = inputData.getString("channelName") ?: ""
        val isAudioOnly = inputData.getBoolean("isAudioOnly", false)
        val quality = inputData.getString("quality")?.takeIf { it.isNotBlank() }
        val playlistId = inputData.getString("playlistId")?.takeIf { it.isNotBlank() }
        val playlistName = inputData.getString("playlistName")?.takeIf { it.isNotBlank() }
        val playlistIndex = inputData.getInt("playlistIndex", 0)

        notificationId = (videoId.hashCode() and 0x7FFFFFFF)
        val db = AppDatabase.getDatabase(applicationContext)

        val safeTitle = title.replace(Regex("[^a-zA-Z0-9 \\-_.]"), "_").trim().take(100)
        val finalSuffix = quality?.let { "_$it" } ?: if (isAudioOnly) ".m4a" else ".mp4"
        val finalFileName = if (finalSuffix.startsWith(".")) "$safeTitle$finalSuffix" else "${safeTitle}_$finalSuffix"

        val tempDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: applicationContext.filesDir
        val finalFile = File(tempDir, finalFileName)
        val partialFile = File(tempDir, "${finalFileName}.part")

        var isFinalSuccess = false
        var lastDownloadedBytes = partialFile.length().coerceAtLeast(0L)
        var lastTotalBytes = 0L

        try {
            // Set initial Foreground Notification
            setForeground(createForegroundInfo(title, -1, lastDownloadedBytes, lastTotalBytes))

            // Ensure DB record exists with DOWNLOADING state
            val existing = db.downloadedVideoDao().getVideoByVideoId(videoId)
            val now = System.currentTimeMillis()
            if (existing == null) {
                db.downloadedVideoDao().insertDownload(
                    DownloadedVideoEntity(
                        fileName = finalFileName,
                        videoId = videoId,
                        title = title,
                        thumbnailUrl = thumbnailUrl,
                        channelName = channelName,
                        isAudioOnly = isAudioOnly,
                        downloadedAt = now,
                        playlistId = playlistId,
                        playlistName = playlistName,
                        playlistIndex = playlistIndex,
                        state = "DOWNLOADING",
                        progress = 0,
                        bytesDownloaded = lastDownloadedBytes,
                        totalBytes = 0L,
                        lastUpdatedAt = now,
                        partialFilePath = partialFile.absolutePath
                    )
                )
            } else {
                db.downloadedVideoDao().updateVideoProgressWithHeartbeat(
                    videoId = videoId,
                    state = "DOWNLOADING",
                    progress = existing.progress,
                    bytesDownloaded = lastDownloadedBytes,
                    totalBytes = existing.totalBytes,
                    lastUpdatedAt = now
                )
                db.downloadedVideoDao().updatePartialFilePath(videoId, partialFile.absolutePath, now)
            }

            DownloadProgressTracker.updateProgress(
                id = notificationId,
                title = safeTitle,
                progress = -1,
                videoId = videoId,
                playlistId = playlistId,
                thumbnailUrl = thumbnailUrl,
                isAudioOnly = isAudioOnly,
                quality = quality,
                downloadedBytes = lastDownloadedBytes,
                totalBytes = lastTotalBytes
            )

            // Extract fresh stream URL
            val streamInfo = ExtractorHelper.getStreamInfo(videoId)
                ?: throw IOException("Could not fetch stream info for $videoId")

            val streamUrl: String
            if (isAudioOnly) {
                val audioStream = (streamInfo.audioStreams ?: emptyList())
                    .filter { !it.content.isNullOrBlank() }
                    .maxByOrNull { it.averageBitrate }
                streamUrl = audioStream?.content ?: throw IOException("Audio stream not available")
            } else {
                val videoStream = (streamInfo.videoStreams ?: emptyList())
                    .filter { !it.content.isNullOrBlank() && !it.isVideoOnly }
                    .maxByOrNull { it.height ?: 0 }
                streamUrl = videoStream?.content ?: throw IOException("Video stream not available")
            }

            // Perform Range-resumed download with 15s connect / 30s read timeout
            downloadWithRangeResume(
                streamUrl = streamUrl,
                partialFile = partialFile,
                finalFile = finalFile,
                videoId = videoId,
                title = title,
                thumbnailUrl = thumbnailUrl,
                playlistId = playlistId,
                isAudioOnly = isAudioOnly,
                quality = quality,
                db = db,
                onProgress = { dl, total ->
                    lastDownloadedBytes = dl
                    lastTotalBytes = total
                }
            )

            isFinalSuccess = true
            Result.success()
        } catch (e: CancellationException) {
            Log.w(TAG, "Download cancelled for $videoId: ${e.message}")
            val userCancelled = DownloadProgressTracker.isCancelledByUser(videoId)
            val now = System.currentTimeMillis()
            if (userCancelled) {
                if (partialFile.exists()) partialFile.delete()
                db.downloadedVideoDao().updateVideoStateWithHeartbeat(
                    videoId, "CANCELLED", "Download cancelled", now
                )
                DownloadProgressTracker.removeDownload(notificationId)
            } else {
                // Pre-empted by OS or network lost: preserve partial data, set PAUSED
                db.downloadedVideoDao().updateVideoProgressWithHeartbeat(
                    videoId = videoId,
                    state = "PAUSED",
                    progress = if (lastTotalBytes > 0) ((lastDownloadedBytes * 100) / lastTotalBytes).toInt().coerceIn(0, 100) else 0,
                    bytesDownloaded = lastDownloadedBytes,
                    totalBytes = lastTotalBytes,
                    lastUpdatedAt = now
                )
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading $videoId (attempt $runAttemptCount): ${e.message}", e)
            val now = System.currentTimeMillis()
            val isNetworkError = e is SocketException || e is SocketTimeoutException || e is IOException

            if (isNetworkError && runAttemptCount < MAX_RETRIES) {
                // Retry with exponential backoff: keep state DOWNLOADING with current bytes
                db.downloadedVideoDao().updateVideoProgressWithHeartbeat(
                    videoId = videoId,
                    state = "DOWNLOADING",
                    progress = if (lastTotalBytes > 0) ((lastDownloadedBytes * 100) / lastTotalBytes).toInt().coerceIn(0, 100) else 0,
                    bytesDownloaded = lastDownloadedBytes,
                    totalBytes = lastTotalBytes,
                    lastUpdatedAt = now
                )
                Result.retry()
            } else {
                // Max retries exceeded or fatal error: mark FAILED
                val errorMsg = e.message ?: "Download failed"
                db.downloadedVideoDao().updateVideoStateWithHeartbeat(
                    videoId = videoId,
                    state = "FAILED",
                    errorMessage = errorMsg,
                    lastUpdatedAt = now
                )
                DownloadProgressTracker.updateProgress(
                    id = notificationId,
                    title = safeTitle,
                    progress = 0,
                    isError = true,
                    videoId = videoId,
                    playlistId = playlistId,
                    errorMessage = errorMsg
                )
                Result.failure()
            }
        } finally {
            // Guaranteed state check in finally: Never leave row stuck as DOWNLOADING with no active worker
            try {
                if (!isFinalSuccess) {
                    val current = db.downloadedVideoDao().getVideoByVideoId(videoId)
                    if (current != null && current.state == "DOWNLOADING") {
                        val finalState = if (runAttemptCount >= MAX_RETRIES) "FAILED" else "PAUSED"
                        db.downloadedVideoDao().updateVideoStateWithHeartbeat(
                            videoId = videoId,
                            state = finalState,
                            errorMessage = if (finalState == "FAILED") "Download stalled" else "Paused",
                            lastUpdatedAt = System.currentTimeMillis()
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun downloadWithRangeResume(
        streamUrl: String,
        partialFile: File,
        finalFile: File,
        videoId: String,
        title: String,
        thumbnailUrl: String,
        playlistId: String?,
        isAudioOnly: Boolean,
        quality: String?,
        db: AppDatabase,
        onProgress: (Long, Long) -> Unit
    ) {
        partialFile.parentFile?.mkdirs()
        var downloaded = if (partialFile.exists()) partialFile.length() else 0L

        val url = URL(streamUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")

        // 3. RESUME: If we have partial bytes, request Range
        if (downloaded > 0L) {
            connection.setRequestProperty("Range", "bytes=$downloaded-")
        }

        connection.connect()
        val responseCode = connection.responseCode

        val outputStream: FileOutputStream
        val totalBytes: Long

        if (responseCode == HttpURLConnection.HTTP_PARTIAL) { // 206
            // Server supports Range resume! Append to existing partial file
            val contentRange = connection.getHeaderField("Content-Range")
            val fullLength = contentRange?.substringAfterLast("/")?.toLongOrNull()
                ?: (downloaded + connection.contentLengthLong)
            totalBytes = fullLength
            outputStream = FileOutputStream(partialFile, true)
        } else if (responseCode == HttpURLConnection.HTTP_OK) { // 200
            // Server does NOT support resume or returned from 0: delete partial file and restart from 0
            if (downloaded > 0L) {
                partialFile.delete()
                downloaded = 0L
            }
            totalBytes = connection.contentLengthLong.coerceAtLeast(1L)
            outputStream = FileOutputStream(partialFile, false)
        } else {
            throw IOException("HTTP error $responseCode: ${connection.responseMessage}")
        }

        val inputStream = connection.inputStream
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var lastHeartbeat = 0L
        var lastByteTime = System.currentTimeMillis()

        try {
            while (true) {
                if (isStopped) {
                    throw CancellationException("Worker stopped by WorkManager")
                }

                bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break

                outputStream.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                lastByteTime = System.currentTimeMillis()
                onProgress(downloaded, totalBytes)

                val now = System.currentTimeMillis()
                // Throttled heartbeat & notification updates (every 500ms)
                if (now - lastHeartbeat >= 500L) {
                    lastHeartbeat = now
                    val percent = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

                    // Write heartbeat to Room DB
                    db.downloadedVideoDao().updateVideoProgressWithHeartbeat(
                        videoId = videoId,
                        state = "DOWNLOADING",
                        progress = percent,
                        bytesDownloaded = downloaded,
                        totalBytes = totalBytes,
                        lastUpdatedAt = now
                    )

                    DownloadProgressTracker.updateProgress(
                        id = notificationId,
                        title = title,
                        progress = percent,
                        downloadedBytes = downloaded,
                        totalBytes = totalBytes,
                        videoId = videoId,
                        playlistId = playlistId,
                        isAudioOnly = isAudioOnly,
                        quality = quality
                    )

                    setForeground(createForegroundInfo(title, percent, downloaded, totalBytes))
                }

                // Timeout check: fail attempt if no bytes arrive for 30s
                if (System.currentTimeMillis() - lastByteTime > STALL_TIMEOUT_MS) {
                    throw SocketTimeoutException("No bytes arrived for 30 seconds")
                }
            }

            outputStream.flush()
        } finally {
            try { outputStream.close() } catch (_: Exception) {}
            try { inputStream.close() } catch (_: Exception) {}
            connection.disconnect()
        }

        // On complete: Rename partialFile (.part) to final playable file
        if (finalFile.exists()) finalFile.delete()
        val renamed = partialFile.renameTo(finalFile)
        if (!renamed) {
            partialFile.copyTo(finalFile, overwrite = true)
            partialFile.delete()
        }

        val now = System.currentTimeMillis()
        val finalLen = finalFile.length()

        // Ensure local thumbnail is downloaded and stored
        try {
            val localThumb = ThumbnailDownloader.downloadThumbnail(
                applicationContext,
                videoId,
                thumbnailUrl
            )
            if (!localThumb.isNullOrBlank()) {
                db.downloadedVideoDao().updateThumbnailUrl(videoId, localThumb)
            }
        } catch (_: Exception) {}

        db.downloadedVideoDao().updateVideoProgressWithHeartbeat(
            videoId = videoId,
            state = "DONE",
            progress = 100,
            bytesDownloaded = finalLen,
            totalBytes = finalLen,
            lastUpdatedAt = now
        )

        DownloadProgressTracker.updateProgress(
            id = notificationId,
            title = title,
            progress = 100,
            isComplete = true,
            downloadedBytes = finalLen,
            totalBytes = finalLen,
            videoId = videoId,
            playlistId = playlistId,
            isAudioOnly = isAudioOnly,
            quality = quality
        )

        // Show completion notification
        showCompletionNotification(title, finalFile.name)
    }

    private fun showCompletionNotification(title: String, fileName: String) {
        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val successNotification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText(cleanTitle(title))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, successNotification)
    }
}
