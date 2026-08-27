package com.videhub.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import com.videhub.PlayQueueItem
import com.videhub.QueueManager
import com.videhub.data.AppDatabase
import com.videhub.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@Composable
fun BelowPlayerContent(
    listState: LazyListState,
    streamInfo: StreamInfo?,
    isLocalFile: Boolean,
    isOfflineFallback: Boolean = false,
    errorMessage: String?,
    title: String,
    channelName: String,
    channelId: String?,
    thumbnailUrl: String,
    onChannelClick: (String) -> Unit,
    context: Context,
    scope: CoroutineScope,
    db: AppDatabase,
    isSubscribedInitial: Boolean,
    isLikedInitial: Boolean,
    isInWatchLaterInitial: Boolean,
    autoplayEnabledInitial: Boolean,
    queue: List<PlayQueueItem>,
    relatedVideos: List<StreamInfoItem>,
    videoUrl: String,
    onShowDownloadDialog: () -> Unit,
    onShowAddToPlaylistDialog: () -> Unit,
    onShowSettingsSheet: () -> Unit,
    onShowEqualizerSheet: () -> Unit = {},
    onShowSleepTimerSheet: () -> Unit = {},
    onVideoPlay: (String, String, String, Boolean) -> Unit,
    mediaPlayer: Player?,
    sharedViewModel: MainViewModel,
    isMusicMode: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier
    ) {
        item {
            PlayerScreenMetadata(
                streamInfo = streamInfo,
                isLocalFile = isLocalFile,
                isOfflineFallback = isOfflineFallback,
                errorMessage = errorMessage,
                title = title,
                channelName = channelName,
                channelId = channelId,
                thumbnailUrl = thumbnailUrl,
                onChannelClick = onChannelClick,
                context = context,
                scope = scope,
                db = db,
                isSubscribedInitial = isSubscribedInitial,
                onSeekToSeconds = { sec ->
                    mediaPlayer?.seekTo(sec * 1000L)
                }
            )
        }

        item {
            PlayerScreenActions(
                streamInfo = streamInfo,
                isLocalFile = isLocalFile,
                isLikedInitial = isLikedInitial,
                isInWatchLaterInitial = isInWatchLaterInitial,
                autoplayEnabledInitial = autoplayEnabledInitial,
                queue = queue,
                relatedVideos = relatedVideos,
                videoUrl = videoUrl,
                title = title,
                thumbnailUrl = thumbnailUrl,
                channelName = channelName,
                onShowDownloadDialog = onShowDownloadDialog,
                onShowAddToPlaylistDialog = onShowAddToPlaylistDialog,
                onShowSettingsSheet = onShowSettingsSheet,
                onShowEqualizerSheet = onShowEqualizerSheet,
                onShowSleepTimerSheet = onShowSleepTimerSheet,
                context = context,
                scope = scope,
                db = db,
                mediaPlayer = mediaPlayer,
                sharedViewModel = sharedViewModel
            )
        }

        queueSection(
            queue = queue,
            onVideoPlay = { url, thumbTitle, thumb, _ -> 
                onVideoPlay(url, thumbTitle, thumb, isMusicMode)
            },
            onRemove = { QueueManager.remove(it) },
            onClear = { QueueManager.clear() }
        )

        relatedVideosSection(
            relatedVideos = relatedVideos,
            isMusicMode = isMusicMode,
            onVideoPlay = { url, thumbTitle, thumb, _ -> 
                onVideoPlay(url, thumbTitle, thumb, isMusicMode)
            },
            onChannelClick = onChannelClick
        )
    }
}
