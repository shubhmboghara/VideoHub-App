package com.videhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videhub.PlayQueueItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

fun LazyListScope.relatedVideosSection(
    relatedVideos: List<StreamInfoItem>,
    isMusicMode: Boolean,
    onVideoPlay: (String, String, String, Boolean) -> Unit,
    onChannelClick: (String) -> Unit
) {
    if (relatedVideos.isNotEmpty()) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        itemsIndexed(
            items = relatedVideos,
            key = { index, video -> "${video.url}_$index" }
        ) { index, video ->
            var expanded by remember { mutableStateOf(false) }
            
            if (expanded && !video.url.isNullOrBlank()) {
                com.videhub.ui.components.VideoActionBottomSheet(
                    videoUrl = video.url ?: "",
                    title = video.name ?: "",
                    thumbnailUrl = video.thumbnails?.firstOrNull()?.url ?: "",
                    channelName = video.uploaderName ?: "",
                    viewCount = video.viewCount ?: 0,
                    uploadDate = video.textualUploadDate ?: "",
                    onDismiss = { expanded = false }
                )
            }
            com.videhub.ui.components.RelatedVideoCard(
                videoUrl = video.url ?: "",
                title = video.name ?: "",
                uploaderName = video.uploaderName ?: "",
                thumbnailUrl = video.thumbnails?.firstOrNull()?.url,
                duration = video.duration,
                viewCount = video.viewCount ?: 0,
                uploadDate = video.textualUploadDate ?: "",
                uploaderAvatarUrl = try { video.uploaderAvatars?.firstOrNull()?.url } catch (e: Exception) { null },
                onChannelClick = { 
                    val uploaderUrl = video.uploaderUrl
                    if (!uploaderUrl.isNullOrBlank()) {
                        onChannelClick(uploaderUrl)
                    }
                },
                onClick = { 
                     val url = video.url ?: ""
                    if (url.isNotBlank()) {
                        onVideoPlay(url, video.name ?: "", video.thumbnails?.firstOrNull()?.url ?: "", isMusicMode) 
                    }
                },
                onMoreClick = { expanded = true }
            )
        }
    }
}

fun LazyListScope.queueSection(
    queue: List<PlayQueueItem>,
    onVideoPlay: (String, String, String, Boolean) -> Unit,
    onRemove: (PlayQueueItem) -> Unit,
    onClear: () -> Unit
) {
    if (queue.isNotEmpty()) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "Up Next in Queue (${queue.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        }
        itemsIndexed(
            items = queue,
            key = { index, video -> "${video.url}_$index" }
        ) { index, video ->
            com.videhub.ui.components.VideoRowItem(
                videoUrl = video.url,
                title = video.title,
                uploaderName = video.uploaderName,
                thumbnailUrl = video.thumbnailUrl,
                duration = video.duration,
                viewCount = -1,
                onClick = { 
                     if (video.url.isNotBlank()) {
                        onRemove(video)
                        onVideoPlay(video.url, video.title, video.thumbnailUrl, false)
                    }
                },
                trailingIcon = {
                    IconButton(onClick = { onRemove(video) }) {
                        Icon(Icons.Default.Clear, contentDescription = "Remove from Queue")
                    }
                }
            )
        }
    }
}
