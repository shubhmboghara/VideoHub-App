package com.videhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val context = LocalContext.current
    var isError by androidx.compose.runtime.remember(thumbnailUrl) { androidx.compose.runtime.mutableStateOf(false) }

    val modelToUse: Any? = androidx.compose.runtime.remember(thumbnailUrl) {
        if (thumbnailUrl.isNullOrBlank()) null
        else {
            val clean = thumbnailUrl.trim()
            when {
                clean.startsWith("file://") -> {
                    val path = clean.removePrefix("file://")
                    val file = File(path)
                    if (file.exists() && file.length() > 0) file else android.net.Uri.parse(clean)
                }
                clean.startsWith("content://") -> android.net.Uri.parse(clean)
                clean.startsWith("http://") || clean.startsWith("https://") -> clean
                clean.startsWith("/") -> {
                    val file = File(clean)
                    if (file.exists() && file.length() > 0) file else null
                }
                else -> {
                    val file = File(clean)
                    if (file.exists() && file.length() > 0) file else clean
                }
            }
        }
    }

    if (modelToUse != null && !isError) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(modelToUse)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            onError = { isError = true },
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
