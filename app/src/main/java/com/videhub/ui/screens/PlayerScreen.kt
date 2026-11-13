package com.videhub.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.videhub.MiniPlayerGlobalState
import com.videhub.MiniPlayerStateEnum
import com.videhub.PlayerStateManager
import com.videhub.PlayerWindowState
import com.videhub.data.AppDatabase
import com.videhub.data.entity.PlaylistEntity
import com.videhub.ui.components.EdgeDirection
import com.videhub.ui.components.EdgePeekTab
import com.videhub.ui.components.MiniPlayerCardChrome
import com.videhub.ui.components.safeBottomSheetPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.math.abs
import kotlin.math.roundToInt

// Use Android YouTube app UA for player — avoids bot detection
private const val UA = "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip"

fun Player.playNewItem(mediaItem: androidx.media3.common.MediaItem) {
    stop()
    clearMediaItems()
    setMediaItem(mediaItem)
    prepare()
    play()
    // Fix #4
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    sharedViewModel: com.videhub.viewmodel.MainViewModel,
    videoUrl: String,
    title: String,
    thumbnailUrl: String,
    onBack: () -> Unit,
    onVideoPlay: (String, String, String, Boolean, Boolean) -> Unit = { _, _, _, _, _ -> },
    initialFullscreen: Boolean = false,
    onChannelClick: (String) -> Unit,
    forceMusicMode: Boolean = false,
    mediaPlayer: Player? = null,
    playerWindowState: PlayerWindowState = if (initialFullscreen) PlayerWindowState.FULLSCREEN else PlayerWindowState.WATCH_PAGE,
    isRestoredFromSavedState: Boolean = false,
    onCollapseToMiniplayer: () -> Unit = onBack,
    onExpandFromMiniplayer: () -> Unit = {},
    onEnterFullscreen: () -> Unit = {},
    onExitFullscreen: () -> Unit = {},
    onClosePlayer: () -> Unit = onBack,
    onCollapseProgressChanged: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val currentSpeed by com.videhub.data.SettingsManager.getPlaybackSpeed(context).collectAsStateWithLifecycle(initialValue = 1f)
    val loopVideoEnabled by com.videhub.data.SettingsManager.getLoopVideo(context).collectAsStateWithLifecycle(initialValue = false)
    val volumeBoostEnabled by com.videhub.data.SettingsManager.getVolumeBoost(context).collectAsStateWithLifecycle(initialValue = false)
    val sponsorBlockEnabled by com.videhub.data.SettingsManager.getSponsorBlockEnabled(context).collectAsStateWithLifecycle(initialValue = true)
    val activity = context.getActivity()
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val downloadViewModel: com.videhub.viewmodel.DownloadViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    var allStreams by remember(videoUrl) { mutableStateOf<List<VideoStream>>(emptyList()) }
    var fallbackIndex by remember(videoUrl) { mutableIntStateOf(0) }
    var lastInitializedMediaId by remember(videoUrl) { mutableStateOf<String?>(null) }
    
    val isSameVideo = (sharedViewModel.currentPlayerUrl == videoUrl) && 
        (sharedViewModel.playerStreamInfoCache?.let { it.url == videoUrl || it.originalUrl == videoUrl } == true)
    
    var streamInfo by remember(videoUrl) { mutableStateOf<StreamInfo?>(
        if (isSameVideo) {
            sharedViewModel.playerStreamInfoCache
        } else if (com.videhub.service.BackgroundAutoplayHandler.currentStreamInfo?.url == videoUrl || com.videhub.service.BackgroundAutoplayHandler.currentStreamInfo?.originalUrl == videoUrl) {
            sharedViewModel.currentPlayerUrl = videoUrl
            sharedViewModel.playerStreamInfoCache = com.videhub.service.BackgroundAutoplayHandler.currentStreamInfo
            com.videhub.service.BackgroundAutoplayHandler.currentStreamInfo
        } else {
            null
        }
    ) }
    
    LaunchedEffect(currentSpeed, mediaPlayer) {
        mediaPlayer?.playbackParameters = androidx.media3.common.PlaybackParameters(currentSpeed)
    }
    
    LaunchedEffect(loopVideoEnabled, mediaPlayer) {
        mediaPlayer?.repeatMode = if (loopVideoEnabled) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
    }

    val isLocalFile = remember(videoUrl) {
        videoUrl.startsWith("/") || videoUrl.startsWith("file://") || videoUrl.startsWith("content://")
    }

    var isAudioOnlyDownload by remember(videoUrl) {
        mutableStateOf(
            isLocalFile && (com.videhub.utils.FileUtils.isAudioFile(videoUrl) || title.contains("kbps", ignoreCase = true))
        )
    }

    var channelId by remember(videoUrl) { mutableStateOf<String?>(if (isSameVideo) streamInfo?.uploaderUrl else null) }
    var channelName by remember(videoUrl) {
        val queueUploader = com.videhub.QueueManager.queue.value.find { it.url == videoUrl }?.uploaderName.orEmpty()
        mutableStateOf(if (isSameVideo) (streamInfo?.uploaderName ?: queueUploader) else queueUploader)
    }

    LaunchedEffect(videoUrl) {
        if (isLocalFile) {
            val fileName = java.io.File(videoUrl).name
            val entity = withContext(Dispatchers.IO) {
                db.downloadedVideoDao().getAllDownloadsSync().find {
                    it.fileName == fileName || it.videoId == videoUrl || videoUrl.endsWith(it.fileName)
                }
            }
            if (entity != null) {
                isAudioOnlyDownload = entity.isAudioOnly || com.videhub.utils.FileUtils.isAudio(entity, videoUrl)
                if (channelName.isBlank() && entity.channelName.isNotBlank()) {
                    channelName = entity.channelName
                }
            }
        } else {
            isAudioOnlyDownload = false
        }
    }
    
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableStateOf<Int?>(null) }
    var sponsorSegments by remember(videoUrl) { mutableStateOf<List<com.videhub.extractor.SponsorSegment>>(emptyList()) }
    
    var isSubscribed by remember(videoUrl) { mutableStateOf(false) }
    var isLiked by remember(videoUrl) { mutableStateOf(false) }
    var activeMediaId by remember(videoUrl) { mutableStateOf(videoUrl) }
    LaunchedEffect(activeMediaId) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            isLiked = db.likedVideoDao().isLiked(activeMediaId)
        }
    }
    var isInWatchLater by remember(videoUrl) { mutableStateOf(false) }
    var errorMessage by remember(videoUrl) { mutableStateOf<String?>(null) }

    var retryTrigger by remember(videoUrl) { mutableIntStateOf(0) }
    var offlineStreamUri by remember(videoUrl) { mutableStateOf<String?>(null) }
    var offlineAudioUri by remember(videoUrl) { mutableStateOf<String?>(null) }

    LaunchedEffect(videoUrl, retryTrigger) {
        com.videhub.PlaybackHistory.addToHistory(
            com.videhub.PlayQueueItem(
                url = videoUrl,
                title = title,
                uploaderName = channelName,
                thumbnailUrl = thumbnailUrl,
                duration = -1L
            )
        )
    }

    var selectedQuality by remember(videoUrl) { mutableStateOf("Auto") }
    var showQualitySelector by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPlaybackSpeedMenu by remember { mutableStateOf(false) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var showLockIconTemp by remember { mutableStateOf(false) }
    val lockIconScope = rememberCoroutineScope()
    var lockIconJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val availableQualities = remember(streamInfo) {
        val list = mutableListOf("Auto")
        streamInfo?.videoStreams?.mapNotNull { it.getResolution() }?.let { list.addAll(it) }
        streamInfo?.videoOnlyStreams?.mapNotNull { it.getResolution() }?.let { list.addAll(it) }
        list.distinct().sortedByDescending { if (it == "Auto") Int.MAX_VALUE else it.replace("p", "").replace("fps", "").trim().toIntOrNull() ?: 0 }
    }

    LaunchedEffect(sleepTimerMinutes) {
        val minutes = sleepTimerMinutes ?: return@LaunchedEffect
        if (minutes > 0) {
            kotlinx.coroutines.delay(minutes * 60 * 1000L)
            mediaPlayer?.pause()
            sleepTimerMinutes = null
        }
    }

    var relatedVideos by remember(videoUrl) {
        val initialRelated = if (isSameVideo) {
            sharedViewModel.playerRelatedItemsCache?.filterIsInstance<StreamInfoItem>()
                ?: streamInfo?.relatedItems?.filterIsInstance<StreamInfoItem>()
                ?: emptyList()
        } else {
            streamInfo?.relatedItems?.filterIsInstance<StreamInfoItem>() ?: emptyList()
        }
        mutableStateOf<List<StreamInfoItem>>(initialRelated)
    }
    var isRelatedLoading by remember(videoUrl) {
        val isLocal = videoUrl.startsWith("/") || videoUrl.startsWith("file://") || videoUrl.startsWith("content://")
        mutableStateOf(!isLocal && relatedVideos.isEmpty())
    }

    LaunchedEffect(videoUrl, retryTrigger, isRestoredFromSavedState, playerWindowState) {
        if (isRestoredFromSavedState && playerWindowState == PlayerWindowState.MINIPLAYER) {
            return@LaunchedEffect
        }
        val cachedInfo = sharedViewModel.playerStreamInfoCache
        val isCachedForThisVideo = cachedInfo != null && (cachedInfo.url == videoUrl || cachedInfo.originalUrl == videoUrl)
        if (retryTrigger == 0 && isCachedForThisVideo) {
            val cachedRelated = cachedInfo?.relatedItems?.filterIsInstance<StreamInfoItem>()?.filter { it.url != videoUrl } ?: emptyList()
            if (cachedRelated.isNotEmpty()) {
                relatedVideos = cachedRelated
                isRelatedLoading = false
            }
            streamInfo = cachedInfo
            channelId = cachedInfo?.uploaderUrl
            channelName = cachedInfo?.uploaderName ?: channelName
            return@LaunchedEffect
        }
        val bgInfo = com.videhub.service.BackgroundAutoplayHandler.currentStreamInfo
        if (retryTrigger == 0 && bgInfo != null && (bgInfo.url == videoUrl || bgInfo.originalUrl == videoUrl)) {
            val bgRelated = bgInfo.relatedItems?.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()?.filter { it.url != videoUrl } ?: emptyList()
            if (bgRelated.isNotEmpty()) {
                relatedVideos = bgRelated
                isRelatedLoading = false
            }
            streamInfo = bgInfo
            channelId = bgInfo.uploaderUrl
            channelName = bgInfo.uploaderName ?: channelName
            sharedViewModel.playerStreamInfoCache = bgInfo
            sharedViewModel.currentPlayerUrl = videoUrl
            sharedViewModel.playerRelatedItemsCache = bgRelated
            return@LaunchedEffect
        }
        val isLocal = videoUrl.startsWith("/") || videoUrl.startsWith("file://") || videoUrl.startsWith("content://")
        if (isLocal) {
            streamInfo = null
            return@LaunchedEffect
        }
        try {
            val info = withContext(Dispatchers.IO) {
                com.videhub.extractor.ExtractorHelper.getStreamInfo(videoUrl, useCache = retryTrigger == 0)
            }
            channelId = info.uploaderUrl
            channelName = info.uploaderName ?: ""
            
            val actualTitle = if (title.isNotBlank() && title != "Video" && title != "Loading...") title else (info.name ?: "Video")
            val actualThumb = if (thumbnailUrl.isNotBlank() && thumbnailUrl != "none") thumbnailUrl else (info.thumbnails?.firstOrNull()?.url ?: "")

            db.historyDao().insert(
                com.videhub.data.entity.HistoryEntity(
                    videoId = videoUrl,
                    title = actualTitle,
                    thumbnailUrl = actualThumb,
                    channelName = channelName,
                    viewCount = info.viewCount,
                    uploadDate = info.textualUploadDate ?: "",
                    timestamp = System.currentTimeMillis()
                )
            )

            com.videhub.PlaybackHistory.addToHistory(
                com.videhub.PlayQueueItem(
                    url = videoUrl,
                    title = actualTitle,
                    uploaderName = channelName,
                    thumbnailUrl = actualThumb,
                    duration = info.duration
                )
            )

            val directRelated = info.relatedItems
                ?.filterIsInstance<StreamInfoItem>()
                ?.filter { it.url != videoUrl }
                ?: emptyList()
            if (directRelated.isNotEmpty()) {
                relatedVideos = directRelated
                isRelatedLoading = false
            }
            sharedViewModel.playerRelatedItemsCache = directRelated
            com.videhub.service.BackgroundAutoplayHandler.relatedItemsCache = info.relatedItems ?: emptyList()
            com.videhub.service.BackgroundAutoplayHandler.currentStreamInfo = info
            com.videhub.service.BackgroundAutoplayHandler.markAsRecentlyPlayed(videoUrl)
            sharedViewModel.playerStreamInfoCache = info
            sharedViewModel.currentPlayerUrl = videoUrl
            streamInfo = info

            val isCurrentlyLiveStream = (info.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
                    info.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM) &&
                    info.duration <= 0L &&
                    (!info.hlsUrl.isNullOrBlank() || !info.dashMpdUrl.isNullOrBlank())
            if (isCurrentlyLiveStream) {
                com.videhub.utils.ChannelLiveStatusManager.registerLiveStream(
                    channelUrl = info.uploaderUrl,
                    channelName = info.uploaderName,
                    videoUrl = videoUrl,
                    title = actualTitle,
                    thumbnailUrl = actualThumb,
                    viewCount = info.viewCount
                )
            } else {
                com.videhub.utils.ChannelLiveStatusManager.unregisterLiveStream(
                    channelUrl = info.uploaderUrl,
                    channelName = info.uploaderName,
                    videoUrl = videoUrl
                )
            }

            // Cache for offline playback
            try {
                val progressive = info.videoStreams ?: emptyList()
                val videoOnly = info.videoOnlyStreams ?: emptyList()
                val audioOnly = info.audioStreams ?: emptyList()

                val bestVideo = progressive.maxByOrNull { it.getResolution()?.replace("p", "")?.toIntOrNull() ?: 0 }?.content
                    ?: videoOnly.firstOrNull()?.content
                val bestAudio = audioOnly.maxByOrNull { it.averageBitrate }?.content

                if (bestVideo != null) {
                    withContext(Dispatchers.IO) {
                        db.videoMetadataDao().insertVideoMetadata(
                            com.videhub.data.entity.VideoMetadataEntity(
                                videoId = videoUrl,
                                title = actualTitle,
                                thumbnailUrl = actualThumb,
                                channelName = channelName,
                                viewCount = info.viewCount,
                                duration = info.duration,
                                streamUrl = bestVideo,
                                audioUrl = bestAudio
                            )
                        )
                    }
                }
            } catch(e: Exception) { e.printStackTrace() }

            // Fetch SponsorBlock segments if it's YouTube
            if (videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be")) {
                val videoId = if (videoUrl.contains("v=")) {
                    videoUrl.substringAfter("v=").substringBefore("&")
                } else if (videoUrl.contains("youtu.be/")) {
                    videoUrl.substringAfter("youtu.be/").substringBefore("?")
                } else null
                if (videoId != null) {
                    sponsorSegments = withContext(Dispatchers.IO) {
                        com.videhub.extractor.ExtractorHelper.getSponsorSegments(videoId)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = e.message ?: "Failed to load video"
        }
    }

    LaunchedEffect(mediaPlayer, sponsorSegments, sponsorBlockEnabled) {
        if (!sponsorBlockEnabled || sponsorSegments.isEmpty() || mediaPlayer == null) return@LaunchedEffect
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            if (mediaPlayer.isPlaying) {
                val currentSecs = mediaPlayer.currentPosition / 1000f
                val skipSegment = sponsorSegments.find { currentSecs >= it.start && currentSecs < it.end }
                if (skipSegment != null) {
                    mediaPlayer.seekTo((skipSegment.end * 1000).toLong())
                    android.widget.Toast.makeText(context, "Skipped sponsor segment", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            kotlinx.coroutines.delay(500)
        }
    }

    LaunchedEffect(videoUrl, streamInfo, selectedQuality, offlineStreamUri, retryTrigger, isRestoredFromSavedState, playerWindowState) {
        if (isRestoredFromSavedState && playerWindowState == PlayerWindowState.MINIPLAYER) {
            return@LaunchedEffect
        }
        val isLocal = videoUrl.startsWith("/") || videoUrl.startsWith("file://") || videoUrl.startsWith("content://")
        try {
            var audioUrlForMerge: String? = offlineAudioUri
            val uri = if (isLocal) {
                if (videoUrl.startsWith("/")) android.net.Uri.fromFile(java.io.File(videoUrl)).toString() else videoUrl
            } else if (offlineStreamUri != null) {
                offlineStreamUri!!
            } else {
                val info = streamInfo ?: return@LaunchedEffect
                val isLive = (info.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM || 
                              info.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM ||
                              info.duration <= 0L) && (!info.hlsUrl.isNullOrBlank() || !info.dashMpdUrl.isNullOrBlank())

                val audioOnly = (info.audioStreams ?: emptyList()).filter { !it.content.isNullOrBlank() }
                if (isLive && !info.hlsUrl.isNullOrBlank()) {
                    info.hlsUrl!!
                } else if (isLive && !info.dashMpdUrl.isNullOrBlank()) {
                    info.dashMpdUrl!!
                } else if (isAudioOnlyDownload && audioOnly.isNotEmpty()) {
                    audioOnly.maxByOrNull { it.averageBitrate }?.content ?: ""
                } else {
                    val progressive = (info.videoStreams ?: emptyList()).filter { !it.content.isNullOrBlank() }
                    val videoOnly = (info.videoOnlyStreams ?: emptyList()).filter { !it.content.isNullOrBlank() }
                    
                    if (selectedQuality == "Auto") {
                        val p = progressive.maxByOrNull { it.getResolution()?.replace("p", "")?.replace("fps", "")?.trim()?.toIntOrNull() ?: 0 }?.content
                        if (p != null) p else {
                            val v = videoOnly.find { it.format?.mimeType == "video/mp4" } ?: videoOnly.firstOrNull()
                            if (v?.content != null) {
                                val isMp4 = v.format?.mimeType == "video/mp4"
                                val matchingAudio = if (isMp4) {
                                    audioOnly.find { it.format?.mimeType == "audio/mp4" || it.format?.mimeType?.contains("mp4") == true }
                                        ?: audioOnly.maxByOrNull { it.averageBitrate }
                                } else {
                                    audioOnly.find { it.format?.mimeType == "audio/webm" || it.format?.mimeType?.contains("webm") == true }
                                        ?: audioOnly.maxByOrNull { it.averageBitrate }
                                }
                                audioUrlForMerge = matchingAudio?.content ?: audioOnly.maxByOrNull { it.averageBitrate }?.content
                                v.content
                            } else {
                                info.hlsUrl ?: info.dashMpdUrl ?: ""
                            }
                        }
                    } else {
                        val cleanSelected = selectedQuality.replace("p", "").replace("fps", "").trim()
                        val matchedProg = progressive.find { 
                            val res = it.getResolution() ?: ""
                            res == selectedQuality || res.startsWith(selectedQuality) || res.replace("p", "").replace("fps", "").trim() == cleanSelected
                        }?.content

                        val matchedVideoOnlyList = videoOnly.filter { 
                            val res = it.getResolution() ?: ""
                            res == selectedQuality || res.startsWith(selectedQuality) || res.replace("p", "").replace("fps", "").trim() == cleanSelected
                        }
                        val matchedVideoOnly = matchedVideoOnlyList.find { it.format?.mimeType == "video/mp4" } 
                            ?: matchedVideoOnlyList.firstOrNull()
                        
                        if (matchedProg != null) {
                            matchedProg
                        } else if (matchedVideoOnly?.content != null) {
                            val isMp4 = matchedVideoOnly.format?.mimeType == "video/mp4"
                            val matchingAudio = if (isMp4) {
                                audioOnly.find { it.format?.mimeType == "audio/mp4" || it.format?.mimeType?.contains("mp4") == true }
                                    ?: audioOnly.maxByOrNull { it.averageBitrate }
                            } else {
                                audioOnly.find { it.format?.mimeType == "audio/webm" || it.format?.mimeType?.contains("webm") == true }
                                    ?: audioOnly.maxByOrNull { it.averageBitrate }
                            }
                            audioUrlForMerge = matchingAudio?.content ?: audioOnly.maxByOrNull { it.averageBitrate }?.content
                            matchedVideoOnly.content
                        } else {
                            progressive.maxByOrNull { it.getResolution()?.replace("p", "")?.replace("fps", "")?.trim()?.toIntOrNull() ?: 0 }?.content
                                ?: videoOnly.firstOrNull()?.content ?: info.hlsUrl ?: info.dashMpdUrl ?: ""
                        }
                    }
                }
            }

            if (uri.isNotBlank()) {
                val subs = streamInfo?.subtitles
                val tracks = com.videhub.ui.components.CaptionTrack.extractTracks(subs)
                com.videhub.ui.components.LiveCaptionsManager.setAvailableTracks(tracks)
                if (isLocal) { com.videhub.ui.components.LiveCaptionsManager.loadCaptionsFromDb(context, videoUrl) }
                
                val effectiveTitle = if (title.isNotBlank() && title != "Video" && title != "Loading...") title else (streamInfo?.name ?: "Video")
                val effectiveThumb = if (thumbnailUrl.isNotBlank() && thumbnailUrl != "none") thumbnailUrl else (streamInfo?.thumbnails?.firstOrNull()?.url ?: "none")
                val metadata = androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(effectiveTitle)
                    .setArtist(channelName.ifBlank { streamInfo?.uploaderName ?: "" })
                    .setArtworkUri(android.net.Uri.parse(if (effectiveThumb.isNotBlank()) effectiveThumb else "none"))
                    .build()
                val mediaItem = androidx.media3.common.MediaItem.Builder()
                    .setMediaId(videoUrl)
                    .setUri(uri)
                    .setMediaMetadata(metadata)
                    .build()
                
                mediaPlayer?.let { player ->
                    val currentPos = if (player.currentPosition > 0L) player.currentPosition else withContext(Dispatchers.IO) {
                        val progress = db.watchProgressDao().get(videoUrl)
                        if (progress != null) {
                            if (progress.durationMs > 0 && progress.positionMs >= progress.durationMs - 5000L) {
                                0L
                            } else {
                                progress.positionMs
                            }
                        } else {
                            0L
                        }
                    } ?: 0L

                    val currentMediaId = player.currentMediaItem?.mediaId
                    val isDifferentMedia = currentMediaId != mediaItem.mediaId
                    
                    val shouldUpdate = when {
                        // Case 1: Player is already playing or prepared for this specific video
                        !isDifferentMedia && player.playbackState != androidx.media3.common.Player.STATE_IDLE -> false
                        
                        // Case 2: Different video is playing, but this specific screen instance already 
                        // initialized the player for this videoUrl (e.g. queue/autoplay moved forward)
                        lastInitializedMediaId == videoUrl && isDifferentMedia && currentMediaId != null -> false
                        
                        // Default: Initialize the player for the video specified in the arguments
                        else -> true
                    }

                    if (shouldUpdate) {
                        val wasPlaying = player.playWhenReady || player.isPlaying
                        val realPlayer = com.videhub.service.MediaSessionManager.player
                        
                        if (realPlayer != null) {
                            realPlayer.stop()
                            realPlayer.clearMediaItems()
                            if (audioUrlForMerge != null) {
                                val dsf = com.videhub.service.MediaSessionManager.dataSourceFactory ?: androidx.media3.datasource.DefaultDataSource.Factory(context)
                                val videoSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dsf).createMediaSource(mediaItem)
                                val audioMediaItem = androidx.media3.common.MediaItem.fromUri(audioUrlForMerge!!)
                                val audioSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dsf).createMediaSource(audioMediaItem)
                                val mergedSource = androidx.media3.exoplayer.source.MergingMediaSource(false, videoSource, audioSource)
                                realPlayer.setMediaSource(mergedSource)
                            } else {
                                realPlayer.setMediaItem(mediaItem)
                            }
                            realPlayer.prepare()
                            if (currentPos > 0) realPlayer.seekTo(currentPos)
                            realPlayer.playWhenReady = wasPlaying || currentPos == 0L
                        } else {
                            player.stop()
                            player.clearMediaItems()
                            if (audioUrlForMerge != null && player is androidx.media3.exoplayer.ExoPlayer) {
                                val dsf = com.videhub.service.MediaSessionManager.dataSourceFactory ?: androidx.media3.datasource.DefaultDataSource.Factory(context)
                                val videoSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dsf).createMediaSource(mediaItem)
                                val audioMediaItem = androidx.media3.common.MediaItem.fromUri(audioUrlForMerge!!)
                                val audioSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dsf).createMediaSource(audioMediaItem)
                                val mergedSource = androidx.media3.exoplayer.source.MergingMediaSource(false, videoSource, audioSource)
                                player.setMediaSource(mergedSource)
                            } else {
                                player.setMediaItem(mediaItem)
                            }
                            player.prepare()
                            if (currentPos > 0) player.seekTo(currentPos)
                            player.playWhenReady = wasPlaying || currentPos == 0L
                        }
                        
                        lastInitializedMediaId = videoUrl
                    } else if (lastInitializedMediaId == null) {
                        lastInitializedMediaId = videoUrl
                    }
                }
            } else {
                errorMessage = "No suitable stream found"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = e.message ?: "Failed to load video"
        }
    }

    LaunchedEffect(streamInfo, errorMessage) {
        val isLocal = videoUrl.startsWith("/") || videoUrl.startsWith("file://") || videoUrl.startsWith("content://")
        if (isLocal || errorMessage != null) {
            isRelatedLoading = false
        } else if (streamInfo != null) {
            val direct = streamInfo!!.relatedItems?.filterIsInstance<StreamInfoItem>()?.filter { it.url != videoUrl } ?: emptyList()
            if (direct.isNotEmpty()) {
                relatedVideos = direct
                isRelatedLoading = false
            }
            if (direct.size < 5) {
                if (direct.isEmpty()) isRelatedLoading = true
                withContext(Dispatchers.IO) {
                    try {
                        val uploaderUrl = streamInfo?.uploaderUrl
                        val uploaderName = streamInfo?.uploaderName
                        val extraItems = mutableListOf<StreamInfoItem>()
                        if (!uploaderUrl.isNullOrBlank()) {
                            val chVids = com.videhub.extractor.ExtractorHelper.getChannelVideosSorted(uploaderUrl, "latest", 1)
                            extraItems.addAll(chVids)
                        }
                        if (extraItems.isEmpty() && !uploaderName.isNullOrBlank()) {
                            val searchVids = com.videhub.extractor.ExtractorHelper.getMoreSearchItems("$uploaderName songs")
                                .filterIsInstance<StreamInfoItem>()
                            extraItems.addAll(searchVids)
                        }
                        val combined = (direct + extraItems).distinctBy { it.url }.filter { it.url != videoUrl }
                        if (combined.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                relatedVideos = combined
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        withContext(Dispatchers.Main) {
                            isRelatedLoading = false
                        }
                    }
                }
            } else {
                isRelatedLoading = false
            }
        }
    }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistNameDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showVideoActionBottomSheet by remember { mutableStateOf(false) }
    var showQueueDownloadDialog by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<PlaylistEntity>>(emptyList()) }

    val initialIsMusic = remember(videoUrl, title, forceMusicMode, isAudioOnlyDownload) {
        forceMusicMode || isAudioOnlyDownload || (isLocalFile && (com.videhub.utils.FileUtils.isAudioFile(videoUrl) || title.contains("kbps", ignoreCase = true)))
    }
    var isMusicMode by remember(videoUrl, initialIsMusic) { mutableStateOf(initialIsMusic) }

    val isFullscreen = playerWindowState == PlayerWindowState.FULLSCREEN
    val isMiniplayer = playerWindowState == PlayerWindowState.MINIPLAYER
    val isPipActiveState = playerWindowState == PlayerWindowState.PICTURE_IN_PICTURE || com.videhub.PipState.isActive.value
    val isLandscapeVideoOrPortrait = com.videhub.PipState.videoAspectRatio?.toFloat()?.let { it in 0.1f..0.9f } == true

    fun syncSystemBarsForFullscreen(fullscreenActive: Boolean) {
        activity?.let {
            val ctrl = WindowInsetsControllerCompat(it.window, it.window.decorView)
            if (fullscreenActive && !isMusicMode && !isMiniplayer) {
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
                ctrl.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun setFullscreen(enable: Boolean) {
        if (enable && isMusicMode) return
        if (enable) {
            onEnterFullscreen()
        } else {
            onExitFullscreen()
        }
    }

    fun switchToMusicMode() {
        if (isFullscreen) {
            onExitFullscreen()
        }
        isMusicMode = true
        PlayerStateManager.updateMusicMode(true)
        activity?.let {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowInsetsControllerCompat(it.window, it.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val currentConfig = LocalConfiguration.current
    val isCurrentlyLandscape = currentConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isCurrentlyPortrait = currentConfig.orientation != Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(isFullscreen, isMusicMode, isMiniplayer, isLandscapeVideoOrPortrait) {
        activity?.let { act ->
            act.requestedOrientation = when {
                isMiniplayer -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                isFullscreen -> if (isLandscapeVideoOrPortrait) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                isMusicMode -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    LaunchedEffect(
        isFullscreen,
        isCurrentlyLandscape,
        isMusicMode,
        isMiniplayer,
        showSettingsSheet,
        showPlaybackSpeedMenu,
        showQualitySelector,
        showSleepTimerDialog,
        showSleepTimerSheet,
        showEqualizerSheet,
        showVideoActionBottomSheet,
        showDownloadDialog,
        showAddToPlaylistDialog
    ) {
        val anySheetOpen = showSettingsSheet || showPlaybackSpeedMenu || showQualitySelector ||
            showSleepTimerDialog || showSleepTimerSheet || showEqualizerSheet ||
            showVideoActionBottomSheet || showDownloadDialog || showAddToPlaylistDialog
        if (!anySheetOpen) {
            syncSystemBarsForFullscreen(!isMiniplayer && (isFullscreen || isCurrentlyLandscape))
        }
    }

    var lastPhysicalState by remember { mutableStateOf("UNKNOWN") }
    DisposableEffect(context, isMiniplayer, isMusicMode, isFullscreen) {
        val listener = object : android.view.OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val isPhysicallyLandscape = (orientation > 250 && orientation < 290) || (orientation > 70 && orientation < 110)
                val isPhysicallyPortrait = (orientation < 20 || orientation > 340) || (orientation > 160 && orientation < 200)

                val currentState = if (isPhysicallyLandscape) "LANDSCAPE" else if (isPhysicallyPortrait) "PORTRAIT" else lastPhysicalState
                if (currentState != lastPhysicalState) {
                    lastPhysicalState = currentState
                    try {
                        val autoRotateEnabled = android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.ACCELEROMETER_ROTATION, 0) == 1
                        if (autoRotateEnabled && !isMusicMode && !isMiniplayer) {
                            if (currentState == "LANDSCAPE" && !isFullscreen) {
                                setFullscreen(true)
                            } else if (currentState == "PORTRAIT" && isFullscreen && !isLandscapeVideoOrPortrait) {
                                setFullscreen(false)
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            // Removed video track toggling on background/foreground 
            // to prevent rebuffering ("stop then start" issue).
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    var isBuffering by remember { mutableStateOf(false) }
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = if (isSameVideo) sharedViewModel.playerScrollIndexCache else 0,
        initialFirstVisibleItemScrollOffset = if (isSameVideo) sharedViewModel.playerScrollOffsetCache else 0
    )

    val autoplayEnabled by com.videhub.data.SettingsManager.getAutoplay(context).collectAsStateWithLifecycle(initialValue = true)
    val autoplayRef = remember { mutableStateOf(true) }
    LaunchedEffect(autoplayEnabled) {
        autoplayRef.value = autoplayEnabled
    }
    val isHandlingAutoplay = remember { com.videhub.utils.MutableRef(false) }

    val showCaptionsRef = remember { mutableStateOf(initialIsMusic) }
    var showUpNext by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableIntStateOf(0) }
    var nextVideoUrl by remember { mutableStateOf("") }
    var nextVideoTitle by remember { mutableStateOf("") }
    var nextVideoThumb by remember { mutableStateOf("") }
    var activeCaptions by remember { mutableStateOf<List<CharSequence>>(emptyList()) }
    val offlineCaptions by com.videhub.ui.components.LiveCaptionsManager.captions.collectAsStateWithLifecycle(initialValue = emptyList())
    var showCaptionSelector by remember { mutableStateOf(false) }
    val availableTracks by com.videhub.ui.components.LiveCaptionsManager.availableTracks.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedTrack by com.videhub.ui.components.LiveCaptionsManager.selectedTrack.collectAsStateWithLifecycle(initialValue = null)
    val selectedLanguageCode by com.videhub.ui.components.LiveCaptionsManager.selectedLanguageCode.collectAsStateWithLifecycle(initialValue = null)

    if (showCaptionSelector) {
        com.videhub.ui.components.CaptionTrackSelectorDialog(
            availableTracks = availableTracks,
            selectedTrack = selectedTrack,
            onTrackSelected = { track ->
                showCaptionSelector = false
                activeCaptions = emptyList()
                com.videhub.ui.components.LiveCaptionsManager.selectTrack(track)
                showCaptionsRef.value = track != null
                if (track != null) {
                    val isLocalUrl = videoUrl.startsWith("/") || videoUrl.startsWith("file://") || videoUrl.startsWith("content://")
                    if (isLocalUrl) {
                        com.videhub.ui.components.LiveCaptionsManager.switchOfflineTrack(track)
                    } else {
                        com.videhub.ui.components.LiveCaptionsManager.fetchCaptions(
                            selectedUrl = track.url,
                            availableTracks = availableTracks,
                            artist = channelName,
                            title = title,
                            description = streamInfo?.description?.content,
                            isMusicMode = forceMusicMode,
                            durationSeconds = streamInfo?.duration ?: 0L,
                            context = context,
                            videoId = videoUrl
                        )
                    }
                }
            },
            onDismiss = { showCaptionSelector = false }
        )
    }
    if (showQualitySelector) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showQualitySelector = false },
            title = { androidx.compose.material3.Text("Video Quality") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    items(count = availableQualities.size, key = { index -> "${availableQualities[index]}_$index" }) { index ->
                        val quality = availableQualities[index]
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedQuality = quality
                                showQualitySelector = false
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Text(
                                text = quality, 
                                modifier = Modifier.weight(1f),
                                fontWeight = if (selectedQuality == quality) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                            )
                            if (selectedQuality == quality) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = null)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showQualitySelector = false }) {
                    androidx.compose.material3.Text("Close")
                }
            }
        )
    }

    if (showSleepTimerDialog) {
        val options = listOf(15, 30, 45, 60, 120)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { androidx.compose.material3.Text("Sleep Timer") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    item {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                sleepTimerMinutes = null
                                showSleepTimerDialog = false
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = sleepTimerMinutes == null,
                                onClick = null
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(16.dp))
                            androidx.compose.material3.Text("Off")
                        }
                    }
                    items(count = options.size, key = { index -> "${options[index]}_$index" }) { index ->
                        val mins = options[index]
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                sleepTimerMinutes = mins
                                showSleepTimerDialog = false
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = sleepTimerMinutes == mins,
                                onClick = null
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(16.dp))
                            androidx.compose.material3.Text("$mins minutes")
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showSleepTimerDialog = false }) {
                    androidx.compose.material3.Text("Close")
                }
            }
        )
    }

    if (showSettingsSheet) {
        val isSheetLandscape = isCurrentlyLandscape || isFullscreen
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = {
                showSettingsSheet = false
                syncSystemBarsForFullscreen(isFullscreen || isCurrentlyLandscape)
            },
            sheetMaxWidth = if (isSheetLandscape) 460.dp else BottomSheetDefaults.SheetMaxWidth,
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (isSheetLandscape) 260.dp else 520.dp)
                    .safeBottomSheetPadding(extraBottom = if (isSheetLandscape) 12.dp else 16.dp)
            ) {
                item {
                    androidx.compose.material3.ListItem(
                        headlineContent = { androidx.compose.material3.Text("Switch to Music Mode", style = MaterialTheme.typography.bodyLarge) },
                        leadingContent = { Icon(androidx.compose.material.icons.Icons.Default.Audiotrack, contentDescription = null) },
                        modifier = Modifier
                            .height(48.dp)
                            .clickable {
                                showSettingsSheet = false
                                switchToMusicMode()
                            }
                    )
                }
                item {
                    androidx.compose.material3.ListItem(
                        headlineContent = { androidx.compose.material3.Text("Video Quality", style = MaterialTheme.typography.bodyLarge) },
                        leadingContent = { Icon(androidx.compose.material.icons.Icons.Default.HighQuality, contentDescription = null) },
                        modifier = Modifier
                            .height(48.dp)
                            .clickable {
                                showSettingsSheet = false
                                showQualitySelector = true
                            }
                    )
                }
                item {
                    androidx.compose.material3.ListItem(
                        headlineContent = { androidx.compose.material3.Text("Speed", style = MaterialTheme.typography.bodyLarge) },
                        leadingContent = { Icon(androidx.compose.material.icons.Icons.Default.Speed, contentDescription = null) },
                        trailingContent = { androidx.compose.material3.Text("${currentSpeed}x") },
                        modifier = Modifier
                            .height(48.dp)
                            .clickable {
                                showSettingsSheet = false
                                showPlaybackSpeedMenu = true
                            }
                    )
                }
                item {
                    androidx.compose.material3.ListItem(
                        headlineContent = { androidx.compose.material3.Text("Sleep Timer", style = MaterialTheme.typography.bodyLarge) },
                        leadingContent = { Icon(androidx.compose.material.icons.Icons.Default.Timer, contentDescription = null) },
                        trailingContent = { if (sleepTimerMinutes != null) androidx.compose.material3.Text("$sleepTimerMinutes min") },
                        modifier = Modifier
                            .height(48.dp)
                            .clickable {
                                showSettingsSheet = false
                                showSleepTimerSheet = true
                            }
                    )
                }
                item {
                    androidx.compose.material3.ListItem(
                        headlineContent = { androidx.compose.material3.Text("Lock Screen", style = MaterialTheme.typography.bodyLarge) },
                        leadingContent = { Icon(androidx.compose.material.icons.Icons.Default.Lock, contentDescription = null) },
                        modifier = Modifier
                            .height(48.dp)
                            .clickable {
                                showSettingsSheet = false
                                setFullscreen(true)
                                isScreenLocked = true
                                showLockIconTemp = true
                                lockIconJob?.cancel()
                                lockIconJob = lockIconScope.launch {
                                    kotlinx.coroutines.delay(2000)
                                    showLockIconTemp = false
                                }
                            }
                    )
                }
                item {
                    androidx.compose.material3.ListItem(
                        headlineContent = { androidx.compose.material3.Text("Sponsor Skip", style = MaterialTheme.typography.bodyLarge) },
                        leadingContent = { Icon(androidx.compose.material.icons.Icons.Default.FastForward, contentDescription = null) },
                        trailingContent = { 
                            androidx.compose.material3.Switch(
                                colors = androidx.compose.material3.SwitchDefaults.colors(uncheckedTrackColor = MaterialTheme.colorScheme.outline, uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                checked = sponsorBlockEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { com.videhub.data.SettingsManager.setSponsorBlockEnabled(context, enabled) }
                                }
                            )
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .clickable {
                                scope.launch { com.videhub.data.SettingsManager.setSponsorBlockEnabled(context, !sponsorBlockEnabled) }
                            }
                    )
                }

                item {
                    androidx.compose.material3.ListItem(
                        headlineContent = { androidx.compose.material3.Text("Loop Video", style = MaterialTheme.typography.bodyLarge) },
                        leadingContent = { Icon(androidx.compose.material.icons.Icons.Default.Repeat, contentDescription = null) },
                        trailingContent = { 
                            androidx.compose.material3.Switch(
                                colors = androidx.compose.material3.SwitchDefaults.colors(uncheckedTrackColor = MaterialTheme.colorScheme.outline, uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                checked = loopVideoEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { com.videhub.data.SettingsManager.setLoopVideo(context, enabled) }
                                }
                            )
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .clickable {
                                scope.launch { com.videhub.data.SettingsManager.setLoopVideo(context, !loopVideoEnabled) }
                            }
                    )
                }
            }
        }
    }
    
    if (showPlaybackSpeedMenu) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showPlaybackSpeedMenu = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .safeBottomSheetPadding(extraBottom = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                androidx.compose.material3.Text(
                    text = "${currentSpeed}x",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                )
                
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                
                // Slider Row
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Surface(
                        onClick = { 
                            val newSpeed = (currentSpeed - 0.05f).coerceAtLeast(0.25f)
                            val roundedSpeed = (newSpeed * 100).toInt() / 100f
                            mediaPlayer?.setPlaybackParameters(androidx.media3.common.PlaybackParameters(roundedSpeed))
                            scope.launch { com.videhub.data.SettingsManager.setPlaybackSpeed(context, roundedSpeed) }
                        },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(48.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Icon(androidx.compose.material.icons.Icons.Default.Remove, contentDescription = "Decrease Speed")
                        }
                    }
                    
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(16.dp))
                    
                    androidx.compose.material3.Slider(
                        value = currentSpeed,
                        onValueChange = { speed ->
                            val roundedSpeed = (speed * 100).toInt() / 100f
                            mediaPlayer?.setPlaybackParameters(androidx.media3.common.PlaybackParameters(roundedSpeed))
                            scope.launch { com.videhub.data.SettingsManager.setPlaybackSpeed(context, roundedSpeed) }
                        },
                        valueRange = 0.25f..4.0f,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.onSurface,
                            activeTrackColor = MaterialTheme.colorScheme.onSurface,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(16.dp))
                    
                    androidx.compose.material3.Surface(
                        onClick = { 
                            val newSpeed = (currentSpeed + 0.05f).coerceAtMost(4.0f)
                            val roundedSpeed = (newSpeed * 100).toInt() / 100f
                            mediaPlayer?.setPlaybackParameters(androidx.media3.common.PlaybackParameters(roundedSpeed))
                            scope.launch { com.videhub.data.SettingsManager.setPlaybackSpeed(context, roundedSpeed) }
                        },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(48.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = "Increase Speed")
                        }
                    }
                }
                
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))
                
                // Presets
                val presets = listOf(0.25f, 1.0f, 1.5f, 2.0f, 3.0f)
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    presets.forEach { speed ->
                        val scale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (currentSpeed == speed) 1.1f else 1f,
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 150),
                            label = "speedScale"
                        )
                        val bgColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (currentSpeed == speed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            animationSpec = androidx.compose.animation.core.tween(300),
                            label = "speedBg"
                        )
                        val textColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (currentSpeed == speed) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = androidx.compose.animation.core.tween(300),
                            label = "speedText"
                        )
                        androidx.compose.material3.Surface(
                            onClick = {
                                mediaPlayer?.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed))
                                scope.launch { com.videhub.data.SettingsManager.setPlaybackSpeed(context, speed) }
                            },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = bgColor,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .widthIn(min = 64.dp)
                                .graphicsLayer(scaleX = scale, scaleY = scale)
                        ) {
                            androidx.compose.foundation.layout.Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                androidx.compose.material3.Text(
                                    text = if (speed == 1.0f) "1.0" else speed.toString(),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Fix #10
    LaunchedEffect(isAudioOnlyDownload) {
        if (isAudioOnlyDownload) {
            switchToMusicMode()
        }
    }
    LaunchedEffect(isMusicMode) {
        showCaptionsRef.value = isMusicMode
        PlayerStateManager.updateMusicMode(isMusicMode)
        if (isMusicMode) {
            if (isFullscreen) {
                onExitFullscreen()
            }
            activity?.let {
                if (!isMiniplayer) {
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                WindowInsetsControllerCompat(it.window, it.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        } else if (!isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(isMusicMode, title, channelName, thumbnailUrl) {
        com.videhub.ui.components.LiveCaptionsManager.setMusicMode(isMusicMode)
        com.videhub.MiniPlayerState.update(title, channelName, thumbnailUrl, isMusicMode)
    }

    LaunchedEffect(showUpNext) {
        if (showUpNext) {
            while (countdownSeconds > 0) {
                kotlinx.coroutines.delay(1000)
                countdownSeconds--
            }
            showUpNext = false
            countdownSeconds = 0
            if (nextVideoUrl.isNotBlank()) {
                onVideoPlay(nextVideoUrl, nextVideoTitle, nextVideoThumb, isMusicMode, isFullscreen)
            }
        }
    }

    val queue by com.videhub.QueueManager.queue.collectAsStateWithLifecycle()

    LaunchedEffect(videoUrl) {
        val isLocal = com.videhub.QueueManager.isLocal(videoUrl)
        if (!isLocal && com.videhub.QueueManager.hasDownloadedItems()) {
            com.videhub.QueueManager.clearDownloadedItems()
        }
    }

    LaunchedEffect(initialFullscreen) {
        if (initialFullscreen && !isMusicMode) {
            onEnterFullscreen()
        }
    }

    LaunchedEffect(isMusicMode, playerWindowState, streamInfo) {
        com.videhub.PipState.canEnterPip = !isMusicMode &&
            (playerWindowState == PlayerWindowState.WATCH_PAGE || playerWindowState == PlayerWindowState.FULLSCREEN)
        if (streamInfo != null && !(isRestoredFromSavedState && isMiniplayer)) {
            val currentCaptions = com.videhub.ui.components.LiveCaptionsManager.captions.value
            // Re-fetch if empty, OR if we just switched to music mode (to prioritize description/LRCLIB over potentially noise CC)
            if (currentCaptions.isEmpty() || isMusicMode) {
                com.videhub.ui.components.LiveCaptionsManager.fetchCaptions(
                    selectedUrl = com.videhub.ui.components.LiveCaptionsManager.selectedTrack.value?.url,
                    availableTracks = com.videhub.ui.components.LiveCaptionsManager.availableTracks.value,
                    artist = channelName,
                    title = title,
                    description = streamInfo?.description?.content,
                    isMusicMode = isMusicMode,
                    durationSeconds = streamInfo?.duration ?: 0L,
                    context = context,
                    videoId = videoUrl
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            com.videhub.ui.components.LiveCaptionsManager.clear()
            com.videhub.PipState.canEnterPip = false
            activity?.let {
                WindowInsetsControllerCompat(it.window, it.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    
    DisposableEffect(mediaPlayer) {
        val player = mediaPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                val customCaptionLines = com.videhub.ui.components.LiveCaptionsManager.captions.value
                val hasCustomCaptions = customCaptionLines.isNotEmpty() || com.videhub.ui.components.LiveCaptionsManager.selectedTrack.value != null
                if (!hasCustomCaptions) {
                    val cueTexts = cueGroup.cues.mapNotNull { it.text?.toString() }
                    activeCaptions = cueTexts
                } else {
                    activeCaptions = emptyList()
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                errorMessage = "Playback Error: ${error.message}"
                // Auto-retry once if it's a network issue (like UnknownHostException)
                if (retryTrigger < 3) {
                    retryTrigger++
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == androidx.media3.common.Player.STATE_BUFFERING
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val currentId = mediaItem?.mediaId
                if (currentId != null && currentId != videoUrl && currentId.isNotBlank()) {
                    val newTitle = mediaItem.mediaMetadata.title?.toString() ?: ""
                    val newThumb = mediaItem.mediaMetadata.artworkUri?.toString()?.takeIf { it != "none" } ?: ""
                    val newArtist = mediaItem.mediaMetadata.artist?.toString() ?: ""
                    try {
                        PlayerStateManager.updateMediaInCurrentState(
                            url = currentId,
                            title = newTitle,
                            thumbnailUrl = newThumb,
                            channelName = newArtist,
                            isMusicMode = isMusicMode
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("PlayerScreen", "Error syncing next item: ", e)
                    }
                }
            }
        }
        player.addListener(listener)
        isBuffering = player.playbackState == androidx.media3.common.Player.STATE_BUFFERING
        onDispose {
            player.removeListener(listener)
        }
    }

    val playerLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(playerLifecycleOwner, mediaPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val p = mediaPlayer
                if (p != null) {
                    val mediaId = p.currentMediaItem?.mediaId
                    if (mediaId != null && mediaId != videoUrl && mediaId.isNotBlank()) {
                        val newTitle = p.currentMediaItem?.mediaMetadata?.title?.toString() ?: ""
                        val newThumb = p.currentMediaItem?.mediaMetadata?.artworkUri?.toString()?.takeIf { it != "none" } ?: ""
                        val newArtist = p.currentMediaItem?.mediaMetadata?.artist?.toString() ?: ""
                        try {
                            PlayerStateManager.updateMediaInCurrentState(
                                url = mediaId,
                                title = newTitle,
                                thumbnailUrl = newThumb,
                                channelName = newArtist,
                                isMusicMode = isMusicMode
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("PlayerScreen", "Error syncing new item on resume: ", e)
                        }
                    }
                }
            }
        }
        playerLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { playerLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(
        enabled = playerWindowState == PlayerWindowState.FULLSCREEN ||
            playerWindowState == PlayerWindowState.WATCH_PAGE
    ) {
        if (playerWindowState == PlayerWindowState.FULLSCREEN) {
            onExitFullscreen()
        } else {
            onCollapseToMiniplayer()
        }
    }

    // ── Connect to shared PlaybackService via MediaController ─────────────────
    
    LaunchedEffect(
        isMusicMode,
        showCaptionsRef.value,
        mediaPlayer,
        selectedLanguageCode,
        offlineCaptions.isNotEmpty()
    ) {
        val player = mediaPlayer ?: return@LaunchedEffect
        
        val usingLyricsCaptions = isMusicMode && offlineCaptions.isNotEmpty() && selectedLanguageCode == null

        when {
            // ✅ Music mode with lyrics available + NO language selected → using synced lyrics → disable ExoPlayer text track
            usingLyricsCaptions -> {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                    .build()
            }

            // ✅ Language IS selected (Hindi, Bengali etc) → ALWAYS enable ExoPlayer text track
            // This applies to BOTH music mode and video mode when a language is explicitly chosen
            selectedLanguageCode != null -> {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                    .setPreferredTextLanguage(selectedLanguageCode)
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !showCaptionsRef.value) // respects user CC toggle
                    .build()
            }

            // ✅ Video mode (or music mode without lyrics), no language selected → enable/disable based on CC toggle
            else -> {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(
                        androidx.media3.common.C.TRACK_TYPE_TEXT,
                        !showCaptionsRef.value  // respects user CC toggle
                    )
                    .build()
            }
        }
    }
    var originalVideoId by remember(videoUrl) { mutableStateOf(videoUrl) }

    val resolvedTitle = if (title.isNotBlank() && title != "Video" && title != "Loading...") title else (streamInfo?.name ?: title.ifBlank { "Video" })
    val resolvedThumbnail = if (thumbnailUrl.isNotBlank() && thumbnailUrl != "none") thumbnailUrl else (streamInfo?.thumbnails?.firstOrNull()?.url ?: "")
    val isLocalVideoFile = videoUrl.startsWith("/") || videoUrl.startsWith("file://") || videoUrl.startsWith("content://")
    val isStreamLoading = !(isRestoredFromSavedState && isMiniplayer) &&
        streamInfo == null && !isLocalVideoFile && offlineStreamUri == null && errorMessage == null

    val miniLoadingNext by com.videhub.MiniPlayerState.isLoadingNext.collectAsState()
    val miniSavedPos by com.videhub.MiniPlayerState.lastSavedPosition.collectAsState()
    val miniSavedDur by com.videhub.MiniPlayerState.lastSavedDuration.collectAsState()

    var miniPlayerProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(mediaPlayer, isMiniplayer, isRestoredFromSavedState, miniSavedPos, miniSavedDur) {
        if (!isMiniplayer) return@LaunchedEffect
        while (true) {
            if (mediaPlayer == null || (isRestoredFromSavedState && (mediaPlayer.mediaItemCount == 0 || mediaPlayer.playbackState == Player.STATE_IDLE))) {
                val dur = miniSavedDur.takeIf { it > 0 } ?: 1L
                miniPlayerProgress = (miniSavedPos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            } else {
                val dur = mediaPlayer.duration.takeIf { it > 0 } ?: 1L
                miniPlayerProgress = (mediaPlayer.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            }
            delay(400)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val containerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
        val navBarBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()

        // Miniplayer calibrated geometry
        val screenWidthDp = with(density) { containerWidthPx.toDp().value }
        val basePipWidthDp = (screenWidthDp * 0.56f).coerceIn(196f, 236f)
        val minPipWidthDp = (screenWidthDp * 0.43f).coerceIn(156f, 176f)
        val maxPipWidthDp = (screenWidthDp * 0.88f).coerceIn(288f, (screenWidthDp - 20f).coerceAtLeast(220f))

        val minScale = (minPipWidthDp / basePipWidthDp).coerceIn(0.68f, 0.88f)
        val maxScale = (maxPipWidthDp / basePipWidthDp).coerceIn(1.30f, 1.75f)
        val basePipWidthPx = with(density) { basePipWidthDp.dp.toPx() }
        val basePipHeightPx = basePipWidthPx * (9f / 16f)

        val pipScale = remember { Animatable(MiniPlayerGlobalState.pipScale.floatValue.coerceIn(minScale, maxScale)) }
        val pipWidthPx = basePipWidthPx * pipScale.value
        val pipHeightPx = basePipHeightPx * pipScale.value
        val pipWidthDp = with(density) { pipWidthPx.toDp() }
        val pipHeightDp = with(density) { pipHeightPx.toDp() }
        val sizeFactor = (pipWidthDp.value / basePipWidthDp).coerceIn(minScale, maxScale)

        val horizontalMarginPx = with(density) { 10.dp.toPx() }
        val topMarginPx = statusBarTopPx + with(density) { 10.dp.toPx() }
        val bottomMarginPx = navBarBottomPx + with(density) { 68.dp.toPx() }

        val minX = horizontalMarginPx
        val maxX = maxOf(minX, containerWidthPx - pipWidthPx - horizontalMarginPx)
        val minY = topMarginPx
        val maxY = maxOf(minY, containerHeightPx - pipHeightPx - bottomMarginPx)

        val pipOffsetX = remember {
            Animatable(
                if (MiniPlayerGlobalState.pipOffsetX.floatValue < 0f) maxX
                else MiniPlayerGlobalState.pipOffsetX.floatValue.coerceIn(minX, maxX)
            )
        }
        val pipOffsetY = remember {
            Animatable(
                if (MiniPlayerGlobalState.pipOffsetY.floatValue < 0f) maxY
                else MiniPlayerGlobalState.pipOffsetY.floatValue.coerceIn(minY, maxY)
            )
        }

        LaunchedEffect(maxX, maxY) {
            val targetX = if (MiniPlayerGlobalState.pipOffsetX.floatValue < 0f) maxX else pipOffsetX.value.coerceIn(minX, maxX)
            val targetY = if (MiniPlayerGlobalState.pipOffsetY.floatValue < 0f) maxY else pipOffsetY.value.coerceIn(minY, maxY)
            pipOffsetX.snapTo(targetX)
            pipOffsetY.snapTo(targetY)
            MiniPlayerGlobalState.pipOffsetX.floatValue = targetX
            MiniPlayerGlobalState.pipOffsetY.floatValue = targetY
        }

        // Collapse state (0f = Watch Page, 1f = Miniplayer)
        val collapseAnimatable = remember {
            Animatable(if (playerWindowState == PlayerWindowState.MINIPLAYER) 1f else 0f)
        }
        var isUserDraggingCollapse by remember { mutableStateOf(false) }

        LaunchedEffect(playerWindowState) {
            if (!isUserDraggingCollapse) {
                val target = if (playerWindowState == PlayerWindowState.MINIPLAYER) 1f else 0f
                if (abs(collapseAnimatable.value - target) > 0.001f) {
                    collapseAnimatable.animateTo(
                        targetValue = target,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }
        }

        LaunchedEffect(collapseAnimatable) {
            snapshotFlow { collapseAnimatable.value }.collect { fraction ->
                onCollapseProgressChanged(fraction)
            }
        }

        val effectiveFullscreen = isFullscreen || (isCurrentlyLandscape && !isMiniplayer) || isPipActiveState
        val fullscreenProgress by animateFloatAsState(
            targetValue = if (effectiveFullscreen) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "fullscreen_progress"
        )

        val cp = collapseAnimatable.value.coerceIn(0f, 1f)
        val fp = fullscreenProgress.coerceIn(0f, 1f)

        val watchX = 0f
        val watchY = statusBarTopPx
        val watchW = containerWidthPx
        val watchH = containerWidthPx * (9f / 16f)
        val collapseDistancePx = (pipOffsetY.value - watchY).coerceAtLeast(180f)

        val watchToMiniX = lerp(watchX, pipOffsetX.value, cp)
        val watchToMiniY = lerp(watchY, pipOffsetY.value, cp)
        val watchToMiniW = lerp(watchW, pipWidthPx, cp)
        val watchToMiniH = lerp(watchH, pipHeightPx, cp)

        val cardX = lerp(watchToMiniX, 0f, fp)
        val cardY = lerp(watchToMiniY, 0f, fp)
        val cardW = lerp(watchToMiniW, containerWidthPx, fp)
        val cardH = lerp(watchToMiniH, containerHeightPx, fp)

        val isSettledMiniplayer = playerWindowState == PlayerWindowState.MINIPLAYER &&
            cp >= 0.99f && !isUserDraggingCollapse && fp < 0.05f

        val miniChromeAlpha = if (isSettledMiniplayer) 1f else ((cp - 0.65f) / 0.35f).coerceIn(0f, 1f) * (1f - fp)
        val watchControlsAlpha = (1f - cp * 2.5f).coerceIn(0f, 1f)
        val backdropAlpha = (1f - cp * 1.15f).coerceIn(0f, 1f) * (1f - fp)
        val detailsAlpha = (1f - cp * 2.2f).coerceIn(0f, 1f) * (1f - fp)

        val cardCornerDp = if (fp > 0.05f) {
            0.dp
        } else {
            val targetMiniCornerDp = (12f * sizeFactor).coerceIn(10f, 16f)
            (targetMiniCornerDp * cp).dp
        }
        val cardShape = RoundedCornerShape(cardCornerDp)

        fun startCollapseDrag() {
            if (!effectiveFullscreen && !isMusicMode && !isSettledMiniplayer) {
                isUserDraggingCollapse = true
            }
        }

        fun dragCollapseBy(deltaPx: Float) {
            if (!isUserDraggingCollapse) return
            val next = (collapseAnimatable.value + deltaPx / collapseDistancePx).coerceIn(0f, 1f)
            scope.launch { collapseAnimatable.snapTo(next) }
        }

        fun finishCollapseDrag(velocityY: Float) {
            if (!isUserDraggingCollapse) return
            isUserDraggingCollapse = false
            val shouldCollapse = PlayerStateManager.shouldCollapseOnDragEnd(collapseAnimatable.value, velocityY)
            scope.launch {
                if (shouldCollapse) {
                    onCollapseToMiniplayer()
                    collapseAnimatable.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                } else {
                    collapseAnimatable.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }
        }

        fun cancelCollapseDrag() {
            if (!isUserDraggingCollapse) return
            isUserDraggingCollapse = false
            scope.launch {
                val target = if (playerWindowState == PlayerWindowState.MINIPLAYER) 1f else 0f
                collapseAnimatable.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }

        val detailsNestedScroll = remember(collapseDistancePx, effectiveFullscreen, isMusicMode, isSettledMiniplayer) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.Drag || effectiveFullscreen || isMusicMode || isSettledMiniplayer) {
                        return Offset.Zero
                    }
                    val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                    if (isUserDraggingCollapse || (isAtTop && available.y > 0f)) {
                        if (!isUserDraggingCollapse) {
                            isUserDraggingCollapse = true
                        }
                        val next = (collapseAnimatable.value + available.y / collapseDistancePx).coerceIn(0f, 1f)
                        scope.launch { collapseAnimatable.snapTo(next) }
                        if (next > 0f || available.y > 0f) {
                            return Offset(0f, available.y)
                        }
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (isUserDraggingCollapse) {
                        finishCollapseDrag(available.y)
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        // ── 1. Watch Page Background & Details Sheet (Unmounted when settled in Miniplayer so background screens stay interactive) ──
        if (!isSettledMiniplayer && (!isPipActiveState)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = backdropAlpha }
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (statusBarTopPx > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) { statusBarTopPx.toDp() })
                            .background(Color.Black)
                    )
                }
            }

            if (!isMusicMode && !effectiveFullscreen && detailsAlpha > 0.01f) {
                val detailsTopOffsetPx = watchY + watchH + (containerHeightPx - watchY - watchH) * cp * 0.25f
                val detailsHeightDp = with(density) { (containerHeightPx - (watchY + watchH)).coerceAtLeast(0f).toDp() }
                BelowPlayerContent(
                    listState = listState,
                    streamInfo = streamInfo,
                    isLocalFile = isLocalFile,
                    isOfflineFallback = offlineStreamUri != null,
                    errorMessage = errorMessage,
                    title = resolvedTitle,
                    channelName = channelName,
                    channelId = channelId,
                    thumbnailUrl = resolvedThumbnail,
                    onChannelClick = { ch ->
                        onCollapseToMiniplayer()
                        onChannelClick(ch)
                    },
                    context = context,
                    scope = scope,
                    db = db,
                    isSubscribedInitial = isSubscribed,
                    isLikedInitial = isLiked,
                    isInWatchLaterInitial = isInWatchLater,
                    autoplayEnabledInitial = autoplayEnabled,
                    queue = queue,
                    relatedVideos = relatedVideos,
                    videoUrl = videoUrl,
                    onShowAddToPlaylistDialog = { showAddToPlaylistDialog = true },
                    onShowDownloadDialog = { showDownloadDialog = true },
                    onShowSettingsSheet = { showSettingsSheet = true },
                    onShowEqualizerSheet = { showEqualizerSheet = true },
                    onShowSleepTimerSheet = { showSleepTimerSheet = true },
                    onToggleMusicMode = { switchToMusicMode() },
                    onVideoPlay = { url, t, th, musicMode -> onVideoPlay(url, t, th, musicMode, false) },
                    mediaPlayer = mediaPlayer,
                    sharedViewModel = sharedViewModel,
                    isMusicMode = isMusicMode,
                    isRelatedLoading = isRelatedLoading,
                    nestedScrollConnection = detailsNestedScroll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(detailsHeightDp)
                        .offset { IntOffset(0, detailsTopOffsetPx.roundToInt()) }
                        .graphicsLayer { alpha = detailsAlpha }
                )
            }
        }

        // ── 2. Edge-Docked Miniplayer Peek Tab (when applicable) ──
        val miniEdgeState = MiniPlayerGlobalState.state.value
        if (isSettledMiniplayer && miniEdgeState == MiniPlayerStateEnum.EdgeDockedLeft) {
            EdgePeekTab(
                direction = EdgeDirection.LEFT,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(y = with(density) { (pipOffsetY.value - containerHeightPx / 2f).toDp() + pipHeightDp / 2 }),
                onClick = {
                    MiniPlayerGlobalState.state.value = MiniPlayerStateEnum.FloatingPip
                    MiniPlayerGlobalState.pipOffsetX.floatValue = minX
                    scope.launch {
                        pipOffsetX.animateTo(
                            minX,
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        )
                    }
                }
            )
        } else if (isSettledMiniplayer && miniEdgeState == MiniPlayerStateEnum.EdgeDockedRight) {
            EdgePeekTab(
                direction = EdgeDirection.RIGHT,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(y = with(density) { (pipOffsetY.value - containerHeightPx / 2f).toDp() + pipHeightDp / 2 }),
                onClick = {
                    MiniPlayerGlobalState.state.value = MiniPlayerStateEnum.FloatingPip
                    MiniPlayerGlobalState.pipOffsetX.floatValue = maxX
                    scope.launch {
                        pipOffsetX.animateTo(
                            maxX,
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        )
                    }
                }
            )
        }

        // ── 3. Floating Miniplayer Drag + Pinch-to-Resize + Bottom-Swipe Dismiss Modifier ──
        val miniDragModifier = if (isSettledMiniplayer) {
            Modifier.pointerInput(containerWidthPx, containerHeightPx, minScale, maxScale) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    var hasMoved = false
                    var initialSlopPan = Offset.Zero
                    var totalDragX = 0f
                    var totalDragY = 0f
                    var isHorizontalLocked = false
                    val touchSlop = viewConfiguration.touchSlop

                    var curScale = pipScale.value.coerceIn(minScale, maxScale)
                    var curX = pipOffsetX.value
                    var curY = pipOffsetY.value

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val pointerCount = event.changes.count { it.pressed }

                        if (!hasMoved) {
                            initialSlopPan += pan
                            if (initialSlopPan.getDistance() > touchSlop || (pointerCount > 1 && abs(zoom - 1f) > 0.01f)) {
                                hasMoved = true
                                totalDragX += initialSlopPan.x
                                totalDragY += initialSlopPan.y
                                if (abs(totalDragX) > abs(totalDragY)) {
                                    isHorizontalLocked = true
                                }
                            }
                        } else {
                            totalDragX += pan.x
                            totalDragY += pan.y
                            if (abs(totalDragX) > abs(totalDragY)) {
                                isHorizontalLocked = true
                            }
                        }

                        if (hasMoved) {
                            event.changes.forEach { it.consume() }

                            val oldW = basePipWidthPx * curScale
                            val oldH = basePipHeightPx * curScale
                            val newScale = if (zoom != 1f) {
                                (curScale * zoom).coerceIn(minScale, maxScale)
                            } else {
                                curScale
                            }
                            val newW = basePipWidthPx * newScale
                            val newH = basePipHeightPx * newScale

                            val dynamicMaxX = maxOf(minX, containerWidthPx - newW - horizontalMarginPx)
                            val dynamicMaxY = maxOf(minY, containerHeightPx - newH - bottomMarginPx)

                            val scaleDeltaX = (newW - oldW) / 2f
                            val scaleDeltaY = (newH - oldH) / 2f

                            curScale = newScale
                            curX = (curX - scaleDeltaX + pan.x).coerceIn(minX, dynamicMaxX)
                            val maxAllowedDragY = if (isHorizontalLocked || pointerCount > 1) dynamicMaxY else containerHeightPx
                            curY = (curY - scaleDeltaY + pan.y).coerceIn(minY, maxAllowedDragY)

                            MiniPlayerGlobalState.pipScale.floatValue = curScale
                            MiniPlayerGlobalState.pipOffsetX.floatValue = curX
                            MiniPlayerGlobalState.pipOffsetY.floatValue = curY

                            scope.launch {
                                pipScale.snapTo(curScale)
                                pipOffsetX.snapTo(curX)
                                pipOffsetY.snapTo(curY)
                            }
                        }

                        event.changes.firstOrNull()?.let { change ->
                            if (change.pressed) {
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    val velocity = velocityTracker.calculateVelocity()
                    if (hasMoved) {
                        val currentW = basePipWidthPx * curScale
                        val currentH = basePipHeightPx * curScale
                        val dynamicMaxX = maxOf(minX, containerWidthPx - currentW - horizontalMarginPx)
                        val dynamicMaxY = maxOf(minY, containerHeightPx - currentH - bottomMarginPx)
                        val x = curX
                        val y = curY

                        val isPredominantlyVerticalDown =
                            !isHorizontalLocked &&
                                totalDragY > 0f &&
                                abs(totalDragY) > abs(totalDragX) * 1.5f

                        val bottomDismissZoneThreshold = containerHeightPx * 0.88f
                        val crossedBottomZone = (y + currentH) >= bottomDismissZoneThreshold && y > dynamicMaxY + horizontalMarginPx
                        val hasDownwardFlingVelocity =
                            velocity.y > 1600f &&
                                abs(velocity.y) > abs(velocity.x) * 1.5f

                        val shouldDismissDownward =
                            isPredominantlyVerticalDown && (crossedBottomZone || hasDownwardFlingVelocity)

                        scope.launch {
                            if (shouldDismissDownward) {
                                pipOffsetY.animateTo(
                                    targetValue = containerHeightPx,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                onClosePlayer()
                            } else {
                                val currentCenterX = x + currentW / 2f
                                val targetX = if (currentCenterX < containerWidthPx / 2f) minX else dynamicMaxX
                                val targetY = y.coerceIn(minY, dynamicMaxY)

                                MiniPlayerGlobalState.pipOffsetX.floatValue = targetX
                                MiniPlayerGlobalState.pipOffsetY.floatValue = targetY

                                launch {
                                    pipOffsetX.animateTo(
                                        targetValue = targetX,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                                launch {
                                    pipOffsetY.animateTo(
                                        targetValue = targetY,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Modifier
        }

        // ── 4. Single Persistent Morphing Video Surface Box ──
        // Never recreated or re-keyed across WATCH_PAGE <-> FULLSCREEN <-> MINIPLAYER <-> PICTURE_IN_PICTURE
        val cardWidthDp = with(density) { cardW.toDp() }
        val cardHeightDp = with(density) { cardH.toDp() }
        val cardElevationDp = if (fp > 0.05f) 0.dp else (18f * cp).dp

        Box(
            modifier = Modifier
                .offset { IntOffset(cardX.roundToInt(), cardY.roundToInt()) }
                .size(width = cardWidthDp, height = cardHeightDp)
                .then(
                    if (cardElevationDp > 0.dp) {
                        Modifier.shadow(
                            elevation = cardElevationDp,
                            shape = cardShape,
                            spotColor = Color.Black,
                            ambientColor = Color.Black
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(cardShape)
                .background(Color.Black)
                .then(
                    if (cp > 0.02f && fp < 0.05f) {
                        Modifier.border(
                            width = 0.75.dp,
                            color = Color.White.copy(alpha = 0.14f * cp),
                            shape = cardShape
                        )
                    } else {
                        Modifier
                    }
                )
                .then(miniDragModifier)
                .testTag(if (isSettledMiniplayer) "miniplayer_card" else "video_player_surface_container")
        ) {
            VideoPlayerContainer(
                modifier = Modifier.fillMaxSize(),
                mediaPlayer = mediaPlayer,
                isFullscreen = effectiveFullscreen,
                isPortrait = isCurrentlyPortrait,
                isMusicMode = isMusicMode,
                showCaptions = showCaptionsRef.value,
                isScreenLocked = isScreenLocked,
                isStreamLoading = isStreamLoading && !isSettledMiniplayer,
                controlsInteractive = !isSettledMiniplayer && !isPipActiveState,
                controlsAlpha = if (isSettledMiniplayer) 0f else watchControlsAlpha,
                onToggleFullscreen = { setFullscreen(!isFullscreen) },
                onEnterFullscreen = { setFullscreen(true) },
                onExitFullscreen = { setFullscreen(false) },
                onCollapseDragStart = ::startCollapseDrag,
                onCollapseDragDelta = ::dragCollapseBy,
                onCollapseDragEnd = ::finishCollapseDrag,
                onCollapseDragCancel = ::cancelCollapseDrag,
                onCaptionsRequested = { showCaptionSelector = true },
                activeCaptions = activeCaptions,
                onActiveCaptionsChanged = { activeCaptions = it },
                offlineCaptions = offlineCaptions,
                onBack = {
                    if (isFullscreen) {
                        setFullscreen(false)
                    } else {
                        onCollapseToMiniplayer()
                    }
                },
                onShowSettingsSheet = { showSettingsSheet = true },
                onShowSpeedMenu = { showPlaybackSpeedMenu = true },
                currentSpeed = currentSpeed,
                onToggleMusicMode = { switchToMusicMode() },
                relatedVideos = relatedVideos,
                onVideoSelect = { url, t, thumb -> onVideoPlay(url, t, thumb, false, false) }
            )

            if (isScreenLocked && !isSettledMiniplayer && !isPipActiveState) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isScreenLocked) {
                            detectTapGestures {
                                showLockIconTemp = true
                                lockIconJob?.cancel()
                                lockIconJob = lockIconScope.launch {
                                    delay(2000)
                                    showLockIconTemp = false
                                }
                            }
                        }
                ) {
                    AnimatedVisibility(
                        visible = showLockIconTemp,
                        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
                        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(600)),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .windowInsetsPadding(WindowInsets.safeGestures)
                            .padding(start = 48.dp)
                    ) {
                        IconButton(
                            onClick = {
                                isScreenLocked = false
                                showLockIconTemp = false
                                lockIconJob?.cancel()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Tap to unlock",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            if (miniChromeAlpha > 0.01f) {
                MiniPlayerCardChrome(
                    player = mediaPlayer,
                    isMusicMode = isMusicMode,
                    artworkUrl = resolvedThumbnail,
                    title = resolvedTitle,
                    channelName = channelName,
                    isLoadingNext = miniLoadingNext,
                    isRestored = isRestoredFromSavedState,
                    progress = miniPlayerProgress,
                    pipHeightDp = cardHeightDp,
                    sizeFactor = sizeFactor,
                    onExpand = onExpandFromMiniplayer,
                    onCloseClick = onClosePlayer,
                    onVideoPlay = { url, t, thumb -> onVideoPlay(url, t, thumb, isMusicMode, false) },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = miniChromeAlpha }
                )
            }
        }

        // ── 5. Music Mode Fullscreen-Card UI (when in Watch Page + Music Mode) ──
        MusicModeUI(
            isMusicMode = isMusicMode && playerWindowState == PlayerWindowState.WATCH_PAGE && cp < 0.5f,
            isFullscreen = isFullscreen,
            title = resolvedTitle,
            channelName = channelName,
            thumbnailUrl = resolvedThumbnail,
            videoUrl = videoUrl,
            isLocalFile = isLocalFile,
            streamInfo = streamInfo,
            mediaPlayer = mediaPlayer,
            context = context,
            scope = scope,
            db = db,
            isLiked = isLiked,
            onLikedChange = { isLiked = it },
            offlineCaptions = offlineCaptions,
            onBack = onCollapseToMiniplayer,
            onDownloadClick = { showDownloadDialog = true },
            onMoreClick = { showVideoActionBottomSheet = true },
            onToggleMode = {
                if (!isAudioOnlyDownload) {
                    isMusicMode = false
                    PlayerStateManager.updateMusicMode(false)
                }
            },
            onChannelClick = {
                channelId?.let { ch ->
                    onCollapseToMiniplayer()
                    onChannelClick(ch)
                }
            },
            autoplayEnabled = autoplayEnabled,
            isBuffering = isBuffering,
            showCaptions = showCaptionsRef.value,
            onCaptionsRequested = { showCaptionSelector = true },
            activeCaptions = activeCaptions,
            onVideoPlay = { url, t, th, musicMode -> onVideoPlay(url, t, th, musicMode, false) },
            isAudioOnly = isAudioOnlyDownload
        )
    }
    
    PlayerScreenDialogs(
        showAddToPlaylistDialog = showAddToPlaylistDialog,
        onDismissAddToPlaylistDialog = { showAddToPlaylistDialog = false },
        showDownloadDialog = showDownloadDialog,
        onDismissDownloadDialog = { showDownloadDialog = false },
        showVideoActionBottomSheet = showVideoActionBottomSheet,
        onDismissVideoActionBottomSheet = { showVideoActionBottomSheet = false },
        showQueueDownloadDialog = showQueueDownloadDialog,
        onDismissQueueDownloadDialog = { showQueueDownloadDialog = false },
        showEqualizerSheet = showEqualizerSheet,
        onDismissEqualizerSheet = { showEqualizerSheet = false },
        showSleepTimerSheet = showSleepTimerSheet,
        onDismissSleepTimerSheet = { showSleepTimerSheet = false },
        originalVideoId = originalVideoId,
        videoUrl = videoUrl,
        title = title,
        thumbnailUrl = thumbnailUrl,
        channelName = channelName,
        streamInfo = streamInfo,
        queue = queue,
        context = context,
        mediaPlayer = mediaPlayer
    )


}

@Composable
private fun DownloadItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = "Download",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}


@Composable
fun VideoPlayerContainer(
    modifier: Modifier,
    mediaPlayer: androidx.media3.common.Player?,
    isFullscreen: Boolean,
    isPortrait: Boolean = true,
    isMusicMode: Boolean,
    showCaptions: Boolean,
    isScreenLocked: Boolean,
    isStreamLoading: Boolean = false,
    controlsInteractive: Boolean = true,
    controlsAlpha: Float = 1f,
    onToggleFullscreen: () -> Unit,
    onEnterFullscreen: () -> Unit = onToggleFullscreen,
    onExitFullscreen: () -> Unit = onToggleFullscreen,
    onCollapseDragStart: () -> Unit = {},
    onCollapseDragDelta: (Float) -> Unit = {},
    onCollapseDragEnd: (Float) -> Unit = {},
    onCollapseDragCancel: () -> Unit = {},
    onCaptionsRequested: () -> Unit,
    activeCaptions: List<CharSequence>,
    onActiveCaptionsChanged: (List<String>) -> Unit,
    offlineCaptions: List<com.videhub.ui.components.CaptionLine3>,
    onBack: () -> Unit,
    onShowSettingsSheet: () -> Unit,
    onShowSpeedMenu: () -> Unit,
    currentSpeed: Float,
    onToggleMusicMode: () -> Unit,
    relatedVideos: List<org.schabi.newpipe.extractor.stream.StreamInfoItem> = emptyList(),
    onVideoSelect: (String, String, String) -> Unit = { _, _, _ -> }
) {
    var controllerVisible by remember { mutableStateOf(false) }
    var videoScale by remember { mutableFloatStateOf(1f) }
    var videoPanOffsetX by remember { mutableFloatStateOf(0f) }
    var videoPanOffsetY by remember { mutableFloatStateOf(0f) }

    val animatedScale by animateFloatAsState(
        targetValue = videoScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "video_zoom_scale"
    )
    val animatedOffsetX by animateFloatAsState(
        targetValue = videoPanOffsetX,
        label = "video_pan_x"
    )
    val animatedOffsetY by animateFloatAsState(
        targetValue = videoPanOffsetY,
        label = "video_pan_y"
    )

    LaunchedEffect(controllerVisible, controlsInteractive) {
        if (!controlsInteractive && controllerVisible) {
            controllerVisible = false
        } else if (controllerVisible) {
            delay(3000)
            controllerVisible = false
        }
    }

    Box(modifier = modifier) {
        ExoPlayerView(
            modifier = Modifier.fillMaxSize(),
            mediaPlayer = mediaPlayer,
            isPipActive = com.videhub.PipState.isActive.value,
            isFullscreen = isFullscreen,
            isMusicMode = isMusicMode,
            showCaptions = showCaptions,
            isScreenLocked = isScreenLocked,
            scale = animatedScale,
            offsetX = animatedOffsetX,
            offsetY = animatedOffsetY,
            onToggleFullscreen = onToggleFullscreen,
            onControllerVisibilityChanged = { controllerVisible = it },
            onCaptionsRequested = onCaptionsRequested,
            onActiveCaptionsChanged = onActiveCaptionsChanged
        )

        if (isStreamLoading && !com.videhub.PipState.isActive.value) {
            com.videhub.ui.components.VideoPlayerBoxShimmer(
                modifier = Modifier.fillMaxSize(),
                onBack = onBack
            )
        }

        if (controlsInteractive && !isStreamLoading && !com.videhub.PipState.isActive.value && !isScreenLocked) {
            ModernPlayerControls(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = controlsAlpha },
                mediaPlayer = mediaPlayer,
                isVisible = controllerVisible,
                isFullscreen = isFullscreen,
                isPortrait = isPortrait,
                showCaptions = showCaptions,
                currentSpeed = currentSpeed,
                videoScale = videoScale,
                onScaleChange = { zoomChange, panChange ->
                    val rawScale = (videoScale * zoomChange).coerceIn(1f, 3.5f)
                    val newScale = if (rawScale < 1.05f && zoomChange < 1f) 1f else rawScale
                    videoScale = newScale
                    if (newScale <= 1.01f) {
                        videoPanOffsetX = 0f
                        videoPanOffsetY = 0f
                    } else {
                        val maxPanX = (newScale - 1f) * 450f
                        val maxPanY = (newScale - 1f) * 300f
                        videoPanOffsetX = (videoPanOffsetX + panChange.x).coerceIn(-maxPanX, maxPanX)
                        videoPanOffsetY = (videoPanOffsetY + panChange.y).coerceIn(-maxPanY, maxPanY)
                    }
                },
                onResetZoom = {
                    videoScale = 1f
                    videoPanOffsetX = 0f
                    videoPanOffsetY = 0f
                },
                onToggleFullscreen = onToggleFullscreen,
                onEnterFullscreen = onEnterFullscreen,
                onExitFullscreen = onExitFullscreen,
                onMinimize = onBack,
                onCollapseDragStart = onCollapseDragStart,
                onCollapseDragDelta = onCollapseDragDelta,
                onCollapseDragEnd = onCollapseDragEnd,
                onCollapseDragCancel = onCollapseDragCancel,
                onSettingsClick = onShowSettingsSheet,
                onCaptionsClick = onCaptionsRequested,
                onPreviousClick = { mediaPlayer?.seekToPrevious() },
                onNextClick = { mediaPlayer?.seekToNext() },
                onSingleTap = { controllerVisible = !controllerVisible },
                relatedVideos = relatedVideos,
                onVideoSelect = onVideoSelect
            )
        }

        CaptionsOverlay(
            isPipActive = com.videhub.PipState.isActive.value,
            showCaptions = showCaptions,
            controllerVisible = controllerVisible && controlsInteractive,
            offlineCaptions = offlineCaptions,
            mediaPlayer = mediaPlayer,
            activeCaptions = activeCaptions,
            isMusicMode = isMusicMode
        )

        AnimatedVisibility(
            visible = controlsInteractive && !isStreamLoading && controllerVisible && !com.videhub.PipState.isActive.value && !isScreenLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = controlsAlpha }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isFullscreen) 20.dp else 8.dp,
                        vertical = if (isFullscreen) 12.dp else 6.dp
                    )
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(40.dp)
                        .testTag("player_minimize_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onShowSettingsSheet,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = onCaptionsRequested,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (showCaptions) Icons.Default.ClosedCaption else Icons.Outlined.ClosedCaption,
                            contentDescription = "Captions",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleMusicMode,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Audiotrack,
                            contentDescription = "Music Mode",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

fun android.content.Context.getActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.getActivity()
    else -> null
}
