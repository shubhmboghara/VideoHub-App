package com.videhub.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.max

/**
 * Custom modern video player seek bar adhering to Material Design 3 and Android UI/UX guidelines:
 * - 4dp track height with rounded caps
 * - Buffered progress secondary track
 * - Interactive thumb with spring scale animations (14dp idle to 18dp dragging)
 * - 48dp accessibility touch target
 * - Monospace, high-contrast time labels with shadow
 */
@Composable
fun VideoSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    bufferedColor: Color = Color.White.copy(alpha = 0.40f),
    inactiveColor: Color = Color.White.copy(alpha = 0.25f),
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    showTimeLabels: Boolean = false
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var isTappedBriefly by remember { mutableStateOf(false) }

    val safeDuration = remember(durationMs) { max(1L, durationMs) }
    val currentFraction = remember(positionMs, safeDuration) {
        (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }
    val bufferedFraction = remember(bufferedPositionMs, safeDuration) {
        (bufferedPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }

    val displayFraction = if (isDragging) dragFraction else currentFraction
    val displayTimeMs = (displayFraction * safeDuration).toLong()

    val isThumbActive = isDragging || isTappedBriefly
    val thumbDiameter: Dp by animateDpAsState(
        targetValue = if (isThumbActive) 18.dp else 14.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "thumb_diameter_anim"
    )

    val density = LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Video seek bar, position ${formatVideoDuration(displayTimeMs)} of ${formatVideoDuration(durationMs)}"
            }
    ) {
        // Touch target container (48dp height minimum for accessibility)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("video_seek_bar_touch_area")
                .pointerInput(safeDuration) {
                    detectTapGestures(
                        onPress = { offset ->
                            val widthPx = size.width.toFloat()
                            if (widthPx > 0f) {
                                val targetFrac = (offset.x / widthPx).coerceIn(0f, 1f)
                                isTappedBriefly = true
                                onSeek((targetFrac * safeDuration).toLong())
                                tryAwaitRelease()
                                isTappedBriefly = false
                            }
                        }
                    )
                }
                .pointerInput(safeDuration) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val widthPx = size.width.toFloat()
                            if (widthPx > 0f) {
                                isDragging = true
                                dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val widthPx = size.width.toFloat()
                            if (widthPx > 0f) {
                                dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd = {
                            val seekTarget = (dragFraction * safeDuration).toLong()
                            onSeek(seekTarget)
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

            // 1. Draw 4dp Track: Inactive, Buffered, Active
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .testTag("video_seek_bar_canvas")
            ) {
                val trackHeight = size.height
                val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

                // Inactive base track (0% to 100%)
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, trackHeight),
                    cornerRadius = cornerRadius
                )

                // Buffered secondary track (0% to bufferedFraction)
                if (bufferedFraction > 0f) {
                    val bufferedWidth = size.width * bufferedFraction
                    drawRoundRect(
                        color = bufferedColor,
                        topLeft = Offset(0f, 0f),
                        size = Size(bufferedWidth, trackHeight),
                        cornerRadius = cornerRadius
                    )
                }

                // Active played track (0% to displayFraction)
                if (displayFraction > 0f) {
                    val activeWidth = size.width * displayFraction
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(0f, 0f),
                        size = Size(activeWidth, trackHeight),
                        cornerRadius = cornerRadius
                    )
                }
            }

            // 2. Interactive Thumb with Drop Shadow and Scale Animation
            val thumbRadiusPx = with(density) { (thumbDiameter / 2).toPx() }
            val thumbOffsetXPx = (displayFraction * totalWidthPx) - thumbRadiusPx
            val clampedThumbOffsetXPx = thumbOffsetXPx.coerceIn(0f, max(0f, totalWidthPx - (thumbRadiusPx * 2f)))
            val thumbOffsetXDp = with(density) { clampedThumbOffsetXPx.toDp() }

            Box(
                modifier = Modifier
                    .offset(x = thumbOffsetXDp)
                    .size(thumbDiameter)
                    .shadow(
                        elevation = 4.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.5f),
                        spotColor = Color.Black.copy(alpha = 0.7f)
                    )
                    .background(thumbColor, CircleShape)
                    .testTag("video_seek_bar_thumb")
            )
        }

        // Optional time labels row if requested directly by component
        if (showTimeLabels) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatVideoDuration(displayTimeMs),
                    style = VideoTimeLabelStyle,
                    textAlign = TextAlign.Start
                )
                Text(
                    text = formatVideoDuration(durationMs),
                    style = VideoTimeLabelStyle,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

val VideoTimeLabelStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    color = Color.White.copy(alpha = 0.90f),
    shadow = Shadow(
        color = Color.Black.copy(alpha = 0.80f),
        offset = Offset(0f, 2f),
        blurRadius = 4f
    )
)

fun formatVideoDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
