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

import androidx.compose.ui.input.nestedscroll.nestedScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    sharedViewModel: com.videhub.viewmodel.MainViewModel,
    onVideoClick: (String, String, String) -> Unit,
    onChannelClick: (String) -> Unit,
    onSearchClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf<List<com.videhub.data.entity.ChannelEntity>>(sharedViewModel.subscribedChannelsCache) }
    var isInitialLoadingChannels by remember { mutableStateOf(sharedViewModel.isInitialSubLoadingCache) }
    var selectedChannelId by remember { mutableStateOf<String?>(sharedViewModel.subscriptionsSelectedChannelCache) }
    val cacheKey = "${selectedChannelId}"
    val selectedChannel = remember(channels, selectedChannelId) { channels.find { it.channelId == selectedChannelId } }
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

    // Auto-heal any channel with missing or video-thumbnail avatars
    LaunchedEffect(channels) {
        if (channels.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                for (channel in channels) {
                    if (channel.thumbnailUrl.isNullOrBlank() || 
                        channel.thumbnailUrl == "none" || 
                        com.videhub.utils.AvatarHelper.isVideoThumbnail(channel.thumbnailUrl)) {
                        val realAvatar = com.videhub.utils.AvatarHelper.resolveRealChannelAvatar(channel.channelId)
                        if (!realAvatar.isNullOrBlank() && !com.videhub.utils.AvatarHelper.isVideoThumbnail(realAvatar)) {
                            db.channelDao().insert(channel.copy(thumbnailUrl = realAvatar))
                        }
                    }
                }
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
                    val realAvatar = try { info.avatars?.firstOrNull()?.url } catch (_: Exception) { null }
                    if (!realAvatar.isNullOrBlank()) {
                        val db = AppDatabase.getDatabase(context)
                        val existing = db.channelDao().getById(chId)
                        if (existing != null && (existing.thumbnailUrl.isNullOrBlank() || existing.thumbnailUrl == "none" || existing.thumbnailUrl?.contains("vi/") == true || existing.thumbnailUrl?.contains("hqdefault") == true || existing.thumbnailUrl?.contains("mqdefault") == true)) {
                            db.channelDao().insert(existing.copy(thumbnailUrl = realAvatar))
                        }
                    }
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

    var showInterestsSheet by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        com.videhub.ui.components.VideoHubTopBar(
            showFilter = true,
            onSearch = onSearchClick,
            onFilter = { showInterestsSheet = true },
            scrollBehavior = scrollBehavior
        )

        Box(modifier = Modifier.weight(1f)) {
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
            // ── Channel strip (Only when 2 or more channels) ────────────────
            if (channels.size >= 2) {
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
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        thickness = 1.dp
                    )
                }
            }
            
            // ── Channel heading ─────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedChannel?.name ?: if (channels.size == 1) channels.first().name else "Latest Videos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                enabled = selectedChannel != null || channels.size == 1,
                                role = androidx.compose.ui.semantics.Role.Button
                            ) {
                                val targetId = selectedChannel?.channelId ?: channels.firstOrNull()?.channelId
                                targetId?.let { onChannelClick(it) }
                            }
                    )
                }
            }

            // ── Video list ───────────────────────────────────────────────────
            if (isLoadingVideos) {
                items(4) { com.videhub.ui.components.VideoRowShimmer() }
            } else {
                val isSingleChannel = channels.size == 1
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
                    
                    val videoAvatar = try { video.uploaderAvatars?.firstOrNull()?.url } catch (e: Exception) { null }
                    val candidateAvatar = videoAvatar ?: selectedChannel?.thumbnailUrl
                    val safeAvatar = if (isSingleChannel) null else candidateAvatar?.takeIf { !com.videhub.utils.AvatarHelper.isVideoThumbnail(it) }
                    val displayUploaderName = if (isSingleChannel) "" else (video.uploaderName ?: "")

                    com.videhub.ui.components.VideoRowItem(
                        modifier = Modifier.animateItem(),
                        videoUrl = video.url ?: "",
                        title = video.name ?: "",
                        uploaderName = displayUploaderName,
                        thumbnailUrl = video.thumbnails?.firstOrNull()?.url,
                        duration = video.duration,
                        viewCount = video.viewCount ?: 0,
                        uploadDate = video.textualUploadDate ?: "",
                        uploaderUrl = video.uploaderUrl,
                        uploaderAvatarUrl = safeAvatar,
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

    if (showInterestsSheet) {
        com.videhub.ui.components.InterestsBottomSheet(
            onDismiss = { showInterestsSheet = false }
        )
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
            val hasValidAvatar = !channel.thumbnailUrl.isNullOrBlank() && 
                                 channel.thumbnailUrl != "none" && 
                                 !com.videhub.utils.AvatarHelper.isVideoThumbnail(channel.thumbnailUrl)

            if (hasValidAvatar) {
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

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun SubscriptionsScreenPreview() {
    com.videhub.ui.theme.AppTheme {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            com.videhub.ui.components.VideoHubTopBar(
                showFilter = true,
                onSearch = {},
                onFilter = {}
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tech Channel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
            }
            com.videhub.ui.components.VideoRowItem(
                videoUrl = "",
                title = "Building Jetpack Compose Apps in 2026",
                uploaderName = "",
                thumbnailUrl = null,
                duration = 645,
                viewCount = 94,
                uploadDate = "1 day ago",
                uploaderAvatarUrl = null,
                onClick = {}
            )
        }
    }
}