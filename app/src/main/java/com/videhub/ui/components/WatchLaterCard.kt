package com.videhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.videhub.data.entity.DownloadedPlaylistEntity
import com.videhub.ui.screens.formatMB

/**
 * Watch Later Download Card for the Downloads Screen.
 * Displays immediate cover, progress bar across bottom of cover while downloading,
 * and accurate state information ("Downloading 1 of 3 • 63%" / "3 videos • 245 MB").
 */
@Composable
fun WatchLaterCard(
    playlist: DownloadedPlaylistEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCount = playlist?.totalCount ?: 0
    val state = playlist?.state ?: "DONE"
    val isPreparing = state.equals("PREPARING", ignoreCase = true) || (state.equals("DOWNLOADING", ignoreCase = true) && totalCount <= 0)
    val isDownloading = !isPreparing && state.equals("DOWNLOADING", ignoreCase = true)
    val isFailed = state.equals("FAILED", ignoreCase = true)

    val currentIdx = (playlist?.currentVideoIndex ?: 1).coerceAtLeast(1)
    val completedCount = playlist?.completedCount ?: 0
    val itemProgress = playlist?.currentVideoProgress ?: 0

    // Calculate overall average progress across all videos in the playlist
    val overallProgress = remember(playlist, totalCount, completedCount, itemProgress) {
        if (totalCount <= 0) 0
        else {
            val dbProg = playlist?.progress ?: 0
            if (dbProg > 0) dbProg.coerceIn(0, 100)
            else (((completedCount * 100) + itemProgress) / totalCount).coerceIn(0, 100)
        }
    }

    val subtitleText = when {
        isPreparing -> "Preparing…"
        isDownloading -> "Downloading $currentIdx of $totalCount • $overallProgress%"
        isFailed -> playlist?.errorMessage ?: "Download failed"
        totalCount > 0 -> {
            val bytes = if ((playlist?.totalBytes ?: 0L) > 0L) playlist!!.totalBytes else (playlist?.downloadedBytes ?: 0L)
            if (bytes > 0L) "$totalCount videos • ${formatMB(bytes)}" else "$totalCount videos"
        }
        else -> "No saved videos"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .clip(RoundedCornerShape(16.dp))
            .testTag("watch_later_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Cover Box with Stacked Deck effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 6.dp, end = 6.dp)
            ) {
                // Stacked deck accent
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(8.dp)
                        .align(Alignment.TopCenter)
                        .offset(y = (-4).dp)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                )

                // 16:9 Cover Thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (!playlist?.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = playlist!!.thumbnailUrl,
                            contentDescription = "Watch Later Cover",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = "Watch Later",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    // Top Status Badge
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
                                    text = if (isPreparing) "…" else "$overallProgress%",
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

                    // Linear progress bar along the bottom of the cover while downloading
                    if (isDownloading || isPreparing) {
                        val activeProgress = if (isPreparing) 0f else (overallProgress / 100f).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { activeProgress },
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

            // Text Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = "Watch Later",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = subtitleText,
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

// ── Previews for WatchLaterCard ─────────────────────────────────────────────

@Preview(name = "Watch Later Card - Preparing", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun WatchLaterCardPreparingPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(modifier = Modifier.padding(16.dp).width(220.dp)) {
            WatchLaterCard(
                playlist = DownloadedPlaylistEntity(
                    playlistId = "WL",
                    title = "Watch Later",
                    thumbnailUrl = null,
                    totalCount = 0,
                    state = "PREPARING"
                ),
                onClick = {}
            )
        }
    }
}

@Preview(name = "Watch Later Card - Downloading", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun WatchLaterCardDownloadingPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(modifier = Modifier.padding(16.dp).width(220.dp)) {
            WatchLaterCard(
                playlist = DownloadedPlaylistEntity(
                    playlistId = "WL",
                    title = "Watch Later",
                    thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                    totalCount = 3,
                    completedCount = 1,
                    state = "DOWNLOADING",
                    currentVideoIndex = 1,
                    currentVideoProgress = 63
                ),
                onClick = {}
            )
        }
    }
}

@Preview(name = "Watch Later Card - Done", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun WatchLaterCardDonePreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(modifier = Modifier.padding(16.dp).width(220.dp)) {
            WatchLaterCard(
                playlist = DownloadedPlaylistEntity(
                    playlistId = "WL",
                    title = "Watch Later",
                    thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                    totalCount = 3,
                    completedCount = 3,
                    state = "DONE",
                    totalBytes = 245 * 1024 * 1024L
                ),
                onClick = {}
            )
        }
    }
}

@Preview(name = "Watch Later Card - Failed", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun WatchLaterCardFailedPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(modifier = Modifier.padding(16.dp).width(220.dp)) {
            WatchLaterCard(
                playlist = DownloadedPlaylistEntity(
                    playlistId = "WL",
                    title = "Watch Later",
                    thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                    totalCount = 3,
                    completedCount = 0,
                    state = "FAILED",
                    errorMessage = "Sign in to download Watch Later"
                ),
                onClick = {}
            )
        }
    }
}
