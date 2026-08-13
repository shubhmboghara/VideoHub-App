package com.videhub.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.videhub.data.AppDatabase
import com.videhub.data.entity.ChannelEntity
import com.videhub.extractor.ExtractorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@Composable
fun SubscriptionsScreen(
    sharedViewModel: com.videhub.viewmodel.MainViewModel,
    onVideoClick: (String, String, String) -> Unit,
    onChannelClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf<List<com.videhub.data.entity.ChannelEntity>>(sharedViewModel.subscribedChannelsCache) }
    var isInitialLoadingChannels by remember { mutableStateOf(sharedViewModel.isInitialSubLoadingCache) }
    var selectedChannelId by remember { mutableStateOf<String?>(sharedViewModel.subscriptionsSelectedChannelCache) }
    val cacheKey = "${selectedChannelId}"
    val latestVideos by remember(cacheKey) { derivedStateOf { (sharedViewModel.subscriptionsVideosCacheMap[cacheKey] ?: emptyList()).filterIsInstance<StreamInfoItem>() } }
    
    var isLoadingVideos by remember { mutableStateOf(selectedChannelId != null && sharedViewModel.subscriptionsVideosCacheMap["${selectedChannelId}"] == null) }
    
    var isPaginating by remember { mutableStateOf(false) }
    var pagingSource by remember { mutableStateOf<com.videhub.extractor.ListExtractorPagingSource?>(sharedViewModel.subscriptionsPagingSourceMap[selectedChannelId ?: ""]) }
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = sharedViewModel.subscriptionsScrollStateMap[selectedChannelId ?: ""]?.first ?: 0,
        initialFirstVisibleItemScrollOffset = sharedViewModel.subscriptionsScrollStateMap[selectedChannelId ?: ""]?.second ?: 0
    )

    var isRefreshing by remember { mutableStateOf(false) }
    var videoRetryTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedChannelId) {
        sharedViewModel.subscriptionsSelectedChannelCache = selectedChannelId
        pagingSource = sharedViewModel.subscriptionsPagingSourceMap[selectedChannelId ?: ""]
        val savedScroll = sharedViewModel.subscriptionsScrollStateMap[selectedChannelId ?: ""]
        if (savedScroll != null) {
            listState.scrollToItem(savedScroll.first, savedScroll.second)
        } else {
            listState.scrollToItem(0)
        }
    }

    DisposableEffect(selectedChannelId) {
        onDispose {
            sharedViewModel.subscriptionsScrollStateMap[selectedChannelId ?: ""] = Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }

    fun refresh() {
        if (isRefreshing || isLoadingVideos || isPaginating || selectedChannelId == null) return
        val chId = selectedChannelId ?: return
        // "new render" - clear cache and show shimmer
        sharedViewModel.subscriptionsVideosCacheMap["${chId}"] = emptyList()
        isLoadingVideos = true
        isRefreshing = true
        videoRetryTrigger++
    }

    LaunchedEffect(Unit) {
        val db = AppDatabase.getDatabase(context)
        db.channelDao().getAll().collectLatest { list ->
            channels = list
            sharedViewModel.subscribedChannelsCache = list
            isInitialLoadingChannels = false
            sharedViewModel.isInitialSubLoadingCache = false
            // Auto-select first channel
            if (selectedChannelId == null && list.isNotEmpty()) {
                selectedChannelId = list.first().channelId
            }
        }
    }

    // Load videos when channel or filter selected
    LaunchedEffect(selectedChannelId, videoRetryTrigger) {
        val chId = selectedChannelId ?: return@LaunchedEffect
        val cKey = "${chId}"
        
        if (sharedViewModel.subscriptionsVideosCacheMap[cKey] != null && !isRefreshing) {
            isLoadingVideos = false
            return@LaunchedEffect
        }
        
        if (!isRefreshing) {
            isLoadingVideos = true
        }
        scope.launch {
            try {
                val url = when {
                    chId.startsWith("http") -> chId
                    chId.startsWith("@") -> "https://www.youtube.com/$chId"
                    else -> "https://www.youtube.com/channel/$chId"
                }
                val items = withContext(Dispatchers.IO) {
                    val info = ExtractorHelper.getChannelInfo(url)
                    val source = ExtractorHelper.getChannelPagingSource(info)
                    pagingSource = source
                    sharedViewModel.subscriptionsPagingSourceMap[chId] = source
                    source?.loadInitial() ?: emptyList()
                }
                sharedViewModel.subscriptionsVideosCacheMap[cKey] = items
            } catch (_: Exception) {}
            isLoadingVideos = false
            isRefreshing = false
        }
    }
    
    fun loadNextPage() {
        if (isPaginating || pagingSource?.hasMore != true) return
        val chId = selectedChannelId ?: return
        scope.launch {
            isPaginating = true
            try {
                val items = pagingSource?.loadNextPage() ?: emptyList()
                val current = sharedViewModel.subscriptionsVideosCacheMap["${chId}"] ?: emptyList()
                sharedViewModel.subscriptionsVideosCacheMap["${chId}"] = current + items
            } catch (e: Exception) {}
            isPaginating = false
        }
    }

    if (isInitialLoadingChannels) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (channels.isEmpty()) {
        com.videhub.ui.components.EmptyState(
            title = "No Subscriptions Yet",
            message = "Subscribe to channels from the\nplayer screen to see them here.",
            icon = Icons.Default.Subscriptions,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
            // ── Channel strip ────────────────────────────────────────────────
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(channels, key = { index, channel -> "${channel.channelId}_$index" }) { index, channel ->
                            ChannelAvatarItem(
                                modifier = Modifier.animateItem(),
                                channel = channel,
                                isSelected = selectedChannelId == channel.channelId,
                                onClick = {
                                    if (selectedChannelId != channel.channelId) {
                                        selectedChannelId = channel.channelId
                                    }
                                },
                                onLongClick = { onChannelClick(channel.channelId) }
                            )
                    }
                }
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 1.dp)
                
}
            
            // ── Latest videos header ─────────────────────────────────────────
            item {
                val selectedChannel = channels.find { it.channelId == selectedChannelId }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedChannel?.name ?: "Latest Videos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { selectedChannel?.let { onChannelClick(it.channelId) } }
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Open Channel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Video list ───────────────────────────────────────────────────
            if (isLoadingVideos) {
                items(4) { com.videhub.ui.components.VideoRowShimmer() }
            } else {
                items(
                    count = latestVideos.size,
                    key = { index -> "${latestVideos[index].url ?: index.toString()}_$index" }
                ) { index ->
                    val video = latestVideos[index]
                    
                    if (index == latestVideos.size - 1 && !isPaginating && pagingSource?.hasMore == true) {
                        LaunchedEffect(Unit) {
                            loadNextPage()
                        }
                    }
                    
                    com.videhub.ui.components.VideoRowItem(
                            modifier = Modifier.animateItem(),
                            videoUrl = video.url ?: "",
                            title = video.name ?: "",
                            uploaderName = video.uploaderName ?: "",
                            thumbnailUrl = video.thumbnails?.firstOrNull()?.url,
                            duration = video.duration,
                            viewCount = video.viewCount ?: 0,
                            uploadDate = video.textualUploadDate ?: "",
                            uploaderUrl = video.uploaderUrl,
                            uploaderAvatarUrl = try { video.uploaderAvatars?.firstOrNull()?.url } catch (e: Exception) { null },
                            onChannelClick = onChannelClick,
                            onClick = { 
                                val url = video.url ?: ""
                                if (url.isNotBlank()) {
                                    onVideoClick(url, video.name ?: "", video.thumbnails?.firstOrNull()?.url ?: "")
                            }
                            }
                        )
                    }
                }
                if (isPaginating) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        }
    }

// ── Channel avatar chip ──────────────────────────────────────────────────────
@Composable
private fun ChannelAvatarItem(modifier: Modifier = Modifier,
    channel: ChannelEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
        animationSpec = tween(200),
        label = "border"
    )
    val interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    
    val scale = 1f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(72.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .border(2.dp, borderColor, CircleShape)
                .padding(2.dp)
        ) {
            if (!channel.thumbnailUrl.isNullOrBlank() && channel.thumbnailUrl != "none") {
                AsyncImage(
                    model = channel.thumbnailUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                // Gradient fallback avatar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.outline
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channel.name.firstOrNull()?.uppercase() ?: "?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 72.dp),
            color = if (isSelected)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────
private fun formatViewCount(count: Long): String = com.videhub.utils.FormatHelper.formatCount(count)