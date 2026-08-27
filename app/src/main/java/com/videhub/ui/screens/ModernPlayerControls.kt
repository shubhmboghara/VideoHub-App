package com.videhub.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.videhub.ui.components.VideoSeekBar
import com.videhub.ui.components.formatVideoDuration
import kotlinx.coroutines.delay

@Composable
fun ModernPlayerControls(
    modifier: Modifier = Modifier,
    mediaPlayer: Player?,
    isVisible: Boolean,
    isFullscreen: Boolean,
    showCaptions: Boolean,
    onToggleFullscreen: () -> Unit,
    onSettingsClick: () -> Unit,
    onCaptionsClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSingleTap: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(mediaPlayer?.isPlaying ?: false) }
    var currentTime by remember { mutableStateOf(mediaPlayer?.currentPosition ?: 0L) }
    var totalTime by remember { mutableStateOf(mediaPlayer?.duration?.coerceAtLeast(0L) ?: 0L) }
    var bufferedTime by remember { mutableStateOf(mediaPlayer?.bufferedPosition?.coerceAtLeast(0L) ?: 0L) }

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

    // Gesture Layer (always present to intercept taps)
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { offset ->
                        val width = size.width
                        if (offset.x < width / 2) {
                            // Rewind 10s
                            mediaPlayer?.let { it.seekTo((it.currentPosition - 10000).coerceAtLeast(0)) }
                        } else {
                            // Fast forward 10s
                            mediaPlayer?.let { it.seekTo((it.currentPosition + 10000).coerceAtMost(it.duration)) }
                        }
                    }
                )
            }
    ) {
        // UI Overlay
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
                // Center Controls (Play/Pause, Prev, Next)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousClick,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .testTag("player_prev_button")
                            .semantics { contentDescription = "Previous video" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (isPlaying) mediaPlayer?.pause() else mediaPlayer?.play()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .testTag("player_play_pause_button")
                            .semantics { contentDescription = if (isPlaying) "Pause video" else "Play video" }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    IconButton(
                        onClick = onNextClick,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .testTag("player_next_button")
                            .semantics { contentDescription = "Next video" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Bottom Bar (Timeline, Settings, CC, Fullscreen)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatVideoDuration(currentTime)} / ${formatVideoDuration(totalTime)}",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.90f),
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.80f),
                                    offset = Offset(0f, 2f),
                                    blurRadius = 4f
                                )
                            ),
                            modifier = Modifier.testTag("player_time_label")
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onCaptionsClick,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("player_captions_button")
                                    .semantics { contentDescription = "Toggle captions" }
                            ) {
                                Icon(
                                    imageVector = if (showCaptions) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionOff,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = onSettingsClick,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("player_settings_button")
                                    .semantics { contentDescription = "Video settings and audio effects" }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = onToggleFullscreen,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("player_fullscreen_button")
                                    .semantics { contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen" }
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Modern Custom Video Seek Bar with 4dp Track, Buffered Progress, and Tactile Spring-Scaled Thumb
                    VideoSeekBar(
                        positionMs = currentTime,
                        durationMs = totalTime,
                        bufferedPositionMs = bufferedTime,
                        onSeek = { seekPosition ->
                            currentTime = seekPosition
                            mediaPlayer?.seekTo(seekPosition)
                        },
                        activeColor = MaterialTheme.colorScheme.primary,
                        bufferedColor = Color.White.copy(alpha = 0.40f),
                        inactiveColor = Color.White.copy(alpha = 0.25f),
                        thumbColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
