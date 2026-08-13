package com.videhub.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo
import com.videhub.data.AppDatabase
import com.videhub.data.entity.LikedVideoEntity
import com.videhub.PlayQueueItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import androidx.media3.common.Player

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreenActions(
    streamInfo: StreamInfo?,
    isLocalFile: Boolean,
    isLikedInitial: Boolean,
    isInWatchLaterInitial: Boolean,
    autoplayEnabledInitial: Boolean,
    queue: List<PlayQueueItem>,
    relatedVideos: List<StreamInfoItem>,
    videoUrl: String,
    title: String,
    thumbnailUrl: String,
    channelName: String,
    onShowAddToPlaylistDialog: () -> Unit,
    onShowDownloadDialog: () -> Unit,
    onShowSettingsSheet: () -> Unit,
    context: Context,
    scope: CoroutineScope,
    db: AppDatabase,
    mediaPlayer: Player?,
    sharedViewModel: com.videhub.viewmodel.MainViewModel
) {
    var isLiked by remember(isLikedInitial) { mutableStateOf(isLikedInitial) }
    var isInWatchLater by remember(isInWatchLaterInitial) { mutableStateOf(isInWatchLaterInitial) }
    var autoplayEnabled by remember(autoplayEnabledInitial) { mutableStateOf(autoplayEnabledInitial) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        @Composable
        fun ActionChipItem(
            label: String,
            icon: androidx.compose.ui.graphics.vector.ImageVector,
            isActive: Boolean = false,
            onClick: () -> Unit
        ) {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(18.dp),
                        tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        ActionChipItem(
            label = "Like",
            icon = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
            onClick = {
                scope.launch {
                    if (isLiked) {
                        withContext(Dispatchers.IO) { db.likedVideoDao().deleteById(videoUrl) }
                        isLiked = false
                        Toast.makeText(context, "Removed from Liked Videos", Toast.LENGTH_SHORT).show()
                    } else {
                        withContext(Dispatchers.IO) {
                            db.likedVideoDao().insert(
                                LikedVideoEntity(
                                    videoId = videoUrl,
                                    title = title,
                                    thumbnailUrl = thumbnailUrl,
                                    channelName = channelName,
                                    viewCount = streamInfo?.viewCount ?: -1,
                                    uploadDate = streamInfo?.textualUploadDate ?: ""
                                )
                            )
                        }
                        isLiked = true
                        Toast.makeText(context, "Added to Liked Videos", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        ActionChipItem(
            label = "Watch Later",
            icon = if (isInWatchLater) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
            onClick = {
                scope.launch {
                    if (isInWatchLater) {
                        withContext(Dispatchers.IO) {
                            db.watchLaterDao().deleteById(videoUrl)
                        }
                        isInWatchLater = false
                        Toast.makeText(context, "Removed from Watch Later", Toast.LENGTH_SHORT).show()
                    } else {
                        withContext(Dispatchers.IO) {
                            db.watchLaterDao().insert(
                                com.videhub.data.entity.WatchLaterEntity(
                                    videoId = videoUrl,
                                    title = title,
                                    thumbnailUrl = thumbnailUrl,
                                    channelName = channelName
                                )
                            )
                        }
                        isInWatchLater = true
                        Toast.makeText(context, "Added to Watch Later", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        
        ActionChipItem(
            label = "Save",
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            onClick = onShowAddToPlaylistDialog
        )
        if (!isLocalFile) {
            ActionChipItem(
                label = "Download",
                icon = Icons.Default.Download,
                onClick = {
                    if (streamInfo != null) {
                        onShowDownloadDialog()
                    } else {
                        Toast.makeText(context, "No stream info available", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        ActionChipItem(
            label = "Queue",
            icon = Icons.Default.QueuePlayNext,
            onClick = {
                val currentMediaItem = mediaPlayer?.currentMediaItem
                val targetUrl = currentMediaItem?.mediaId ?: videoUrl
                val targetTitle = currentMediaItem?.mediaMetadata?.title?.toString() ?: title
                val targetChannel = currentMediaItem?.mediaMetadata?.artist?.toString() ?: channelName
                val targetThumb = currentMediaItem?.mediaMetadata?.artworkUri?.toString() ?: thumbnailUrl
                val targetDuration = mediaPlayer?.duration?.takeIf { it > 0 } ?: (streamInfo?.duration ?: -1L)

                val alreadyQueued = queue.map { it.url }.toSet()
                if (targetUrl !in alreadyQueued) {
                    val item = PlayQueueItem(
                        url = targetUrl,
                        title = targetTitle,
                        uploaderName = targetChannel,
                        thumbnailUrl = targetThumb,
                        duration = targetDuration
                    )
                    com.videhub.QueueManager.enqueue(item)
                    Toast.makeText(context, "Added to Queue", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Already in Queue", Toast.LENGTH_SHORT).show()
                }
            }
        )
        
        ActionChipItem(
            label = "Autoplay",
            icon = Icons.Default.Autorenew,
            isActive = autoplayEnabled,
            onClick = { 
                scope.launch {
                    com.videhub.data.SettingsManager.setAutoplay(context, !autoplayEnabled)
                    autoplayEnabled = !autoplayEnabled
                }
            }
        )
    }
}
