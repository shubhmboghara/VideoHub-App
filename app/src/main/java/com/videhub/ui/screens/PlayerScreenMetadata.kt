package com.videhub.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.schabi.newpipe.extractor.stream.StreamInfo
import com.videhub.data.AppDatabase
import com.videhub.data.entity.ChannelEntity
import com.videhub.ui.components.AnimatedSubscribeButton

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
    isSubscribedInitial: Boolean
) {
    var isSubscribed by remember(isSubscribedInitial) { mutableStateOf(isSubscribedInitial) }

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
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
                Text(
                    text = title, 
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                if (!isLocalFile) {
                    streamInfo?.viewCount?.takeIf { it > 0 }?.let { viewCount ->
                        Text(
                            text = "${com.videhub.utils.FormatHelper.formatCount(viewCount)} views • ${com.videhub.utils.FormatHelper.formatDate(streamInfo?.textualUploadDate)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val avatarUrl = runCatching {
                            streamInfo?.uploaderAvatars?.firstOrNull()?.url
                        }.getOrNull()
                        if (avatarUrl != null) {
                            coil.compose.AsyncImage(
                                model = avatarUrl,
                                contentDescription = "Channel Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { channelId?.let { onChannelClick(it) } }
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = channelName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { channelId?.let { onChannelClick(it) } }
                                .padding(vertical = 4.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        AnimatedSubscribeButton(
                            isSubscribed = isSubscribed,
                            onClick = {
                                channelId?.let { chId ->
                                    scope.launch {
                                        if (isSubscribed) {
                                            withContext(Dispatchers.IO) { db.channelDao().deleteById(chId) }
                                            isSubscribed = false
                                            Toast.makeText(context, "Unsubscribed", Toast.LENGTH_SHORT).show()
                                        } else {
                                            withContext(Dispatchers.IO) {
                                                db.channelDao().insert(
                                                    ChannelEntity(
                                                        channelId = chId,
                                                        name = channelName,
                                                        thumbnailUrl = thumbnailUrl
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

                    Spacer(Modifier.height(16.dp))
                    
                    var expandedDesc by remember { mutableStateOf(false) }

                    val rawDesc = streamInfo?.description?.content?.takeIf { it.isNotBlank() }
                    if (rawDesc != null) {
                        val parsedDesc = androidx.compose.runtime.remember(rawDesc) {
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
                        Pair(cleanLines.joinToString("\n").trim(), hashtags)
                        }
                        val finalDesc = parsedDesc.first
                        val hashtags = parsedDesc.second

                        if (finalDesc.isNotEmpty() || hashtags.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                onClick = { expandedDesc = !expandedDesc }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Description",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    if (expandedDesc) {
                                        Text(
                                            text = finalDesc,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (hashtags.isNotEmpty()) {
                                            Spacer(Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                hashtags.forEach { tag ->
                                                    SuggestionChip(
                                                        onClick = { },
                                                        label = { Text(tag) }
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "Show less",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    } else {
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(
                                                text = finalDesc,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (finalDesc.lines().size > 2 || finalDesc.length > 80) {
                                                Text(
                                                    text = "...more",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(start = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
