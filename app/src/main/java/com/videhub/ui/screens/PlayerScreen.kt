package com.videhub.ui.screens
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTapGestures

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.background


import android.app.Activity
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.videhub.data.AppDatabase
import com.videhub.data.entity.ChannelEntity
import com.videhub.data.entity.HistoryEntity
import com.videhub.data.entity.PlaylistEntity
import com.videhub.data.entity.PlaylistVideoEntity
import com.videhub.data.entity.LikedVideoEntity
import com.videhub.ui.components.AnimatedSubscribeButton
import com.videhub.extractor.ExtractorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

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
    mediaPlayer: Player? = null
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

    var allStreams by remember { mutableStateOf<List<VideoStream>>(emptyList()) }
    var fallbackIndex by remember { mutableIntStateOf(0) }
    var hasInitializedPlayer by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    
    val isSameVideo = sharedViewModel.currentPlayerUrl == videoUrl
    var streamInfo by remember { mutableStateOf<StreamInfo?>(
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

    LaunchedEffect(videoUrl) {
        if (isLocalFile) {
            val fileName = java.io.File(videoUrl).name
            withContext(Dispatchers.IO) {
                val entity = db.downloadedVideoDao().getAllDownloadsSync().find {
                    it.fileName == fileName || it.videoId == videoUrl || videoUrl.endsWith(it.fileName)
                }
                if (entity != null) {
                    isAudioOnlyDownload = entity.isAudioOnly || com.videhub.utils.FileUtils.isAudio(entity, videoUrl)
                }
            }
        } else {
            isAudioOnlyDownload = false
        }
    }

    var channelId by remember { mutableStateOf<String?>(if (isSameVideo) streamInfo?.uploaderUrl else null) }
    var channelName by remember { mutableStateOf(if (isSameVideo) streamInfo?.uploaderName ?: "" else "") }
    
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableStateOf<Int?>(null) }
    var sponsorSegments by remember { mutableStateOf<List<com.videhub.extractor.SponsorSegment>>(emptyList()) }
    
    var isSubscribed by remember { mutableStateOf(false) }
    var isLiked by remember { mutableStateOf(false) }
    var activeMediaId by remember { mutableStateOf(videoUrl) }
    LaunchedEffect(activeMediaId) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            isLiked = db.likedVideoDao().isLiked(activeMediaId)
        }
    }
    var isInWatchLater by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    
    var retryTrigger by remember { mutableIntStateOf(0) }
    var offlineStreamUri by remember { mutableStateOf<String?>(null) }
    var offlineAudioUri by remember { mutableStateOf<String?>(null) }

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

    var selectedQuality by remember { mutableStateOf("Auto") }
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

    LaunchedEffect(videoUrl, retryTrigger) {
        if (retryTrigger == 0 && sharedViewModel.currentPlayerUrl == videoUrl && sharedViewModel.playerStreamInfoCache != null) {
            streamInfo = sharedViewModel.playerStreamInfoCache
            channelId = streamInfo?.uploaderUrl
            channelName = streamInfo?.uploaderName ?: channelName
            return@LaunchedEffect
        }
        val bgInfo = com.videhub.service.BackgroundAutoplayHandler.currentStreamInfo
        if (retryTrigger == 0 && bgInfo != null && (bgInfo.url == videoUrl || bgInfo.originalUrl == videoUrl)) {
            streamInfo = bgInfo
            channelId = bgInfo.uploaderUrl
            channelName = bgInfo.uploaderName ?: channelName
            sharedViewModel.playerStreamInfoCache = bgInfo
            sharedViewModel.currentPlayerUrl = videoUrl
            sharedViewModel.playerRelatedItemsCache = bgInfo.relatedItems?.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>() ?: emptyList()
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
            
            db.historyDao().insert(
                com.videhub.data.entity.HistoryEntity(
                    videoId = videoUrl,
                    title = title,
                    thumbnailUrl = thumbnailUrl,
                    channelName = channelName,
                    viewCount = info.viewCount,
                    uploadDate = info.textualUploadDate ?: "",
                    timestamp = System.currentTimeMillis()
                )
            )

            sharedViewModel.playerRelatedItemsCache = info.relatedItems?.filterIsInstance<StreamInfoItem>() ?: emptyList()
            com.videhub.service.BackgroundAutoplayHandler.relatedItemsCache = info.relatedItems ?: emptyList()
            sharedViewModel.playerStreamInfoCache = info
            sharedViewModel.currentPlayerUrl = videoUrl
            streamInfo = info

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
                                title = title,
                                thumbnailUrl = thumbnailUrl,
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

    LaunchedEffect(videoUrl, streamInfo, selectedQuality, offlineStreamUri, retryTrigger) {
        val isLocal = videoUrl.startsWith("/") || videoUrl.startsWith("file://") || videoUrl.startsWith("content://")
        try {
            var audioUrlForMerge: String? = offlineAudioUri
            val uri = if (isLocal) {
                if (videoUrl.startsWith("/")) android.net.Uri.fromFile(java.io.File(videoUrl)).toString() else videoUrl
            } else if (offlineStreamUri != null) {
                offlineStreamUri!!
            } else {
                val info = streamInfo ?: return@LaunchedEffect
                val audioOnly = (info.audioStreams ?: emptyList()).filter { !it.content.isNullOrBlank() }
                if (isAudioOnlyDownload && audioOnly.isNotEmpty()) {
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
                            } else ""
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
                                ?: videoOnly.firstOrNull()?.content ?: ""
                        }
                    }
                }
            }

            if (uri.isNotBlank()) {
                val subs = streamInfo?.subtitles
                val tracks = subs?.mapNotNull {
                    if (it.content.isNullOrBlank() || it.languageTag.isNullOrBlank()) null
                    else com.videhub.ui.components.CaptionTrack(
                        languageTag = it.languageTag ?: "en",
                        displayName = java.util.Locale(it.languageTag ?: "en").displayLanguage.takeIf { d -> d.isNotBlank() } ?: it.languageTag ?: "English",
                        url = it.content ?: "",
                        isAutoGenerated = it.isAutoGenerated
                    )
                } ?: emptyList()
                com.videhub.ui.components.LiveCaptionsManager.setAvailableTracks(tracks)
                if (isLocal) { com.videhub.ui.components.LiveCaptionsManager.loadCaptionsFromDb(context, videoUrl) }
                
                val metadata = androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(channelName)
                    .setArtworkUri(android.net.Uri.parse(if (thumbnailUrl.isNotBlank()) thumbnailUrl else "none"))
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
                    
                    val shouldUpdate = if (hasInitializedPlayer && isDifferentMedia && currentMediaId != null) {
                        false // Player moved on to autoplay/queue, do not interrupt
                    } else if (!hasInitializedPlayer && !isDifferentMedia && player.playbackState != androidx.media3.common.Player.STATE_IDLE) {
                        false // Player is already playing this video (e.g. from autoplay)
                    } else {
                        true
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
                        
                        hasInitializedPlayer = true
                    } else if (!hasInitializedPlayer) {
                        hasInitializedPlayer = true
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

    var relatedVideos by remember { mutableStateOf<List<StreamInfoItem>>(if (isSameVideo) sharedViewModel.playerRelatedItemsCache?.filterIsInstance<StreamInfoItem>() ?: emptyList() else emptyList()) }
    LaunchedEffect(streamInfo) {
        if (streamInfo != null) {
            relatedVideos = streamInfo!!.relatedItems?.filterIsInstance<StreamInfoItem>() ?: emptyList()
        }
    }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistNameDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showVideoActionBottomSheet by remember { mutableStateOf(false) }
    var showQueueDownloadDialog by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<PlaylistEntity>>(emptyList()) }

    var isFullscreen by remember { mutableStateOf(initialFullscreen) }
    fun setFullscreen(enable: Boolean) {
        isFullscreen = enable
        activity?.let {
            it.requestedOrientation = if (enable)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val ctrl = WindowInsetsControllerCompat(it.window, it.window.decorView)
            if (enable) {
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
                ctrl.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var lastPhysicalState by remember { mutableStateOf("UNKNOWN") }
    DisposableEffect(context) {
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
                        if (autoRotateEnabled) {
                            if (currentState == "LANDSCAPE" && !isFullscreen) {
                                setFullscreen(true)
                            } else if (currentState == "PORTRAIT" && isFullscreen) {
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

    val initialIsMusic = remember(videoUrl, title, forceMusicMode, isAudioOnlyDownload) {
        forceMusicMode || isAudioOnlyDownload || (isLocalFile && (com.videhub.utils.FileUtils.isAudioFile(videoUrl) || title.contains("kbps", ignoreCase = true)))
    }
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
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCaptionSelector = false },
            title = { androidx.compose.material3.Text("Select Captions") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    item {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                com.videhub.ui.components.LiveCaptionsManager.selectTrack(null)
                                showCaptionsRef.value = false
                                showCaptionSelector = false
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Text("Off", modifier = Modifier.weight(1f))
                            if (!showCaptionsRef.value) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = null)
                            }
                        }
                    }
                    if (availableTracks.isEmpty() && offlineCaptions.isNotEmpty()) {
                        item {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    showCaptionsRef.value = true
                                    showCaptionSelector = false
                                }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Text("On", modifier = Modifier.weight(1f))
                                if (showCaptionsRef.value) {
                                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = null)
                                }
                            }
                        }
                    }
                    items(count = availableTracks.size, key = { index -> "${availableTracks[index].url}_$index" }) { index ->
                        val track = availableTracks[index]
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                com.videhub.ui.components.LiveCaptionsManager.selectTrack(track)
                                showCaptionsRef.value = true
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
                                        isMusicMode = forceMusicMode
                                    )
                                }
                                showCaptionSelector = false
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val label = track.displayName + (if (track.isAutoGenerated) " (auto)" else "")
                            androidx.compose.material3.Text(
                                text = label, 
                                modifier = Modifier.weight(1f),
                                fontWeight = if (selectedTrack?.languageTag == track.languageTag && showCaptionsRef.value) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                            )
                            if (selectedTrack?.languageTag == track.languageTag && showCaptionsRef.value) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = null)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showCaptionSelector = false }) {
                    androidx.compose.material3.Text("Close")
                }
            }
        )
    }
    if (showQualitySelector) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showQualitySelector = false },
            title = { androidx.compose.material3.Text("Video Quality") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
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
                androidx.compose.foundation.lazy.LazyColumn {
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
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
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
                                showSleepTimerDialog = true
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
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, top = 8.dp)
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
    var isMusicMode by remember(videoUrl, initialIsMusic) { mutableStateOf(initialIsMusic) }
    LaunchedEffect(isAudioOnlyDownload) {
        if (isAudioOnlyDownload) {
            isMusicMode = true
        }
    }
    androidx.compose.runtime.LaunchedEffect(isMusicMode) { showCaptionsRef.value = isMusicMode }

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


    
    LaunchedEffect(Unit) {
        if (initialFullscreen) {
            setFullscreen(true)
        }
    }


    LaunchedEffect(isMusicMode, streamInfo) {
        com.videhub.PipState.canEnterPip = !isMusicMode
        if (streamInfo != null) {
            val currentCaptions = com.videhub.ui.components.LiveCaptionsManager.captions.value
            if (currentCaptions.isEmpty()) {
                com.videhub.ui.components.LiveCaptionsManager.fetchCaptions(
                    selectedUrl = com.videhub.ui.components.LiveCaptionsManager.selectedTrack.value?.url,
                    availableTracks = com.videhub.ui.components.LiveCaptionsManager.availableTracks.value,
                    artist = channelName,
                    title = title,
                    description = streamInfo?.description?.content,
                    isMusicMode = isMusicMode
                )
            }
        }
    }

    DisposableEffect(Unit) { 
        onDispose { 
            com.videhub.ui.components.LiveCaptionsManager.clear()
            com.videhub.PipState.canEnterPip = false
            setFullscreen(false)
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } 
    }
    
    DisposableEffect(mediaPlayer) {
        val player = mediaPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                val customCaptionLines = com.videhub.ui.components.LiveCaptionsManager.captions.value
                val isInMusicMode = com.videhub.ui.components.LiveCaptionsManager.isMusicMode.value
                val usingLyricsCaptions = isInMusicMode && customCaptionLines.isNotEmpty() && selectedLanguageCode == null
                if (!usingLyricsCaptions) {
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
                    try {
                        onVideoPlay(currentId, newTitle, newThumb, com.videhub.MiniPlayerState.isMusicMode.value, isFullscreen)
                    } catch (e: Exception) {
                        android.util.Log.e("PlayerScreen", "Error navigating to next item: ", e)
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
                        try {
                            onVideoPlay(mediaId, newTitle, newThumb, com.videhub.MiniPlayerState.isMusicMode.value, isFullscreen)
                        } catch (e: Exception) {
                            android.util.Log.e("PlayerScreen", "Error navigating to new item on resume: ", e)
                        }
                    }
                }
            }
        }
        playerLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { playerLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = isFullscreen) { setFullscreen(false) }

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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val isPip = com.videhub.PipState.isActive.value
            val videoHeight = if (isFullscreen || isLandscape || isPip) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            
            Box(modifier = videoHeight.background(Color.Black)) {
                VideoPlayerContainer(
                    modifier = Modifier.fillMaxSize(),
                    mediaPlayer = mediaPlayer,
                    isFullscreen = isFullscreen,
                    isMusicMode = isMusicMode,
                    showCaptions = showCaptionsRef.value,
                    isScreenLocked = isScreenLocked,
                    onToggleFullscreen = { setFullscreen(!isFullscreen) },
                    onCaptionsRequested = { showCaptionSelector = true },
                    activeCaptions = activeCaptions,
                    onActiveCaptionsChanged = { activeCaptions = it },
                    offlineCaptions = offlineCaptions,
                    onBack = {
                        if (isFullscreen) {
                            setFullscreen(false)
                        } else {
                            onBack()
                        }
                    },
                    onShowSettingsSheet = { showSettingsSheet = true },
                    onShowSpeedMenu = { showPlaybackSpeedMenu = true },
                    currentSpeed = currentSpeed,
                    onToggleMusicMode = { isMusicMode = true }
                )
                
                val isLocalFile = videoUrl.startsWith("/") || videoUrl.startsWith("file://") || videoUrl.startsWith("content://")
                if (isBuffering && !isLocalFile) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (isScreenLocked) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(isScreenLocked) {
                                detectTapGestures {
                                    showLockIconTemp = true
                                    lockIconJob?.cancel()
                                    lockIconJob = lockIconScope.launch {
                                        kotlinx.coroutines.delay(2000)
                                        showLockIconTemp = false
                                    }
                                }
                            }
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showLockIconTemp,
                            enter = androidx.compose.animation.fadeIn(
                                animationSpec = androidx.compose.animation.core.tween(200)
                            ),
                            exit = androidx.compose.animation.fadeOut(
                                animationSpec = androidx.compose.animation.core.tween(600)
                            ),
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeGestures)
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
                                    .background(
                                        Color.Black.copy(alpha = 0.6f),
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Lock,
                                    contentDescription = "Tap to unlock",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            if (!isFullscreen && !isLandscape && !com.videhub.PipState.isActive.value) {
                BelowPlayerContent(
                    listState = listState,
                    streamInfo = streamInfo,
                    isLocalFile = isLocalFile,
                    isOfflineFallback = offlineStreamUri != null,
                    errorMessage = errorMessage,
                    title = title,
                    channelName = channelName,
                    channelId = channelId,
                    thumbnailUrl = thumbnailUrl,
                    onChannelClick = onChannelClick,
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
                    onVideoPlay = { url, t, th, musicMode -> onVideoPlay(url, t, th, musicMode, false) },
                    mediaPlayer = mediaPlayer,
                    sharedViewModel = sharedViewModel,
                    isMusicMode = isMusicMode,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        MusicModeUI(
            isMusicMode = isMusicMode,
            isFullscreen = isFullscreen,
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            videoUrl = videoUrl,
            isLocalFile = isLocalFile,
            streamInfo = streamInfo,
            mediaPlayer = mediaPlayer,
            context = context,
            scope = scope,
            db = db,
            isLiked = isLiked,
            onLikedChange = { isLiked = it },
            onBack = onBack,
            onDownloadClick = { showDownloadDialog = true },
            onMoreClick = { showVideoActionBottomSheet = true },
            onToggleMode = {
                if (!isAudioOnlyDownload) {
                    isMusicMode = false
                }
            },
            onChannelClick = { channelId?.let { onChannelClick(it) } },
            autoplayEnabled = autoplayEnabled,
            isBuffering = isBuffering,
            showCaptions = showCaptionsRef.value,
            onCaptionsRequested = { showCaptionSelector = true },
            activeCaptions = activeCaptions,
            offlineCaptions = offlineCaptions,
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
        originalVideoId = originalVideoId,
        videoUrl = videoUrl,
        title = title,
        thumbnailUrl = thumbnailUrl,
        channelName = channelName,
        streamInfo = streamInfo,
        queue = queue,
        context = context
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
    isMusicMode: Boolean,
    showCaptions: Boolean,
    isScreenLocked: Boolean,
    onToggleFullscreen: () -> Unit,
    onCaptionsRequested: () -> Unit,
    activeCaptions: List<CharSequence>,
    onActiveCaptionsChanged: (List<String>) -> Unit,
    offlineCaptions: List<com.videhub.ui.components.CaptionLine3>,
    onBack: () -> Unit,
    onShowSettingsSheet: () -> Unit,
    onShowSpeedMenu: () -> Unit,
    currentSpeed: Float,
    onToggleMusicMode: () -> Unit
) {
    var controllerVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(controllerVisible) {
        if (controllerVisible) {
            kotlinx.coroutines.delay(3000)
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
            onToggleFullscreen = onToggleFullscreen,
            onControllerVisibilityChanged = { controllerVisible = it },
            onCaptionsRequested = onCaptionsRequested,
            onActiveCaptionsChanged = onActiveCaptionsChanged
        )
        CaptionsOverlay(
            isPipActive = com.videhub.PipState.isActive.value,
            showCaptions = showCaptions,
            controllerVisible = controllerVisible,
            offlineCaptions = offlineCaptions,
            mediaPlayer = mediaPlayer,
            activeCaptions = activeCaptions,
            isMusicMode = isMusicMode
        )
        
        androidx.compose.animation.AnimatedVisibility(
            visible = controllerVisible && !com.videhub.PipState.isActive.value && !isScreenLocked,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    ))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
                
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onShowSettingsSheet) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                    if (!isFullscreen) {
                        IconButton(onClick = onToggleMusicMode) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Audiotrack,
                                contentDescription = "Music Mode",
                                tint = Color.White
                            )
                        }
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
