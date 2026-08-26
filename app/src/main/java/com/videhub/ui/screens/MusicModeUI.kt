package com.videhub.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.videhub.data.AppDatabase
import com.videhub.data.entity.LikedVideoEntity
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MusicModeUI(
    isMusicMode: Boolean,
    isFullscreen: Boolean,
    title: String,
    channelName: String,
    thumbnailUrl: String,
    videoUrl: String,
    isLocalFile: Boolean,
    streamInfo: StreamInfo?,
    mediaPlayer: Player?,
    context: Context,
    scope: CoroutineScope,
    db: AppDatabase,
    isLiked: Boolean,
    onLikedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onDownloadClick: () -> Unit,
    onMoreClick: () -> Unit,
    onToggleMode: () -> Unit,
    onChannelClick: () -> Unit,
    autoplayEnabled: Boolean,
    isBuffering: Boolean,
    showCaptions: Boolean = false,
    activeCaptions: List<CharSequence> = emptyList(),
    offlineCaptions: List<com.videhub.ui.components.CaptionLine3> = emptyList(),
    onCaptionsRequested: () -> Unit = {},
    onVideoPlay: (String, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    isAudioOnly: Boolean = false
) {
    AnimatedVisibility(
        visible = isMusicMode && !isFullscreen,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = Modifier.zIndex(2f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .clickable(
                    interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            if (com.videhub.PipState.isActive.value) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = if (thumbnailUrl.isNotBlank()) thumbnailUrl else if (isLocalFile) File(videoUrl) else "",
                        contentDescription = "Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    )
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = channelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    MaterialTheme(colorScheme = com.videhub.ui.theme.BogharaDarkColorScheme) {
                    com.videhub.ui.components.NowPlayingScreen(
                    title = title,
                    artist = channelName,
                    thumbnailUrl = thumbnailUrl,
                    exoPlayer = mediaPlayer,
                    positionProvider = { mediaPlayer?.currentPosition ?: 0L },
                    durationProvider = { mediaPlayer?.duration ?: 0L },
                    isPlayingProvider = { mediaPlayer?.isPlaying ?: false },
                    onBack = {
                        if (mediaPlayer?.currentMediaItem != null) {
                            com.videhub.MiniPlayerState.show(videoUrl, title, if (thumbnailUrl.isNotBlank()) thumbnailUrl else if (isLocalFile) videoUrl else "", channelName, isMusicMode)
                        }
                        onBack()
                    },
                    isLiked = isLiked,
                    onLikeClick = {
                        scope.launch {
                            if (isLiked) {
                                withContext(Dispatchers.IO) { db.likedVideoDao().deleteById(videoUrl) }
                                onLikedChange(false)
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
                                onLikedChange(true)
                                Toast.makeText(context, "Added to Liked Videos", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDownloadClick = onDownloadClick,
                    onShareClick = {
                        val shareIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, "$title\n$videoUrl")
                            type = "text/plain"
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Video"))
                    },
                    onMoreClick = onMoreClick,
                    onToggleMode = onToggleMode,
                    onChannelClick = onChannelClick,
                    onVideoPlay = { url, t, thumb -> onVideoPlay(url, t, thumb, true) },
                    onCaptionsRequested = onCaptionsRequested,
                    showCaptions = showCaptions,
                    offlineCaptions = offlineCaptions,
                    isAudioOnly = isAudioOnly,
                    description = streamInfo?.description?.content
                )
                    }
                
                if (isBuffering) {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
}
