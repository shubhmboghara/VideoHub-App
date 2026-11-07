package com.videhub.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@Composable
fun VideoCardShimmer() {
    val brush = shimmerBrush()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(brush)
            )
            
            Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(18.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(14.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(brush)
                    )
                }
            }
        }
    }
}

@Composable
fun VideoCard(
    item: InfoItem,
    onClick: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
    onChannelClick: (String) -> Unit = {},
    channelAvatarUrl: String? = null
) {
    if (item !is StreamInfoItem) return
    val thumb = item.thumbnails?.firstOrNull()?.url ?: ""
    val title = item.name ?: "Unknown"
    val channel = item.uploaderName ?: ""
    val url = item.url ?: return
    val duration = item.duration
    val views = item.viewCount
    val isLive = item.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
                 item.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM ||
                 (duration <= 0L && (item.textualUploadDate?.contains("Live", ignoreCase = true) == true || title.contains("LIVE", ignoreCase = true)))

    val scope = rememberCoroutineScope()
    val channelUrl = item.uploaderUrl ?: ""

    val handleLiveAvatarClick: () -> Unit = {
        if (channelUrl.isNotBlank()) {
            scope.launch {
                try {
                    val streams = withContext(Dispatchers.IO) {
                        com.videhub.extractor.ExtractorHelper.getChannelLiveStreams(channelUrl)
                    }
                    if (streams.isNotEmpty()) {
                        val activeStreams = streams.filter { s ->
                            s.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
                            s.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM ||
                            s.duration <= 0L
                        }.ifEmpty { streams }
                        val topStream = activeStreams.maxByOrNull { it.viewCount }
                        val targetUrl = topStream?.url ?: url
                        val targetTitle = topStream?.name ?: title
                        val targetThumb = topStream?.thumbnails?.firstOrNull()?.url ?: thumb
                        if (targetUrl.isNotBlank()) {
                            onClick(targetUrl, targetTitle, targetThumb)
                        } else {
                            onChannelClick(channelUrl)
                        }
                    } else if (isLive && url.isNotBlank()) {
                        onClick(url, title, thumb)
                    } else {
                        onChannelClick(channelUrl)
                    }
                } catch (e: Exception) {
                    if (isLive && url.isNotBlank()) {
                        onClick(url, title, thumb)
                    } else {
                        onChannelClick(channelUrl)
                    }
                }
            }
        } else if (isLive && url.isNotBlank()) {
            onClick(url, title, thumb)
        }
    }

    VideoCard(
        title = title,
        channelName = channel,
        thumbnailUrl = thumb,
        url = url,
        duration = duration,
        viewCount = views,
        channelAvatarUrl = channelAvatarUrl ?: try { item.uploaderAvatars?.firstOrNull()?.url } catch (e: Exception) { null },
        uploadDate = (item as? StreamInfoItem)?.uploadDate?.toString() ?: item.textualUploadDate ?: "unknown",
        channelUrl = channelUrl,
        isLive = isLive,
        onClick = onClick,
        onChannelClick = onChannelClick,
        onLiveAvatarClick = if (isLive || channelUrl.isNotBlank()) handleLiveAvatarClick else null,
        modifier = modifier
    )
}

@Composable
fun VideoCard(
    title: String,
    channelName: String,
    thumbnailUrl: String,
    url: String,
    duration: Long,
    viewCount: Long,
    channelAvatarUrl: String?,
    uploadDate: String = "",
    channelUrl: String = "",
    isLive: Boolean = false,
    onClick: (String, String, String) -> Unit,
    onChannelClick: (String) -> Unit = {},
    onLiveAvatarClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val thumb = thumbnailUrl
    val channel = channelName
    val views = viewCount
    val haptic = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }

    if (expanded && url.isNotBlank()) {
        VideoActionBottomSheet(
            videoUrl = url,
            title = title,
            thumbnailUrl = thumbnailUrl,
            channelName = channelName,
            viewCount = viewCount,
            uploadDate = uploadDate,
            onDismiss = { expanded = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, by $channel"
            }
            .clickable(
                enabled = url.isNotBlank(),
                role = Role.Button
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick(url, title, thumb)
            }
            .padding(bottom = 16.dp)
    ) {
        // Thumbnail - Full Width Edge-to-Edge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (thumb.isNotBlank()) {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Duration badge / LIVE badge at BottomEnd
            if (isLive || (duration <= 0L && uploadDate.contains("Live", ignoreCase = true))) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFCC0000)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (duration > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = com.videhub.utils.FormatHelper.formatDuration(duration),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Info Row: Avatar + (Title & Metadata) + 3-Dots Menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Channel Avatar with glowing red ring & LIVE badge if isLive or onLiveAvatarClick != null
            val avatarUrl = channelAvatarUrl
            val isAvatarLive = isLive
            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .clickable(
                        role = Role.Button,
                        enabled = channelUrl.isNotBlank() || isAvatarLive || onLiveAvatarClick != null
                    ) {
                        if (isAvatarLive && onLiveAvatarClick != null) {
                            onLiveAvatarClick()
                        } else if (isLive && url.isNotBlank()) {
                            onClick(url, title, thumbnailUrl)
                        } else if (channelUrl.isNotBlank()) {
                            onChannelClick(channelUrl)
                        }
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .then(
                            if (isAvatarLive) {
                                Modifier
                                    .border(
                                        width = 2.5.dp,
                                        brush = Brush.horizontalGradient(
                                            listOf(Color(0xFFE50914), Color(0xFFFF2E63))
                                        ),
                                        shape = CircleShape
                                    )
                                    .padding(2.5.dp)
                            } else Modifier
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = channel.firstOrNull()?.uppercase() ?: "?",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (isAvatarLive) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFCC0000),
                        modifier = Modifier.offset(y = 5.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Title and Metadata column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Top
            ) {
                // Line 1: Title & Horizontal 3-dot Menu (...)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = com.videhub.utils.FormatHelper.cleanDisplayTitle(title),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    IconButton(
                        onClick = { expanded = true },
                        modifier = Modifier
                            .size(32.dp)
                            .semantics {
                                contentDescription = "More options for $title"
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(3.dp))

                // Line 2: Channel Name  ▷ views/watching  uploadDate
                val viewsFormatted = if (views > 0) {
                    if (isLive) "▷${com.videhub.utils.FormatHelper.formatCount(views)} watching"
                    else "▷${com.videhub.utils.FormatHelper.formatCount(views)}"
                } else if (isLive) {
                    "▷watching"
                } else ""

                val formattedDate = if (!isLive && uploadDate.isNotBlank()) com.videhub.utils.FormatHelper.formatDate(uploadDate) else ""
                val metaLine = buildString {
                    append(channel)
                    if (viewsFormatted.isNotBlank()) {
                        append("   ")
                        append(viewsFormatted)
                    }
                    if (formattedDate.isNotBlank()) {
                        append("   ")
                        append(formattedDate)
                    }
                }

                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(
                        role = Role.Button,
                        enabled = channelUrl.isNotBlank()
                    ) { onChannelClick(channelUrl) }
                )
            }
        }
    }
}

