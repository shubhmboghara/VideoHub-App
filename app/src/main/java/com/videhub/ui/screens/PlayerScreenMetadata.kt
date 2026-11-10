package com.videhub.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo
import com.videhub.data.AppDatabase
import com.videhub.data.entity.ChannelEntity
import com.videhub.ui.components.AnimatedSubscribeButton
import com.videhub.ui.components.ClickableDescriptionText
import com.videhub.ui.components.openUrlSafely

@Composable
fun PlayerScreenMetadata(
    streamInfo: StreamInfo?,
    isLocalFile: Boolean,
    isOfflineFallback: Boolean = false,
    errorMessage: String?,
    title: String,
    channelName: String,
    channelId: String?,
    thumbnailUrl: String,
    onChannelClick: (String) -> Unit,
    context: Context,
    scope: CoroutineScope,
    db: AppDatabase,
    isSubscribedInitial: Boolean,
    onSeekToSeconds: ((Long) -> Unit)? = null
) {
    var isSubscribed by remember(isSubscribedInitial) { mutableStateOf(isSubscribedInitial) }
    var expandedDesc by remember(streamInfo?.url, title) { mutableStateOf(false) }

    AnimatedContent(
        targetState = streamInfo == null && !isLocalFile && !isOfflineFallback && errorMessage == null,
        transitionSpec = {
            fadeIn(tween(200)).togetherWith(fadeOut(tween(200)))
        },
        modifier = Modifier.fillMaxWidth(),
        label = "MetadataTransition"
    ) { showShimmer ->
        if (showShimmer) {
            com.videhub.ui.components.PlayerMetadataShimmer()
        } else {
            val rawDesc = streamInfo?.description?.content?.takeIf { it.isNotBlank() }
            val parsedDesc = remember(rawDesc) {
                if (rawDesc == null) {
                    Pair("", emptyList<String>())
                } else {
                    val cleanDesc = rawDesc
                        .replace(Regex("(?i)<br\\s*/?>"), "\n")
                        .replace(Regex("(?i)</p>"), "\n\n")
                        .replace(Regex("<[^>]*>"), "")
                        .replace("&nbsp;", " ")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                        .replace("&apos;", "'")
                        .replace("&#x27;", "'")
                        .replace("&amp;apos;", "'")
                        .trim()

                    val lines = cleanDesc.lines()
                    val cleanLines = mutableListOf<String>()
                    val hashtags = mutableListOf<String>()
                    for (line in lines) {
                        val words = line.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
                        if (words.isNotEmpty() && words.all { it.startsWith("#") }) {
                            hashtags.addAll(words)
                        } else {
                            cleanLines.add(line)
                        }
                    }
                    Pair(cleanLines.joinToString("\n").trim(), hashtags.toList())
                }
            }
            val finalDesc = parsedDesc.first
            val hashtags = parsedDesc.second
            val hasDescription = finalDesc.isNotEmpty() || hashtags.isNotEmpty()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // 1. YouTube-style Title & Metadata ("views  date  ...more")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = hasDescription) { expandedDesc = !expandedDesc }
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            lineHeight = 23.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expandedDesc) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!isLocalFile) {
                        Spacer(Modifier.height(6.dp))
                        val isLiveStream = streamInfo?.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
                                          streamInfo?.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM ||
                                          ((streamInfo?.duration ?: 0L) <= 0L && !streamInfo?.hlsUrl.isNullOrBlank())

                        if (isLiveStream) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = androidx.compose.ui.graphics.Color(0xFFE50914),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        color = androidx.compose.ui.graphics.Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                val viewCount = streamInfo?.viewCount ?: 0L
                                val viewsText = if (viewCount > 0) "${com.videhub.utils.FormatHelper.formatCount(viewCount)} watching now" else "Live Stream"
                                Text(
                                    text = viewsText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (hasDescription && !expandedDesc) {
                                    Text(
                                        text = "  ...more",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        } else {
                            val viewCount = streamInfo?.viewCount ?: 0L
                            val dateStr = com.videhub.utils.FormatHelper.formatDate(streamInfo?.textualUploadDate)
                            val metaParts = buildList {
                                if (viewCount > 0) add("${com.videhub.utils.FormatHelper.formatCount(viewCount)} views")
                                if (dateStr.isNotBlank()) add(dateStr)
                            }
                            if (metaParts.isNotEmpty() || hasDescription) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (metaParts.isNotEmpty()) {
                                        Text(
                                            text = metaParts.joinToString("  "),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (hasDescription && !expandedDesc) {
                                        Text(
                                            text = if (metaParts.isNotEmpty()) "  ...more" else "...more",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Expandable Description Box when tapped
                if (!isLocalFile && hasDescription && expandedDesc) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (finalDesc.isNotEmpty()) {
                                ClickableDescriptionText(
                                    text = finalDesc,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    linkColor = MaterialTheme.colorScheme.primary,
                                    onSeekToSeconds = onSeekToSeconds,
                                    onUrlClick = { url ->
                                        openUrlSafely(context, null, url)
                                    }
                                )
                            }
                            if (hashtags.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    hashtags.forEach { tag ->
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Show less",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .clickable { expandedDesc = false }
                            )
                        }
                    }
                }

                if (!isLocalFile) {
                    Spacer(Modifier.height(12.dp))

                    // 2. YouTube-style Channel Information Row
                    val effectiveChannelId = channelId ?: streamInfo?.uploaderUrl
                    LaunchedEffect(effectiveChannelId) {
                        if (!effectiveChannelId.isNullOrBlank()) {
                            withContext(Dispatchers.IO) {
                                isSubscribed = db.channelDao().isSubscribed(effectiveChannelId)
                            }
                        }
                    }

                    val avatarUrl = com.videhub.utils.AvatarHelper.getBestAvatarUrl(streamInfo?.uploaderAvatars)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = !effectiveChannelId.isNullOrBlank()) {
                                    effectiveChannelId?.let { onChannelClick(it) }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!avatarUrl.isNullOrBlank()) {
                                    coil.compose.AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = "Channel Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    val initial = channelName.trim().firstOrNull()?.uppercaseChar()?.toString()
                                    if (!initial.isNullOrBlank()) {
                                        Text(
                                            text = initial,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(10.dp))

                            Text(
                                text = channelName.ifBlank { streamInfo?.uploaderName ?: "Channel" },
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        AnimatedSubscribeButton(
                            isSubscribed = isSubscribed,
                            onClick = {
                                effectiveChannelId?.let { chId ->
                                    scope.launch {
                                        if (isSubscribed) {
                                            withContext(Dispatchers.IO) { db.channelDao().deleteById(chId) }
                                            isSubscribed = false
                                            Toast.makeText(context, "Unsubscribed", Toast.LENGTH_SHORT).show()
                                        } else {
                                            withContext(Dispatchers.IO) {
                                                val candidateAvatar = avatarUrl?.takeIf { !com.videhub.utils.AvatarHelper.isVideoThumbnail(it) }
                                                val finalAvatar = candidateAvatar
                                                    ?: com.videhub.utils.AvatarHelper.resolveRealChannelAvatar(chId)
                                                    ?: ""

                                                db.channelDao().insert(
                                                    ChannelEntity(
                                                        channelId = chId,
                                                        name = channelName,
                                                        thumbnailUrl = finalAvatar
                                                    )
                                                )
                                            }
                                            isSubscribed = true
                                            Toast.makeText(context, "Subscribed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

