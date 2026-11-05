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
    private val FOREGROUND_NOTIFICATION_ID = 20000

    private fun ensureForegroundStarted() {
        val foregroundNotification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("VideHub Playlist Downloads")
            .setContentText("Playlist download service active")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_NOTIFICATION_ID, foregroundNotification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, foregroundNotification)
        }
    }

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
        ensureForegroundStarted()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForegroundStarted()

        if (intent == null) {
            if (activePlaylists.isEmpty() && downloadJobs.isEmpty()) {
                stopForeground(true)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        if (intent.action?.startsWith("CANCEL_PLAYLIST_") == true) {
            val pId = intent.action?.removePrefix("CANCEL_PLAYLIST_")
            if (pId != null) {
                cancelPlaylist(pId)
            }
            if (activePlaylists.isEmpty() && downloadJobs.isEmpty()) {
                stopForeground(true)
                stopSelf()
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
        var durations = intent.getStringArrayListExtra("durations")
        var channels = intent.getStringArrayListExtra("channels")
        var playlistId = intent.getStringExtra("playlistId")
        var playlistName = intent.getStringExtra("playlistName")
        val singleUrl = intent.getStringExtra("url")
        val isAudioOnly = intent.getBooleanExtra("isAudioOnly", false)
        var playlistThumbnail = intent.getStringExtra("playlistThumbnail") ?: ""

        if ((urls == null || urls.isEmpty()) && singleUrl == null) {
            stopForeground(true)
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
                val db = AppDatabase.getDatabase(this@PlaylistDownloadService)
                val pId = playlistId ?: singleUrl ?: "playlist_${System.currentTimeMillis()}"
                activePlaylists[pId] = currentNotificationId

                // If info not extracted yet, insert PREPARING state into Room immediately
                if (urls == null || urls!!.isEmpty()) {
                    db.downloadedPlaylistDao().insertPlaylist(
                        DownloadedPlaylistEntity(
                            playlistId = pId,
                            title = playlistName ?: "Playlist",
                            thumbnailUrl = playlistThumbnail.ifBlank { null },
                            totalCount = 0,
                            completedCount = 0,
                            state = "PREPARING",
                            progress = 0
                        )
                    )

                    updateNotification(currentNotificationId, playlistName ?: "Playlist", "Fetching playlist info...", 0, true, cancelPending)
                    val info = ExtractorHelper.getPlaylistInfo(singleUrl!!)
                    val streamItems = info.relatedItems.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                    urls = java.util.ArrayList(streamItems.map { it.url })
                    titles = java.util.ArrayList(streamItems.map { it.name })
                    thumbnails = java.util.ArrayList(streamItems.map { it.thumbnails?.firstOrNull()?.url ?: "" })
                    durations = java.util.ArrayList(streamItems.map { 
                        val s = it.duration
                        val m = s / 60
                        val sec = s % 60
                        if (s > 0) String.format(java.util.Locale.US, "%d:%02d", m, sec) else ""
                    })
                    channels = java.util.ArrayList(streamItems.map { it.uploaderName ?: "" })
                    playlistId = info.url ?: singleUrl
                    playlistName = info.name
                    if (playlistThumbnail.isBlank()) {
                        playlistThumbnail = info.thumbnails?.firstOrNull()?.url ?: thumbnails?.firstOrNull { it.isNotBlank() } ?: ""
                    }
                }

                if (urls == null || urls!!.isEmpty() || titles == null || urls!!.size != titles!!.size) {
                    db.downloadedPlaylistDao().updatePlaylistState(pId, "FAILED")
                    notificationManager.cancel(currentNotificationId)
                    return@launch
                }

                val total = urls!!.size
                val rawPlaylistThumb = playlistThumbnail.ifBlank { thumbnails?.firstOrNull { it.isNotBlank() } ?: "" }
                val finalPlaylistThumb = if (rawPlaylistThumb.isNotBlank()) {
                    com.videhub.utils.ThumbnailDownloader.downloadThumbnail(
                        this@PlaylistDownloadService,
                        pId,
                        rawPlaylistThumb
                    ) ?: rawPlaylistThumb
                } else rawPlaylistThumb

                // STEP 1: Immediately insert playlist into Room before any file download
                db.downloadedPlaylistDao().insertPlaylist(
                    DownloadedPlaylistEntity(
                        playlistId = pId,
                        title = playlistName ?: "Playlist",
                        thumbnailUrl = finalPlaylistThumb,
                        totalCount = total,
                        completedCount = 0,
                        state = "DOWNLOADING",
                        progress = 0,
                        downloadedBytes = 0L,
                        totalBytes = 0L,
                        currentVideoTitle = titles!!.firstOrNull(),
                        currentVideoIndex = 1,
                        currentVideoProgress = 0
                    )
                )

                // Insert one row per video with state QUEUED
                for (i in 0 until total) {
                    val vidUrl = urls!![i]
                    val vidTitle = titles!![i]
                    val vidThumb = thumbnails?.getOrNull(i)?.takeIf { it.isNotBlank() } ?: finalPlaylistThumb
                    val vidDur = durations?.getOrNull(i) ?: ""
                    val vidChan = channels?.getOrNull(i) ?: ""

                    // Check if already completely downloaded
                    val existing = db.downloadedVideoDao().getVideoByVideoId(vidUrl)
                    if (existing != null && existing.state == "DONE") {
                        db.downloadedPlaylistDao().insertPlaylistVideoCrossRef(
                            DownloadedPlaylistVideoCrossRef(playlistId = pId, videoId = vidUrl)
                        )
                        continue
                    }

                    db.downloadedVideoDao().insertDownload(
                        DownloadedVideoEntity(
                            fileName = "PENDING_${pId}_${i}_${vidUrl.hashCode()}",
                            videoId = vidUrl,
                            title = vidTitle,
                            thumbnailUrl = vidThumb,
                            channelName = vidChan,
                            viewCount = 0L,
                            uploadDate = "",
                            isAudioOnly = isAudioOnly,
                            downloadedAt = System.currentTimeMillis() + i,
                            lyrics = null,
                            playlistId = pId,
                            playlistName = playlistName,
                            playlistIndex = i,
                            state = "QUEUED",
                            progress = 0,
                            bytesDownloaded = 0L,
                            totalBytes = 0L,
                            duration = vidDur
                        )
                    )
                    db.downloadedPlaylistDao().insertPlaylistVideoCrossRef(
                        DownloadedPlaylistVideoCrossRef(
                            playlistId = pId,
                            videoId = vidUrl
                        )
                    )
                }

                // Dispatch downloads to DownloadService strictly ONE BY ONE sequentially
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

                    // Monitor current item download until finished or failed
                    var isItemDone = false
                    while (!isItemDone) {
                        delay(400L)
                        val allVideos = db.downloadedVideoDao().getPlaylistVideosSync(pId)
                        val readyCount = allVideos.count { it.state == "DONE" }
                        val currentVideo = allVideos.find { it.videoId == url }
                        val currentProg = currentVideo?.progress?.coerceIn(0, 100) ?: 0
                        val trackIndex = (readyCount + 1).coerceAtMost(total)

                        val overallWeightedProg = if (total > 0) {
                            (((readyCount * 100) + currentProg) / total).coerceIn(0, 100)
                        } else 0

                        if (currentVideo != null && (currentVideo.state == "DONE" || currentVideo.state == "FAILED")) {
                            isItemDone = true
                        } else {
                            // Update cover summary notification with weighted average percentage
                            updateNotification(
                                currentNotificationId,
                                playlistName ?: "Downloading Playlist",
                                "Downloading $trackIndex of $total • $overallWeightedProg%",
                                overallWeightedProg,
                                false,
                                cancelPending
                            )
                        }
                    }
                }

                // Playlist finished!
                val finalVideos = db.downloadedVideoDao().getPlaylistVideosSync(pId)
                val finalReadyCount = finalVideos.count { it.state == "DONE" }
                val hasFailures = finalVideos.any { it.state == "FAILED" }

                val finalState = if (hasFailures) "FAILED" else "DONE"
                val sumBytes = finalVideos.sumOf { it.bytesDownloaded }
                db.downloadedPlaylistDao().updatePlaylistProgress(
                    playlistId = pId,
                    completedCount = finalReadyCount,
                    progress = if (hasFailures) (((finalReadyCount * 100)) / total).coerceIn(0, 100) else 100,
                    downloadedBytes = sumBytes,
                    totalBytes = sumBytes,
                    state = finalState,
                    currentVideoIndex = total,
                    currentVideoProgress = 100,
                    currentVideoTitle = null
                )

                val openAppIntent = Intent(this@PlaylistDownloadService, com.videhub.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val pendingIntent = PendingIntent.getActivity(
                    this@PlaylistDownloadService,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val successNotif = NotificationCompat.Builder(this@PlaylistDownloadService, channelId)
                    .setContentTitle(if (hasFailures) "Playlist Download Finished with Errors" else "Playlist Download Complete")
                    .setContentText("${playlistName ?: "Playlist"}: $finalReadyCount of $total videos downloaded")
                    .setSmallIcon(if (hasFailures) android.R.drawable.stat_notify_error else android.R.drawable.stat_sys_download_done)
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
        DownloadService.cancelPlaylist(this, pId)

        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@PlaylistDownloadService)
                db.downloadedPlaylistDao().deletePendingPlaylistVideoCrossRefs(pId)
                db.downloadedVideoDao().deletePendingVideosByPlaylist(pId)
                val plVideos = db.downloadedVideoDao().getPlaylistVideosSync(pId)
                if (plVideos.none { it.state == "DONE" }) {
                    db.downloadedPlaylistDao().deletePlaylist(pId)
                } else {
                    db.downloadedPlaylistDao().updatePlaylistState(pId, "DONE")
                }
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