// ── Video row item (For History, Downloads, Subscriptions) ───────────────────
@Composable
fun VideoRowItem(
    videoUrl: String,
    title: String,
    uploaderName: String,
    thumbnailUrl: String?,
    duration: Long = -1,
    viewCount: Long = -1,
    uploadDate: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    uploaderUrl: String? = null,
    uploaderAvatarUrl: String? = null,
    onChannelClick: (String) -> Unit = {},
    iconBadge: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    var expanded by remember { mutableStateOf(false) }

    if (expanded && videoUrl.isNotEmpty()) {
        VideoActionBottomSheet(
            videoUrl = videoUrl,
            title = title,
            thumbnailUrl = thumbnailUrl ?: "",
            channelName = uploaderName,
            viewCount = viewCount,
            uploadDate = uploadDate,
            onDismiss = { expanded = false }
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$title by $uploaderName"
            }
            .clickable(
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple()
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Left Column: Thumbnail (16:9, ~140dp wide, 12dp corners)
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).size(24.dp)
                )
            }
            
            // Duration badge: black 80% background, white labelSmall, 4dp radius, 4dp inset from the bottom-right corner
            if (duration > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = com.videhub.utils.FormatHelper.formatDuration(duration),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            if (iconBadge != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = iconBadge,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(14.dp)
                    )
                }
            }
        }

        // Right Column: Title, Menu, Avatar, Metadata
        Column(modifier = Modifier.weight(1f)) {
            // Title and Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = com.videhub.utils.FormatHelper.cleanDisplayTitle(title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Box(
                    modifier = Modifier.wrapContentSize(),
                    contentAlignment = Alignment.TopEnd
                ) {
                    if (trailingIcon != null) {
                        trailingIcon()
                    } else if (videoUrl.isNotEmpty()) {
                        IconButton(
                            onClick = { expanded = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(48.dp)
                                .semantics {
                                    contentDescription = "More options for $title"
                                }
                        ) {
                            Icon(
                                Icons.Default.MoreHoriz,
                                contentDescription = "More options for $title",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        
            Spacer(Modifier.height(4.dp))
        
            // Avatar and Metadata on ONE line with ellipsis
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uploaderAvatarUrl != null) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(
                                role = Role.Button,
                                enabled = uploaderUrl != null
                            ) { uploaderUrl?.let { onChannelClick(it) } }
                            .semantics {
                                contentDescription = "View channel $uploaderName"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = uploaderAvatarUrl,
                            contentDescription = "Channel avatar for $uploaderName",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                }
            
                val viewsText = if (viewCount > 0) com.videhub.utils.FormatHelper.formatCount(viewCount) else null
                val formattedDate = if (uploadDate.isNotBlank()) com.videhub.utils.FormatHelper.formatDate(uploadDate).takeIf { it.isNotBlank() } else null
                val metaText = listOfNotNull(
                    viewsText,
                    formattedDate
                ).joinToString(" · ")
            
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            role = Role.Button,
                            enabled = uploaderUrl != null && uploaderName.isNotBlank()
                        ) { uploaderUrl?.let { onChannelClick(it) } }
                )
            }
        }
}
}

@Composable
fun AudioRowItem(
    title: String,
    uploaderName: String,
    thumbnailUrl: String?,
    duration: Long = -1,
    fileSizeStr: String = "",
    viewCount: Long = -1,
    uploadDate: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    iconBadge: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$title by $uploaderName"
            }
            .clickable(
                role = Role.Button,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art with music note overlay
            Box(modifier = Modifier.size(56.dp)) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(thumbnailUrl)
                        .placeholder(com.videhub.R.drawable.ic_music_placeholder)
                        .error(com.videhub.R.drawable.ic_music_placeholder)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
                // Music note badge bottom-left
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(2.dp)
                        .size(16.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title + Artist + Size
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = com.videhub.utils.FormatHelper.cleanDisplayTitle(title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(uploaderName.ifBlank { "Unknown Artist" })
                        if (fileSizeStr.isNotBlank()) {
                            append(" • ")
                            append(fileSizeStr)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // More options button
            if (trailingIcon != null) {
                trailingIcon()
            }
        }
    }
}

@Composable
fun VideoRowShimmer() {
    val brush = shimmerBrush()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(150.dp)
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.medium)
                .background(brush)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(12.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(8.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(brush)
            )
        }
    }
}

