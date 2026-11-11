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
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
    onMoreClick: () -> Unit,
    onToggleMode: () -> Unit,
    onChannelClick: () -> Unit,
    showCaptions: Boolean = false,
    offlineCaptions: List<CaptionLine3> = emptyList(),
    onCaptionsRequested: () -> Unit = {},
    onVideoPlay: (String, String, String) -> Unit = { _, _, _ -> },
    isAudioOnly: Boolean = false,
    description: String? = null,
    videoId: String? = null,
    isStreamLoading: Boolean = false
) {
    val context = LocalContext.current
    val resolvedPlayer = exoPlayer
        ?: MediaSessionManager.player
        ?: MediaSessionManager.getOrCreatePlayer(context)

    var uiState by remember(videoId) {
        mutableStateOf(
            PlayerUiState(
                title = title.takeUnless { it.isBlank() || it == "Video" || it == "Loading..." }
                    ?: resolvedPlayer?.currentMediaItem?.mediaMetadata?.title?.toString()?.takeUnless { it.isBlank() || it == "Video" || it == "Loading..." }
                    ?: title,
                artist = artist.takeUnless { it.isBlank() }
                    ?: resolvedPlayer?.currentMediaItem?.mediaMetadata?.artist?.toString()?.takeUnless { it.isBlank() }
                    ?: artist,
                artworkUri = thumbnailUrl.takeUnless { it.isBlank() || it == "none" }
                    ?: resolvedPlayer?.currentMediaItem?.mediaMetadata?.artworkUri?.toString().orEmpty(),
                artworkData = resolvedPlayer?.currentMediaItem?.mediaMetadata?.artworkData,
                isPlaying = resolvedPlayer?.isPlaying ?: false,
                playWhenReady = resolvedPlayer?.playWhenReady ?: false,
                playbackState = resolvedPlayer?.playbackState ?: Player.STATE_IDLE,
                isLoading = resolvedPlayer?.playbackState == Player.STATE_BUFFERING
            )
        )
    }

    LaunchedEffect(title, artist, thumbnailUrl, videoId) {
        val validPropTitle = title.takeUnless { it.isBlank() || it == "Video" || it == "Loading..." }
        val validPropArtist = artist.takeUnless { it.isBlank() }
        val validPropArt = thumbnailUrl.takeUnless { it.isBlank() || it == "none" }

        uiState = uiState.copy(
            title = validPropTitle ?: uiState.title.takeUnless { it.isBlank() || it == "Video" || it == "Loading..." } ?: title,
            artist = validPropArtist ?: uiState.artist.takeUnless { it.isBlank() } ?: artist,
            artworkUri = validPropArt ?: uiState.artworkUri.takeUnless { it.isBlank() || it == "none" } ?: thumbnailUrl
        )
    }

    val coroutineScope = rememberCoroutineScope()

    // Keep the latest provider lambdas available to effects and player listeners.
    val currentDurationProvider by rememberUpdatedState(durationProvider)
    val currentPositionProvider by rememberUpdatedState(positionProvider)

    var duration by remember { mutableLongStateOf(0L) }

    // Read a valid duration from the supplied provider first, then Media3.
    // TIME_UNSET and non-positive values are treated as unavailable.
    val readCurrentDuration = {
        val providerDuration = runCatching {
            currentDurationProvider()
        }.getOrDefault(C.TIME_UNSET)

        val playerDuration = runCatching {
            resolvedPlayer?.duration ?: C.TIME_UNSET
        }.getOrDefault(C.TIME_UNSET)

        when {
            providerDuration > 0L && providerDuration != C.TIME_UNSET -> providerDuration
            playerDuration > 0L && playerDuration != C.TIME_UNSET -> playerDuration
            else -> 0L
        }
    }

    // Poll duration because some sources expose metadata after playback preparation.
    LaunchedEffect(resolvedPlayer) {
        while (true) {
            duration = readCurrentDuration()
            delay(250)
        }
    }

    val autoplayEnabled by com.videhub.data.SettingsManager
        .getAutoplay(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val isActuallyPlaying by MediaSessionManager.isPlayingFlow.collectAsStateWithLifecycle()

    DisposableEffect(resolvedPlayer) {
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
                duration = readCurrentDuration()
            }

            override fun onTimelineChanged(
                timeline: androidx.media3.common.Timeline,
                reason: Int
            ) {
                duration = readCurrentDuration()
            }

            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int
            ) {
                val newTitle = mediaItem?.mediaMetadata?.title?.toString().takeUnless { it.isNullOrBlank() } ?: title
                val newArtist = mediaItem?.mediaMetadata?.artist?.toString().takeUnless { it.isNullOrBlank() } ?: artist
                uiState = uiState.copy(
                    title = newTitle,
                    artist = newArtist,
                    artworkUri = mediaItem?.mediaMetadata?.artworkUri?.toString() ?: uiState.artworkUri,
                    artworkData = mediaItem?.mediaMetadata?.artworkData,
                    isPlaying = resolvedPlayer?.isPlaying ?: false
                )

                // Avoid showing the previous track's duration while the new item loads.
                duration = 0L
            }

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                val metaTitle = mediaMetadata.title?.toString().takeUnless { it.isNullOrBlank() } ?: uiState.title.ifBlank { title }
                val metaArtist = mediaMetadata.artist?.toString().takeUnless { it.isNullOrBlank() } ?: uiState.artist.ifBlank { artist }
                uiState = uiState.copy(
                    title = metaTitle,
                    artist = metaArtist,
                    artworkUri = mediaMetadata.artworkUri?.toString() ?: uiState.artworkUri,
                    artworkData = mediaMetadata.artworkData
                )
                duration = readCurrentDuration()
            }
        }

        resolvedPlayer?.addListener(listener)
        onDispose {
            resolvedPlayer?.removeListener(listener)
        }
    }

    // Observe live captions and available caption tracks.
    val lyrics by LiveCaptionsManager.captions.collectAsStateWithLifecycle(initialValue = emptyList())

    var isLyricsMode by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(false)
    }
    var showTrackDialog by remember { mutableStateOf(false) }
    val availableTracks by LiveCaptionsManager.availableTracks.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedTrack by LiveCaptionsManager.selectedTrack.collectAsStateWithLifecycle(initialValue = null)
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    val isEqEnabled by com.videhub.audio.EqualizerManager.isEnabled.collectAsState()
    val sleepTimerSecs by com.videhub.audio.SleepTimerManager.remainingSeconds.collectAsState()
    val isSleepTimerEndOfTrack by com.videhub.audio.SleepTimerManager.isEndOfTrackMode.collectAsState()

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
        // Screen-level ambient background, derived from the current track artwork.
        val displayArt: Any? = uiState.artworkData
            ?.takeIf { it.isNotEmpty() }
            ?: uiState.artworkUri.takeUnless { it.isBlank() || it == "none" }

        androidx.compose.animation.Crossfade(
            targetState = displayArt,
            animationSpec = tween(durationMillis = 600),
            label = "thumbnailCrossfade"
        ) { currentArt ->
            val backgroundImageSource = remember(currentArt) {
                if (currentArt is String && currentArt.startsWith("/")) {
                    java.io.File(currentArt)
                } else {
                    currentArt
                }
            }

            val surfaceColor = MaterialTheme.colorScheme.surface

            if (backgroundImageSource != null) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(backgroundImageSource)
                        .crossfade(true)
                        .error(android.R.drawable.ic_menu_gallery)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.15f
                            scaleY = 1.15f
                        }
                        .blur(
                            radius = 50.dp,
                            edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded
                        )
                        .drawWithCache {
                            val gradient = Brush.verticalGradient(
                                colors = listOf(
                                    surfaceColor.copy(alpha = 0.48f),
                                    surfaceColor.copy(alpha = 0.78f)
                                )
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(gradient)
                            }
                        }
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with system status-bar inset support.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
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
                    // Equalizer button.
                    IconButton(onClick = { showEqualizerSheet = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Equalizer,
                            contentDescription = "Equalizer",
                            tint = if (isEqEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            }
                        )
                    }

                    // Sleep timer button.
                    IconButton(onClick = { showSleepTimerSheet = true }) {
                        val isSleepActive = sleepTimerSecs != null || isSleepTimerEndOfTrack
                        Icon(
                            imageVector = Icons.Rounded.Bedtime,
                            contentDescription = "Sleep Timer",
                            tint = if (isSleepActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            }
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

            Spacer(modifier = Modifier.height(6.dp))

            // Flexible content area: artwork or lyrics.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isLyricsMode,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(400)) +
                            scaleIn(initialScale = 0.9f, animationSpec = tween(400)))
                            .togetherWith(fadeOut(animationSpec = tween(300)))
                    },
                    label = "ArtworkVsLyrics"
                ) { showLyrics ->
                    if (showLyrics) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Lyrics header and language selector.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.clickable { showTrackDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Translate,
                                            contentDescription = "Change Language",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = selectedTrack?.displayName
                                                ?: if (availableTracks.isNotEmpty()) "Change Language" else "Language",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            SyncedLyricsView(
                                title = uiState.title,
                                channelName = uiState.artist,
                                durationSeconds = (duration / 1000L).coerceAtLeast(0L),
                                mediaPlayer = resolvedPlayer,
                                offlineCaptions = if (lyrics.isNotEmpty()) lyrics else offlineCaptions,
                                description = description,
                                videoId = videoId,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                    } else {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulseInfinite")
                        val enterTransition = remember { Animatable(1f) }
                        val dragTranslationX = remember { Animatable(0f) }
                        val dragTranslationY = remember { Animatable(0f) }

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

                            val artModel = remember(displayArt) {
                                coil.request.ImageRequest.Builder(context)
                                    .data(if (displayArt is String && displayArt.startsWith("/")) java.io.File(displayArt) else displayArt)
                                    .crossfade(true)
                                    .error(android.R.drawable.ic_menu_gallery)
                                    .build()
                            }

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

                                // The card: only the artwork, no blurred layers.
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
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
                                    var isArtworkLoading by remember(displayArt) {
                                        mutableStateOf(displayArt != null && displayArt != "" && displayArt != "none")
                                    }
                                    val showArtShimmer = (isStreamLoading && displayArt == null) || isArtworkLoading

                                    // Layer 1: Blurred background fill inside the card for remaining letterbox space
                                    AsyncImage(
                                        model = artModel,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(
                                                radius = 28.dp,
                                                edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded
                                            )
                                    )

                                    // Layer 2: Subtle darkening overlay for optimal contrast
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Black.copy(alpha = 0.15f),
                                                        Color.Black.copy(alpha = 0.25f),
                                                        Color.Black.copy(alpha = 0.35f)
                                                    )
                                                )
                                            )
                                    )

                                    // Layer 3: FULL UNCROPPED THUMBNAIL (100% visible, no cropped edges)
                                    AsyncImage(
                                        model = artModel,
                                        contentDescription = "Full Album Art",
                                        contentScale = ContentScale.Fit,
                                        onSuccess = { isArtworkLoading = false },
                                        onError = { isArtworkLoading = false },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight()
                                            .align(Alignment.Center)
                                    )

                                    // Layer 4: Shimmer Skeleton inside Artwork Card while loading
                                    if (showArtShimmer) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(shimmerBrush())
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Fixed bottom controls with system navigation-bar inset support.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 8.dp)
            ) {
                val displayTitle = remember(uiState.title, title) {
                    val raw = uiState.title.takeUnless { it.isBlank() || it == "Video" || it == "Loading..." }
                        ?: title.takeUnless { it.isBlank() || it == "Video" || it == "Loading..." }
                        ?: ""
                    com.videhub.utils.FormatHelper.cleanDisplayTitle(raw)
                }
                val displayArtist = remember(uiState.artist, artist) {
                    uiState.artist.takeUnless { it.isBlank() }
                        ?: artist.takeUnless { it.isBlank() }
                        ?: ""
                }

                // Track title & channel name block with signboard marquee (or Shimmer only while still unknown)
                val isTitleShimmer = isStreamLoading && displayTitle.isBlank()
                val isArtistShimmer = isStreamLoading && displayArtist.isBlank()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    if (isTitleShimmer) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.76f)
                                .height(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(shimmerBrush())
                        )
                    } else {
                        Text(
                            text = displayTitle.ifBlank { title.ifBlank { "Unknown Track" } },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                lineHeight = 26.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                initialDelayMillis = 1200
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (isArtistShimmer) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.45f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBrush())
                        )
                    } else {
                        Text(
                            text = displayArtist.ifBlank { "VideoHub" },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(
                                    role = androidx.compose.ui.semantics.Role.Button,
                                    onClickLabel = "Open channel"
                                ) {
                                    onChannelClick()
                                }
                                .padding(vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons row below title & channel name (Like, Download, Lyrics, More)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val likeScale = remember { Animatable(1f) }
                    val likeRotZ = remember { Animatable(0f) }

                    // Like / Favorite button
                    IconButton(
                        onClick = {
                            onLikeClick()
                            coroutineScope.launch {
                                launch {
                                    likeScale.animateTo(1.35f, tween(120, easing = FastOutSlowInEasing))
                                    likeScale.animateTo(
                                        1f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioHighBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                                launch {
                                    likeRotZ.animateTo(-15f, tween(80))
                                    likeRotZ.animateTo(15f, tween(80))
                                    likeRotZ.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioHighBouncy)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = likeScale.value
                                scaleY = likeScale.value
                                rotationZ = likeRotZ.value
                            }
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (isLiked) "Unlike" else "Like",
                            tint = if (isLiked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            },
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Download button
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download Track",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Lyrics / Caption toggle button
                    IconButton(
                        onClick = {
                            val nextMode = !isLyricsMode
                            isLyricsMode = nextMode
                            if (nextMode) {
                                LiveCaptionsManager.fetchCaptions(
                                    selectedUrl = selectedTrack?.url,
                                    availableTracks = availableTracks,
                                    artist = artist,
                                    title = title,
                                    description = description,
                                    isMusicMode = true,
                                    durationSeconds = (duration / 1000L).coerceAtLeast(0L),
                                    context = context,
                                    videoId = videoId
                                )
                            }
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Subtitles,
                            contentDescription = if (isLyricsMode) "Hide Lyrics" else "Show Lyrics",
                            tint = if (isLyricsMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            },
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // More Options 3-dot button
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More Options",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Existing seek bar, now receives the validated duration state.
                MusicSeekBar(
                    positionProvider = currentPositionProvider,
                    duration = duration,
                    bufferedPositionProvider = {
                        resolvedPlayer?.bufferedPosition?.coerceAtLeast(0L) ?: 0L
                    },
                    onSeek = { pos ->
                        val player = MediaSessionManager.getOrCreatePlayer(context)
                        player.seekTo(pos)
                    },
                    durationProvider = currentDurationProvider,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                NowPlayingPlaybackControlsRow(
                    context = context,
                    isActuallyPlaying = isActuallyPlaying,
                    isLoading = uiState.isLoading ||
                        (resolvedPlayer?.playbackState == Player.STATE_BUFFERING),
                    onVideoPlay = onVideoPlay
                )

                Spacer(modifier = Modifier.height(4.dp))
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

        if (showTrackDialog) {
            CaptionTrackSelectorDialog(
                availableTracks = availableTracks,
                selectedTrack = selectedTrack,
                onTrackSelected = { track ->
                    showTrackDialog = false
                    if (track != null) {
                        isLyricsMode = true
                        LiveCaptionsManager.selectTrack(track)
                        LiveCaptionsManager.fetchCaptions(
                            selectedUrl = track.url,
                            availableTracks = availableTracks,
                            artist = artist,
                            title = title,
                            description = description,
                            isMusicMode = true,
                            durationSeconds = (duration / 1000L).coerceAtLeast(0L),
                            context = context,
                            videoId = videoId
                        )
                    } else {
                        LiveCaptionsManager.selectTrack(null)
                        LiveCaptionsManager.fetchCaptions(
                            selectedUrl = null,
                            availableTracks = availableTracks,
                            artist = artist,
                            title = title,
                            description = description,
                            isMusicMode = true,
                            durationSeconds = (duration / 1000L).coerceAtLeast(0L),
                            context = context,
                            videoId = videoId
                        )
                    }
                },
                onDismiss = { showTrackDialog = false }
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
fun NowPlayingPlaybackControlsRow(
    context: android.content.Context,
    isActuallyPlaying: Boolean,
    isLoading: Boolean = false,
    onVideoPlay: (String, String, String) -> Unit = { _, _, _ -> }
) {
    var lastPreviousPressTime by remember { mutableLongStateOf(0L) }
    val isShuffled by com.videhub.QueueManager.isShuffled.collectAsStateWithLifecycle()

    // Playback controls row.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IconButton(
                onClick = { com.videhub.QueueManager.toggleShuffle() },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle Queue",
                    tint = if (isShuffled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Box(modifier = Modifier.weight(1.1f), contentAlignment = Alignment.Center) {
            IconButton(
                onClick = {
                    val player = MediaSessionManager.getOrCreatePlayer(context)
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
                    val player = MediaSessionManager.getOrCreatePlayer(context)
                    if (player.playbackState == Player.STATE_IDLE) {
                        player.prepare()
                        player.play()
                    } else if (player.playbackState == Player.STATE_ENDED) {
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
                    AnimatedContent(
                        targetState = isActuallyPlaying,
                        label = "play_pause"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
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
                    MediaSessionManager.getOrCreatePlayer(context).seekToNext()
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
            val loopVideoEnabled by com.videhub.data.SettingsManager
                .getLoopVideo(context)
                .collectAsStateWithLifecycle(initialValue = false)
            val scope = rememberCoroutineScope()

            LaunchedEffect(loopVideoEnabled) {
                val player = MediaSessionManager.getOrCreatePlayer(context)
                player.repeatMode = if (loopVideoEnabled) {
                    Player.REPEAT_MODE_ONE
                } else {
                    Player.REPEAT_MODE_OFF
                }
            }

            IconButton(
                onClick = {
                    val player = MediaSessionManager.getOrCreatePlayer(context)
                    val newRepeat = !loopVideoEnabled
                    player.repeatMode = if (newRepeat) {
                        Player.REPEAT_MODE_ONE
                    } else {
                        Player.REPEAT_MODE_OFF
                    }
                    scope.launch {
                        com.videhub.data.SettingsManager.setLoopVideo(context, newRepeat)
                    }
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Repeat,
                    contentDescription = "Repeat",
                    tint = if (loopVideoEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * VideoHub adaptive artwork container matching the reference card style:
 * - A central rounded square card with 20.dp rounded corners.
 * - Background inside card: Blurred artwork cropped to fill the card height & width.
 * - Foreground inside card: Original uncropped full thumbnail centered smoothly.
 */
@Composable
fun AdaptiveArtworkContainer(
    artwork: Any?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val imageSource = remember(artwork) {
        if (artwork is String && artwork.startsWith("/")) {
            java.io.File(artwork)
        } else {
            artwork
        }
    }

    val hasArtwork = when (imageSource) {
        null -> false
        is String -> imageSource.isNotBlank()
        is ByteArray -> imageSource.isNotEmpty()
        else -> true
    }

    val imageRequest = remember(context, imageSource) {
        coil.request.ImageRequest.Builder(context)
            .data(imageSource)
            .crossfade(true)
            .error(android.R.drawable.ic_menu_gallery)
            .build()
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (hasArtwork) {
            // Main Rounded Square Artwork Card Container
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Layer 1: Blurred background fill inside the rounded card
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(
                                radius = 28.dp,
                                edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded
                            )
                    )

                    // Layer 2: Darkening overlay inside the rounded card for contrast
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.15f),
                                        Color.Black.copy(alpha = 0.25f),
                                        Color.Black.copy(alpha = 0.35f)
                                    )
                                )
                            )
                    )

                    // Layer 3: Uncropped full original thumbnail centered inside the card
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "Full Album Artwork",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .align(Alignment.Center)
                    )
                }
            }
        }
    }
}
