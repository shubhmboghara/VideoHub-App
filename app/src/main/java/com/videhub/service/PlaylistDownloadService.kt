package com.videhub.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.videhub.data.AppDatabase
import com.videhub.data.entity.DownloadedPlaylistEntity
import com.videhub.data.entity.DownloadedPlaylistVideoCrossRef
import com.videhub.data.entity.DownloadedVideoEntity
import com.videhub.extractor.ExtractorHelper
import com.videhub.utils.DownloadProgressTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class PlaylistDownloadService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var notificationManager: NotificationManager
    private val channelId = "DownloadChannel"
    private var notificationId = 3000

    private val downloadJobs = ConcurrentHashMap<Int, Job>()
    private val activePlaylists = ConcurrentHashMap<String, Int>()

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
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
                cancelPlaylist(pId)
            }
            return START_NOT_STICKY
        }

        if (intent.action?.startsWith("CANCEL_") == true) {
            val id = intent.action?.removePrefix("CANCEL_")?.toIntOrNull()
            if (id != null) {
                val pId = activePlaylists.entries.find { it.value == id }?.key
                if (pId != null) {
                    cancelPlaylist(pId)
                } else {
                    downloadJobs[id]?.cancel()
                    downloadJobs.remove(id)
                    notificationManager.cancel(id)
                }
            }
            return START_NOT_STICKY
        }

        var urls = intent.getStringArrayListExtra("urls")
        var titles = intent.getStringArrayListExtra("titles")
        var thumbnails = intent.getStringArrayListExtra("thumbnails")
        var playlistId = intent.getStringExtra("playlistId")
        var playlistName = intent.getStringExtra("playlistName")
        val singleUrl = intent.getStringExtra("url")
        val isAudioOnly = intent.getBooleanExtra("isAudioOnly", false)
        var playlistThumbnail = intent.getStringExtra("playlistThumbnail") ?: ""

        if ((urls == null || urls.isEmpty()) && singleUrl == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        notificationId++
        val currentNotificationId = notificationId

        val cancelIntent = Intent(this, PlaylistDownloadService::class.java).apply {
            action = "CANCEL_$currentNotificationId"
        }
        val cancelPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, currentNotificationId, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getService(this, currentNotificationId, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val initialNotif = NotificationCompat.Builder(this, channelId)
            .setContentTitle(playlistName ?: "Downloading Playlist")
            .setContentText("Preparing playlist videos...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelPending)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(currentNotificationId, initialNotif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(currentNotificationId, initialNotif)
        }

        val job = serviceScope.launch {
            try {
                if (urls == null || urls!!.isEmpty()) {
                    updateNotification(currentNotificationId, playlistName ?: "Playlist", "Fetching playlist info...", 0, true, cancelPending)
                    val info = ExtractorHelper.getPlaylistInfo(singleUrl!!)
                    val streamItems = info.relatedItems.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                    urls = java.util.ArrayList(streamItems.map { it.url })
                    titles = java.util.ArrayList(streamItems.map { it.name })
                    thumbnails = java.util.ArrayList(streamItems.map { it.thumbnails?.firstOrNull()?.url ?: "" })
                    playlistId = info.url ?: singleUrl
                    playlistName = info.name
                    if (playlistThumbnail.isBlank()) {
                        playlistThumbnail = info.thumbnails?.firstOrNull()?.url ?: thumbnails?.firstOrNull { it.isNotBlank() } ?: ""
                    }
                }

                if (urls == null || urls!!.isEmpty() || titles == null || urls!!.size != titles!!.size) {
                    notificationManager.cancel(currentNotificationId)
                    return@launch
                }

                val pId = playlistId ?: singleUrl ?: "playlist_${System.currentTimeMillis()}"
                activePlaylists[pId] = currentNotificationId

                val db = AppDatabase.getDatabase(this@PlaylistDownloadService)
                val rawPlaylistThumb = playlistThumbnail.ifBlank { thumbnails?.firstOrNull { it.isNotBlank() } ?: "" }
                val finalPlaylistThumb = if (rawPlaylistThumb.isNotBlank()) {
                    com.videhub.utils.ThumbnailDownloader.downloadThumbnail(
                        this@PlaylistDownloadService,
                        pId,
                        rawPlaylistThumb
                    ) ?: rawPlaylistThumb
                } else rawPlaylistThumb

                // Insert Playlist Entity
                db.downloadedPlaylistDao().insertPlaylist(
                    DownloadedPlaylistEntity(
                        playlistId = pId,
                        title = playlistName ?: "Playlist",
                        thumbnailUrl = finalPlaylistThumb
                    )
                )

                // Insert Placeholders
                val total = urls!!.size
                for (i in 0 until total) {
                    val vidUrl = urls!![i]
                    val vidTitle = titles!![i]
                    val vidThumb = thumbnails?.getOrNull(i) ?: finalPlaylistThumb
                    db.downloadedVideoDao().insertDownload(
                        DownloadedVideoEntity(
                            fileName = "PENDING_${vidUrl.hashCode()}",
                            videoId = vidUrl,
                            title = vidTitle,
                            thumbnailUrl = vidThumb,
                            channelName = "",
                            viewCount = 0L,
                            uploadDate = "",
                            isAudioOnly = isAudioOnly,
                            downloadedAt = System.currentTimeMillis() + i,
                            lyrics = null,
                            playlistId = pId,
                            playlistName = playlistName,
                            playlistIndex = i
                        )
                    )
                    db.downloadedPlaylistDao().insertPlaylistVideoCrossRef(
                        DownloadedPlaylistVideoCrossRef(
                            playlistId = pId,
                            videoId = vidUrl
                        )
                    )
                }

                // Dispatch downloads to DownloadService
                for (i in 0 until total) {
                    val url = urls!![i]
                    val title = titles!![i]
                    val vidThumb = thumbnails?.getOrNull(i) ?: finalPlaylistThumb
                    val downloadIntent = Intent(this@PlaylistDownloadService, DownloadService::class.java).apply {
                        putExtra("needsExtraction", true)
                        putExtra("url", url)
                        putExtra("videoId", url)
                        putExtra("title", title)
                        putExtra("thumbnailUrl", vidThumb)
                        putExtra("playlistId", pId)
                        putExtra("playlistName", playlistName)
                        putExtra("playlistIndex", i)
                        putExtra("isAudioOnly", isAudioOnly)
                    }
                    androidx.core.content.ContextCompat.startForegroundService(this@PlaylistDownloadService, downloadIntent)
                }

                // Monitor downloads until all completed or cancelled
                var isDone = false
                while (!isDone) {
                    delay(500L)
                    val allDownloaded = db.downloadedVideoDao().getPlaylistVideosSync(pId)
                    val readyCount = allDownloaded.count { !it.fileName.startsWith("PENDING_") }
                    val activeMap = DownloadProgressTracker.activeDownloads.value
                    val activeDownload = activeMap.values.find { it.playlistId == pId }

                    val pendingCount = allDownloaded.count { it.fileName.startsWith("PENDING_") }
                    if (pendingCount == 0 && activeDownload == null) {
                        isDone = true
                    }

                    val currentProg = activeDownload?.progress?.coerceAtLeast(0) ?: 0
                    val currentTitle = activeDownload?.title ?: if (readyCount < total) titles!![readyCount.coerceAtMost(total - 1)] else "Processing"
                    val overallPercent = (((readyCount * 100) + currentProg) / total).coerceIn(0, 100)

                    if (!isDone) {
                        updateNotification(
                            currentNotificationId,
                            playlistName ?: "Downloading Playlist",
                            "Downloading ${readyCount + 1} of $total: $currentTitle ($currentProg%)",
                            overallPercent,
                            false,
                            cancelPending
                        )
                    }
                }

                // Playlist finished!
                val finalDownloaded = db.downloadedVideoDao().getPlaylistVideosSync(pId)
                val finalReadyCount = finalDownloaded.count { !it.fileName.startsWith("PENDING_") }

                val viewIntent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                val pendingIntent = PendingIntent.getActivity(
                    this@PlaylistDownloadService,
                    0,
                    viewIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )

                val successNotif = NotificationCompat.Builder(this@PlaylistDownloadService, channelId)
                    .setContentTitle("Playlist Download Complete")
                    .setContentText("${playlistName ?: "Playlist"}: $finalReadyCount of $total videos downloaded")
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(currentNotificationId, successNotif)

            } catch (e: CancellationException) {
                Log.e("PlaylistDownloadService", "Playlist download cancelled")
                notificationManager.cancel(currentNotificationId)
                if (playlistId != null) {
                    cancelPlaylist(playlistId!!)
                }
                throw e
            } catch (e: Exception) {
                Log.e("PlaylistDownloadService", "Error downloading playlist", e)
                notificationManager.cancel(currentNotificationId)
            } finally {
                downloadJobs.remove(currentNotificationId)
                val pId = playlistId ?: singleUrl
                if (pId != null) activePlaylists.remove(pId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    stopForeground(false)
                }
                if (downloadJobs.isEmpty()) {
                    stopSelf()
                }
            }
        }

        downloadJobs[currentNotificationId] = job
        if (playlistId != null) {
            activePlaylists[playlistId!!] = currentNotificationId
        }
        return START_NOT_STICKY
    }

    private fun cancelPlaylist(pId: String) {
        val jobId = activePlaylists[pId]
        if (jobId != null) {
            downloadJobs[jobId]?.cancel()
            downloadJobs.remove(jobId)
            notificationManager.cancel(jobId)
            activePlaylists.remove(pId)
        }
        // Send cancel intent to DownloadService
        val cancelServiceIntent = Intent(this, DownloadService::class.java).apply {
            action = "CANCEL_PLAYLIST_$pId"
        }
        startService(cancelServiceIntent)

        // Clean up pending items from DB
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@PlaylistDownloadService)
                db.downloadedPlaylistDao().deletePendingPlaylistVideoCrossRefs(pId)
                db.downloadedVideoDao().deletePendingVideosByPlaylist(pId)
            } catch (ex: Exception) {
                Log.e("PlaylistDownloadService", "Error cleaning up cancelled playlist", ex)
            }
        }
    }

    private fun updateNotification(
        id: Int,
        title: String,
        text: String,
        progress: Int,
        indeterminate: Boolean,
        cancelPending: PendingIntent
    ) {
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, indeterminate)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelPending)
        notificationManager.notify(id, builder.build())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
