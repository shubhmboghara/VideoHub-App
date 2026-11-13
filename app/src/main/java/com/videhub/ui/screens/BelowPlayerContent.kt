package com.videhub.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
    onToggleMusicMode: () -> Unit = {},
    onVideoPlay: (String, String, String, Boolean) -> Unit,
    mediaPlayer: Player?,
    sharedViewModel: MainViewModel,
    isMusicMode: Boolean,
    isRelatedLoading: Boolean = false,
    nestedScrollConnection: NestedScrollConnection? = null,
    modifier: Modifier = Modifier
) {
    val isCurrentLocal = com.videhub.QueueManager.isLocal(videoUrl)
    val visibleQueue = remember(queue, isCurrentLocal) {
        if (!isCurrentLocal) queue.filter { !com.videhub.QueueManager.isLocal(it.url) } else queue
    }
    val isInitialStreamLoading = streamInfo == null && !isLocalFile && !isOfflineFallback && errorMessage == null

    LazyColumn(
        state = listState,
        modifier = modifier
            .then(
                if (nestedScrollConnection != null) {
                    Modifier.nestedScroll(nestedScrollConnection)
                } else {
                    Modifier
                }
            )
            .navigationBarsPadding()
            .testTag("watch_page_details"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (isInitialStreamLoading) {
            item(key = "player_metadata_shimmer") {
                com.videhub.ui.components.PlayerMetadataShimmer()
            }
        } else {
            item(key = "player_metadata") {
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

            item(key = "player_actions") {
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
                    onToggleMusicMode = onToggleMusicMode,
                    context = context,
                    scope = scope,
                    db = db,
                    mediaPlayer = mediaPlayer,
                    sharedViewModel = sharedViewModel
                )
            }
        }

        queueSection(
            queue = visibleQueue,
            onVideoPlay = { url, thumbTitle, thumb, _ -> 
                onVideoPlay(url, thumbTitle, thumb, isMusicMode)
            },
            onRemove = { QueueManager.remove(it) },
            onClear = { QueueManager.clear() }
        )

        relatedVideosSection(
            relatedVideos = relatedVideos,
            isLoading = (isInitialStreamLoading || isRelatedLoading) && !isLocalFile && !isOfflineFallback,
            isMusicMode = isMusicMode,
            onVideoPlay = { url, thumbTitle, thumb, _ -> 
                onVideoPlay(url, thumbTitle, thumb, isMusicMode)
            },
            onChannelClick = onChannelClick
        )
    }
}
