package com.videhub.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.videhub.extractor.ExtractorHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileOutputStream

class QueueDownloadService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var notificationManager: NotificationManager
    private val channelId = "DownloadChannel"
    private var notificationId = 4000

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Downloads",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        


        val urls = intent.getStringArrayListExtra("urls") ?: return START_NOT_STICKY
        val titles = intent.getStringArrayListExtra("titles") ?: return START_NOT_STICKY

        if (urls.isEmpty() || titles.isEmpty() || urls.size != titles.size) {
            stopForeground(true)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        notificationId++
        val currentNotificationId = notificationId

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Queue Download")
            .setContentText("Starting download...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

        val foregroundNotification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("VideHub Downloads")
            .setContentText("Downloads in progress...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
            
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(10001, foregroundNotification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(10001, foregroundNotification)
        }
        
        notificationManager.notify(currentNotificationId, notification)
        com.videhub.utils.DownloadProgressTracker.updateProgress(currentNotificationId, "Queue Download", -1)

        serviceScope.launch {
            var successCount = 0
            val total = urls.size

            try {
                for (i in 0 until total) {
                    val url = urls[i]
                    val title = titles[i]
                    val db = com.videhub.data.AppDatabase.getDatabase(this@QueueDownloadService)
                    if (db.downloadedVideoDao().isFinalVideoDownloaded(url)) { continue }
                    
                    val safeTitle = title.replace(Regex("[^a-zA-Z0-9 \\-_.]"), "_").trim().take(80)

                    updateNotification(currentNotificationId, "Downloading video ${i + 1} of $total", safeTitle, true)

                    try {
                        val info = ExtractorHelper.getStreamInfo(url)
                        val stream = (info.videoStreams ?: emptyList()).filter { !it.content.isNullOrBlank() && !it.isVideoOnly }.maxByOrNull { it.height }
                        
                        if (stream != null && stream.content != null) {
                            val suffix = "${stream.resolution}.mp4"
                            val finalFileName = "${safeTitle}_$suffix"
                            val tempDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                            val finalFile = File(tempDir, finalFileName)
                            
                            downloadWithRetry(stream.content ?: "", finalFile)
                            
                            // Save subtitles
                            var savedLyrics: String? = null
                            try {
                                val subs = info.subtitles
                                val enSubUrl = subs?.find { it.languageTag.equals("en", true) }?.content ?: subs?.find { it.languageTag.startsWith("en", true) }?.content
                                val nativeSubUrl = subs?.find { !it.languageTag.startsWith("en", true) }?.content ?: subs?.firstOrNull()?.content
                                val subArr = org.json.JSONArray()
                                subs?.forEach { s ->
                                    val o = org.json.JSONObject()
                                    o.put("url", s.content)
                                    o.put("languageTag", s.languageTag)
                                    o.put("isAutoGenerated", s.isAutoGenerated)
                                    subArr.put(o)
                                }
                                savedLyrics = com.videhub.ui.components.LiveCaptionsManager.downloadAndSaveCaptions(
                                    context = this@QueueDownloadService,
                                    videoId = info.url ?: url,
                                    nativeUrl = nativeSubUrl,
                                    englishUrl = enSubUrl,
                                    artist = info.uploaderName ?: "",
                                    title = info.name,
                                    description = info.description?.content,
                                    subtitlesJson = subArr.toString()
                                )
                            } catch (e: Exception) {}
                            
                            var finalUri: android.net.Uri? = null
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val values = android.content.ContentValues().apply {
                                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, finalFileName)
                                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "video/mp4")
                                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                                }
                                val resolver = contentResolver
                                val collection = android.provider.MediaStore.Downloads.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                                finalUri = resolver.insert(collection, values)
            
                                if (finalUri != null) {
                                    resolver.openOutputStream(finalUri).use { outStream ->
                                        java.io.FileInputStream(finalFile).use { inStream ->
                                            outStream?.let { inStream.copyTo(it) }
                                        }
                                    }
                                    values.clear()
                                    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                                    resolver.update(finalUri, values, null, null)
                                }
                            } else {
                                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                downloadManager.addCompletedDownload(
                                    finalFileName,
                                    "Downloaded via VideoHub",
                                    true,
                                    "video/mp4",
                                    finalFile.absolutePath,
                                    finalFile.length(),
                                    true
                                )
                                finalUri = FileProvider.getUriForFile(this@QueueDownloadService, "$packageName.provider", finalFile)
                            }
                            
                            if (finalUri != null) {
                                successCount++
                                // Save to Room database
                                val db = com.videhub.data.AppDatabase.getDatabase(this@QueueDownloadService)
                                val originalThumbUrl = info.thumbnails.firstOrNull()?.url ?: ""
                                val localThumbPath = com.videhub.utils.ThumbnailDownloader.downloadThumbnail(
                                    this@QueueDownloadService,
                                    url,
                                    originalThumbUrl
                                )
                                db.downloadedVideoDao().insertDownload(
                                    com.videhub.data.entity.DownloadedVideoEntity(
                                        videoId = url,
                                        title = title,
                                        thumbnailUrl = localThumbPath ?: originalThumbUrl,
                                        channelName = info.uploaderName ?: "",
                                        viewCount = info.viewCount ?: 0L,
                                        uploadDate = info.uploadDate?.offsetDateTime()?.toString() ?: "",
                                        fileName = finalFileName,
                                        isAudioOnly = false,
                                        downloadedAt = System.currentTimeMillis(),
                                        lyrics = savedLyrics
                                    )
                                )
                            }
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && finalFile.exists()) {
                                finalFile.delete()
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("QueueDownloadService", "Failed to download $url", e)
                    }
                }
                
                val viewIntent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                val pendingIntent = PendingIntent.getActivity(
                    this@QueueDownloadService,
                    0,
                    viewIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )

                val successNotification = NotificationCompat.Builder(this@QueueDownloadService, channelId)
                    .setContentTitle("Queue Download Complete")
                    .setContentText("Downloaded $successCount of $total videos")
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(currentNotificationId, successNotification)

            } catch (e: CancellationException) {
                Log.e("QueueDownloadService", "Download cancelled")
                notificationManager.cancel(currentNotificationId)
                throw e
            } catch (e: Exception) {
                Log.e("QueueDownloadService", "Error during queue download", e)
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    stopForeground(true)
                }
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }
    
    private suspend fun downloadWithRetry(url: String, dest: File) {
        var lastException: Exception? = null
        repeat(3) { attempt ->
            try {
                return downloadFileInner(url, dest)
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

    private fun downloadFileInner(urlString: String, destFile: File) {
        destFile.parentFile?.mkdirs()
        
        val url = java.net.URL(urlString)
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        connection.setRequestProperty("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        connection.setRequestProperty("Referer", "https://www.youtube.com/")
        connection.setRequestProperty("Origin", "https://www.youtube.com")
        connection.setRequestProperty("X-YouTube-Client-Name", "3")
        connection.setRequestProperty("X-YouTube-Client-Version", "19.09.37")
        
        connection.connect()
        
        if (connection.responseCode !in 200..299) {
            throw RuntimeException("HTTP Error ${connection.responseCode} for $urlString")
        }
        
        val input = connection.inputStream
        val output = FileOutputStream(destFile)

        val data = ByteArray(4096)
        var count: Int
        while (input.read(data).also { count = it } != -1) {
            output.write(data, 0, count)
        }
        output.flush()
        output.close()
        input.close()
        connection.disconnect()
    }
    
    private fun updateNotification(id: Int, title: String, text: String, ongoing: Boolean) {
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(ongoing)

        if (ongoing) {
            builder.setProgress(100, 0, true)
            com.videhub.utils.DownloadProgressTracker.updateProgress(id, title, -1)
        } else {
            com.videhub.utils.DownloadProgressTracker.updateProgress(id, title, 100, isComplete = true)
            kotlinx.coroutines.GlobalScope.launch {
                kotlinx.coroutines.delay(3000)
                com.videhub.utils.DownloadProgressTracker.removeDownload(id)
            }
        }

        notificationManager.notify(id, builder.build())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
