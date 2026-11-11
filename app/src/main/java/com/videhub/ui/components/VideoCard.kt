package com.videhub.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@Composable
fun PulsatingLiveBadge(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_pulse_alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_pulse_scale"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFCC0000)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Sensors,
                contentDescription = "Live",
                tint = Color.White,
                modifier = Modifier
                    .size(12.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                        this.scaleX = scale
                        this.scaleY = scale
                    }
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
}

@Composable
fun LiveAvatar(
    avatarUrl: String?,
    channelName: String,
    isLive: Boolean,
    size: Dp = 38.dp,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isLarge = size.value >= 50f
    val ringBorderWidth = if (isLarge) 2.5.dp else 2.dp
    val innerGapWidth = if (isLarge) 2.dp else 1.5.dp

    val liveGradient = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFFE50914),
                Color(0xFFFF0055),
                Color(0xFFFF0033),
                Color(0xFFE50914)
            )
        )
    }

    val liveAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isLive) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 250),
        label = "LiveAvatarAlpha"
    )

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                role = Role.Button,
                enabled = true,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Main Avatar Circle Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (liveAlpha > 0.01f) {
                        Modifier
                            .background(liveGradient, CircleShape)
                            .padding(ringBorderWidth)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(innerGapWidth)
                    } else Modifier
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "$channelName avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = channelName.firstOrNull()?.uppercase() ?: "?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = if (isLarge) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Integrated "LIVE" Pill Overlay aligned at the bottom edge inside avatar container
        if (liveAlpha > 0.01f) {
            val pillHeight = if (isLarge) 13.dp else 10.dp
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = Color(0xFFE50914),
                shadowElevation = 1.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(pillHeight)
                    .graphicsLayer { alpha = liveAlpha }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(
                        horizontal = if (isLarge) 5.dp else 3.5.dp,
                        vertical = 0.dp
                    )
                ) {
                    Text(
                        text = "LIVE",
                        color = Color.White.copy(alpha = liveAlpha),
                        fontSize = if (isLarge) 8.5.sp else 6.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        lineHeight = if (isLarge) 8.5.sp else 6.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun VideoCardShimmer() {
    val brush = shimmerBrush()

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // 16:9 full-width thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(brush)
        )

        // Metadata row matching VideoCard & RelatedVideoCard (36dp avatar + 2 title lines + meta line + 3-dot icon)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(brush)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(width = 6.dp, height = 18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(brush)
            )
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
    val isLive = com.videhub.utils.ChannelLiveStatusManager.isStreamCurrentlyLive(item)

    val scope = rememberCoroutineScope()
    val channelUrl = item.uploaderUrl ?: ""

    val handleLiveAvatarClick: () -> Unit = {
        if (channelUrl.isNotBlank()) {
            scope.launch {
                try {
                    val streams = withContext(Dispatchers.IO) {
                        com.videhub.extractor.ExtractorHelper.getChannelLiveStreams(channelUrl)
                    }
                    val verifiedTop = withContext(Dispatchers.IO) {
                        com.videhub.utils.ChannelLiveStatusManager.verifyAndSetChannelLiveStreams(
                            channelUrl = channelUrl,
                            secondaryChannelUrl = null,
                            channelName = channel,
                            streams = streams
                        )
                    }
                    if (verifiedTop != null) {
                        val targetUrl = verifiedTop.url ?: url
                        val targetTitle = verifiedTop.name ?: title
                        val targetThumb = verifiedTop.thumbnails?.firstOrNull()?.url ?: thumb
                        if (targetUrl.isNotBlank()) {
                            onClick(targetUrl, targetTitle, targetThumb)
                        } else {
                            onChannelClick(channelUrl)
                        }
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
    val context = androidx.compose.ui.platform.LocalContext.current
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
            if (isLive) {
                PulsatingLiveBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )
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

            var watchPercent by remember(url) { mutableFloatStateOf(0f) }
            LaunchedEffect(url) {
                if (url.isNotBlank()) {
                    val p = withContext(Dispatchers.IO) {
                        try {
                            val db = com.videhub.data.AppDatabase.getDatabase(context)
                            db.watchProgressDao().get(url)
                        } catch (_: Exception) { null }
                    }
                    if (p != null && p.durationMs > 0) {
                        watchPercent = (p.positionMs.toFloat() / p.durationMs.toFloat()).coerceIn(0f, 1f)
                    }
                }
            }
            if (watchPercent > 0.02f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomStart)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(watchPercent)
                            .fillMaxHeight()
                            .background(Color(0xFFE50914))
                    )
                }
            }
        }

        // Info Row: Avatar + (Title & Metadata) + 3-Dots Menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F0F))
                .padding(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Check if creator is currently live (either video is live or channel has an ongoing live stream)
            val liveKeys by com.videhub.utils.ChannelLiveStatusManager.liveChannelKeys.collectAsState()
            val isChannelBroadcastingLive = remember(liveKeys, channelUrl, channel) {
                com.videhub.utils.ChannelLiveStatusManager.isChannelLive(channelUrl, channel)
            }
            val isAvatarLive = isLive || isChannelBroadcastingLive

            // Proactively check/verify channel live status in background if channelUrl is known
            val scope = rememberCoroutineScope()
            LaunchedEffect(channelUrl, channel) {
                if (channelUrl.isNotBlank()) {
                    com.videhub.utils.ChannelLiveStatusManager.checkChannelLiveStatus(channelUrl, channel)
                }
            }

            // Channel Avatar with pixel-perfect LiveAvatar component
            LiveAvatar(
                avatarUrl = channelAvatarUrl,
                channelName = channel,
                isLive = isAvatarLive,
                size = 38.dp,
                onClick = {
                    if (isAvatarLive) {
                        // Check if registered top live stream exists
                        val topLive = com.videhub.utils.ChannelLiveStatusManager.getTopLiveStream(channelUrl, channel)
                        if (topLive != null && topLive.videoUrl.isNotBlank()) {
                            onClick(topLive.videoUrl, topLive.title, topLive.thumbnailUrl)
                        } else if (onLiveAvatarClick != null) {
                            onLiveAvatarClick()
                        } else if (isLive && url.isNotBlank()) {
                            onClick(url, title, thumbnailUrl)
                        } else if (channelUrl.isNotBlank()) {
                            scope.launch {
                                val fetchedTop = com.videhub.utils.ChannelLiveStatusManager.checkChannelLiveStatus(channelUrl, channel)
                                if (fetchedTop != null && fetchedTop.videoUrl.isNotBlank()) {
                                    onClick(fetchedTop.videoUrl, fetchedTop.title, fetchedTop.thumbnailUrl)
                                } else {
                                    onChannelClick(channelUrl)
                                }
                            }
                        } else if (channelUrl.isNotBlank()) {
                            onChannelClick(channelUrl)
                        }
                    } else if (channelUrl.isNotBlank()) {
                        onChannelClick(channelUrl)
                    }
                }
            )

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
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.5.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    IconButton(
                        onClick = { expanded = true },
                        modifier = Modifier
                            .size(30.dp)
                            .semantics {
                                contentDescription = "More options for $title"
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

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
                        fontSize = 12.5.sp,
                        color = Color(0xFFAAAAAA)
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
                    val liveKeys by com.videhub.utils.ChannelLiveStatusManager.liveChannelKeys.collectAsState()
                    val isChannelBroadcastingLive = remember(liveKeys, uploaderUrl, uploaderName) {
                        com.videhub.utils.ChannelLiveStatusManager.isChannelLive(uploaderUrl, uploaderName)
                    }
                    val scope = rememberCoroutineScope()
                    LaunchedEffect(uploaderUrl, uploaderName) {
                        if (!uploaderUrl.isNullOrBlank() && !isChannelBroadcastingLive) {
                            com.videhub.utils.ChannelLiveStatusManager.checkChannelLiveStatus(uploaderUrl, uploaderName)
                        }
                    }

                    LiveAvatar(
                        avatarUrl = uploaderAvatarUrl,
                        channelName = uploaderName,
                        isLive = isChannelBroadcastingLive,
                        size = 26.dp,
                        onClick = {
                            if (isChannelBroadcastingLive) {
                                val topLive = com.videhub.utils.ChannelLiveStatusManager.getTopLiveStream(uploaderUrl, uploaderName)
                                if (topLive != null && topLive.videoUrl.isNotBlank()) {
                                    onClick() // or open live
                                } else if (!uploaderUrl.isNullOrBlank()) {
                                    onChannelClick(uploaderUrl)
                                }
                            } else if (!uploaderUrl.isNullOrBlank()) {
                                onChannelClick(uploaderUrl)
                            }
                        }
                    )
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Left Column: 140dp 16:9 thumbnail with 12dp corners
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(brush)
        )

        // Middle Column: Title lines + avatar & metadata line matching VideoRowItem
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(15.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(15.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
        }

        // Right Column: 3-dot icon placeholder
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(width = 6.dp, height = 16.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(brush)
        )
    }
}

