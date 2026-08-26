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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .aspectRatio(16f / 9f)
                    .background(brush)
            )
            
            Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
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
                 item.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM

    VideoCard(
        title = title,
        channelName = channel,
        thumbnailUrl = thumb,
        url = url,
        duration = duration,
        viewCount = views,
        channelAvatarUrl = channelAvatarUrl ?: try { item.uploaderAvatars?.firstOrNull()?.url } catch (e: Exception) { null },
        uploadDate = (item as? org.schabi.newpipe.extractor.stream.StreamInfoItem)?.uploadDate?.toString() ?: item.textualUploadDate ?: "unknown",
        channelUrl = item.uploaderUrl ?: "",
        isLive = isLive,
        onClick = onClick,
        onChannelClick = onChannelClick,
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
    modifier: Modifier = Modifier,
) {
    val thumb = thumbnailUrl
    val channel = channelName
    val views = viewCount
    val haptic = LocalHapticFeedback.current

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
            .padding(bottom = 8.dp)
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .aspectRatio(16f / 9f)
        ) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Duration badge / LIVE badge
            if (isLive || (duration <= 0L && uploadDate.contains("Live", ignoreCase = true))) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.error
                ) {
                    Text(
                        text = "LIVE",
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else if (duration > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = "%d:%02d".format(duration / 60, duration % 60),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Info row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar
            val avatarUrl = channelAvatarUrl
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(
                        role = Role.Button,
                        enabled = channelUrl.isNotBlank()
                    ) { onChannelClick(channelUrl) }
                    .semantics {
                        contentDescription = "View channel $channel"
                    },
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
            // Text info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = com.videhub.utils.FormatHelper.cleanDisplayTitle(title),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                val viewsText = if (views > 0) "${com.videhub.utils.FormatHelper.formatCount(views)} views" else ""
                val formattedDate = com.videhub.utils.FormatHelper.formatDate(if (uploadDate.isNotBlank()) uploadDate else "unknown")
                val separator = if (views > 0 && formattedDate.isNotBlank()) " · " else ""
                val metaText = "$viewsText$separator$formattedDate"
                
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Start,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(role = Role.Button) { onChannelClick(channelUrl) }
                    )
                    if (metaText.isNotEmpty()) {
                        Text(
                            text = " · $metaText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            textAlign = TextAlign.Start,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            // 3-dot menu
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(48.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                var expanded by remember { mutableStateOf(false) }
                
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.semantics {
                        contentDescription = "More options for $title"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (expanded) {
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
        // Left Column: Thumbnail
        Box(
            modifier = Modifier
                .width(150.dp)
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.small)
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
            
            // Duration badge
            if (duration > 0) {
                val mins = duration / 60
                val secs = duration % 60
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = "%d:%02d".format(mins, secs),
                        color = MaterialTheme.colorScheme.onSurface,
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
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = iconBadge,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Box(
                    modifier = Modifier.size(48.dp),
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
                                Icons.Default.MoreVert,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(4.dp))
            
            // Avatar and Metadata
            Row(
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
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                }
                
                val viewsText = if (viewCount > 0) "${com.videhub.utils.FormatHelper.formatCount(viewCount)} views" else "No views"
                val formattedDate = com.videhub.utils.FormatHelper.formatDate(uploadDate)
                val separator = if (viewCount > 0 && formattedDate.isNotEmpty()) " • " else ""
                val metaText = "$uploaderName • $viewsText$separator$formattedDate"
                
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(
                        role = Role.Button,
                        enabled = uploaderUrl != null
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

