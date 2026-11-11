package com.videhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Clean 16:9 Top Video Player ("upsider") Skeleton shown inside the video player box
 * while stream info is being extracted. Uses a sleek dark-surface shimmer without
 * fake control circles or overlapping icons.
 */
@Composable
fun VideoPlayerBoxShimmer(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val brush = playerShimmerBrush()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
        )

        if (onBack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
            )
        }
    }
}

/**
 * 1-to-1 Skeleton for PlayerScreenMetadata + PlayerScreenActions
 * matching the exact paddings, 1-line channel name, 36.dp Subscribe pill,
 * and 34.dp horizontally scrollable action chips.
 */
@Composable
fun PlayerMetadataShimmer() {
    val brush = shimmerBrush()

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Metadata section matching PlayerScreenMetadata (padding horizontal = 12.dp, vertical = 10.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // 1. Title (2 lines of 18.sp bold text)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(19.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.60f)
                    .height(19.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Views + upload date + "...more" line (12.sp)
            Box(
                modifier = Modifier
                    .width(165.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Channel Row: 34.dp Avatar + 1-line Channel Name + 36.dp Subscribe Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .width(124.dp)
                            .height(15.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
            }
        }

        // 3. Action Chips Row matching PlayerScreenActions (horizontalScroll, top = 2.dp, bottom = 10.dp, height = 34.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState(), enabled = false)
                .padding(top = 2.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(4.dp))
            listOf(74.dp, 112.dp, 98.dp, 110.dp, 112.dp).forEach { pillWidth ->
                Box(
                    modifier = Modifier
                        .width(pillWidth)
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

/**
 * 1-to-1 Skeleton for YouTube-style Playlist Screens
 * (OnlinePlaylistScreen, PlaylistDetailScreen, WatchLaterScreen, LikedVideosScreen).
 */
@Composable
fun OnlinePlaylistShimmer(
    modifier: Modifier = Modifier,
    showSortChip: Boolean = true
) {
    val brush = shimmerBrush()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 4.dp)
    ) {
        // Playlist Header Skeleton matching PlaylistHeader.kt / WatchLaterScreen.kt / PlaylistDetailScreen.kt
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // 16:9 Rounded Cover Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Left-aligned 26.sp Title bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Author row (24.dp circular avatar + "by Author")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle: "Playlist • Private • X videos"
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Row: Full-width Play all pill (weight 1f, height 42.dp) + Circular 42.dp icon buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
            }

            if (showSortChip) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .width(92.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brush)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        repeat(5) {
            VideoRowShimmer()
        }
    }
}

/**
 * 1-to-1 Skeleton for Channel Home Tab ("For you" horizontal 220.dp cards + "Videos >" header + VideoRowShimmer items).
 */
@Composable
fun ChannelHomeTabShimmer(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()

    Column(modifier = modifier.fillMaxWidth()) {
        // "For you" Section Title
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .width(96.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )

        // Horizontal Row of 220.dp "For you" cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(2) {
                Column(modifier = Modifier.width(220.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // "Videos >" Section Title
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .width(104.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )

        repeat(4) {
            VideoRowShimmer()
        }
    }
}

/**
 * 1-to-1 Skeleton for CompactPlaylistResultItem (used in Channel Playlists tab & Explore Playlists tab).
 */
@Composable
fun CompactPlaylistRowShimmer() {
    val brush = shimmerBrush()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 140dp 16:9 playlist thumbnail
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(brush)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .height(15.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(15.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
    }
}

/**
 * 1-to-1 Skeleton for ChannelResultItem in ExploreScreen Channels tab.
 */
@Composable
fun ChannelResultCardShimmer() {
    val brush = shimmerBrush()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(brush)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(brush)
        )
    }
}

/**
 * 1-to-1 Skeleton for SpotifyMusicItem / AudioRowItem in DownloadsScreen.
 */
@Composable
fun AudioRowShimmer() {
    val brush = shimmerBrush()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(15.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(width = 6.dp, height = 18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(brush)
        )
    }
}

/**
 * 1-to-1 Skeleton for PlaylistsScreen (PlaylistCard items).
 */
@Composable
fun PlaylistsListShimmer(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
            }
        }
    }
}

/**
 * 1-to-1 Skeleton for SyncedLyricsView in Music Player.
 */
@Composable
fun SyncedLyricsShimmer(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    val lineWidths = listOf(0.72f, 0.54f, 0.88f, 0.64f, 0.82f, 0.48f, 0.76f, 0.60f, 0.70f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        lineWidths.forEachIndexed { index, widthFraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(if (index == 2) 26.dp else 20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
fun ChannelAboutShimmer(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Description Section Skeleton
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }

        // More Info Section Skeleton
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .width(105.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
            repeat(4) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (it == 0) 0.65f else 0.45f)
                            .height(15.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
            }
        }
    }
}
