package com.videhub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class SeekDirection {
    BACKWARD,
    FORWARD
}

/**
 * YouTube-style animated quick seek overlay for double-tap rewind and fast-forward gestures.
 * Features:
 * - Half-screen curved animated pill/arc ripple on the active side
 * - Pulsing triple directional chevrons/icons
 * - Dynamic cumulative duration label (10s, 20s, 30s...)
 * - Smooth auto-fadeout on completion
 */
@Composable
fun DoubleTapSeekOverlay(
    direction: SeekDirection?,
    seconds: Int,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible && direction != null,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier.fillMaxSize()
    ) {
        if (direction == null) return@AnimatedVisibility

        val isForward = direction == SeekDirection.FORWARD
        val iconAnimScale = remember { Animatable(1f) }

        LaunchedEffect(seconds, direction) {
            iconAnimScale.snapTo(0.75f)
            iconAnimScale.animateTo(
                targetValue = 1.15f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )
            iconAnimScale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (isForward) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            // Half-screen curved ripple background
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.48f)
                    .clip(
                        if (isForward) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                        else RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
                    )
                    .background(
                        Brush.horizontalGradient(
                            colors = if (isForward) {
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.22f))
                            } else {
                                listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)
                            }
                        )
                    )
                    .testTag(if (isForward) "seek_forward_overlay" else "seek_rewind_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    // Pulsing animated icon
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .scale(iconAnimScale.value)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Text label with accumulated seconds
                    Text(
                        text = if (isForward) "+$seconds seconds" else "-$seconds seconds",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}
