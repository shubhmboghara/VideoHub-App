package com.videhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

@Composable
fun PlaylistThumbnail(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 48.dp,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null
) {
    val modelToUse: Any? = when {
        !thumbnailUrl.isNullOrBlank() && (thumbnailUrl.startsWith("http://") || thumbnailUrl.startsWith("https://")) -> thumbnailUrl
        !thumbnailUrl.isNullOrBlank() && File(thumbnailUrl).exists() -> File(thumbnailUrl)
        else -> null
    }

    if (modelToUse != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(modelToUse)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
