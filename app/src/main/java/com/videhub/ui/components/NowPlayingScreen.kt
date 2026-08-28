package com.videhub.ui.components

import androidx.lifecycle.compose.collectAsStateWithLifecycle


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.videhub.service.MediaSessionManager
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@androidx.compose.runtime.Immutable
data class PlayerUiState(
    val title: String = "",
    val artist: String = "",
    val artworkUri: String = "",
    val artworkData: ByteArray? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val playWhenReady: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    title: String,
    artist: String,
    thumbnailUrl: String,
    exoPlayer: Player?,
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    isPlayingProvider: () -> Boolean,
    onBack: () -> Unit,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoreClick: () -> Unit,
    onToggleMode: () -> Unit,
    onChannelClick: () -> Unit,
    showCaptions: Boolean = false,
    offlineCaptions: List<CaptionLine3> = emptyList(),
    onCaptionsRequested: () -> Unit = {},
    onVideoPlay: (String, String, String) -> Unit = { _,_,_ -> },
    isAudioOnly: Boolean = false,
    description: String? = null
) {
    var uiState by remember { 
        mutableStateOf(
            PlayerUiState(
                title = exoPlayer?.currentMediaItem?.mediaMetadata?.title?.toString() ?: title,
                artist = exoPlayer?.currentMediaItem?.mediaMetadata?.artist?.toString() ?: artist,
                artworkUri = exoPlayer?.currentMediaItem?.mediaMetadata?.artworkUri?.toString() ?: thumbnailUrl,
                artworkData = exoPlayer?.currentMediaItem?.mediaMetadata?.artworkData,
                isPlaying = exoPlayer?.isPlaying ?: false,
                playWhenReady = exoPlayer?.playWhenReady ?: false,
                playbackState = exoPlayer?.playbackState ?: Player.STATE_IDLE,
                isLoading = exoPlayer?.playbackState == Player.STATE_BUFFERING
            )
        )
    }
    val coroutineScope = rememberCoroutineScope()

    var duration by remember { mutableLongStateOf(0L) }

    val context = LocalContext.current
    LaunchedEffect(exoPlayer) {
        while (true) {
            try {
                if (exoPlayer?.playbackState != androidx.media3.common.Player.STATE_ENDED) {
                }
                val d = durationProvider()
                duration = if (d == androidx.media3.common.C.TIME_UNSET || d < 0L) 0L else d
            } catch (e: Exception) {
            }
            delay(500) // update every 500ms
        }
    }
    val autoplayEnabled by com.videhub.data.SettingsManager.getAutoplay(context).collectAsStateWithLifecycle(initialValue = true)
    val isActuallyPlaying by MediaSessionManager.isPlayingFlow.collectAsStateWithLifecycle()

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingParam: Boolean) {
                uiState = uiState.copy(isPlaying = isPlayingParam)
            }
            override fun onPlayWhenReadyChanged(playWhenReadyParam: Boolean, reason: Int) {
                uiState = uiState.copy(playWhenReady = playWhenReadyParam)
            }
            override fun onPlaybackStateChanged(state: Int) {
                uiState = uiState.copy(
                    playbackState = state,
                    isLoading = state == Player.STATE_BUFFERING
                )
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                uiState = uiState.copy(
                    title = mediaItem?.mediaMetadata?.title?.toString() ?: uiState.title,
                    artist = mediaItem?.mediaMetadata?.artist?.toString() ?: uiState.artist,
                    artworkUri = mediaItem?.mediaMetadata?.artworkUri?.toString() ?: uiState.artworkUri,
                    artworkData = mediaItem?.mediaMetadata?.artworkData,
                    isPlaying = exoPlayer?.isPlaying ?: false
                )
            }
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                uiState = uiState.copy(
                    title = mediaMetadata.title?.toString() ?: uiState.title,
                    artist = mediaMetadata.artist?.toString() ?: uiState.artist,
                    artworkUri = mediaMetadata.artworkUri?.toString() ?: uiState.artworkUri,
                    artworkData = mediaMetadata.artworkData
                )
            }
        }
        exoPlayer?.addListener(listener)
        onDispose {
            exoPlayer?.removeListener(listener)
        }
    }

    // 1. Observe Player currentPosition in real-time
    val lyrics by LiveCaptionsManager.captions.collectAsStateWithLifecycle(initialValue = emptyList())

    var isLyricsMode by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    val isEqEnabled by com.videhub.audio.EqualizerManager.isEnabled.collectAsState()
    val sleepTimerSecs by com.videhub.audio.SleepTimerManager.remainingSeconds.collectAsState()
    val isSleepTimerEndOfTrack by com.videhub.audio.SleepTimerManager.isEndOfTrackMode.collectAsState()

    // 3D Animation States
    val dragRotationX = remember { Animatable(0f) }
    val dragRotationY = remember { Animatable(0f) }
    val dragTranslationX = remember { Animatable(0f) }
    val dragTranslationY = remember { Animatable(0f) }

    val enterTransition = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enterTransition.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "idle")
    val idleRotationX by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleRotationX"
    )
    val idleRotationY by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleRotationY"
    )

    val trackChangeFlip = remember { Animatable(0f) }
    LaunchedEffect(uiState.artworkUri, uiState.artworkData) {
        trackChangeFlip.snapTo(-90f)
        trackChangeFlip.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        // Dynamic blurred background with Parallax depth
        val displayArt = uiState.artworkData ?: if (uiState.artworkUri == "none") "" else uiState.artworkUri
        androidx.compose.animation.Crossfade(
            targetState = displayArt,
            animationSpec = tween(durationMillis = 800),
            label = "thumbnailCrossfade"
        ) { currentArt ->
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(if (currentArt is String && currentArt.startsWith("/")) java.io.File(currentArt) else currentArt)
                    .error(android.R.drawable.ic_menu_gallery)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.1f
                        scaleY = 1.1f
                        translationX = -dragTranslationX.value * 0.05f
                        translationY = -dragTranslationY.value * 0.05f
                    }
                    .blur(radius = 50.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
                    .let { modifier -> val surfaceColor = MaterialTheme.colorScheme.surface; modifier.drawWithCache {
                        val gradient = Brush.verticalGradient(
                            colors = listOf(
                                surfaceColor.copy(alpha = 0.5f),
                                surfaceColor.copy(alpha = 0.85f)
                            )
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(gradient)
                        }
                    }
                    }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "VideoHub",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Equalizer Button
                    IconButton(onClick = { showEqualizerSheet = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Equalizer,
                            contentDescription = "Equalizer",
                            tint = if (isEqEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    // Sleep Timer Button
                    IconButton(onClick = { showSleepTimerSheet = true }) {
                        val isSleepActive = sleepTimerSecs != null || isSleepTimerEndOfTrack
                        Icon(
                            imageVector = Icons.Rounded.Bedtime,
                            contentDescription = "Sleep Timer",
                            tint = if (isSleepActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    if (!isAudioOnly) {
                        IconButton(onClick = onToggleMode) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = "Video Mode",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Flexible content area: Artwork OR Lyrics
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isLyricsMode,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.9f, animationSpec = tween(400)))
                            .togetherWith(fadeOut(animationSpec = tween(300)))
                    },
                    label = "ArtworkVsLyrics"
                ) { showLyrics ->
                    if (showLyrics) {
                        SyncedLyricsView(
                            title = uiState.title,
                            channelName = uiState.artist,
                            durationSeconds = (durationProvider() / 1000L).coerceAtLeast(0L),
                            mediaPlayer = exoPlayer,
                            offlineCaptions = if (lyrics.isNotEmpty()) lyrics else offlineCaptions,
                            description = description,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.05f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulseAnim"
                            )
                            val pulseWeight by animateFloatAsState(
                                targetValue = if (uiState.isPlaying) 1f else 0f,
                                animationSpec = tween(500),
                                label = "pulseWeight"
                            )
                            
                            val ring1Scale by infiniteTransition.animateFloat(
                                initialValue = 1f, targetValue = 1.5f,
                                animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Restart),
                                label = "ring1Scale"
                            )
                            val ring1Alpha by infiniteTransition.animateFloat(
                                initialValue = 0.5f, targetValue = 0f,
                                animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Restart),
                                label = "ring1Alpha"
                            )
                            
                            val ring2Scale by infiniteTransition.animateFloat(
                                initialValue = 1f, targetValue = 1.5f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2000, 1000, easing = LinearOutSlowInEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "ring2Scale"
                            )
                            val ring2Alpha by infiniteTransition.animateFloat(
                                initialValue = 0.5f, targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2000, 1000, easing = LinearOutSlowInEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "ring2Alpha"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp)
                                    .aspectRatio(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (pulseWeight > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                scaleX = ring1Scale
                                                scaleY = ring1Scale
                                                alpha = ring1Alpha * pulseWeight
                                            }
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                scaleX = ring2Scale
                                                scaleY = ring2Scale
                                                alpha = ring2Alpha * pulseWeight
                                            }
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .graphicsLayer {
                                        val enterScaleVal = 0.3f + (0.7f * enterTransition.value)
                                        val animatedScaleVal = 1f + (pulseScale - 1f) * pulseWeight
                                        val finalScaleVal = enterScaleVal * animatedScaleVal
                                        
                                        translationX = dragTranslationX.value
                                        translationY = dragTranslationY.value
                                        scaleX = finalScaleVal
                                        scaleY = finalScaleVal
                                        shadowElevation = 32.dp.toPx() * enterTransition.value
                                        shape = RoundedCornerShape(16.dp)
                                        clip = true
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                coroutineScope.launch { dragTranslationX.animateTo(0f, spring()) }
                                                coroutineScope.launch { dragTranslationY.animateTo(0f, spring()) }
                                            },
                                            onDragCancel = {
                                                coroutineScope.launch { dragTranslationX.animateTo(0f, spring()) }
                                                coroutineScope.launch { dragTranslationY.animateTo(0f, spring()) }
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                coroutineScope.launch {
                                                    dragTranslationX.snapTo(dragTranslationX.value + dragAmount.x)
                                                    dragTranslationY.snapTo(dragTranslationY.value + dragAmount.y)
                                                }
                                            }
                                        )
                                    }
                            ) {
                                // Blurred Background Layer
                                AsyncImage(
                                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                        .data(if (displayArt is String && displayArt.startsWith("/")) java.io.File(displayArt) else displayArt)
                                        .error(android.R.drawable.ic_menu_gallery)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop, // Crop to fill
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .blur(radius = 20.dp)
                                )
                                // Darkening overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                                )
                                 // Main Foreground Image
                                AsyncImage(
                                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                        .data(if (displayArt is String && displayArt.startsWith("/")) java.io.File(displayArt) else displayArt)
                                        .error(android.R.drawable.ic_menu_gallery)
                                        .build(),
                                    contentDescription = "Album Art",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                )
                            }
                            }
                        
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Fixed Bottom Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
            ) {
                // Title & Favorite Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = uiState.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = uiState.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(
                                    role = androidx.compose.ui.semantics.Role.Button,
                                    onClickLabel = "Open channel"
                                ) { onChannelClick() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    val likeScale = remember { Animatable(1f) }
                    val likeRotZ = remember { Animatable(0f) }

                    IconButton(
                        onClick = {
                            onLikeClick()
                            coroutineScope.launch {
                                launch {
                                    likeScale.animateTo(1.35f, tween(120, easing = FastOutSlowInEasing))
                                    likeScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium))
                                }
                                launch {
                                    likeRotZ.animateTo(-15f, tween(80))
                                    likeRotZ.animateTo(15f, tween(80))
                                    likeRotZ.animateTo(0f, spring(dampingRatio = Spring.DampingRatioHighBouncy))
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = likeScale.value
                                scaleY = likeScale.value
                                rotationZ = likeRotZ.value
                            }
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (isLiked) "Unlike" else "Like",
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                NowPlayingActionRow(
                    title = title,
                    artist = artist,
                    autoplayEnabled = autoplayEnabled,
                    context = context,
                    coroutineScope = coroutineScope,
                    isLyricsMode = isLyricsMode,
                    onLyricsModeChange = { isLyricsMode = it },
                    onDownloadClick = onDownloadClick,
                    onShareClick = onShareClick,
                    onMoreClick = onMoreClick,
                    description = description,
                    durationProvider = durationProvider
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                // Modern Music Seek Bar with 3-Layer Track, Floating Time Bubble, Haptics & Toggleable Remaining Duration
                MusicSeekBar(
                    positionProvider = positionProvider,
                    duration = duration,
                    bufferedPositionProvider = { exoPlayer?.bufferedPosition?.coerceAtLeast(0L) ?: 0L },
                    onSeek = { pos -> 
                        val player = com.videhub.service.MediaSessionManager.getOrCreatePlayer(context)
                        player.seekTo(pos)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                NowPlayingPlaybackControlsRow(
                    context = context,
                    isActuallyPlaying = isActuallyPlaying,
                    isLoading = uiState.isLoading || (exoPlayer?.playbackState == androidx.media3.common.Player.STATE_BUFFERING),
                    onVideoPlay = onVideoPlay
                )
            }
        }

        if (showEqualizerSheet) {
            EqualizerBottomSheet(
                mediaPlayer = exoPlayer,
                onDismiss = { showEqualizerSheet = false }
            )
        }

        if (showSleepTimerSheet) {
            SleepTimerBottomSheet(
                mediaPlayer = exoPlayer,
                onDismiss = { showSleepTimerSheet = false }
            )
        }
    }
}

@Composable
fun CustomSeekBar(
    positionProvider: () -> Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onSeekComplete: (Long) -> Unit = {}
) {
    MusicSeekBar(
        positionProvider = positionProvider,
        duration = duration,
        onSeek = onSeek,
        onSeekComplete = onSeekComplete,
        modifier = Modifier.fillMaxWidth()
    )
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0L) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}



