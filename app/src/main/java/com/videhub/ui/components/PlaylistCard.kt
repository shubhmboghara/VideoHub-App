package com.videhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun PlaylistCard(
    title: String,
    subtitle: String? = null,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    downloadProgress: Float? = null, // null if not downloading, 0..1f if downloading
    state: String = "DONE", // PREPARING, DOWNLOADING, DONE, FAILED
    currentTrackIndex: Int = 1,
    totalCount: Int = 0,
    itemProgress: Int = 0
) {
    val isPreparing = state.equals("PREPARING", ignoreCase = true) || (state.equals("DOWNLOADING", ignoreCase = true) && totalCount <= 0)
    val isDownloading = !isPreparing && (state.equals("DOWNLOADING", ignoreCase = true) || downloadProgress != null)
    val isFailed = state.equals("FAILED", ignoreCase = true)

    val overallPercent = ((downloadProgress ?: 0f) * 100).toInt().coerceIn(0, 100)

    val resolvedSubtitle = when {
        isPreparing -> "Preparing…"
        isDownloading -> {
            val currIdx = if (currentTrackIndex > 0) currentTrackIndex else 1
            val tot = if (totalCount > 0) totalCount else 1
            "Downloading $currIdx of $tot • $overallPercent%"
        }
        isFailed -> "Download failed"
        else -> subtitle ?: if (totalCount > 0) "$totalCount videos" else "Preparing…"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .clip(RoundedCornerShape(16.dp))
            .testTag("playlist_card_${title.hashCode()}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top: Stacked 16:9 Playlist Cover
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 6.dp, end = 6.dp)
            ) {
                // Background stacked deck layer for 3D playlist effect
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(8.dp)
                        .align(Alignment.TopCenter)
                        .offset(y = (-4).dp)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                )

                // Main Cover Thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (!thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = "Playlist Cover",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Placeholder icon when thumbnail missing or still loading
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    // Badge at top end for status
                    if (isDownloading || isPreparing) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(11.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (isPreparing) "…" else "$overallPercent%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (isFailed) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Failed",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Linear progress bar across the bottom of the cover while downloading/preparing
                    if (isDownloading || isPreparing) {
                        val activeProgress = if (isPreparing) 0f else (downloadProgress ?: 0f)
                        LinearProgressIndicator(
                            progress = { activeProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .align(Alignment.BottomCenter),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
            
            // Bottom: Text Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = resolvedSubtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isDownloading) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ── Previews for PlaylistCard: preparing, downloading, done, failed ───────────

@Preview(name = "Playlist Card - Preparing", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PlaylistCardPreparingPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(modifier = Modifier.padding(16.dp).width(220.dp)) {
            PlaylistCard(
                title = "Lo-Fi Study Beats",
                thumbnailUrl = null,
                onClick = {},
                state = "PREPARING",
                totalCount = 0
            )
        }
    }
}

@Preview(name = "Playlist Card - Downloading", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PlaylistCardDownloadingPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(modifier = Modifier.padding(16.dp).width(220.dp)) {
            PlaylistCard(
                title = "Cyberpunk Synthwave",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                onClick = {},
                downloadProgress = 0.63f,
                state = "DOWNLOADING",
                currentTrackIndex = 1,
                totalCount = 3,
                itemProgress = 63
            )
        }
    }
}

@Preview(name = "Playlist Card - Done", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PlaylistCardDonePreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(modifier = Modifier.padding(16.dp).width(220.dp)) {
            PlaylistCard(
                title = "Chill Acoustic Mix",
                subtitle = "3 videos • 245.0 MB",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                onClick = {},
                state = "DONE",
                totalCount = 3
            )
        }
    }
}

@Preview(name = "Playlist Card - Failed", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PlaylistCardFailedPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(modifier = Modifier.padding(16.dp).width(220.dp)) {
            PlaylistCard(
                title = "Top Hits 2026",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                onClick = {},
                state = "FAILED",
                totalCount = 5
            )
        }
    }
}
