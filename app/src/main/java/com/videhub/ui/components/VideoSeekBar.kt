package com.videhub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Custom modern video player seek bar with YouTube-style Pull-Up Fine Scrubbing:
 * - 3dp track height (expands to 5dp while scrubbing) with rounded caps
 * - Buffered progress secondary track
 * - Interactive thumb with spring scale animations
 * - Pull-up while dragging switches to precision fine-tune scrubbing (0.22x sensitivity) with live preview pill
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
    var dragStartPositionMs by remember { mutableLongStateOf(0L) }
    var cumulativeDragY by remember { mutableFloatStateOf(0f) }
    var isFineScrubbing by remember { mutableStateOf(false) }
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
    val trackHeightDp: Dp by animateDpAsState(
        targetValue = if (isThumbActive) 5.dp else 3.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "track_height_anim"
    )

    val density = LocalDensity.current
    val fineScrubPullThresholdPx = with(density) { 28.dp.toPx() }

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
                                cumulativeDragY = 0f
                                isFineScrubbing = false
                                dragStartPositionMs = positionMs
                                dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val widthPx = size.width.toFloat()
                            if (widthPx > 0f) {
                                cumulativeDragY += dragAmount.y
                                val pulledUp = cumulativeDragY < -fineScrubPullThresholdPx
                                isFineScrubbing = pulledUp
                                if (pulledUp) {
                                    // Fine-tune scrubbing: 0.22x horizontal sensitivity
                                    val deltaFraction = (dragAmount.x / widthPx) * 0.22f
                                    dragFraction = (dragFraction + deltaFraction).coerceIn(0f, 1f)
                                } else {
                                    dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                                }
                            }
                        },
                        onDragEnd = {
                            val seekTarget = (dragFraction * safeDuration).toLong()
                            onSeek(seekTarget)
                            isDragging = false
                            isFineScrubbing = false
                            cumulativeDragY = 0f
                        },
                        onDragCancel = {
                            isDragging = false
                            isFineScrubbing = false
                            cumulativeDragY = 0f
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()

            // Floating Scrubbing Preview Pill above thumb
            if (isDragging && totalWidthPx > 0f) {
                val pillHalfWidthPx = with(density) { 64.dp.toPx() }
                val rawX = displayFraction * totalWidthPx
                val clampedCenterPx = rawX.coerceIn(pillHalfWidthPx, max(pillHalfWidthPx, totalWidthPx - pillHalfWidthPx))
                val deltaSec = ((displayTimeMs - dragStartPositionMs) / 1000L).toInt()
                val deltaPrefix = if (deltaSec >= 0) "+${deltaSec}s" else "${deltaSec}s"

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (clampedCenterPx - pillHalfWidthPx).roundToInt(),
                                y = with(density) { (-46).dp.roundToPx() }
                            )
                        }
                        .width(128.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier.border(
                            0.5.dp,
                            Color.White.copy(alpha = 0.18f),
                            RoundedCornerShape(12.dp)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${formatVideoDuration(displayTimeMs)} ($deltaPrefix)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isFineScrubbing) "Fine-tuning" else "Pull up for fine seek",
                                color = if (isFineScrubbing) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.68f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 1. Draw YouTube-style Track: Inactive, Buffered, Active
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeightDp)
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
