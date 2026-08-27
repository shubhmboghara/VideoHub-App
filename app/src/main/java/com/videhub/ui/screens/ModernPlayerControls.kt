package com.videhub.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.videhub.ui.components.DoubleTapSeekOverlay
import com.videhub.ui.components.SeekDirection
import com.videhub.ui.components.VideoSeekBar
import com.videhub.ui.components.formatVideoDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ModernPlayerControls(
    modifier: Modifier = Modifier,
    mediaPlayer: Player?,
    isVisible: Boolean,
    isFullscreen: Boolean,
    showCaptions: Boolean,
    videoScale: Float = 1f,
    onScaleChange: (Float, Offset) -> Unit = { _, _ -> },
    onResetZoom: () -> Unit = {},
    onToggleFullscreen: () -> Unit,
    onSettingsClick: () -> Unit,
    onCaptionsClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSingleTap: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(mediaPlayer?.isPlaying ?: false) }
    var currentTime by remember { mutableStateOf(mediaPlayer?.currentPosition ?: 0L) }
    var totalTime by remember { mutableStateOf(mediaPlayer?.duration?.coerceAtLeast(0L) ?: 0L) }
    var bufferedTime by remember { mutableStateOf(mediaPlayer?.bufferedPosition?.coerceAtLeast(0L) ?: 0L) }

    // Double-Tap Quick Seek State
    var seekDirection by remember { mutableStateOf<SeekDirection?>(null) }
    var seekSeconds by remember { mutableIntStateOf(0) }
    var isSeekOverlayVisible by remember { mutableStateOf(false) }
    var hideSeekJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(mediaPlayer) {
        while (true) {
            mediaPlayer?.let {
                isPlaying = it.isPlaying
                currentTime = it.currentPosition
                totalTime = it.duration.coerceAtLeast(0L)
                bufferedTime = it.bufferedPosition.coerceAtLeast(0L)
            }
            delay(200)
        }
    }

    // Gesture Layer (Transforms + Taps)
    Box(
        modifier = modifier
            .fillMaxSize()
            // 1. Two-Finger Pinch-to-Zoom & Pan Gesture Handler
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, _ ->
                    if (zoom != 1f || pan != Offset.Zero) {
                        onScaleChange(zoom, pan)
                    }
                }
            }
            // 2. Single-Tap Toggle & YouTube-style Double-Tap Quick Seek
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSingleTap() },
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

        // Floating Zoom Level Indicator (Shows when video is scaled)
        AnimatedVisibility(
            visible = videoScale > 1.05f,
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
                    .clickable { onResetZoom() }
                    .testTag("zoom_indicator_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Zoom ${String.format(Locale.US, "%.1f", videoScale)}x",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset zoom",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Standard Player Controls UI Overlay
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Center Playback Controls (Previous, Play/Pause, Next) perfectly centered
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("player_center_controls"),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousClick,
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("player_prev_button")
                            .semantics { contentDescription = "Previous video" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (isPlaying) mediaPlayer?.pause() else mediaPlayer?.play()
                        },
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .testTag("player_play_pause_button")
                            .semantics { contentDescription = if (isPlaying) "Pause video" else "Play video" }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(44.dp)
                                .then(if (!isPlaying) Modifier.offset(x = 6.dp) else Modifier)
                        )
                    }

                    IconButton(
                        onClick = onNextClick,
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("player_next_button")
                            .semantics { contentDescription = "Next video" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Bottom Bar (Timeline, Seekbar, Settings, CC, Fullscreen) with smooth gradient scrim
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    // Video Progress Seek Bar
                    VideoSeekBar(
                        positionMs = currentTime,
                        durationMs = totalTime,
                        bufferedPositionMs = bufferedTime,
                        onSeek = { seekPosition ->
                            currentTime = seekPosition
                            mediaPlayer?.seekTo(seekPosition)
                        },
                        activeColor = MaterialTheme.colorScheme.primary,
                        bufferedColor = Color.White.copy(alpha = 0.45f),
                        inactiveColor = Color.White.copy(alpha = 0.25f),
                        thumbColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Bottom Action Row (Timestamp & Functional Controls)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatVideoDuration(currentTime)} / ${formatVideoDuration(totalTime)}",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.92f),
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.85f),
                                    offset = Offset(0f, 2f),
                                    blurRadius = 4f
                                )
                            ),
                            modifier = Modifier.testTag("player_time_label")
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = onCaptionsClick,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("player_captions_button")
                                    .semantics { contentDescription = "Toggle captions" }
                            ) {
                                Icon(
                                    imageVector = if (showCaptions) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionOff,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(
                                onClick = onSettingsClick,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("player_settings_button")
                                    .semantics { contentDescription = "Video settings and audio effects" }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
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
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
