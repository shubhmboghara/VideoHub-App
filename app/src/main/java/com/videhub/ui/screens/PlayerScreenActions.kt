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
import com.videhub.audio.EqualizerManager
import com.videhub.audio.SleepTimerManager

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
    onShowEqualizerSheet: () -> Unit = {},
    onShowSleepTimerSheet: () -> Unit = {},
    onToggleMusicMode: () -> Unit = {},
    context: Context,
    scope: CoroutineScope,
    db: AppDatabase,
    mediaPlayer: Player?,
    sharedViewModel: com.videhub.viewmodel.MainViewModel
) {
    var isLiked by remember(isLikedInitial) { mutableStateOf(isLikedInitial) }
    var isInWatchLater by remember(isInWatchLaterInitial) { mutableStateOf(isInWatchLaterInitial) }
    var autoplayEnabled by remember(autoplayEnabledInitial) { mutableStateOf(autoplayEnabledInitial) }

    val isEqEnabled by EqualizerManager.isEnabled.collectAsState()
    val sleepSeconds by SleepTimerManager.remainingSeconds.collectAsState()
    val isEndOfTrack by SleepTimerManager.isEndOfTrackMode.collectAsState()
    val isSleepActive = sleepSeconds != null || isEndOfTrack
    val isRadioActive by com.videhub.audio.RadioManager.isRadioActive.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(modifier = Modifier.width(4.dp))

        @Composable
        fun ActionChipItem(
            label: String,
            icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
            painter: androidx.compose.ui.graphics.painter.Painter? = null,
            isActive: Boolean = false,
            onClick: () -> Unit
        ) {
            val containerColor = if (isActive) {
                MaterialTheme.colorScheme.inverseSurface
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val contentColor = if (isActive) {
                MaterialTheme.colorScheme.inverseOnSurface
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = containerColor,
                contentColor = contentColor,
                modifier = Modifier.height(34.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (painter != null) {
                        Icon(
                            painter = painter,
                            contentDescription = label,
                            modifier = Modifier.size(18.dp),
                            tint = contentColor
                        )
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(18.dp),
                            tint = contentColor
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = contentColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        ActionChipItem(
            label = "Like",
            icon = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
            isActive = isLiked,
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
            label = "Music Mode",
            icon = Icons.Default.Audiotrack,
            onClick = onToggleMusicMode
        )

        ActionChipItem(
            label = if (isRadioActive) "Radio: On" else "Song Radio",
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            isActive = isRadioActive,
            onClick = {
                if (isRadioActive) {
                    com.videhub.audio.RadioManager.stopRadio()
                    Toast.makeText(context, "Song Radio Stopped", Toast.LENGTH_SHORT).show()
                } else {
                    com.videhub.audio.RadioManager.startRadio(videoUrl, title, channelName, thumbnailUrl)
                    Toast.makeText(context, "📻 Infinite Song Radio Started!", Toast.LENGTH_SHORT).show()
                }
            }
        )

        ActionChipItem(
            label = "Equalizer",
            icon = Icons.Default.Equalizer,
            isActive = isEqEnabled,
            onClick = onShowEqualizerSheet
        )

        ActionChipItem(
            label = if (isSleepActive) (if (isEndOfTrack) "Sleep: End" else "Sleep: ${SleepTimerManager.formatRemainingTime()}") else "Sleep Timer",
            icon = Icons.Default.Bedtime,
            isActive = isSleepActive,
            onClick = onShowSleepTimerSheet
        )

        ActionChipItem(
            label = "Watch Later",
            icon = if (isInWatchLater) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
            isActive = isInWatchLater,
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
            label = "Share",
            painter = androidx.compose.ui.res.painterResource(id = com.videhub.R.drawable.ic_share_youtube),
            onClick = {
                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, videoUrl)
                }
                context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Video"))
            }
        )
        ActionChipItem(
            label = "Queue",
            painter = androidx.compose.ui.res.painterResource(id = com.videhub.R.drawable.ic_play_next),
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

        Spacer(modifier = Modifier.width(8.dp))
    }
}
