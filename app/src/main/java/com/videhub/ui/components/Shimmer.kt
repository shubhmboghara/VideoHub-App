package com.videhub.ui.components

import androidx.compose.animation.core.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun shimmerBrush(): Brush {
    val baseColor = MaterialTheme.colorScheme.onSurface
    val shimmerColors = listOf(
        baseColor.copy(alpha = 0.08f),
        baseColor.copy(alpha = 0.18f),
        baseColor.copy(alpha = 0.08f)
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerWidth = 450f
    val translateAnim by transition.animateFloat(
        initialValue = -shimmerWidth,
        targetValue = 1350f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnim, y = translateAnim * 0.35f),
        end = Offset(x = translateAnim + shimmerWidth, y = (translateAnim + shimmerWidth) * 0.35f)
    )
}

@Composable
fun playerShimmerBrush(): Brush {
    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.05f),
        Color.White.copy(alpha = 0.14f),
        Color.White.copy(alpha = 0.05f)
    )
    val transition = rememberInfiniteTransition(label = "player_shimmer")
    val shimmerWidth = 500f
    val translateAnim by transition.animateFloat(
        initialValue = -shimmerWidth,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "player_shimmer_translate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnim, y = translateAnim * 0.35f),
        end = Offset(x = translateAnim + shimmerWidth, y = (translateAnim + shimmerWidth) * 0.35f)
    )
}
