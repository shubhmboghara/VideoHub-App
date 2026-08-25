package com.videhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.videhub.data.AppDatabase
import com.videhub.data.entity.HistoryEntity
import kotlinx.coroutines.flow.collectLatest

import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import android.widget.Toast
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onVideoClick: (String, String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf<List<HistoryEntity>>(emptyList()) }
    val onBackPressedDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    LaunchedEffect(Unit) {
        val db = AppDatabase.getDatabase(context)
        db.historyDao().getAllHistory().collectLatest { list ->
            history = list
        }
    }

    fun playAll(shuffle: Boolean = false) {
        if (history.isEmpty()) return
        val listToPlay = if (shuffle) history.shuffled() else history
        com.videhub.QueueManager.clear()
        val first = listToPlay.first()
        for (i in 1 until listToPlay.size) {
            val qItem = listToPlay[i]
            com.videhub.QueueManager.enqueue(
                com.videhub.PlayQueueItem(
                    url = qItem.videoId,
                    title = qItem.title,
                    uploaderName = qItem.channelName,
                    thumbnailUrl = qItem.thumbnailUrl ?: ""
                )
            )
        }
        onVideoClick(first.videoId, first.title, first.thumbnailUrl ?: "")
    }

    fun saveAsPlaylist() {
        if (history.isEmpty()) return
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val playlistName = "History Mix (${java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date())})"
            val playlistId = db.playlistDao().insertPlaylist(
                com.videhub.data.entity.PlaylistEntity(name = playlistName)
            )
            history.forEach { video ->
                db.playlistDao().insertVideo(
                    com.videhub.data.entity.PlaylistVideoEntity(
                        playlistId = playlistId.toInt(),
                        videoId = video.videoId,
                        title = video.title,
                        channelName = video.channelName,
                        thumbnailUrl = video.thumbnailUrl ?: ""
                    )
                )
            }
            Toast.makeText(context, "Saved ${history.size} videos as \"$playlistName\"", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { saveAsPlaylist() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Save as Playlist"
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0.dp)
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            com.videhub.ui.components.EmptyState(
                icon = androidx.compose.material.icons.Icons.Default.History,
                title = "No History",
                message = "Videos you watch will appear here.",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "${history.size} videos in history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { playAll(shuffle = false) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play All", maxLines = 1)
                            }
                            FilledTonalButton(
                                onClick = { playAll(shuffle = true) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Shuffle", maxLines = 1)
                            }
                        }
                    }
                }
                itemsIndexed(history, key = { index, item -> "${item.videoId}_$index" }) { index, item ->
                    com.videhub.ui.components.VideoRowItem(
                            videoUrl = item.videoId,
                            title = item.title,
                            uploaderName = item.channelName,
                            thumbnailUrl = item.thumbnailUrl,
                            viewCount = item.viewCount,
                            uploadDate = com.videhub.utils.FormatHelper.formatDate(item.uploadDate),
                            modifier = Modifier.animateItem(),
                            onClick = {
                                com.videhub.QueueManager.clear()
                                val currentIndex = history.indexOf(item)
                                if (currentIndex in 0 until history.lastIndex) {
                                    for (i in (currentIndex + 1)..history.lastIndex) {
                                        val qItem = history[i]
                                        com.videhub.QueueManager.enqueue(com.videhub.PlayQueueItem(
                                            url = qItem.videoId,
                                            title = qItem.title,
                                            uploaderName = qItem.channelName,
                                            thumbnailUrl = qItem.thumbnailUrl ?: ""
                                        ))
                                    }
                                }
                                onVideoClick(item.videoId, item.title, item.thumbnailUrl ?: "")
                            }
                        )
                    }
                }
            }
        }
    }


