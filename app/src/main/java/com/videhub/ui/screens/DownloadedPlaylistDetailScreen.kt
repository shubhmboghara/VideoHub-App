package com.videhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.videhub.data.AppDatabase
import com.videhub.data.entity.DownloadedPlaylistEntity
import com.videhub.data.entity.DownloadedVideoEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedPlaylistDetailScreen(
    playlistId: String,
    onVideoClick: (String, String, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var videos by remember { mutableStateOf<List<DownloadedVideoEntity>>(emptyList()) }
    var playlist by remember { mutableStateOf<DownloadedPlaylistEntity?>(null) }
    val onBackPressedDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val activeDownloads by com.videhub.utils.DownloadProgressTracker.activeDownloads.collectAsStateWithLifecycle(initialValue = emptyMap())
    
    LaunchedEffect(playlistId) {
        val db = AppDatabase.getDatabase(context)
        playlist = db.downloadedPlaylistDao().getPlaylistById(playlistId)
        db.downloadedPlaylistDao().getVideosForPlaylist(playlistId).collectLatest { list ->
            videos = list.filter { video ->
                if (video.fileName.startsWith("PENDING_")) return@filter true
                val fullPath = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), video.fileName)
                fullPath.exists() && fullPath.length() > 0
            }
        }
    }

    val totalBytes = remember(videos) {
        videos.sumOf { video ->
            if (video.fileName.startsWith("PENDING_")) 0L
            else {
                val f = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), video.fileName)
                if (f.exists()) f.length() else 0L
            }
        }
    }
    val totalMbStr = "${totalBytes / (1024 * 1024)} MB"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = playlist?.title ?: "Downloaded Playlist",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val activeForPlaylist = activeDownloads.values.filter { it.playlistId == playlistId }
                        activeForPlaylist.forEach { progress ->
                            val cancelIntent = android.content.Intent(context, com.videhub.service.DownloadService::class.java).apply {
                                action = "CANCEL_${progress.notificationId}"
                            }
                            context.startService(cancelIntent)
                        }
                        val cancelPlaylistIntent = android.content.Intent(context, com.videhub.service.PlaylistDownloadService::class.java).apply {
                            action = "CANCEL_PLAYLIST_$playlistId"
                        }
                        context.startService(cancelPlaylistIntent)
                        coroutineScope.launch {
                            val db = AppDatabase.getDatabase(context)
                            videos.forEach { video ->
                                val fullPath = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), video.fileName)
                                if (fullPath.exists()) fullPath.delete()
                                db.downloadedVideoDao().deleteDownload(video)
                            }
                            db.downloadedPlaylistDao().deletePlaylist(playlistId)
                            onBackPressedDispatcher?.onBackPressed()
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Playlist")
                    }
                }
            )
        }
    ) { padding ->
        val readyVideos = remember(videos) { videos.filter { !it.fileName.startsWith("PENDING_") } }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                val rawCover = playlist?.thumbnailUrl.takeIf { !it.isNullOrBlank() }
                    ?: videos.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
                
                val firstVideoFile = readyVideos.firstOrNull()?.let { v ->
                    val f = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), v.fileName)
                    if (f.exists()) f.absolutePath else null
                }

                val coverPathToUse = rawCover ?: firstVideoFile
                val pendingCount = videos.count { it.fileName.startsWith("PENDING_") }

                com.videhub.ui.components.PlaylistHeader(
                    title = playlist?.title ?: "Downloaded Playlist",
                    subtitle = "${readyVideos.size} tracks ready • $totalMbStr",
                    thumbnailUrl = coverPathToUse,
                    isPlayAllEnabled = readyVideos.isNotEmpty(),
                    isShuffleEnabled = readyVideos.isNotEmpty(),
                    onPlayAllClick = {
                        if (readyVideos.isNotEmpty()) {
                            com.videhub.QueueManager.clear()
                            for (i in 1 until readyVideos.size) {
                                val qItem = readyVideos[i]
                                val qPath = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), qItem.fileName).absolutePath
                                com.videhub.QueueManager.enqueue(
                                    com.videhub.PlayQueueItem(
                                        url = qPath,
                                        title = qItem.title,
                                        uploaderName = qItem.channelName,
                                        thumbnailUrl = qItem.thumbnailUrl
                                    )
                                )
                            }
                            val first = readyVideos.first()
                            val firstPath = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), first.fileName).absolutePath
                            onVideoClick(firstPath, first.title, first.thumbnailUrl, first.isAudioOnly)
                        }
                    },
                    onShuffleClick = {
                        if (readyVideos.isNotEmpty()) {
                            com.videhub.QueueManager.clear()
                            val shuffled = readyVideos.shuffled()
                            for (i in 1 until shuffled.size) {
                                val qItem = shuffled[i]
                                val qPath = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), qItem.fileName).absolutePath
                                com.videhub.QueueManager.enqueue(
                                    com.videhub.PlayQueueItem(
                                        url = qPath,
                                        title = qItem.title,
                                        uploaderName = qItem.channelName,
                                        thumbnailUrl = qItem.thumbnailUrl
                                    )
                                )
                            }
                            val first = shuffled.first()
                            val firstPath = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), first.fileName).absolutePath
                            onVideoClick(firstPath, first.title, first.thumbnailUrl, first.isAudioOnly)
                        }
                    },
                    topBadge = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (pendingCount > 0) Color.Black.copy(alpha = 0.75f) else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                if (pendingCount > 0) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Downloading (${videos.size - pendingCount}/${videos.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.DownloadDone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Downloaded Playlist",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
            }

            itemsIndexed(videos, key = { index, video -> "${video.videoId}_$index" }) { index, video ->
                val fullPath = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), video.fileName)
                val fullPathStr = fullPath.absolutePath
                val fileSizeStr = if (fullPath.exists()) "${fullPath.length() / (1024 * 1024)} MB" else ""

                val onClickAction = {
                    if (!video.fileName.startsWith("PENDING_") && fullPath.exists()) {
                        com.videhub.QueueManager.clear()
                        val currentIndex = readyVideos.indexOf(video)
                        if (currentIndex in 0 until readyVideos.lastIndex) {
                            for (i in (currentIndex + 1)..readyVideos.lastIndex) {
                                val qItem = readyVideos[i]
                                val qPath = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), qItem.fileName).absolutePath
                                com.videhub.QueueManager.enqueue(com.videhub.PlayQueueItem(
                                    url = qPath,
                                    title = qItem.title,
                                    uploaderName = qItem.channelName,
                                    thumbnailUrl = qItem.thumbnailUrl
                                ))
                            }
                        }
                        onVideoClick(fullPathStr, video.title, video.thumbnailUrl, video.isAudioOnly)
                    }
                }

                if (video.fileName.startsWith("PENDING_")) {
                    val progressInfo = activeDownloads.values.find { it.videoId == video.videoId }
                    val progressPercent = progressInfo?.progress ?: -1
                    val downloadedBytes = progressInfo?.downloadedBytes ?: 0L
                    val totalItemBytes = progressInfo?.totalBytes ?: 0L
                    val safeProgress = (progressPercent.coerceAtLeast(0) / 100f).coerceIn(0f, 1f)

                    com.videhub.ui.components.VideoRowItem(
                        modifier = Modifier.alpha(0.6f),
                        videoUrl = "",
                        title = video.title,
                        uploaderName = video.channelName,
                        thumbnailUrl = video.thumbnailUrl.takeIf { !it.isNullOrBlank() } ?: playlist?.thumbnailUrl,
                        onClick = {},
                        trailingIcon = {
                            Column(horizontalAlignment = Alignment.End) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (progressPercent < 0) "Queued..." else "${progressPercent}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                } else {
                    com.videhub.ui.components.VideoRowItem(
                        videoUrl = video.videoId,
                        title = video.title,
                        uploaderName = video.channelName,
                        thumbnailUrl = video.thumbnailUrl,
                        onClick = onClickAction,
                        trailingIcon = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = fileSizeStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                IconButton(
                                    modifier = Modifier.size(24.dp),
                                    onClick = {
                                        coroutineScope.launch {
                                            val db = AppDatabase.getDatabase(context)
                                            if (fullPath.exists()) fullPath.delete()
                                            db.downloadedVideoDao().deleteDownload(video)
                                            db.downloadedPlaylistDao().deletePlaylistVideoCrossRef(playlistId, video.videoId)
                                        }
                                    }
                                ) {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
