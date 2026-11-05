package com.videhub.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.videhub.data.AppDatabase
import com.videhub.data.entity.DownloadedPlaylistEntity
import com.videhub.data.entity.DownloadedVideoEntity
import com.videhub.service.DownloadService
import com.videhub.service.PlaylistDownloadService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun DownloadedPlaylistDetailScreen(
    playlistId: String,
    onVideoClick: (String, String, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val playlist by db.downloadedPlaylistDao().getPlaylistByIdFlow(playlistId).collectAsStateWithLifecycle(initialValue = null)
    var videos by remember { mutableStateOf<List<DownloadedVideoEntity>>(emptyList()) }
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    LaunchedEffect(playlistId) {
        db.downloadedPlaylistDao().getVideosForPlaylist(playlistId).collectLatest { list ->
            videos = list
        }
    }

    val readyVideos = remember(videos) {
        videos.filter { video ->
            if (video.state == "DONE") {
                val fullPath = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), video.fileName)
                fullPath.exists() && fullPath.length() > 0
            } else false
        }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete '${playlist?.title ?: "Playlist"}' and all its downloaded files?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        coroutineScope.launch {
                            videos.forEach { video ->
                                val fullPath = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), video.fileName)
                                if (fullPath.exists()) fullPath.delete()
                                db.downloadedVideoDao().deleteDownload(video)
                            }
                            db.downloadedPlaylistDao().deletePlaylist(playlistId)
                            onBackPressedDispatcher?.onBackPressed()
                            Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    DownloadedPlaylistDetailContent(
        playlist = playlist,
        videos = videos,
        onBackClick = { onBackPressedDispatcher?.onBackPressed() },
        onCancelDownload = {
            val cancelPlaylistIntent = Intent(context, PlaylistDownloadService::class.java).apply {
                action = "CANCEL_PLAYLIST_$playlistId"
            }
            context.startService(cancelPlaylistIntent)
            coroutineScope.launch {
                db.downloadedPlaylistDao().deletePendingPlaylistVideoCrossRefs(playlistId)
                db.downloadedVideoDao().deletePendingVideosByPlaylist(playlistId)
                if (readyVideos.isEmpty()) {
                    db.downloadedPlaylistDao().deletePlaylist(playlistId)
                    onBackPressedDispatcher?.onBackPressed()
                } else {
                    db.downloadedPlaylistDao().updatePlaylistState(playlistId, "DONE")
                }
            }
            Toast.makeText(context, "Playlist download cancelled", Toast.LENGTH_SHORT).show()
        },
        onDeletePlaylistClick = { showDeleteConfirmDialog = true },
        onPlayAllClick = {
            if (readyVideos.isNotEmpty()) {
                com.videhub.QueueManager.clear()
                for (i in 1 until readyVideos.size) {
                    val qItem = readyVideos[i]
                    val qPath = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), qItem.fileName).absolutePath
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
                val firstPath = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), first.fileName).absolutePath
                onVideoClick(firstPath, first.title, first.thumbnailUrl, first.isAudioOnly)
            }
        },
        onShuffleClick = {
            if (readyVideos.isNotEmpty()) {
                com.videhub.QueueManager.clear()
                val shuffled = readyVideos.shuffled()
                for (i in 1 until shuffled.size) {
                    val qItem = shuffled[i]
                    val qPath = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), qItem.fileName).absolutePath
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
                val firstPath = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), first.fileName).absolutePath
                onVideoClick(firstPath, first.title, first.thumbnailUrl, first.isAudioOnly)
            }
        },
        onVideoClick = { video ->
            val fullPath = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), video.fileName)
            if (video.state == "DONE" && fullPath.exists()) {
                com.videhub.QueueManager.clear()
                val currentIndex = readyVideos.indexOf(video)
                if (currentIndex in 0 until readyVideos.lastIndex) {
                    for (i in (currentIndex + 1)..readyVideos.lastIndex) {
                        val qItem = readyVideos[i]
                        val qPath = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), qItem.fileName).absolutePath
                        com.videhub.QueueManager.enqueue(
                            com.videhub.PlayQueueItem(
                                url = qPath,
                                title = qItem.title,
                                uploaderName = qItem.channelName,
                                thumbnailUrl = qItem.thumbnailUrl
                            )
                        )
                    }
                }
                onVideoClick(fullPath.absolutePath, video.title, video.thumbnailUrl, video.isAudioOnly)
            }
        },
        onDeleteVideo = { video ->
            coroutineScope.launch {
                val fullPath = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), video.fileName)
                if (fullPath.exists()) fullPath.delete()
                db.downloadedVideoDao().deleteDownload(video)
                db.downloadedPlaylistDao().deletePlaylistVideoCrossRef(playlistId, video.videoId)
                Toast.makeText(context, "Track removed", Toast.LENGTH_SHORT).show()
            }
        },
        onCancelVideoDownload = { video ->
            val cancelIntent = Intent(context, DownloadService::class.java).apply {
                action = "CANCEL_${video.videoId.hashCode()}"
            }
            context.startService(cancelIntent)
            coroutineScope.launch {
                db.downloadedVideoDao().deletePlaceholderByVideoId(video.videoId)
                db.downloadedPlaylistDao().deletePlaylistVideoCrossRef(playlistId, video.videoId)
            }
            Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
        },
        onRetryVideoDownload = { video ->
            val retryIntent = Intent(context, DownloadService::class.java).apply {
                putExtra("needsExtraction", true)
                putExtra("url", video.videoId)
                putExtra("videoId", video.videoId)
                putExtra("title", video.title)
                putExtra("thumbnailUrl", video.thumbnailUrl)
                putExtra("playlistId", playlistId)
                putExtra("playlistName", playlist?.title)
                putExtra("playlistIndex", video.playlistIndex)
                putExtra("isAudioOnly", video.isAudioOnly)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(retryIntent)
            } else {
                context.startService(retryIntent)
            }
            coroutineScope.launch {
                db.downloadedVideoDao().updateVideoState(video.videoId, state = "QUEUED", errorMessage = null)
                db.downloadedPlaylistDao().updatePlaylistState(playlistId, "DOWNLOADING")
            }
            Toast.makeText(context, "Retrying download...", Toast.LENGTH_SHORT).show()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedPlaylistDetailContent(
    playlist: DownloadedPlaylistEntity?,
    videos: List<DownloadedVideoEntity>,
    onBackClick: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeletePlaylistClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onVideoClick: (DownloadedVideoEntity) -> Unit,
    onDeleteVideo: (DownloadedVideoEntity) -> Unit,
    onCancelVideoDownload: (DownloadedVideoEntity) -> Unit,
    onRetryVideoDownload: (DownloadedVideoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCount = playlist?.totalCount?.takeIf { it > 0 } ?: videos.size
    val readyVideos = videos.filter { it.state == "DONE" && !it.fileName.startsWith("PENDING_") }
    val readyCount = readyVideos.size

    val isPreparing = playlist?.state.equals("PREPARING", ignoreCase = true) || (playlist?.state.equals("DOWNLOADING", ignoreCase = true) && totalCount == 0)
    val isDownloading = !isPreparing && (playlist?.state.equals("DOWNLOADING", ignoreCase = true) || (totalCount > 0 && readyCount < totalCount && !playlist?.state.equals("FAILED", ignoreCase = true)))
    val isFailed = playlist?.state.equals("FAILED", ignoreCase = true)

    val downloadedBytes = videos.sumOf { it.bytesDownloaded }
    val totalEstimatedBytes = if (playlist?.totalBytes != null && playlist.totalBytes > 0) {
        playlist.totalBytes
    } else {
        val sumTotal = videos.sumOf { it.totalBytes }
        if (sumTotal > 0) sumTotal else downloadedBytes
    }

    val headerSubtitle = "${readyCount} of ${totalCount} downloaded • ${formatMB(downloadedBytes)} of ~${formatMB(totalEstimatedBytes.coerceAtLeast(downloadedBytes))}"
    val overallProgress = if (readyCount >= totalCount && totalCount > 0) 1f else {
        ((playlist?.progress ?: if (totalCount > 0) (readyCount * 100) / totalCount else 0) / 100f).coerceIn(0f, 1f)
    }

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
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isDownloading || isPreparing) {
                        TextButton(
                            onClick = onCancelDownload,
                            modifier = Modifier.testTag("cancel_playlist_download_button")
                        ) {
                            Text("Cancel download", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(
                            onClick = onDeletePlaylistClick,
                            modifier = Modifier.testTag("delete_playlist_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Playlist")
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Header Item
            item(key = "playlist_header") {
                val coverThumb = playlist?.thumbnailUrl.takeIf { !it.isNullOrBlank() }
                    ?: videos.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cover Thumbnail
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!coverThumb.isNullOrBlank()) {
                            AsyncImage(
                                model = coverThumb,
                                contentDescription = "Playlist Cover",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(56.dp)
                            )
                        }

                        // State Badge inside cover
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                isFailed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
                                isDownloading || isPreparing -> Color.Black.copy(alpha = 0.78f)
                                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                when {
                                    isPreparing -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Preparing…",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    isDownloading -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Downloading…",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    isFailed -> {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Failed",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.DownloadDone,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Downloaded",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = playlist?.title ?: "Downloaded Playlist",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = headerSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // Overall Progress Bar
                    if (isDownloading || isPreparing || isFailed) {
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { overallProgress },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    // Play All & Shuffle Buttons
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPlayAllClick,
                            enabled = readyVideos.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play All", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onShuffleClick,
                            enabled = readyVideos.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Per-Row Videos
            itemsIndexed(videos, key = { index, video -> "${video.videoId}_$index" }) { index, video ->
                val isRowDone = video.state.equals("DONE", ignoreCase = true) && !video.fileName.startsWith("PENDING_")
                val isRowDownloading = video.state.equals("DOWNLOADING", ignoreCase = true)
                val isRowFailed = video.state.equals("FAILED", ignoreCase = true)
                val isRowQueued = video.state.equals("QUEUED", ignoreCase = true) || (!isRowDone && !isRowDownloading && !isRowFailed)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isRowDone) { onVideoClick(video) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("playlist_item_${video.videoId}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left: 16:9 Thumbnail
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (!video.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = video.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (video.isAudioOnly) Icons.Default.MusicNote else Icons.AutoMirrored.Filled.PlaylistPlay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Duration badge for DONE items
                        if (isRowDone && video.duration.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = video.duration,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Middle: Title & Metadata
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isRowDone) FontWeight.Medium else FontWeight.Normal,
                            color = if (isRowFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        val metaText = when {
                            isRowQueued -> "${video.channelName.ifBlank { "Channel" }} • Queued"
                            isRowDownloading -> "${video.channelName.ifBlank { "Channel" }} • ${formatMB(video.bytesDownloaded)} / ${formatMB(video.totalBytes)}"
                            isRowFailed -> "${video.channelName.ifBlank { "Channel" }} • Download failed"
                            else -> "${video.channelName.ifBlank { "Channel" }} • ${formatMB(video.bytesDownloaded)}"
                        }

                        Text(
                            text = metaText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRowFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Right: State-Specific Trailing Control
                    when {
                        isRowQueued -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Queued",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Queued",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        isRowDownloading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${video.progress}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = { onCancelVideoDownload(video) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel Download",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        isRowFailed -> {
                            FilledTonalButton(
                                onClick = { onRetryVideoDownload(video) },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        isRowDone -> {
                            IconButton(
                                onClick = { onDeleteVideo(video) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Track",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



// ── Previews for PlaylistDetailScreen: preparing, downloading, done, failed ───

@Preview(name = "Playlist Detail - Preparing", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PlaylistDetailPreparingPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        DownloadedPlaylistDetailContent(
            playlist = DownloadedPlaylistEntity(
                playlistId = "sample_p1",
                title = "Study & Relax Chill Beats",
                thumbnailUrl = null,
                totalCount = 0,
                completedCount = 0,
                state = "PREPARING",
                progress = 0
            ),
            videos = emptyList(),
            onBackClick = {},
            onCancelDownload = {},
            onDeletePlaylistClick = {},
            onPlayAllClick = {},
            onShuffleClick = {},
            onVideoClick = {},
            onDeleteVideo = {},
            onCancelVideoDownload = {},
            onRetryVideoDownload = {}
        )
    }
}

@Preview(name = "Playlist Detail - Downloading", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PlaylistDetailDownloadingPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        DownloadedPlaylistDetailContent(
            playlist = DownloadedPlaylistEntity(
                playlistId = "sample_p2",
                title = "Synthwave Neon Nights",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                totalCount = 3,
                completedCount = 1,
                state = "DOWNLOADING",
                progress = 45,
                downloadedBytes = 45 * 1024 * 1024L,
                totalBytes = 120 * 1024 * 1024L
            ),
            videos = listOf(
                DownloadedVideoEntity(
                    fileName = "track_1.mp4",
                    videoId = "v1",
                    title = "Neon Dreams (Intro)",
                    thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                    channelName = "SynthWave Hub",
                    state = "DONE",
                    progress = 100,
                    bytesDownloaded = 32 * 1024 * 1024L,
                    totalBytes = 32 * 1024 * 1024L,
                    duration = "3:45"
                ),
                DownloadedVideoEntity(
                    fileName = "PENDING_v2",
                    videoId = "v2",
                    title = "Midnight Expressway",
                    thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                    channelName = "SynthWave Hub",
                    state = "DOWNLOADING",
                    progress = 63,
                    bytesDownloaded = 22 * 1024 * 1024L,
                    totalBytes = 35 * 1024 * 1024L,
                    duration = "4:12"
                ),
                DownloadedVideoEntity(
                    fileName = "PENDING_v3",
                    videoId = "v3",
                    title = "Sunset Overdrive",
                    thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                    channelName = "SynthWave Hub",
                    state = "QUEUED",
                    progress = 0,
                    bytesDownloaded = 0L,
                    totalBytes = 0L,
                    duration = "5:01"
                )
            ),
            onBackClick = {},
            onCancelDownload = {},
            onDeletePlaylistClick = {},
            onPlayAllClick = {},
            onShuffleClick = {},
            onVideoClick = {},
            onDeleteVideo = {},
            onCancelVideoDownload = {},
            onRetryVideoDownload = {}
        )
    }
}

@Preview(name = "Playlist Detail - Done", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PlaylistDetailDonePreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        DownloadedPlaylistDetailContent(
            playlist = DownloadedPlaylistEntity(
                playlistId = "sample_p3",
                title = "Acoustic Coffeehouse",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                totalCount = 2,
                completedCount = 2,
                state = "DONE",
                progress = 100,
                downloadedBytes = 58 * 1024 * 1024L,
                totalBytes = 58 * 1024 * 1024L
            ),
            videos = listOf(
                DownloadedVideoEntity(
                    fileName = "track_a.mp4",
                    videoId = "va",
                    title = "Morning Sunshine Acoustic",
                    thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                    channelName = "Indie Sessions",
                    state = "DONE",
                    progress = 100,
                    bytesDownloaded = 28 * 1024 * 1024L,
                    totalBytes = 28 * 1024 * 1024L,
                    duration = "3:20"
                ),
                DownloadedVideoEntity(
                    fileName = "track_b.mp4",
                    videoId = "vb",
                    title = "Rainy Windowpane Melodies",
                    thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                    channelName = "Indie Sessions",
                    state = "DONE",
                    progress = 100,
                    bytesDownloaded = 30 * 1024 * 1024L,
                    totalBytes = 30 * 1024 * 1024L,
                    duration = "4:05"
                )
            ),
            onBackClick = {},
            onCancelDownload = {},
            onDeletePlaylistClick = {},
            onPlayAllClick = {},
            onShuffleClick = {},
            onVideoClick = {},
            onDeleteVideo = {},
            onCancelVideoDownload = {},
            onRetryVideoDownload = {}
        )
    }
}

@Preview(name = "Playlist Detail - Failed", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PlaylistDetailFailedPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        DownloadedPlaylistDetailContent(
            playlist = DownloadedPlaylistEntity(
                playlistId = "sample_p4",
                title = "Hard Rock Anthems",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                totalCount = 2,
                completedCount = 1,
                state = "FAILED",
                progress = 50,
                downloadedBytes = 25 * 1024 * 1024L,
                totalBytes = 50 * 1024 * 1024L
            ),
            videos = listOf(
                DownloadedVideoEntity(
                    fileName = "rock_1.mp4",
                    videoId = "r1",
                    title = "Thunderstruck Live",
                    thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                    channelName = "Rock Classics",
                    state = "DONE",
                    progress = 100,
                    bytesDownloaded = 25 * 1024 * 1024L,
                    totalBytes = 25 * 1024 * 1024L,
                    duration = "4:52"
                ),
                DownloadedVideoEntity(
                    fileName = "PENDING_r2",
                    videoId = "r2",
                    title = "Back in Black HD",
                    thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                    channelName = "Rock Classics",
                    state = "FAILED",
                    progress = 0,
                    bytesDownloaded = 0L,
                    totalBytes = 0L,
                    duration = "4:15",
                    errorMessage = "Network timeout"
                )
            ),
            onBackClick = {},
            onCancelDownload = {},
            onDeletePlaylistClick = {},
            onPlayAllClick = {},
            onShuffleClick = {},
            onVideoClick = {},
            onDeleteVideo = {},
            onCancelVideoDownload = {},
            onRetryVideoDownload = {}
        )
    }
}
