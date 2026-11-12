package com.videhub.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.videhub.ui.components.DoubleTapSeekOverlay
import com.videhub.ui.components.SeekDirection
import com.videhub.ui.components.VideoSeekBar
import com.videhub.ui.components.formatVideoDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@Composable
fun ModernPlayerControls(
    modifier: Modifier = Modifier,
    mediaPlayer: Player?,
    isVisible: Boolean,
    isFullscreen: Boolean,
    showCaptions: Boolean,
    currentSpeed: Float = 1f,
    videoScale: Float = 1f,
    onScaleChange: (Float, Offset) -> Unit = { _, _ -> },
    onResetZoom: () -> Unit = {},
    onToggleFullscreen: () -> Unit,
    onEnterFullscreen: () -> Unit = onToggleFullscreen,
    onExitFullscreen: () -> Unit = onToggleFullscreen,
    onMinimize: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onCaptionsClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSingleTap: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val currentIsFullscreen by rememberUpdatedState(isFullscreen)
    val currentPlaybackSpeed by rememberUpdatedState(currentSpeed)
    val currentOnEnterFullscreen by rememberUpdatedState(onEnterFullscreen)
    val currentOnExitFullscreen by rememberUpdatedState(onExitFullscreen)
    val currentOnMinimize by rememberUpdatedState(onMinimize)
    val currentOnScaleChange by rememberUpdatedState(onScaleChange)
    val currentOnResetZoom by rememberUpdatedState(onResetZoom)
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)

    var isPlaying by remember { mutableStateOf(mediaPlayer?.isPlaying ?: false) }
    var isBuffering by remember { mutableStateOf(mediaPlayer?.playbackState == Player.STATE_BUFFERING) }
    var currentTime by remember { mutableLongStateOf(mediaPlayer?.currentPosition ?: 0L) }
    var totalTime by remember { mutableLongStateOf(mediaPlayer?.duration?.coerceAtLeast(0L) ?: 0L) }
    var bufferedTime by remember { mutableLongStateOf(mediaPlayer?.bufferedPosition?.coerceAtLeast(0L) ?: 0L) }

    // Double-Tap Quick Seek State
    var seekDirection by remember { mutableStateOf<SeekDirection?>(null) }
    var seekSeconds by remember { mutableIntStateOf(0) }
    var isSeekOverlayVisible by remember { mutableStateOf(false) }
    var hideSeekJob by remember { mutableStateOf<Job?>(null) }

    // Hold-for-2x Speed State (YouTube signature gesture)
    var isHold2xActive by remember { mutableStateOf(false) }

    // Zoom Pill Toast State
    var showZoomToast by remember { mutableStateOf(false) }
    var hideZoomToastJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(mediaPlayer) {
        while (true) {
            mediaPlayer?.let {
                isPlaying = it.isPlaying
                isBuffering = it.playbackState == Player.STATE_BUFFERING || (it.playbackState == Player.STATE_IDLE && it.playWhenReady)
                currentTime = it.currentPosition
                totalTime = it.duration.coerceAtLeast(0L)
                bufferedTime = it.bufferedPosition.coerceAtLeast(0L)
            }
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isHold2xActive) {
                mediaPlayer?.playbackParameters = PlaybackParameters(currentPlaybackSpeed)
            }
        }
    }

    val swipeThresholdPx = with(density) { 42.dp.toPx() }
    val topEdgeIgnorePx = with(density) { 36.dp.toPx() }

    // Gesture Layer (YouTube-style Multi-Touch Pinch Zoom, 1-Finger Vertical Fullscreen Swipe, Single Tap, Double Tap Seek, and Hold 2x Speed)
    Box(
        modifier = modifier
            .fillMaxSize()
            // 1. Multi-touch Pinch-to-Zoom AND 1-Finger Vertical Swipe (Fullscreen / Minimize)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                    val startY = firstDown.position.y
                    val touchSlop = viewConfiguration.touchSlop

                    var isMultiTouchZoom = false
                    var isVerticalSwipe = false
                    var totalPanX = 0f
                    var totalPanY = 0f
                    var verticalSwipeTriggered = false

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressedCount = event.changes.count { it.pressed }

                        if (pressedCount >= 2) {
                            // Two-finger Pinch-to-Zoom & Pan (like real YouTube)
                            isMultiTouchZoom = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (abs(zoom - 1f) > 0.005f || pan != Offset.Zero) {
                                currentOnScaleChange(zoom, pan)
                                showZoomToast = true
                                hideZoomToastJob?.cancel()
                                hideZoomToastJob = coroutineScope.launch {
                                    delay(1200)
                                    showZoomToast = false
                                }
                            }
                            event.changes.forEach { it.consume() }
                        } else if (!isMultiTouchZoom && pressedCount == 1 && !isHold2xActive) {
                            val pan = event.calculatePan()
                            totalPanX += pan.x
                            totalPanY += pan.y

                            if (!isVerticalSwipe) {
                                val dist = kotlin.math.hypot(totalPanX, totalPanY)
                                if (dist > touchSlop * 1.4f) {
                                    if (abs(totalPanY) > abs(totalPanX) * 1.35f && startY > topEdgeIgnorePx) {
                                        isVerticalSwipe = true
                                    }
                                }
                            }

                            if (isVerticalSwipe) {
                                event.changes.forEach { it.consume() }
                                if (!verticalSwipeTriggered && abs(totalPanY) >= swipeThresholdPx) {
                                    verticalSwipeTriggered = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (totalPanY < 0f) {
                                        // Swipe UP -> Enter Fullscreen
                                        if (!currentIsFullscreen) {
                                            currentOnEnterFullscreen()
                                        }
                                    } else {
                                        // Swipe DOWN -> Exit Fullscreen (if fullscreen) or Minimize to MiniPlayer (if portrait)
                                        if (currentIsFullscreen) {
                                            currentOnExitFullscreen()
                                        } else {
                                            currentOnMinimize()
                                        }
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            // 2. Single-Tap Toggle, Double-Tap 10s Seek, and Long-Press Hold for 2x Speed
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnSingleTap() },
                    onDoubleTap = { offset ->
                        val width = size.width
                        val isFwd = offset.x >= width / 2
                        val dir = if (isFwd) SeekDirection.FORWARD else SeekDirection.BACKWARD

                        if (seekDirection == dir && isSeekOverlayVisible) {
                            seekSeconds += 10
                        } else {
                            seekDirection = dir
                            seekSeconds = 10
                        }
                        isSeekOverlayVisible = true

                        val deltaMs = if (isFwd) 10000L else -10000L
                        mediaPlayer?.let {
                            val newPos = (it.currentPosition + deltaMs).coerceIn(0L, it.duration.coerceAtLeast(0L))
                            it.seekTo(newPos)
                        }

                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                        hideSeekJob?.cancel()
                        hideSeekJob = coroutineScope.launch {
                            delay(650)
                            isSeekOverlayVisible = false
                            seekDirection = null
                            seekSeconds = 0
                        }
                    },
                    onLongPress = {
                        mediaPlayer?.let { player ->
                            if (player.isPlaying) {
                                isHold2xActive = true
                                player.playbackParameters = PlaybackParameters(2.0f)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    },
                    onPress = {
                        try {
                            awaitRelease()
                        } finally {
                            if (isHold2xActive) {
                                isHold2xActive = false
                                mediaPlayer?.playbackParameters = PlaybackParameters(currentPlaybackSpeed)
                            }
                        }
                    }
                )
            }
    ) {
        // Double-Tap Quick Seek Animated Ripple & Chevrons Overlay
        DoubleTapSeekOverlay(
            direction = seekDirection,
            seconds = seekSeconds,
            isVisible = isSeekOverlayVisible
        )

        // Standalone buffering indicator when controls overlay is hidden
        if (!isVisible && isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // YouTube-style Hold-for-2x Speed Pill Indicator
        AnimatedVisibility(
            visible = isHold2xActive,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.78f),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "2x speed",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Floating Zoom Level Indicator (Shows when video is zoomed)
        AnimatedVisibility(
            visible = !isHold2xActive && (videoScale > 1.05f || showZoomToast),
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.75f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .clickable { currentOnResetZoom() }
                    .testTag("zoom_indicator_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val zoomLabel = when {
                        videoScale <= 1.05f -> "Original"
                        videoScale in 1.22f..1.42f -> "Zoomed to fill (${String.format(Locale.US, "%.1f", videoScale)}x)"
                        else -> "Zoom ${String.format(Locale.US, "%.1f", videoScale)}x"
                    }
                    Text(
                        text = zoomLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        )
                    )
                    if (videoScale > 1.05f) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset zoom",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Standard Player Controls UI Overlay
        AnimatedVisibility(
            visible = isVisible && !isHold2xActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .then(
                        if (isFullscreen) {
                            Modifier
                                .windowInsetsPadding(WindowInsets.displayCutout)
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                        } else {
                            Modifier
                        }
                    )
            ) {
                // Center Playback Controls (Previous, Play/Pause, Next) matching YouTube player
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .testTag("player_center_controls"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousClick,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .testTag("player_prev_button")
                            .semantics { contentDescription = "Previous video" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(40.dp))

                    IconButton(
                        onClick = {
                            mediaPlayer?.let { player ->
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
                            }
                        },
                        modifier = Modifier
                            .size(60.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .testTag("player_play_pause_button")
                            .semantics { contentDescription = if (isPlaying) "Pause video" else "Play video" }
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(40.dp))

                    IconButton(
                        onClick = onNextClick,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .testTag("player_next_button")
                            .semantics { contentDescription = "Next video" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // YouTube-style Bottom Bar (Timestamp on left, Fullscreen on right, SeekBar along bottom)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isFullscreen) 20.dp else 0.dp,
                            vertical = if (isFullscreen) 14.dp else 0.dp
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = if (isFullscreen) 0.dp else 12.dp,
                                end = if (isFullscreen) 0.dp else 4.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("player_time_label")
                        ) {
                            Text(
                                text = formatVideoDuration(currentTime),
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFeatureSettings = "tnum",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = " / ${formatVideoDuration(totalTime)}",
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFeatureSettings = "tnum",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            )
                        }

                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("player_fullscreen_button")
                                .semantics { contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen" }
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    VideoSeekBar(
                        positionMs = currentTime,
                        durationMs = totalTime,
                        bufferedPositionMs = bufferedTime,
                        onSeek = { seekPosition ->
                            currentTime = seekPosition
                            mediaPlayer?.seekTo(seekPosition)
                        },
                        activeColor = Color.White,
                        bufferedColor = Color.White.copy(alpha = 0.45f),
                        inactiveColor = Color.White.copy(alpha = 0.25f),
                        thumbColor = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isFullscreen) 0.dp else 12.dp)
                    )
                }
            }
        }
    }
}
