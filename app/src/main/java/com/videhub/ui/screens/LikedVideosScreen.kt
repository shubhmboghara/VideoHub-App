package com.videhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.videhub.data.AppDatabase
import com.videhub.data.entity.LikedVideoEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedVideosScreen(
    onVideoClick: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    var likedVideos by remember { mutableStateOf<List<LikedVideoEntity>>(emptyList()) }
    val onBackPressedDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    LaunchedEffect(Unit) {
        db.likedVideoDao().getAll().collectLatest { list ->
            likedVideos = list
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Liked Videos") },
                navigationIcon = {
                    IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (likedVideos.isEmpty()) {
            com.videhub.ui.components.EmptyState(
                icon = Icons.Default.ThumbUp,
                title = "No Liked Videos Yet",
                message = "Tap the Like button on any video\nto save it here.",
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
            item {
                Text(
                    "${likedVideos.size} liked videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            itemsIndexed(likedVideos, key = { index, video -> "${video.videoId}_$index" }) { index, video ->
                com.videhub.ui.components.VideoRowItem(
                    videoUrl = video.videoId,
                    title = video.title,
                    uploaderName = video.channelName,
                    thumbnailUrl = video.thumbnailUrl,
                    viewCount = video.viewCount,
                    uploadDate = com.videhub.utils.FormatHelper.formatDate(video.uploadDate),
                    onClick = {
                        com.videhub.QueueManager.clear()
                        val currentIndex = likedVideos.indexOf(video)
                        if (currentIndex in 0 until likedVideos.lastIndex) {
                            for (i in (currentIndex + 1)..likedVideos.lastIndex) {
                                val qItem = likedVideos[i]
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
                                db.likedVideoDao().deleteById(video.videoId)
                            }
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
    }
}
}



