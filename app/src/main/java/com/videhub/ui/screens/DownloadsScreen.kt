package com.videhub.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle


import android.os.Environment
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File
import com.videhub.data.entity.DownloadedVideoEntity
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.ui.platform.testTag
import com.videhub.utils.DownloadProgress
import com.videhub.utils.DownloadProgressTracker

data class DownloadItemData(
    val file: File,
    val dbEntity: DownloadedVideoEntity?
)

// ── Helper: clean ugly underscored filenames ──────────────────────────────────
fun cleanTitle(raw: String): String =
    raw.replace("_", " ").replace(Regex("\\s{2,}"), " ").trim()

// ── Helper: clean garbled text from channel name ────────────────────────────
fun cleanChannelName(raw: String): String =
    raw.replace(Regex("\\p{C}"), "").trim()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(navController: androidx.navigation.NavController, onVideoClick: (String, String, String, Boolean) -> Unit) {
    val context = LocalContext.current
    val downloadViewModel: com.videhub.viewmodel.DownloadViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val scope = rememberCoroutineScope()
    val db = com.videhub.data.AppDatabase.getDatabase(context)
    
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val oldDownloads = db.downloadedVideoDao().getAllDownloads().first()
                for (download in oldDownloads) {
                    if (download.thumbnailUrl.startsWith("http")) {
                        val localThumb = com.videhub.utils.ThumbnailDownloader.downloadThumbnail(
                            context,
                            download.videoId,
                            download.thumbnailUrl
                        )
                        if (localThumb != null && localThumb != download.thumbnailUrl) {
                            db.downloadedVideoDao().insertDownload(
                                download.copy(thumbnailUrl = localThumb)
                            )
                        }
                    }
                }
                val oldPlaylists = db.downloadedPlaylistDao().getAllDownloadedPlaylists().first()
                for (pl in oldPlaylists) {
                    if (pl.thumbnailUrl?.startsWith("http") == true) {
                        val localThumb = com.videhub.utils.ThumbnailDownloader.downloadThumbnail(
                            context,
                            pl.playlistId,
                            pl.thumbnailUrl
                        )
                        if (localThumb != null && localThumb != pl.thumbnailUrl) {
                            db.downloadedPlaylistDao().updatePlaylistThumbnail(pl.playlistId, localThumb)
                        }
                    }
                }
            } catch (e: Exception) {}
        }
    }
    val downloadedPlaylists by db.downloadedPlaylistDao().getDownloadedPlaylistsSummary().collectAsStateWithLifecycle(initialValue = emptyList())
    val allDownloads by db.downloadedVideoDao().getSingleDownloads().collectAsStateWithLifecycle(initialValue = emptyList())
    val playlistDownloads by db.downloadedVideoDao().getPlaylistDownloads().collectAsStateWithLifecycle(initialValue = emptyList())
    val activeSingleDownloadsMap by com.videhub.utils.DownloadProgressTracker.activeDownloads.collectAsStateWithLifecycle(initialValue = emptyMap())
    
    val activeDownloads: List<DownloadProgress> = remember(activeSingleDownloadsMap) {
        activeSingleDownloadsMap.values
            .filter { it.playlistId == null && !it.isComplete }
            .sortedBy { it.notificationId }
    }

    var filesOnDisk by remember { mutableStateOf<List<File>>(emptyList()) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletedItems by remember { mutableStateOf(setOf<String>()) }
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    suspend fun loadFiles() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir1 = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val dir2 = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val exts = setOf("mp4", "webm", "m4a")
            val f1 = dir1?.takeIf { it.exists() }?.listFiles { f -> f.isFile && f.extension in exts }?.toList() ?: emptyList()
            val f2 = dir2.takeIf { it.exists() }?.listFiles { f -> f.isFile && f.extension in exts }?.toList() ?: emptyList()
            val merged = (f1 + f2).distinctBy { it.absolutePath }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                filesOnDisk = merged
            }
        }
    }

    LaunchedEffect(Unit) { loadFiles() }

    val displayItems = remember(filesOnDisk, allDownloads, playlistDownloads) {
        val playlistFileNames = playlistDownloads.map { it.fileName }.toSet()
        filesOnDisk.filter { !playlistFileNames.contains(it.name) }.map { file ->
            DownloadItemData(file, allDownloads.find { it.fileName == file.name })
        }.sortedByDescending { it.file.lastModified() }
    }

    val videos = remember(displayItems, deletedItems) { displayItems.filter { !com.videhub.utils.FileUtils.isAudio(it.dbEntity, it.file.absolutePath) && !deletedItems.contains(it.file.absolutePath) } }
    val audios = remember(displayItems, deletedItems) { displayItems.filter { com.videhub.utils.FileUtils.isAudio(it.dbEntity, it.file.absolutePath) && !deletedItems.contains(it.file.absolutePath) } }

    // ── Delete dialog ─────────────────────────────────────────────────────────
    if (showDeleteDialog && fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Download") },
            text = { Text("Delete '${cleanTitle(fileToDelete?.nameWithoutExtension ?: "")}'? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    fileToDelete?.let { f ->
                        if (f.exists()) f.delete()
                        deletedItems = deletedItems + f.absolutePath
                        scope.launch { db.downloadedVideoDao().deleteDownloadByFileName(f.name) }
                    }
                    showDeleteDialog = false; fileToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (displayItems.isEmpty() && activeDownloads.isEmpty() && downloadedPlaylists.isEmpty()) {
            // ── Empty state ───────────────────────────────────────────────────
            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "scale"
            )
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(80.dp).scale(scale)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("No Downloads Yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Videos and music you download\nwill appear here.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // ── Active Downloads Section ──────────────────────────────────
                
                if (downloadedPlaylists.isNotEmpty()) {
                    item {
                        Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Downloaded Playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(downloadedPlaylists, key = { index, playlist -> "${playlist.playlistId}_$index" }) { index, playlist ->
                                PlaylistDownloadCard(playlist = playlist, onClick = {
                                    navController.navigate(com.videhub.navigation.Screen.DownloadedPlaylistDetail.createRoute(playlist.playlistId))
                                })
                            }
                        }
                    }
                }

                if (activeDownloads.isNotEmpty()) {
                    item {
                        Text("Downloading", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp))
                    }
                    itemsIndexed(activeDownloads, key = { index, progress -> "${progress.notificationId}_$index" }) { index, progress ->
                        ActiveDownloadCard(
                            item = progress,
                            onCancel = {
                                val cancelIntent = android.content.Intent(context, com.videhub.service.DownloadService::class.java).apply {
                                    action = "CANCEL_${progress.notificationId}"
                                }
                                context.startService(cancelIntent)

                                // Immediate local removal from UI state
                                com.videhub.utils.DownloadProgressTracker.removeDownload(progress.notificationId)
                                com.videhub.viewmodel.DownloadViewModel.removeActiveDownload(progress.videoId)

                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        db.downloadedVideoDao().deletePlaceholderByVideoId(progress.videoId)
                                    } catch (_: Exception) {}
                                }
                                android.widget.Toast.makeText(context, "Download cancelled", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // ── Music Section — Spotify Style ─────────────────────────────
                if (audios.isNotEmpty()) {
                    item {
                        Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Music", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    itemsIndexed(audios, key = { index, item -> "${item.file.absolutePath}_$index" }) { index, item ->
                            SpotifyMusicItem(
                                modifier = Modifier.animateItem(),
                                title = item.dbEntity?.title ?: cleanTitle(item.file.nameWithoutExtension),
                                artist = cleanChannelName(item.dbEntity?.channelName ?: "Unknown Artist"),
                                thumbnailUrl = item.dbEntity?.thumbnailUrl,
                                fileSize = "${item.file.length() / (1024 * 1024)} MB",
                                audioFilePath = item.file.absolutePath,
                                onClick = {
                                    com.videhub.QueueManager.clear()
                                    val validItems = audios
                                    val currentIndex = validItems.indexOf(item)
                                    if (currentIndex in 0 until validItems.lastIndex) {
                                        for (i in (currentIndex + 1)..validItems.lastIndex) {
                                            val qItem = validItems[i]
                                            com.videhub.QueueManager.enqueue(com.videhub.PlayQueueItem(
                                                url = qItem.file.absolutePath,
                                                title = qItem.dbEntity?.title ?: cleanTitle(qItem.file.nameWithoutExtension),
                                                uploaderName = qItem.dbEntity?.channelName ?: "Unknown Artist",
                                                thumbnailUrl = qItem.dbEntity?.thumbnailUrl ?: ""
                                            ))
                                        }
                                    }
                                    onVideoClick(item.file.absolutePath,
                                        item.dbEntity?.title ?: item.file.nameWithoutExtension,
                                        item.dbEntity?.thumbnailUrl ?: "", true)
                                },
                                onMoreClick = { fileToDelete = item.file; showDeleteDialog = true }
                            )
                    }
                }

                // ── Videos Section — YouTube Style ────────────────────────────
                if (videos.isNotEmpty()) {
                    item {
                        Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Videos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    itemsIndexed(videos, key = { index, item -> "${item.file.absolutePath}_$index" }) { index, item ->
                            YouTubeVideoItem(
                                modifier = Modifier.animateItem(),
                                title = item.dbEntity?.title ?: cleanTitle(item.file.nameWithoutExtension),
                                channel = cleanChannelName(item.dbEntity?.channelName ?: ""),
                                thumbnailUrl = item.dbEntity?.thumbnailUrl,
                                fileSize = "${item.file.length() / (1024 * 1024)} MB",
                                videoFilePath = item.file.absolutePath,
                                onClick = {
                                    com.videhub.QueueManager.clear()
                                    val validItems = videos
                                    val currentIndex = validItems.indexOf(item)
                                    if (currentIndex in 0 until validItems.lastIndex) {
                                        for (i in (currentIndex + 1)..validItems.lastIndex) {
                                            val qItem = validItems[i]
                                            com.videhub.QueueManager.enqueue(com.videhub.PlayQueueItem(
                                                url = qItem.file.absolutePath,
                                                title = qItem.dbEntity?.title ?: cleanTitle(qItem.file.nameWithoutExtension),
                                                uploaderName = qItem.dbEntity?.channelName ?: "",
                                                thumbnailUrl = qItem.dbEntity?.thumbnailUrl ?: ""
                                            ))
                                        }
                                    }
                                    onVideoClick(item.file.absolutePath,
                                        item.dbEntity?.title ?: item.file.nameWithoutExtension,
                                        item.dbEntity?.thumbnailUrl ?: "", false)
                                },
                                onMoreClick = { fileToDelete = item.file; showDeleteDialog = true }
                            )
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveDownloadCard(
    item: com.videhub.utils.DownloadProgress,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("active_download_card_${item.notificationId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isComplete)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Media Thumbnail Preview (16:9 ratio, width 96.dp, height 54.dp)
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (!item.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (item.isAudioOnly) Icons.Default.MusicNote else Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Mini badge overlay on thumbnail indicating VIDEO or AUDIO
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(3.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.75f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isAudioOnly) Icons.Default.MusicNote else Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = if (item.isAudioOnly) "AUDIO" else "VIDEO",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and Quality Pills / Download Stats
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = cleanTitle(item.title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Quality/Resolution pill
                        val qualityText = item.quality?.takeIf { it.isNotBlank() } ?: if (item.isAudioOnly) "Audio" else "Video"
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = qualityText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Size or status
                        Text(
                            text = if (item.isComplete)
                                "Complete"
                            else if (item.totalBytes > 0)
                                "${String.format(java.util.Locale.US, "%.1f", item.downloadedBytes / (1024f * 1024f))} / ${String.format(java.util.Locale.US, "%.1f", item.totalBytes / (1024f * 1024f))} MB"
                            else if (item.progress >= 0)
                                "${item.progress}%"
                            else
                                "Connecting...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: Percentage and Direct Cancel Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!item.isComplete) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (item.progress >= 0) "${item.progress}%" else "...",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
                                .testTag("cancel_download_button_${item.notificationId}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Download",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Progress Bar
            if (!item.isComplete) {
                Spacer(modifier = Modifier.height(10.dp))
                if (item.progress >= 0) {
                    LinearProgressIndicator(
                        progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDownloadCard(
    playlist: com.videhub.data.model.PlaylistDownloadSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSingleDownloadsMap by com.videhub.utils.DownloadProgressTracker.activeDownloads.collectAsStateWithLifecycle(initialValue = emptyMap())
    
    val isDownloading = playlist.videoCount < playlist.totalVideos
    val activeItems = activeSingleDownloadsMap.values.filter { it.playlistId == playlist.playlistId && !it.isComplete && it.progress > 0 }
    val partialProgress = if (activeItems.isNotEmpty() && playlist.totalVideos > 0) {
        activeItems.sumOf { (it.progress.coerceAtLeast(0).toFloat() / 100f).toDouble() }.toFloat() / playlist.totalVideos.toFloat()
    } else 0f
    
    val progressPercent = if (playlist.totalVideos > 0) (playlist.videoCount.toFloat() / playlist.totalVideos.toFloat()) + partialProgress else 0f

    com.videhub.ui.components.PlaylistCard(
        title = playlist.playlistName ?: "Unknown",
        subtitle = "${playlist.videoCount} / ${playlist.totalVideos} videos",
        thumbnailUrl = playlist.coverThumbnailUrl,
        onClick = onClick,
        modifier = modifier.width(220.dp),
        downloadProgress = if (isDownloading) progressPercent else null
    )
}

@Composable
fun SpotifyMusicItem(
    modifier: Modifier = Modifier,
    title: String,
    artist: String,
    thumbnailUrl: String?,
    fileSize: String,
    audioFilePath: String,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Square album art — Spotify style
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            val modelToUse = if (!thumbnailUrl.isNullOrBlank()) thumbnailUrl else java.io.File(audioFilePath)
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(modelToUse)
                    .error(android.R.drawable.ic_media_play)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .align(Alignment.BottomEnd)
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.Center)
                )
            }
        }

        // Text info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "$artist • $fileSize",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── YouTube Video Item ────────────────────────────────────────────────────────
@Composable
fun YouTubeVideoItem(
    modifier: Modifier = Modifier,
    title: String,
    channel: String,
    thumbnailUrl: String?,
    fileSize: String,
    videoFilePath: String,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 16:9 thumbnail
        Box(
            modifier = Modifier
                .width(130.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
        ) {
            val modelToUse = if (!thumbnailUrl.isNullOrBlank()) thumbnailUrl else java.io.File(videoFilePath)
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(modelToUse)
                    .error(android.R.drawable.ic_media_play)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.8f)
            ) {
                Text(
                    text = fileSize,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // Info column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (channel.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = channel,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.MoreVert,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
