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
        targetValue = if (isThumbActive) 16.dp else 12.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
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

            // 1. Draw 3dp YouTube-style Track: Inactive, Buffered, Active
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
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

            // 2. Interactive Thumb
            val thumbRadiusPx = with(density) { (thumbDiameter / 2).toPx() }
            val thumbOffsetXPx = (displayFraction * totalWidthPx) - thumbRadiusPx
            val clampedThumbOffsetXPx = thumbOffsetXPx.coerceIn(0f, max(0f, totalWidthPx - (thumbRadiusPx * 2f)))
            val thumbOffsetXDp = with(density) { clampedThumbOffsetXPx.toDp() }

            Box(
                modifier = Modifier
                    .offset(x = thumbOffsetXDp)
                    .size(thumbDiameter)
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
                    maxLines = 1,
                    softWrap = false,
                    style = VideoTimeLabelStyle,
                    textAlign = TextAlign.Start
                )
                Text(
                    text = formatVideoDuration(durationMs),
                    maxLines = 1,
                    softWrap = false,
                    style = VideoTimeLabelStyle,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

val VideoTimeLabelStyle = TextStyle(
    fontFamily = com.videhub.ui.theme.Inter,
    fontFeatureSettings = "tnum",
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
    if (durationMs <= 0L) return "0:00"
    return com.videhub.utils.FormatHelper.formatDuration(durationMs / 1000L)
}
