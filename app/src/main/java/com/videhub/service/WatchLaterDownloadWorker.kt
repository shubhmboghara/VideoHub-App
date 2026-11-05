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
import com.videhub.data.WatchLaterSessionManager
import com.videhub.data.entity.DownloadedVideoEntity
import com.videhub.extractor.ExtractorHelper
import com.videhub.ui.screens.cleanTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.TimeUnit

class WatchLaterDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val UNIQUE_WORK_NAME = "watch_later"
        const val CHANNEL_ID = "WatchLaterDownloadChannel"
        const val NOTIFICATION_ID = 4001
        const val MAX_RETRIES = 3
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val STALL_TIMEOUT_MS = 30_000L
        const val THROTTLE_INTERVAL_MS = 250L // At most 4 writes per second

        fun enqueue(context: Context, replace: Boolean = true) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<WatchLaterDownloadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag("watch_later_download")
                .build()

            val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                policy,
                workRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Watch Later Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for Watch Later playlist download progress"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        trackIndex: Int,
        totalCount: Int,
        itemProgress: Int,
        itemTitle: String?
    ): NotificationCompat.Builder {
        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Clear label: "Watch Later 1 of 3 • 63%", with bar showing the current item
        val subtitle = "Watch Later $trackIndex of $totalCount • $itemProgress%"

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(itemTitle ?: "Downloading Watch Later")
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, itemProgress, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
    }

    private fun createForegroundInfo(
        trackIndex: Int,
        totalCount: Int,
        itemProgress: Int,
        itemTitle: String?
    ): ForegroundInfo {
        val notification = buildNotification(trackIndex, totalCount, itemProgress, itemTitle).build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(applicationContext)
        val playlistDao = db.downloadedPlaylistDao()
        val videoDao = db.downloadedVideoDao()

        // 1. Session check
        if (!WatchLaterSessionManager.isSessionValid(applicationContext)) {
            playlistDao.updatePlaylistStateAndError(
                playlistId = WatchLaterSyncManager.WATCH_LATER_ID,
                state = "FAILED",
                errorMessage = "Sign in to download Watch Later"
            )
            return@withContext Result.failure()
        }

        var isCancelledByUser = false

        try {
            // Initial foreground setup
            val initialPlaylist = playlistDao.getPlaylistById(WatchLaterSyncManager.WATCH_LATER_ID)
            val total = initialPlaylist?.totalCount ?: 0
            if (total == 0) {
                playlistDao.updatePlaylistState(WatchLaterSyncManager.WATCH_LATER_ID, "DONE")
                return@withContext Result.success()
            }

            setForeground(
                createForegroundInfo(
                    trackIndex = 1,
                    totalCount = total,
                    itemProgress = 0,
                    itemTitle = initialPlaylist?.currentVideoTitle ?: "Watch Later"
                )
            )

            // Loop through videos in Watch Later that are not yet DONE
            val playlistId = WatchLaterSyncManager.WATCH_LATER_ID
            val videos = videoDao.getPlaylistVideosSync(playlistId)
            val downloadDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: return@withContext Result.failure()

            for ((idx, video) in videos.withIndex()) {
                if (isStopped) {
                    isCancelledByUser = true
                    break
                }

                val currentTrackNumber = idx + 1

                // Check if already completely downloaded on disk
                val safeTitle = cleanTitle(video.title)
                val targetFileName = "${safeTitle}_${video.videoId.hashCode()}.${if (video.isAudioOnly) "m4a" else "mp4"}"
                val targetFile = File(downloadDir, targetFileName)

                if (video.state == "DONE" && targetFile.exists() && targetFile.length() > 0) {
                    continue
                }

                // Update video state to DOWNLOADING and set heartbeat
                videoDao.updateVideoProgressWithHeartbeat(
                    videoId = video.videoId,
                    state = "DOWNLOADING",
                    progress = 0,
                    bytesDownloaded = 0L,
                    totalBytes = 0L,
                    lastUpdatedAt = System.currentTimeMillis()
                )

                // Update playlist progress
                val currentVideosSnapshot = videoDao.getPlaylistVideosSync(playlistId)
                val readyCount = currentVideosSnapshot.count { it.state == "DONE" }
                val overallProgress = if (total > 0) ((readyCount * 100) / total) else 0

                val sumDownloadedBytes = currentVideosSnapshot.sumOf { it.bytesDownloaded }
                val sumTotalBytes = currentVideosSnapshot.sumOf { it.totalBytes }

                playlistDao.updatePlaylistProgress(
                    playlistId = playlistId,
                    completedCount = readyCount,
                    progress = overallProgress,
                    downloadedBytes = sumDownloadedBytes,
                    totalBytes = sumTotalBytes,
                    state = "DOWNLOADING",
                    currentVideoIndex = currentTrackNumber,
                    currentVideoProgress = 0,
                    currentVideoTitle = video.title
                )

                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildNotification(currentTrackNumber, total, 0, video.title).build()
                )

                // Download single video with Range resume
                val videoSuccess = downloadSingleVideo(
                    video = video,
                    targetFile = targetFile,
                    downloadDir = downloadDir,
                    currentTrackNumber = currentTrackNumber,
                    totalCount = total
                )

                if (!videoSuccess && isStopped) {
                    isCancelledByUser = true
                    break
                }
            }

            if (isCancelledByUser) {
                return@withContext Result.success()
            }

            // Check final playlist status
            val finalVideos = videoDao.getPlaylistVideosSync(playlistId)
            val finalReadyCount = finalVideos.count { it.state == "DONE" }
            val hasFailures = finalVideos.any { it.state == "FAILED" }

            if (finalReadyCount == total) {
                Result.success()
            } else if (hasFailures && runAttemptCount >= MAX_RETRIES) {
                Result.failure()
            } else {
                Result.retry()
            }

        } catch (e: CancellationException) {
            isCancelledByUser = true
            Result.success()
        } catch (e: Exception) {
            Log.e("WatchLaterWorker", "Download error", e)
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                playlistDao.updatePlaylistStateAndError(
                    playlistId = WatchLaterSyncManager.WATCH_LATER_ID,
                    state = "FAILED",
                    errorMessage = e.localizedMessage ?: "Download failed"
                )
                Result.failure()
            }
        } finally {
            // Guarantee database state is written in finally so it's never left stale
            try {
                val finalVideos = videoDao.getPlaylistVideosSync(WatchLaterSyncManager.WATCH_LATER_ID)
                val finalReady = finalVideos.count { it.state == "DONE" }
                val totalItems = finalVideos.size
                val hasPending = finalVideos.any { it.state == "DOWNLOADING" || it.state == "QUEUED" }
                val hasFailed = finalVideos.any { it.state == "FAILED" }

                val finalState = when {
                    totalItems > 0 && finalReady == totalItems -> "DONE"
                    isCancelledByUser -> "PAUSED"
                    !hasPending && hasFailed -> "FAILED"
                    else -> if (hasPending) "DOWNLOADING" else "DONE"
                }

                val finalProg = if (totalItems > 0) ((finalReady * 100) / totalItems) else 0
                val sumBytes = finalVideos.sumOf { it.bytesDownloaded }

                playlistDao.updatePlaylistProgress(
                    playlistId = WatchLaterSyncManager.WATCH_LATER_ID,
                    completedCount = finalReady,
                    progress = finalProg,
                    downloadedBytes = sumBytes,
                    totalBytes = sumBytes,
                    state = finalState,
                    currentVideoIndex = finalReady,
                    currentVideoProgress = 100,
                    currentVideoTitle = null
                )

                if (finalState == "DONE" || finalState == "FAILED" || isCancelledByUser) {
                    notificationManager.cancel(NOTIFICATION_ID)
                }
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Downloads an individual video stream using HTTP Range resume header.
     * Throttles database updates to at most 4 per second (every 250ms).
     */
    private suspend fun downloadSingleVideo(
        video: DownloadedVideoEntity,
        targetFile: File,
        downloadDir: File,
        currentTrackNumber: Int,
        totalCount: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(applicationContext)
        val videoDao = db.downloadedVideoDao()
        val playlistDao = db.downloadedPlaylistDao()

        val partialFile = File(downloadDir, "${targetFile.name}.part")

        try {
            // Extract stream URL
            val streamInfo = try {
                ExtractorHelper.getStreamInfo(video.videoId)
            } catch (e: Exception) {
                // If individual video is private/deleted/unavailable, mark that video FAILED and continue with the rest
                videoDao.updateVideoStateWithHeartbeat(
                    videoId = video.videoId,
                    state = "FAILED",
                    errorMessage = "Video unavailable or private",
                    lastUpdatedAt = System.currentTimeMillis()
                )
                return@withContext false
            }

            val directUrl = if (video.isAudioOnly) {
                streamInfo.audioStreams.maxByOrNull { it.averageBitrate }?.content
                    ?: streamInfo.videoStreams.firstOrNull()?.content
            } else {
                streamInfo.videoStreams.filter { !it.content.isNullOrBlank() && !it.isVideoOnly }
                    .maxByOrNull { it.height ?: 0 }?.content
                    ?: streamInfo.videoStreams.firstOrNull()?.content
            }

            if (directUrl.isNullOrBlank()) {
                videoDao.updateVideoStateWithHeartbeat(
                    videoId = video.videoId,
                    state = "FAILED",
                    errorMessage = "No stream available",
                    lastUpdatedAt = System.currentTimeMillis()
                )
                return@withContext false
            }

            var downloadedBytes = if (partialFile.exists()) partialFile.length() else 0L

            val urlObj = URL(directUrl)
            var connection = urlObj.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true

            // Send Range header if partial download exists
            if (downloadedBytes > 0L) {
                connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
            }

            connection.connect()

            var responseCode = connection.responseCode

            // Handle redirect if needed
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                connection = URL(newUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                if (downloadedBytes > 0L) {
                    connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
                }
                connection.connect()
                responseCode = connection.responseCode
            }

            var appendToFile = false
            var totalContentLength = connection.contentLengthLong

            if (responseCode == 206) {
                // Range request accepted!
                appendToFile = true
                if (totalContentLength > 0) {
                    totalContentLength += downloadedBytes
                }
            } else if (responseCode == 200) {
                // Server returned full stream instead of range: delete partial file and restart from 0
                if (partialFile.exists()) {
                    partialFile.delete()
                }
                downloadedBytes = 0L
                appendToFile = false
            } else if (responseCode == 416) {
                // Range not satisfiable: restart from 0
                if (partialFile.exists()) {
                    partialFile.delete()
                }
                downloadedBytes = 0L
                appendToFile = false
            } else {
                throw IOException("Server returned HTTP $responseCode")
            }

            val inputStream: InputStream = connection.inputStream
            val outputStream = FileOutputStream(partialFile, appendToFile)
            val buffer = ByteArray(16 * 1024)
            var bytesRead: Int

            var lastDbWriteTime = 0L
            var lastByteReceivedTime = System.currentTimeMillis()

            try {
                while (true) {
                    if (isStopped) {
                        outputStream.flush()
                        outputStream.close()
                        inputStream.close()
                        connection.disconnect()
                        videoDao.updateVideoStateWithHeartbeat(
                            videoId = video.videoId,
                            state = "PAUSED",
                            errorMessage = null,
                            lastUpdatedAt = System.currentTimeMillis()
                        )
                        return@withContext false
                    }

                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break

                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    lastByteReceivedTime = System.currentTimeMillis()

                    val now = System.currentTimeMillis()
                    // Check stall timeout
                    if (now - lastByteReceivedTime > STALL_TIMEOUT_MS) {
                        throw SocketTimeoutException("Download stalled: no data for 30s")
                    }

                    // Throttled writes to DB (at most 4 updates per second)
                    if (now - lastDbWriteTime >= THROTTLE_INTERVAL_MS) {
                        lastDbWriteTime = now
                        val itemPercent = if (totalContentLength > 0) {
                            ((downloadedBytes * 100) / totalContentLength).toInt().coerceIn(0, 99)
                        } else 0

                        videoDao.updateVideoProgressWithHeartbeat(
                            videoId = video.videoId,
                            state = "DOWNLOADING",
                            progress = itemPercent,
                            bytesDownloaded = downloadedBytes,
                            totalBytes = totalContentLength.coerceAtLeast(downloadedBytes),
                            lastUpdatedAt = now
                        )

                        val overallWeightedProgress = if (totalCount > 0) {
                            (((currentTrackNumber - 1) * 100 + itemPercent) / totalCount).coerceIn(0, 99)
                        } else 0

                        playlistDao.updatePlaylistProgress(
                            playlistId = WatchLaterSyncManager.WATCH_LATER_ID,
                            completedCount = currentTrackNumber - 1,
                            progress = overallWeightedProgress,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalContentLength,
                            state = "DOWNLOADING",
                            currentVideoIndex = currentTrackNumber,
                            currentVideoProgress = itemPercent,
                            currentVideoTitle = video.title
                        )

                        notificationManager.notify(
                            NOTIFICATION_ID,
                            buildNotification(currentTrackNumber, totalCount, overallWeightedProgress, video.title).build()
                        )
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                connection.disconnect()

                // Finalize file: partial file becomes final file
                if (targetFile.exists()) targetFile.delete()
                partialFile.renameTo(targetFile)

                // Download and store thumbnail locally
                val localThumbPath = try {
                    com.videhub.utils.ThumbnailDownloader.downloadThumbnail(
                        applicationContext,
                        video.videoId,
                        video.thumbnailUrl
                    ) ?: video.thumbnailUrl
                } catch (_: Exception) {
                    video.thumbnailUrl
                }

                // Clean up placeholder records for this video
                if (video.fileName != targetFile.name) {
                    videoDao.deleteDownloadByFileName(video.fileName)
                }
                videoDao.deletePlaceholderByVideoId(video.videoId)

                // Update video state to DONE
                videoDao.insertDownload(
                    video.copy(
                        fileName = targetFile.name,
                        thumbnailUrl = localThumbPath,
                        state = "DONE",
                        progress = 100,
                        bytesDownloaded = targetFile.length(),
                        totalBytes = targetFile.length(),
                        errorMessage = null,
                        lastUpdatedAt = System.currentTimeMillis(),
                        partialFilePath = null
                    )
                )

                true
            } catch (e: Exception) {
                outputStream.flush()
                outputStream.close()
                inputStream.close()
                connection.disconnect()
                throw e
            }
        } catch (e: CancellationException) {
            videoDao.updateVideoStateWithHeartbeat(
                videoId = video.videoId,
                state = "PAUSED",
                errorMessage = null,
                lastUpdatedAt = System.currentTimeMillis()
            )
            false
        } catch (e: Exception) {
            Log.e("WatchLaterWorker", "Failed to download video ${video.videoId}", e)
            videoDao.updateVideoStateWithHeartbeat(
                videoId = video.videoId,
                state = "FAILED",
                errorMessage = e.localizedMessage ?: "Failed",
                lastUpdatedAt = System.currentTimeMillis()
            )
            false
        }
    }
}
