package com.videhub.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicInteger

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class DownloadService : Service() {
    private val downloadSemaphore = Semaphore(3)
    private val downloadJobs = java.util.concurrent.ConcurrentHashMap<Int, kotlinx.coroutines.Job>()
    private val jobPlaylistMap = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO.limitedParallelism(3) + serviceJob)
    private lateinit var notificationManager: NotificationManager
    private val channelId = "DownloadChannel"
    private var notificationId = 2000
    private val activeDownloads = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        


        if (intent.action?.startsWith("CANCEL_PLAYLIST_") == true) {
            val pId = intent.action?.removePrefix("CANCEL_PLAYLIST_")
            if (pId != null) {
                val matchingIds = jobPlaylistMap.entries.filter { it.value == pId }.map { it.key }
                for (id in matchingIds) {
                    downloadJobs[id]?.cancel()
                    downloadJobs.remove(id)
                    jobPlaylistMap.remove(id)
                    notificationManager.cancel(id)
                    com.videhub.utils.DownloadProgressTracker.removeDownload(id)
                }
                if (activeDownloads.get() == 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        stopForeground(true)
                    }
                    stopSelf()
                }
            }
            return START_NOT_STICKY
        }

        if (intent.action?.startsWith("CANCEL_") == true) {
            val id = intent.action?.removePrefix("CANCEL_")?.toIntOrNull()
            if (id != null) {
                val job = downloadJobs[id]
                if (job != null) {
                    job.cancel()
                } else {
                    if (activeDownloads.get() == 0) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } else {
                            stopForeground(true)
                        }
                        stopSelf()
                    }
                }
                downloadJobs.remove(id)
                jobPlaylistMap.remove(id)
                notificationManager.cancel(id)
                com.videhub.utils.DownloadProgressTracker.removeDownload(id)
            }
            return START_NOT_STICKY
        }


        val url = intent.getStringExtra("url") ?: return START_NOT_STICKY
        val title = intent.getStringExtra("title") ?: "Unknown"
        val suffix = intent.getStringExtra("suffix") ?: ".mp4"
        val videoId = intent.getStringExtra("videoId") ?: ""
        val thumbnailUrl = intent.getStringExtra("thumbnailUrl") ?: ""
        val channelName = intent.getStringExtra("channelName") ?: ""
        val viewCount = intent.getLongExtra("viewCount", 0L)
        val uploadDate = intent.getStringExtra("uploadDate") ?: ""
        val isAudioOnly = intent.getBooleanExtra("isAudioOnly", false)
        val description = intent.getStringExtra("description") ?: ""
        val nativeUrl = intent.getStringExtra("nativeUrl")
        val englishUrl = intent.getStringExtra("englishUrl")
        val playlistId = intent.getStringExtra("playlistId")
        val playlistName = intent.getStringExtra("playlistName")
        val playlistIndex = intent.getIntExtra("playlistIndex", 0)

        val safeTitle = title.replace(Regex("[^a-zA-Z0-9 \\-_.]"), "_").trim().take(100)
        val finalFileName = "${safeTitle}_$suffix"
        val downloadKey = videoId

        notificationId++
        val currentNotificationId = notificationId

        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = "CANCEL_$currentNotificationId"
        }
        val cancelPending = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, currentNotificationId, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getService(this, currentNotificationId, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Starting Download: $safeTitle")
            .setContentText("Downloading file...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelPending)
            .build()

        val foregroundNotification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("VideHub Downloads")
            .setContentText("Downloads in progress...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
            
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(10000, foregroundNotification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(10000, foregroundNotification)
        }
        
        if (playlistId == null) {
            notificationManager.notify(currentNotificationId, notification)
        }
        com.videhub.utils.DownloadProgressTracker.updateProgress(currentNotificationId, safeTitle, -1, videoId = downloadKey, playlistId = playlistId)

        activeDownloads.incrementAndGet()

        val job = serviceScope.launch {
            downloadSemaphore.withPermit {
            var finalFile: File? = null
            try {
                val needsExtraction = intent.getBooleanExtra("needsExtraction", false)
                var streamUrl = url
                var finalSuffix = suffix
                var finalThumb = thumbnailUrl
                var finalChannel = channelName
                var finalViewCount = viewCount
                var finalUpload = uploadDate
                var finalAudioOnly = isAudioOnly
                var finalDesc = description
                var finalNativeSub = nativeUrl
                var finalEnSub = englishUrl
                var subArrStr = intent.getStringExtra("subtitlesJson")

                if (needsExtraction) {
                    val info = com.videhub.extractor.ExtractorHelper.getStreamInfo(videoId)
                    val selectedStream = (info.videoStreams ?: emptyList())
                        .filter { !it.url.isNullOrBlank() && !it.isVideoOnly }
                        .maxByOrNull { it.height }
                    val audioStream = (info.audioStreams ?: emptyList())
                        .filter { !it.url.isNullOrBlank() }
                        .maxByOrNull { it.averageBitrate }
                    streamUrl = selectedStream?.url ?: audioStream?.url ?: url
                    finalSuffix = if (selectedStream != null) "${selectedStream.resolution}.mp4" else "audio.m4a"
                    finalThumb = info.thumbnails.firstOrNull()?.url ?: ""
                    finalChannel = info.uploaderName ?: ""
                    finalViewCount = try { info.viewCount } catch (_: Exception) { 0L }
                    finalUpload = info.uploadDate?.offsetDateTime()?.toString() ?: ""
                    finalAudioOnly = selectedStream == null
                    finalDesc = info.description?.content ?: ""
                    
                    val subs = info.subtitles
                    finalEnSub = subs?.find { it.languageTag.equals("en", true) }?.url ?: subs?.find { it.languageTag.startsWith("en", true) }?.url
                    finalNativeSub = subs?.find { !it.languageTag.startsWith("en", true) }?.url ?: subs?.firstOrNull()?.url
                    
                    val subArr = org.json.JSONArray()
                    subs?.forEach { s ->
                        val o = org.json.JSONObject()
                        o.put("url", s.url)
                        o.put("languageTag", s.languageTag)
                        o.put("isAutoGenerated", s.isAutoGenerated)
                        subArr.put(o)
                    }
                    subArrStr = subArr.toString()
                }

                val finalFileNameForSave = "${safeTitle}_$finalSuffix"
                val tempDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                finalFile = File(tempDir, finalFileNameForSave)

                downloadWithRetry(streamUrl, finalFile!!, currentNotificationId, safeTitle, playlistId)

                if (playlistId == null) {
                    val notif = NotificationCompat.Builder(this@DownloadService, channelId).setContentTitle(cleanTitle(safeTitle)).setContentText("Finalizing file...").setSmallIcon(android.R.drawable.stat_sys_download).setOngoing(true).setProgress(100, 0, true).build()
                    notificationManager.notify(currentNotificationId, notif)
                }
                com.videhub.utils.DownloadProgressTracker.updateProgress(currentNotificationId, safeTitle, 100, videoId = downloadKey, playlistId = playlistId)

                val finalUri = FileProvider.getUriForFile(this@DownloadService, "$packageName.provider", finalFile!!)

                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(finalUri, if (finalSuffix.contains("m4a")) "audio/mp4" else if (finalSuffix.contains("webm")) "audio/webm" else "video/mp4")
                    this.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    this@DownloadService,
                    0,
                    viewIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )

                val successNotification = NotificationCompat.Builder(this@DownloadService, channelId)
                    .setContentTitle("Download Complete")
                    .setContentText(finalFileNameForSave)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                if (playlistId == null) {
                    notificationManager.notify(currentNotificationId, successNotification)
                } else {
                    notificationManager.cancel(currentNotificationId)
                }
                com.videhub.utils.DownloadProgressTracker.updateProgress(currentNotificationId, safeTitle, 100, isComplete = true, videoId = downloadKey, playlistId = playlistId)
                kotlinx.coroutines.delay(3000)
                com.videhub.utils.DownloadProgressTracker.removeDownload(currentNotificationId)

                // Save Captions
                val vidIdForCaps = if (videoId.isNotEmpty()) videoId else finalFileNameForSave
                val savedLyricsJson = com.videhub.ui.components.LiveCaptionsManager.downloadAndSaveCaptions(
                    context = this@DownloadService,
                    videoId = vidIdForCaps,
                    nativeUrl = finalNativeSub,
                    englishUrl = finalEnSub,
                    artist = finalChannel,
                    title = title,
                    description = finalDesc,
                    subtitlesJson = subArrStr
                )

                // Save to Room Database
                val db = com.videhub.data.AppDatabase.getDatabase(this@DownloadService)
                
                // Download thumbnail
                val localThumbPath = com.videhub.utils.ThumbnailDownloader.downloadThumbnail(
                    this@DownloadService, 
                    if (videoId.isNotEmpty()) videoId else finalFileNameForSave, 
                    finalThumb
                )
                
                val actualVideoId = if (videoId.isNotEmpty()) videoId else finalFileNameForSave
                db.downloadedVideoDao().deletePlaceholderByVideoId(actualVideoId)
                db.downloadedVideoDao().insertDownload(
                    com.videhub.data.entity.DownloadedVideoEntity(
                        videoId = actualVideoId,
                        title = title,
                        thumbnailUrl = localThumbPath ?: finalThumb,
                        channelName = finalChannel.ifBlank { "Unknown Artist" },
                        viewCount = finalViewCount,
                        uploadDate = finalUpload,
                        fileName = finalFileNameForSave,
                        isAudioOnly = finalAudioOnly,
                        downloadedAt = System.currentTimeMillis(),
                        lyrics = savedLyricsJson,
                        playlistId = playlistId,
                        playlistName = playlistName,
                        playlistIndex = playlistIndex
                    )
                )
                
                if (playlistId != null) {
                    db.downloadedPlaylistDao().insertPlaylistVideoCrossRef(
                        com.videhub.data.entity.DownloadedPlaylistVideoCrossRef(
                            playlistId = playlistId,
                            videoId = actualVideoId
                        )
                    )
                    try {
                        val existingP = db.downloadedPlaylistDao().getPlaylistById(playlistId)
                        val thumbToUse = localThumbPath ?: finalThumb
                        if (existingP != null && existingP.thumbnailUrl.isNullOrBlank() && thumbToUse.isNotBlank()) {
                            db.downloadedPlaylistDao().insertPlaylist(
                                existingP.copy(thumbnailUrl = thumbToUse)
                            )
                        }
                    } catch (_: Exception) {}
                }


            } catch (e: CancellationException) {
                // Job was cancelled, don't show error notification
                Log.e("DownloadService", "Download cancelled: $safeTitle")
                finalFile?.let { if (it.exists()) it.delete() }
                if (playlistId == null) notificationManager.cancel(currentNotificationId)
                val actualVideoId = if (videoId.isNotEmpty()) videoId else finalFileName
                try {
                    val db = com.videhub.data.AppDatabase.getDatabase(this@DownloadService)
                    db.downloadedVideoDao().deletePlaceholderByVideoId(actualVideoId)
                    if (playlistId != null) {
                        db.downloadedPlaylistDao().deletePlaylistVideoCrossRef(playlistId, actualVideoId)
                    }
                } catch (ex: Exception) {}
                throw e
            } catch (e: Exception) {
                Log.e("DownloadService", "Error during download", e)
                finalFile?.let { if (it.exists()) it.delete() }
                val actualVideoId = if (videoId.isNotEmpty()) videoId else finalFileName
                try {
                    val db = com.videhub.data.AppDatabase.getDatabase(this@DownloadService)
                    db.downloadedVideoDao().deletePlaceholderByVideoId(actualVideoId)
                    if (playlistId != null) {
                        db.downloadedPlaylistDao().deletePlaylistVideoCrossRef(playlistId, actualVideoId)
                    }
                } catch (ex: Exception) {}
                val msg = e.message ?: "Download failed: $safeTitle"
                showError(currentNotificationId, msg, playlistId)
            } finally {
                downloadJobs.remove(currentNotificationId)
                jobPlaylistMap.remove(currentNotificationId)
                com.videhub.viewmodel.DownloadViewModel.removeActiveDownload(downloadKey)
                if (activeDownloads.decrementAndGet() == 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        stopForeground(true)
                    }
                    stopSelf()
                }
            }
            }
        }
        downloadJobs[currentNotificationId] = job
        if (playlistId != null) {
            jobPlaylistMap[currentNotificationId] = playlistId
        }
        return START_NOT_STICKY
    }

    private suspend fun downloadWithRetry(url: String, dest: File, notifId: Int, title: String, playlistId: String?) {
        var lastException: Exception? = null
        repeat(3) { attempt ->
            try {
                return downloadFileInner(url, dest, notifId, title, playlistId)
            } catch (e: java.net.SocketException) {
                lastException = e
                kotlinx.coroutines.delay(1000L * (1 shl attempt))
            } catch (e: java.io.IOException) {
                lastException = e
                kotlinx.coroutines.delay(1000L * (1 shl attempt))
            }
        }
        throw lastException ?: Exception("Download failed - network error")
    }

    private suspend fun downloadFileInner(urlString: String, destFile: File, notifId: Int, title: String, playlistId: String?) {
        destFile.parentFile?.mkdirs()
        val url = java.net.URL(urlString)
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        connection.setRequestProperty("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
        connection.setRequestProperty("Referer", "https://www.youtube.com/")
        connection.setRequestProperty("Origin", "https://www.youtube.com")
        connection.setRequestProperty("X-YouTube-Client-Name", "3")
        connection.setRequestProperty("X-YouTube-Client-Version", "19.09.37")
        connection.connect()

        if (connection.responseCode !in 200..299)
            throw RuntimeException("HTTP ${connection.responseCode}")

        val totalBytes = connection.contentLengthLong.coerceAtLeast(1L)
        val input = connection.inputStream
        val output = FileOutputStream(destFile)
        val buffer = ByteArray(8192)
        var downloaded = 0L
        var lastNotifUpdate = 0L
        var count: Int

        while (input.read(buffer).also { count = it } != -1) {
            kotlinx.coroutines.yield()
            output.write(buffer, 0, count)
            downloaded += count

            // Update every 500ms to avoid notification spam
            val now = System.currentTimeMillis()
            if (now - lastNotifUpdate > 500) {
                lastNotifUpdate = now
                val percent = ((downloaded * 100) / totalBytes).toInt()
                val dlMb = downloaded / (1024 * 1024)
                val totalMb = totalBytes / (1024 * 1024)

                // Update in-app tracker with byte info
                com.videhub.utils.DownloadProgressTracker.updateProgress(
                    notifId, title, percent,
                    downloadedBytes = downloaded,
                    totalBytes = totalBytes
                )

                // Update notification
                updateNotificationProgress(playlistId, notifId, title, percent, dlMb, totalMb)
            }
        }

        output.flush()
        output.close()
        input.close()
        connection.disconnect()
    }

    private fun updateNotificationProgress(playlistId: String?,
        id: Int,
        title: String,
        percent: Int,
        dlMb: Long,
        totalMb: Long
    ) {
        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = "CANCEL_$id"
        }
        val cancelPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, id, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getService(this, id, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(cleanTitle(title))
            .setContentText("Downloading... $percent%")
            .setSubText(if (totalMb > 0) "$dlMb MB / $totalMb MB" else "")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent, percent < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelPending)
            .build()

        if (playlistId == null) notificationManager.notify(id, notification)
    }

    private fun cleanTitle(raw: String): String =
        raw.replace("_", " ").replace(Regex("\\s{2,}"), " ").trim()

    private fun loadBitmapFromUrl(url: String): android.graphics.Bitmap? {
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.connect()
            android.graphics.BitmapFactory.decodeStream(conn.inputStream)
        } catch (_: Exception) { null }
    }

    private fun showError(id: Int, msg: String, playlistId: String?) {
        val errNotification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Error")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        if (playlistId == null) notificationManager.notify(id, errNotification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
