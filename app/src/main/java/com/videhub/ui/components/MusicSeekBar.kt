package com.videhub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max

/**
 * State-of-the-art Material Design 3 Music Seek Bar for Music Mode / Now Playing Screen.
 * Features:
 * - Dynamic 6dp-to-8dp reactive track expansion on interaction
 * - 3-tier rendering (Inactive base, Buffered network progress, Vibrant active gradient track)
 * - Tactile spring-scaled thumb with glowing drop shadow and inner core accent
 * - Floating time preview bubble during scrubbing
 * - Elapsed & toggleable Remaining / Total duration labels with Monospace typography
 * - Tactile micro-haptic feedback
 * - 48dp minimum accessible touch target
 */
@Composable
fun MusicSeekBar(
    positionProvider: () -> Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    bufferedPositionProvider: (() -> Long)? = null,
    onSeekComplete: ((Long) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var currentPosition by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var isTappedBriefly by remember { mutableStateOf(false) }
    var showRemainingTime by remember { mutableStateOf(false) }
    var lastHapticFraction by remember { mutableFloatStateOf(0f) }

    // Silky smooth 100ms position polling
    LaunchedEffect(Unit) {
        while (true) {
            if (!isDragging) {
                currentPosition = positionProvider()
                bufferedPosition = bufferedPositionProvider?.invoke() ?: 0L
            }
            delay(100)
        }
    }

    val safeDuration = remember(duration) { if (duration <= 0L) 0L else duration }
    val displaySafeDuration = max(1L, safeDuration)

    val currentFraction = remember(currentPosition, displaySafeDuration) {
        (currentPosition.toFloat() / displaySafeDuration.toFloat()).coerceIn(0f, 1f)
    }
    val bufferedFraction = remember(bufferedPosition, displaySafeDuration) {
        (bufferedPosition.toFloat() / displaySafeDuration.toFloat()).coerceIn(0f, 1f)
    }

    val displayFraction = if (isDragging) dragFraction else currentFraction
    val displayPositionMs = (displayFraction * displaySafeDuration).toLong()

    val isInteracting = isDragging || isTappedBriefly

    // Dynamic track height: 6dp resting, 8dp interacting
    val trackHeightDp: Dp by animateDpAsState(
        targetValue = if (isInteracting) 8.dp else 6.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "music_track_height"
    )

    // Dynamic thumb diameter: 14dp resting, 22dp active with spring bounce
    val thumbDiameter: Dp by animateDpAsState(
        targetValue = if (isInteracting) 22.dp else 14.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "music_thumb_diameter"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val activeBrush = remember(primaryColor, tertiaryColor) {
        Brush.horizontalGradient(
            colors = listOf(primaryColor, tertiaryColor.copy(alpha = 0.95f), primaryColor)
        )
    }
    val bufferedColor = onSurfaceColor.copy(alpha = 0.28f)
    val inactiveColor = onSurfaceColor.copy(alpha = 0.12f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Music playback track, position ${formatMusicDuration(displayPositionMs)} of ${formatMusicDuration(safeDuration)}"
            }
    ) {
        // Scrubbing Preview Bubble (Shown when actively dragging)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isDragging,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 6.dp,
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = formatMusicDuration(displayPositionMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // 48dp Minimum Accessible Touch Region
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("music_seek_bar_touch_area")
                .pointerInput(displaySafeDuration) {
                    detectTapGestures(
                        onPress = { offset ->
                            val widthPx = size.width.toFloat()
                            if (widthPx > 0f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val targetFrac = (offset.x / widthPx).coerceIn(0f, 1f)
                                isTappedBriefly = true
                                val targetPos = (targetFrac * displaySafeDuration).toLong()
                                currentPosition = targetPos
                                onSeek(targetPos)
                                onSeekComplete?.invoke(targetPos)
                                tryAwaitRelease()
                                isTappedBriefly = false
                            }
                        }
                    )
                }
                .pointerInput(displaySafeDuration) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val widthPx = size.width.toFloat()
                            if (widthPx > 0f) {
                                isDragging = true
                                dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                                lastHapticFraction = dragFraction
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val widthPx = size.width.toFloat()
                            if (widthPx > 0f) {
                                val newFrac = (change.position.x / widthPx).coerceIn(0f, 1f)
                                dragFraction = newFrac
                                if (abs(newFrac - lastHapticFraction) > 0.015f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    lastHapticFraction = newFrac
                                }
                            }
                        },
                        onDragEnd = {
                            val seekTarget = (dragFraction * displaySafeDuration).toLong()
                            currentPosition = seekTarget
                            onSeek(seekTarget)
                            onSeekComplete?.invoke(seekTarget)
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()

            // 1. Draw Responsive 3-Layer Track
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeightDp)
                    .testTag("music_seek_bar_canvas")
            ) {
                val trackHeightPx = size.height
                val cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

                // Layer A: Base Inactive Track
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, trackHeightPx),
                    cornerRadius = cornerRadius
                )

                // Layer B: Buffered Track (Network streaming progress)
                if (bufferedFraction > 0f) {
                    val bufferedWidth = size.width * bufferedFraction
                    drawRoundRect(
                        color = bufferedColor,
                        topLeft = Offset(0f, 0f),
                        size = Size(bufferedWidth, trackHeightPx),
                        cornerRadius = cornerRadius
                    )
                }

                // Layer C: Active Played Track (Vibrant Gradient)
                if (displayFraction > 0f) {
                    val activeWidth = size.width * displayFraction
                    drawRoundRect(
                        brush = activeBrush,
                        topLeft = Offset(0f, 0f),
                        size = Size(activeWidth, trackHeightPx),
                        cornerRadius = cornerRadius
                    )
                }
            }

            // 2. Interactive Tactile Glow Thumb with Center Core
            val thumbRadiusPx = with(density) { (thumbDiameter / 2).toPx() }
            val thumbOffsetXPx = (displayFraction * totalWidthPx) - thumbRadiusPx
            val clampedThumbOffsetXPx = thumbOffsetXPx.coerceIn(0f, max(0f, totalWidthPx - (thumbRadiusPx * 2f)))
            val thumbOffsetXDp = with(density) { clampedThumbOffsetXPx.toDp() }

            Box(
                modifier = Modifier
                    .offset(x = thumbOffsetXDp)
                    .size(thumbDiameter)
                    .shadow(
                        elevation = if (isInteracting) 8.dp else 4.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = primaryColor.copy(alpha = 0.5f),
                        spotColor = primaryColor
                    )
                    .background(primaryColor, CircleShape)
                    .testTag("music_seek_bar_thumb"),
                contentAlignment = Alignment.Center
            ) {
                // Inner white accent core on hover/drag for precision feel
                if (isInteracting) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }
        }

        // Dual High-Contrast Time Labels (Elapsed & Remaining / Total duration)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elapsed Time
            Text(
                text = formatMusicDuration(displayPositionMs),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                ),
                textAlign = TextAlign.Start,
                modifier = Modifier.testTag("music_time_elapsed")
            )

            // Total or Remaining Countdown Time (Clickable to toggle)
            val remainingMs = max(0L, safeDuration - displayPositionMs)
            val displayRightText = if (showRemainingTime && safeDuration > 0L) {
                "-${formatMusicDuration(remainingMs)}"
            } else {
                formatMusicDuration(safeDuration)
            }

            Text(
                text = displayRightText,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                ),
                textAlign = TextAlign.End,
                modifier = Modifier
                    .testTag("music_time_remaining")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showRemainingTime = !showRemainingTime
                    }
            )
        }
    }
}

/**
 * Formats milliseconds into clean M:SS or H:MM:SS duration string.
 */
fun formatMusicDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
