package com.videhub.ui.screens

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.videhub.data.AppDatabase
import com.videhub.data.entity.PlaylistVideoEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(playlistId: Int, onVideoClick: (String, String, String) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var videos by remember { mutableStateOf<List<PlaylistVideoEntity>>(emptyList()) }
    var deletedItems by remember { mutableStateOf(setOf<Int>()) }
    val onBackPressedDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("Playlist") }

    LaunchedEffect(playlistId) {
        val db = AppDatabase.getDatabase(context)
        db.playlistDao().getAllPlaylistsOnce().find { it.id == playlistId }?.let {
            playlistName = it.name
        }
        db.playlistDao().getVideos(playlistId).collectLatest { list ->
            videos = list
        }
    }

    if (showDeletePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePlaylistDialog = false },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete '$playlistName'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        coroutineScope.launch {
                            val db = AppDatabase.getDatabase(context)
                            db.playlistDao().getAllPlaylistsOnce().find { it.id == playlistId }?.let { entity ->
                                db.playlistDao().deletePlaylist(entity)
                            }
                            onBackPressedDispatcher?.onBackPressed()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePlaylistDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Download Playlist") },
            text = { Text("How would you like to download this playlist?") },
            confirmButton = {
                TextButton(onClick = {
                    showDownloadDialog = false
                    val urls = ArrayList(videos.map { it.videoId })
                    val titles = ArrayList(videos.map { it.title })
                    val intent = android.content.Intent(context, com.videhub.service.PlaylistDownloadService::class.java).apply {
                        putStringArrayListExtra("urls", urls)
                        putStringArrayListExtra("titles", titles)
                        putExtra("playlistId", playlistId.toString())
                        putExtra("playlistName", playlistName)
                        putExtra("isAudioOnly", false)
                    }
                    androidx.core.content.ContextCompat.startForegroundService(context, intent)
                    android.widget.Toast.makeText(context, "Playlist download started...", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Video") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDownloadDialog = false
                    val urls = ArrayList(videos.map { it.videoId })
                    val titles = ArrayList(videos.map { it.title })
                    val intent = android.content.Intent(context, com.videhub.service.PlaylistDownloadService::class.java).apply {
                        putStringArrayListExtra("urls", urls)
                        putStringArrayListExtra("titles", titles)
                        putExtra("playlistId", playlistId.toString())
                        putExtra("playlistName", playlistName)
                        putExtra("isAudioOnly", true)
                    }
                    androidx.core.content.ContextCompat.startForegroundService(context, intent)
                    android.widget.Toast.makeText(context, "Playlist download started...", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Audio") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlistName) },
                navigationIcon = {
                    IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (videos.isNotEmpty()) {
                        IconButton(onClick = { showDownloadDialog = true }) {
                            Icon(Icons.Default.Download, contentDescription = "Download all", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    IconButton(onClick = { showDeletePlaylistDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete playlist", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                windowInsets = WindowInsets(0.dp)
            )
        }
    ) { padding ->
        androidx.compose.animation.AnimatedContent(
            targetState = videos.isEmpty(),
            transitionSpec = {
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)).togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)))
            },
            label = "PlaylistDetailTransition"
        ) { isEmpty ->
            if (isEmpty) {
                com.videhub.ui.components.EmptyState(
                    title = "Empty Playlist",
                    message = "Add videos here to watch them later.",
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            } else {
                val validVideos = androidx.compose.runtime.remember(videos, deletedItems) { videos.filter { !deletedItems.contains(it.id) } }
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item {
                        val firstThumb = validVideos.firstOrNull()?.thumbnailUrl
                        com.videhub.ui.components.PlaylistHeader(
                            title = playlistName,
                            subtitle = "Local Playlist • ${validVideos.size} videos",
                            thumbnailUrl = firstThumb,
                            isPlayAllEnabled = validVideos.isNotEmpty(),
                            isShuffleEnabled = validVideos.isNotEmpty(),
                            onPlayAllClick = {
                                if (validVideos.isNotEmpty()) {
                                    com.videhub.QueueManager.clear()
                                    for (i in 1 until validVideos.size) {
                                        val v = validVideos[i]
                                        com.videhub.QueueManager.enqueue(
                                            com.videhub.PlayQueueItem(
                                                url = v.videoId,
                                                title = v.title,
                                                uploaderName = v.channelName,
                                                thumbnailUrl = v.thumbnailUrl ?: ""
                                            )
                                        )
                                    }
                                    val first = validVideos.first()
                                    onVideoClick(first.videoId, first.title, first.thumbnailUrl ?: "")
                                }
                            },
                            onShuffleClick = {
                                if (validVideos.isNotEmpty()) {
                                    com.videhub.QueueManager.clear()
                                    val shuffled = validVideos.shuffled()
                                    for (i in 1 until shuffled.size) {
                                        val v = shuffled[i]
                                        com.videhub.QueueManager.enqueue(
                                            com.videhub.PlayQueueItem(
                                                url = v.videoId,
                                                title = v.title,
                                                uploaderName = v.channelName,
                                                thumbnailUrl = v.thumbnailUrl ?: ""
                                            )
                                        )
                                    }
                                    val first = shuffled.first()
                                    onVideoClick(first.videoId, first.title, first.thumbnailUrl ?: "")
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.alpha(0.5f))
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    itemsIndexed(validVideos, key = { index, video -> "${video.id}_$index" }) { index, video ->
                        com.videhub.ui.components.VideoRowItem(
                            modifier = Modifier.animateItem(),
                            videoUrl = video.videoId,
                            title = video.title,
                            uploaderName = video.channelName,
                            thumbnailUrl = video.thumbnailUrl,
                            viewCount = video.viewCount,
                            uploadDate = video.uploadDate,
                            onClick = { 
                                com.videhub.QueueManager.clear()
                                if (index in 0 until validVideos.lastIndex) {
                                    for (i in (index + 1)..validVideos.lastIndex) {
                                        val v = validVideos[i]
                                        com.videhub.QueueManager.enqueue(
                                            com.videhub.PlayQueueItem(
                                                url = v.videoId,
                                                title = v.title,
                                                uploaderName = v.channelName,
                                                thumbnailUrl = v.thumbnailUrl ?: ""
                                            )
                                        )
                                    }
                                }
                                onVideoClick(video.videoId, video.title, video.thumbnailUrl ?: "") 
                            },
                            trailingIcon = {
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        deletedItems = deletedItems + video.id
                                        kotlinx.coroutines.delay(300)
                                        AppDatabase.getDatabase(context).playlistDao().deleteVideo(video)
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
