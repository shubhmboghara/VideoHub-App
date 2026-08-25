package com.videhub.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle


import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.videhub.data.AppDatabase
import com.videhub.ui.components.VideoRowItem
import kotlinx.coroutines.launch

import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchLaterScreen(
    onVideoClick: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val watchLaterVideos by db.watchLaterDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    fun playAll(shuffle: Boolean = false) {
        if (watchLaterVideos.isEmpty()) return
        val listToPlay = if (shuffle) watchLaterVideos.shuffled() else watchLaterVideos
        com.videhub.QueueManager.clear()
        val first = listToPlay.first()
        for (i in 1 until listToPlay.size) {
            val qItem = listToPlay[i]
            com.videhub.QueueManager.enqueue(
                com.videhub.PlayQueueItem(
                    url = qItem.videoId,
                    title = qItem.title,
                    uploaderName = qItem.channelName,
                    thumbnailUrl = qItem.thumbnailUrl
                )
            )
        }
        onVideoClick(first.videoId, first.title, first.thumbnailUrl)
    }

    fun saveAsPlaylist() {
        if (watchLaterVideos.isEmpty()) return
        scope.launch {
            val playlistName = "Watch Later (${java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date())})"
            val playlistId = db.playlistDao().insertPlaylist(
                com.videhub.data.entity.PlaylistEntity(name = playlistName)
            )
            watchLaterVideos.forEach { video ->
                db.playlistDao().insertVideo(
                    com.videhub.data.entity.PlaylistVideoEntity(
                        playlistId = playlistId.toInt(),
                        videoId = video.videoId,
                        title = video.title,
                        channelName = video.channelName,
                        thumbnailUrl = video.thumbnailUrl
                    )
                )
            }
            Toast.makeText(context, "Saved ${watchLaterVideos.size} videos as \"$playlistName\"", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watch Later") },
                navigationIcon = {
                    IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (watchLaterVideos.isNotEmpty()) {
                        IconButton(onClick = { saveAsPlaylist() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Save as Playlist"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (watchLaterVideos.isEmpty()) {
            com.videhub.ui.components.EmptyState(
                icon = Icons.Default.Bookmark,
                title = "No videos saved",
                message = "Tap the clock icon on any video to save it here.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "${watchLaterVideos.size} saved videos",
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
                itemsIndexed(watchLaterVideos, key = { index, video -> "${video.videoId}_$index" }) { index, video ->
                    VideoRowItem(
                        videoUrl = video.videoId,
                        title = video.title,
                        uploaderName = video.channelName,
                        thumbnailUrl = video.thumbnailUrl,
                        viewCount = -1, // Hidden
                        uploadDate = "", // Hidden
                        onClick = {
                            com.videhub.QueueManager.clear()
                            val currentIndex = watchLaterVideos.indexOf(video)
                            if (currentIndex in 0 until watchLaterVideos.lastIndex) {
                                for (i in (currentIndex + 1)..watchLaterVideos.lastIndex) {
                                    val qItem = watchLaterVideos[i]
                                    com.videhub.QueueManager.enqueue(com.videhub.PlayQueueItem(
                                        url = qItem.videoId,
                                        title = qItem.title,
                                        uploaderName = qItem.channelName,
                                        thumbnailUrl = qItem.thumbnailUrl
                                    ))
                                }
                            }
                            onVideoClick(video.videoId, video.title, video.thumbnailUrl)
                        },
                        modifier = Modifier.animateItem(),
                        trailingIcon = {
                            IconButton(onClick = {
                                scope.launch {
                                    db.watchLaterDao().deleteById(video.videoId)
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove")
                            }
                        }
                    )
                }
            }
        }
    }
}
