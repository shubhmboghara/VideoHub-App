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
            } catch (e: Exception) {}
        }
    }
    val downloadedPlaylists by db.downloadedPlaylistDao().getDownloadedPlaylistsSummary().collectAsStateWithLifecycle(initialValue = emptyList())
    val allDownloads by db.downloadedVideoDao().getSingleDownloads().collectAsStateWithLifecycle(initialValue = emptyList())
    val playlistDownloads by db.downloadedVideoDao().getPlaylistDownloads().collectAsStateWithLifecycle(initialValue = emptyList())
    val activeSingleDownloadsMap by com.videhub.utils.DownloadProgressTracker.activeDownloads.collectAsStateWithLifecycle(initialValue = emptyMap())
    
    val activeDownloads: List<com.videhub.service.ActiveDownloadItem> = remember(activeSingleDownloadsMap) {
        activeSingleDownloadsMap.values
            .filter { it.playlistId == null }
            .map { 
                com.videhub.service.ActiveDownloadItem(
                    videoId = it.notificationId.toString(),
                    title = it.title,
                    progress = it.progress,
                    downloadedBytes = it.downloadedBytes,
                    totalBytes = it.totalBytes,
                    isComplete = it.isComplete
                )
            }
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
                    itemsIndexed(activeDownloads, key = { index, progress -> "${progress.videoId}_$index" }) { index, progress ->
                        ActiveDownloadCard(item = progress)
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
fun ActiveDownloadCard(item: com.videhub.service.ActiveDownloadItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isComplete)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (item.isComplete) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.isComplete) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Complete",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Downloading",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (item.isComplete)
                                "Download complete"
                            else if (item.totalBytes > 0)
                                "${String.format(java.util.Locale.US, "%.1f", item.downloadedBytes / (1024f * 1024f))} MB / ${String.format(java.util.Locale.US, "%.1f", item.totalBytes / (1024f * 1024f))} MB"
                            else
                                "Downloading ${item.progress}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                if (!item.isComplete) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${item.progress}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (!item.isComplete) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
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