@Composable
fun NowPlayingActionRow(
    title: String,
    artist: String,
    autoplayEnabled: Boolean,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    isLyricsMode: Boolean,
    onLyricsModeChange: (Boolean) -> Unit,
    onDownloadClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoreClick: () -> Unit,
    description: String? = null,
    durationProvider: () -> Long = { 0L },
    isLiked: Boolean = false,
    onLikeClick: () -> Unit = {}
) {
    // Action Row with 5 sleek, modern Material 3 actions
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Lyrics / Captions Toggle
        Box {
            var showTrackDialog by remember { mutableStateOf(false) }
            val availableTracks by LiveCaptionsManager.availableTracks.collectAsStateWithLifecycle(initialValue = emptyList())
            val selectedTrack by LiveCaptionsManager.selectedTrack.collectAsStateWithLifecycle(initialValue = null)

            val lyricsContainerColor by animateColorAsState(
                targetValue = if (isLyricsMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                label = "lyricsContainerColor"
            )
            val lyricsContentColor by animateColorAsState(
                targetValue = if (isLyricsMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "lyricsContentColor"
            )

            FilledTonalIconButton(
                onClick = {
                    val nextMode = !isLyricsMode
                    onLyricsModeChange(nextMode)
                    if (nextMode) {
                        LiveCaptionsManager.fetchCaptions(
                            selectedUrl = selectedTrack?.url,
                            availableTracks = availableTracks,
                            artist = artist,
                            title = title,
                            description = description,
                            isMusicMode = true,
                            durationSeconds = (durationProvider() / 1000L).coerceAtLeast(0L)
                        )
                    }
                },
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = lyricsContainerColor,
                    contentColor = lyricsContentColor
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Subtitles,
                    contentDescription = if (isLyricsMode) "Hide Lyrics" else "Show Lyrics",
                    modifier = Modifier.size(22.dp)
                )
            }

            if (showTrackDialog && availableTracks.isNotEmpty()) {
                CaptionTrackSelectorDialog(
                    availableTracks = availableTracks,
                    selectedTrack = selectedTrack,
                    onTrackSelected = { track ->
                        showTrackDialog = false
                        if (track != null) {
                            onLyricsModeChange(true)
                            LiveCaptionsManager.selectTrack(track)
                            LiveCaptionsManager.fetchCaptions(
                                selectedUrl = track.url,
                                availableTracks = availableTracks,
                                artist = artist,
                                title = title,
                                description = description,
                                isMusicMode = true,
                                durationSeconds = (durationProvider() / 1000L).coerceAtLeast(0L)
                            )
                        } else {
                            onLyricsModeChange(false)
                        }
                    },
                    onDismiss = { showTrackDialog = false }
                )
            }
        }

        // 2. Download Button
        FilledTonalIconButton(
            onClick = onDownloadClick,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = "Download Song",
                modifier = Modifier.size(22.dp)
            )
        }

        // 3. Share Button
        FilledTonalIconButton(
            onClick = onShareClick,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = "Share Song",
                modifier = Modifier.size(22.dp)
            )
        }

        // 4. Autoplay Toggle
        val autoplayContainerColor by animateColorAsState(
            targetValue = if (autoplayEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            label = "autoplayContainerColor"
        )
        val autoplayContentColor by animateColorAsState(
            targetValue = if (autoplayEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            label = "autoplayContentColor"
        )

        FilledTonalIconButton(
            onClick = {
                coroutineScope.launch {
                    com.videhub.data.SettingsManager.setAutoplay(context, !autoplayEnabled)
                }
            },
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = autoplayContainerColor,
                contentColor = autoplayContentColor
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Autorenew,
                contentDescription = "Autoplay Next",
                modifier = Modifier.size(22.dp)
            )
        }

        // 5. More Options / Add to Playlist
        FilledTonalIconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "More Options",
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun NowPlayingPlaybackControlsRow(
    context: android.content.Context,
    isActuallyPlaying: Boolean,
    isLoading: Boolean = false,
    onVideoPlay: (String, String, String) -> Unit = { _,_,_ -> }
) {
    var lastPreviousPressTime by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val isShuffled by com.videhub.QueueManager.isShuffled.collectAsStateWithLifecycle()
    
    // Playback Controls Row with golden-ratio M3 dimensions
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IconButton(
                onClick = { 
                    com.videhub.QueueManager.toggleShuffle()
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle Queue",
                    tint = if (isShuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        
        Box(modifier = Modifier.weight(1.1f), contentAlignment = Alignment.Center) {
            IconButton(
                onClick = { 
                    val player = com.videhub.service.MediaSessionManager.getOrCreatePlayer(context)
                    val currentPosition = player.currentPosition
                    val now = System.currentTimeMillis()
                    val isDoubleTap = (now - lastPreviousPressTime) < 500L
                    val withinFirst3Seconds = currentPosition < 3000L
                    
                    fun playPreviousTrack() {
                        val previousItem = com.videhub.PlaybackHistory.getPrevious()
                        if (previousItem != null) {
                            onVideoPlay(
                                previousItem.url,
                                previousItem.title,
                                previousItem.thumbnailUrl
                            )
                        } else {
                            player.seekTo(0)
                        }
                    }
                    
                    if (isDoubleTap) {
                        playPreviousTrack()
                    } else {
                        if (withinFirst3Seconds) {
                            if (com.videhub.PlaybackHistory.hasPrevious()) {
                                playPreviousTrack()
                            } else {
                                player.seekTo(0)
                            }
                        } else {
                            player.seekTo(0)
                        }
                    }
                    lastPreviousPressTime = now
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Skip Previous",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        Box(modifier = Modifier.weight(1.4f), contentAlignment = Alignment.Center) {
            val playInteractionSource = remember { MutableInteractionSource() }
            val isPlayPressed by playInteractionSource.collectIsPressedAsState()
            
            val playButtonScale by animateFloatAsState(
                targetValue = if (isPlayPressed) 0.92f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "playButtonScale"
            )
            val playButtonRotX by animateFloatAsState(
                targetValue = if (isPlayPressed) -12f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "playButtonRotX"
            )
            FilledIconButton(
                onClick = { 
                    val player = com.videhub.service.MediaSessionManager.getOrCreatePlayer(context)
                    if (player.playbackState == androidx.media3.common.Player.STATE_IDLE) {
                        player.prepare()
                        player.play()
                    } else if (player.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                        player.seekTo(0)
                        player.play()
                    } else if (player.isPlaying || player.playWhenReady) {
                        player.pause()
                    } else {
                        player.play()
                    }
                },
                interactionSource = playInteractionSource,
                modifier = Modifier
                    .size(68.dp)
                    .graphicsLayer {
                        cameraDistance = 8f * density
                        scaleX = playButtonScale
                        scaleY = playButtonScale
                        rotationX = playButtonRotX
                    },
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 3.dp
                    )
                } else {
                    AnimatedContent(targetState = isActuallyPlaying, label = "play_pause") { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1.1f), contentAlignment = Alignment.Center) {
            IconButton(
                onClick = { 
                    com.videhub.service.MediaSessionManager.getOrCreatePlayer(context).seekToNext()
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Skip Next",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            var isRepeatEnabled by remember { mutableStateOf(false) }
            IconButton(
                onClick = { 
                    val player = com.videhub.service.MediaSessionManager.getOrCreatePlayer(context)
                    val newRepeat = player.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF
                    player.repeatMode = if (newRepeat) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
                    isRepeatEnabled = newRepeat
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Repeat,
                    contentDescription = "Repeat",
                    tint = if (isRepeatEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
