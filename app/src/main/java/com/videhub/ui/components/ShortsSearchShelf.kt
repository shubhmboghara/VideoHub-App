package com.videhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.videhub.utils.FormatHelper
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * YouTube-style Shorts shelf in Search and Explore feeds.
 */
@Composable
fun ShortsSearchShelf(
    shorts: List<StreamInfoItem>,
    onShortClick: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
    onViewAllClick: (() -> Unit)? = null
) {
    if (shorts.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Shelf Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = "Shorts icon",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "Shorts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (onViewAllClick != null && shorts.size > 4) {
                TextButton(
                    onClick = onViewAllClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "View all",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal Carousel of Shorts cards
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = shorts,
                key = { it.url ?: it.name ?: "" }
            ) { short ->
                ShortSearchCard(
                    short = short,
                    onClick = {
                        val thumb = short.thumbnails?.firstOrNull()?.url ?: ""
                        onShortClick(short.url ?: "", short.name ?: "Short", thumb)
                    }
                )
            }
        }
    }
}

/**
 * Vertical 9:16 aspect ratio Short card for search results & explore shelves.
 */
@Composable
fun ShortSearchCard(
    short: StreamInfoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbUrl = short.thumbnails?.firstOrNull()?.url ?: ""
    val title = short.name ?: "Short"
    val viewsStr = if (short.viewCount > 0) {
        "${com.videhub.utils.FormatHelper.formatCount(short.viewCount)} views"
    } else {
        short.uploaderName ?: ""
    }

    Card(
        modifier = modifier
            .width(140.dp)
            .height(230.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Short: $title, $viewsStr"
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                role = Role.Button,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail Image
            AsyncImage(
                model = thumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient scrim at bottom for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.85f)
                            ),
                            startY = 100f
                        )
                    )
            )

            // Top-right Shorts badge
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "SHORT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Bottom Title and View Count
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )

                if (viewsStr.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = viewsStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

/**
 * Skeleton shimmer for Shorts shelf during search/loading.
 */
@Composable
fun ShortsShelfShimmer(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Shelf Header skeleton
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal row of skeleton cards
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(4) {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(230.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(brush)
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                        )
                    }
                }
            }
        }
    }
}
