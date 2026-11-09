package com.videhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.videhub.ui.components.VideoCard
import com.videhub.ui.components.VideoCardShimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfoItem

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
    var channels by remember { mutableStateOf<List<ChannelEntity>>(sharedViewModel.subscribedChannelsCache) }
    var isInitialLoadingChannels by remember { mutableStateOf(sharedViewModel.isInitialSubLoadingCache) }
    var selectedChannelId by remember { mutableStateOf<String?>(sharedViewModel.subscriptionsSelectedChannelCache) }

    val selectedChannel = remember(channels, selectedChannelId) {
        channels.find { it.channelId == selectedChannelId }
            ?: channels.firstOrNull()
    }

    val activeChannelId = selectedChannel?.channelId ?: ""

    val latestVideos by remember(activeChannelId, sharedViewModel.subscriptionsVideosCacheMap[activeChannelId]) {
        derivedStateOf {
            (sharedViewModel.subscriptionsVideosCacheMap[activeChannelId] ?: emptyList()).filterIsInstance<StreamInfoItem>()
        }
    }

    var isLoadingVideos by remember { mutableStateOf(activeChannelId.isNotBlank() && sharedViewModel.subscriptionsVideosCacheMap[activeChannelId] == null) }
    var isPaginating by remember { mutableStateOf(false) }
    var pagingSource by remember { mutableStateOf<com.videhub.extractor.ListExtractorPagingSource?>(sharedViewModel.subscriptionsPagingSourceMap[activeChannelId]) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = sharedViewModel.subscriptionsScrollStateMap[activeChannelId]?.first ?: 0,
        initialFirstVisibleItemScrollOffset = sharedViewModel.subscriptionsScrollStateMap[activeChannelId]?.second ?: 0
    )

    var isRefreshing by remember { mutableStateOf(false) }
    var videoRetryTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeChannelId) {
        if (activeChannelId.isNotBlank()) {
            sharedViewModel.subscriptionsSelectedChannelCache = activeChannelId
            pagingSource = sharedViewModel.subscriptionsPagingSourceMap[activeChannelId]
            val savedScroll = sharedViewModel.subscriptionsScrollStateMap[activeChannelId]
            if (savedScroll != null) {
                listState.scrollToItem(savedScroll.first, savedScroll.second)
            } else {
                listState.scrollToItem(0)
            }
        }
    }

    DisposableEffect(activeChannelId) {
        onDispose {
            if (activeChannelId.isNotBlank()) {
                sharedViewModel.subscriptionsScrollStateMap[activeChannelId] = Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            }
        }
    }

    fun refresh() {
        if (isRefreshing || isLoadingVideos || isPaginating || activeChannelId.isBlank()) return
        sharedViewModel.subscriptionsVideosCacheMap[activeChannelId] = emptyList()
        isLoadingVideos = true
        isRefreshing = true
        videoRetryTrigger++
    }

    // Observe real subscribed channels from Room AppDatabase dynamically,
    // and purge any dummy screenshot channels that were previously inserted
    LaunchedEffect(Unit) {
        val db = AppDatabase.getDatabase(context)
        withContext(Dispatchers.IO) {
            val staleDummyIds = listOf(
                "https://www.youtube.com/@Adda247Skills",
                "https://www.youtube.com/@nomadshubham",
                "https://www.youtube.com/@SarthakGoswami",
                "https://www.youtube.com/@RomRomJi",
                "https://www.youtube.com/@namandeshmukh",
                "https://www.youtube.com/@nikhilsharma"
            )
            val staleDummyThumbs = setOf(
                "https://yt3.googleusercontent.com/ytc/AIdro_k6W08fA4gJv-P8310qGgV2DkWqH4gB4qB8=s176-c-k-c0x00ffffff-no-rj",
                "https://yt3.googleusercontent.com/ytc/AIdro_n4Rj9q_8vF9K1Yk-Ww2kQ7W0w-1234=s176-c-k-c0x00ffffff-no-rj",
                "https://yt3.googleusercontent.com/ytc/AIdro_mQ1234=s176-c-k-c0x00ffffff-no-rj",
                "https://yt3.googleusercontent.com/ytc/AIdro_RomRomJi=s176-c-k-c0x00ffffff-no-rj",
                "https://yt3.googleusercontent.com/ytc/AIdro_Naman=s176-c-k-c0x00ffffff-no-rj",
                "https://yt3.googleusercontent.com/ytc/AIdro_Nikhil=s176-c-k-c0x00ffffff-no-rj"
            )
            // Delete any channel row that has one of the fake dummy URLs from previous build
            for (dummyId in staleDummyIds) {
                db.channelDao().deleteById(dummyId)
            }
        }

        db.channelDao().getAll().collectLatest { rawList ->
            val staleDummyIds = setOf(
                "https://www.youtube.com/@Adda247Skills",
                "https://www.youtube.com/@nomadshubham",
                "https://www.youtube.com/@SarthakGoswami",
                "https://www.youtube.com/@RomRomJi",
                "https://www.youtube.com/@namandeshmukh",
                "https://www.youtube.com/@nikhilsharma"
            )
            val list = rawList.filterNot { it.channelId in staleDummyIds }

            channels = list
            sharedViewModel.subscribedChannelsCache = list
            isInitialLoadingChannels = false
            sharedViewModel.isInitialSubLoadingCache = false

            // Automatically select the first channel in subscriptions if none selected or previous selection no longer exists
            if (list.isNotEmpty()) {
                if (selectedChannelId == null || list.none { it.channelId == selectedChannelId }) {
                    selectedChannelId = list.first().channelId
                }
            } else {
                selectedChannelId = null
            }
        }
    }

    // Check authentic live status for subscribed channels and cache real avatars
    LaunchedEffect(channels) {
        if (channels.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                for (channel in channels) {
                    com.videhub.utils.ChannelLiveStatusManager.checkChannelLiveStatus(channel.channelId, channel.name)
                    if (channel.thumbnailUrl.isNullOrBlank() ||
                        channel.thumbnailUrl == "none" ||
                        com.videhub.utils.AvatarHelper.isVideoThumbnail(channel.thumbnailUrl)
                    ) {
                        val realAvatar = com.videhub.utils.AvatarHelper.resolveRealChannelAvatar(channel.channelId)
                        if (!realAvatar.isNullOrBlank() && !com.videhub.utils.AvatarHelper.isVideoThumbnail(realAvatar)) {
                            db.channelDao().insert(channel.copy(thumbnailUrl = realAvatar))
                        }
                    }
                }
            }
        }
    }

    // Load selected channel videos, verify live status, and refresh channel's real avatar from ChannelInfo
    LaunchedEffect(activeChannelId, videoRetryTrigger) {
        if (activeChannelId.isBlank()) {
            isLoadingVideos = false
            return@LaunchedEffect
        }

        if (sharedViewModel.subscriptionsVideosCacheMap[activeChannelId] != null && !isRefreshing) {
            isLoadingVideos = false
            return@LaunchedEffect
        }

        if (!isRefreshing) {
            isLoadingVideos = true
        }

        scope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    val url = when {
                        activeChannelId.startsWith("http") -> activeChannelId
                        activeChannelId.startsWith("@") -> "https://www.youtube.com/$activeChannelId"
                        else -> "https://www.youtube.com/channel/$activeChannelId"
                    }
                    val (info, liveStreams) = kotlinx.coroutines.coroutineScope {
                        val infoDef = async { ExtractorHelper.getChannelInfo(url) }
                        val liveDef = async { ExtractorHelper.getChannelLiveStreams(url) }
                        Pair(infoDef.await(), liveDef.await())
                    }

                    // Update channel's real avatar in DB if ChannelInfo has a fresher valid avatar
                    val freshAvatar = com.videhub.utils.AvatarHelper.getBestAvatarUrl(info.avatars)
                    if (!freshAvatar.isNullOrBlank() && selectedChannel != null && selectedChannel.thumbnailUrl != freshAvatar) {
                        val db = AppDatabase.getDatabase(context)
                        db.channelDao().insert(selectedChannel.copy(thumbnailUrl = freshAvatar))
                    }

                    val verifiedActiveLive = com.videhub.utils.ChannelLiveStatusManager.verifyAndSetChannelLiveStreams(
                        channelUrl = activeChannelId,
                        secondaryChannelUrl = info.url,
                        channelName = info.name ?: selectedChannel?.name,
                        streams = liveStreams
                    )

                    val source = ExtractorHelper.getChannelPagingSource(info)
                    pagingSource = source
                    sharedViewModel.subscriptionsPagingSourceMap[activeChannelId] = source
                    val rawVids = source?.loadInitial()?.filterIsInstance<StreamInfoItem>() ?: emptyList()

                    val combinedVids = buildList {
                        if (verifiedActiveLive != null) {
                            add(verifiedActiveLive)
                        }
                        for (v in rawVids) {
                            if (!com.videhub.utils.ChannelLiveStatusManager.isEndedOrPastLiveStream(v) &&
                                (verifiedActiveLive == null || v.url != verifiedActiveLive.url)
                            ) {
                                add(v)
                            }
                        }
                    }

                    // Ensure video uploader avatar, name, and URL match the selected channel
                    val channelAvatar = freshAvatar ?: selectedChannel?.thumbnailUrl
                    val fallbackName = info.name?.ifBlank { null } ?: selectedChannel?.name ?: ""
                    val fallbackUrl = info.url.ifBlank { activeChannelId }

                    combinedVids.forEach { video ->
                        if (video.uploaderName.isNullOrBlank() && fallbackName.isNotBlank()) {
                            video.uploaderName = fallbackName
                        }
                        if (video.uploaderUrl.isNullOrBlank() && fallbackUrl.isNotBlank()) {
                            video.uploaderUrl = fallbackUrl
                        }
                        if (!channelAvatar.isNullOrBlank() && !com.videhub.utils.AvatarHelper.isVideoThumbnail(channelAvatar)) {
                            if (video.uploaderAvatars.isNullOrEmpty()) {
                                video.uploaderAvatars = listOf(
                                    org.schabi.newpipe.extractor.Image(
                                        channelAvatar, 50, 50,
                                        org.schabi.newpipe.extractor.Image.ResolutionLevel.UNKNOWN
                                    )
                                )
                            }
                        }
                    }

                    combinedVids
                }
                sharedViewModel.subscriptionsVideosCacheMap[activeChannelId] = items
            } catch (_: Exception) {}
            isLoadingVideos = false
            isRefreshing = false
        }
    }

    fun loadNextPage() {
        if (isPaginating || pagingSource?.hasMore != true) return
        scope.launch {
            isPaginating = true
            try {
                val items = pagingSource?.loadNextPage()?.filterIsInstance<StreamInfoItem>() ?: emptyList()
                items.forEach { video ->
                    com.videhub.utils.ChannelLiveStatusManager.processStreamItem(video)
                }
                val current = sharedViewModel.subscriptionsVideosCacheMap[activeChannelId] ?: emptyList()
                sharedViewModel.subscriptionsVideosCacheMap[activeChannelId] = current + items
            } catch (_: Exception) {}
            isPaginating = false
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        // Minimal top bar with clean dark theme
        com.videhub.ui.components.VideoHubTopBar(
            showFilter = false,
            onSearch = onSearchClick,
            scrollBehavior = scrollBehavior
        )

        Box(modifier = Modifier.weight(1f)) {
            if (isInitialLoadingChannels) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        if (channels.isNotEmpty()) {
                            // ── 1. Top Subscribed Channel Avatar Strip (Exact YouTube replica) ────
                            item(key = "subscriptions_channel_carousel") {
                                val liveKeys by com.videhub.utils.ChannelLiveStatusManager.liveChannelKeys.collectAsState()

                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp, bottom = 6.dp),
                                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    itemsIndexed(
                                        channels,
                                        key = { index, channel -> "${channel.channelId}_$index" }
                                    ) { _, channel ->
                                        val isLive = remember(liveKeys, channel.channelId, channel.name) {
                                            com.videhub.utils.ChannelLiveStatusManager.isChannelLive(channel.channelId, channel.name)
                                        }
                                        val isSelected = activeChannelId == channel.channelId

                                        YouTubeChannelAvatarItem(
                                            channel = channel,
                                            isSelected = isSelected,
                                            isLive = isLive,
                                            onClick = {
                                                selectedChannelId = channel.channelId
                                            }
                                        )
                                    }
                                }
                            }

                            // ── 2. "Visit channel" Pill Button Row (Right Aligned as shown in screenshot) ──
                            item(key = "visit_channel_button_row") {
                                val targetChannel = selectedChannel ?: channels.firstOrNull()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 12.dp, top = 8.dp, bottom = 14.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color(0xFF272727), // YouTube dark grey pill button
                                        modifier = Modifier
                                            .height(38.dp)
                                            .clickable {
                                                targetChannel?.let { onChannelClick(it.channelId) }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                painter = androidx.compose.ui.res.painterResource(id = com.videhub.R.drawable.ic_account_circle_outlined),
                                                contentDescription = "Visit channel",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "Visit channel",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── 3. Full-Width Edge-to-Edge 16:9 Video Cards ────────────────
                        if (isLoadingVideos) {
                            items(4, key = { "sub_shimmer_$it" }) {
                                VideoCardShimmer()
                            }
                        } else if (channels.isEmpty()) {
                            item(key = "no_subscribed_channels") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 32.dp, vertical = 64.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subscriptions,
                                        contentDescription = null,
                                        tint = Color(0xFF717171),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = "Don't miss new videos",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Subscribe to your favorite channels to see their latest videos and live streams here.",
                                        color = Color(0xFFAAAAAA),
                                        fontSize = 13.5.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(Modifier.height(24.dp))
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color.White,
                                        modifier = Modifier
                                            .height(40.dp)
                                            .clickable { onSearchClick() }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 20.dp)
                                        ) {
                                            Text(
                                                text = "Discover channels",
                                                color = Color.Black,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (latestVideos.isEmpty()) {
                            item(key = "empty_subscriptions_videos") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No videos available for this channel.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFAAAAAA)
                                    )
                                }
                            }
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

                                VideoCard(
                                    item = video,
                                    onClick = onVideoClick,
                                    onChannelClick = onChannelClick,
                                    channelAvatarUrl = selectedChannel?.thumbnailUrl?.takeIf {
                                        it.isNotBlank() && it != "none" && !com.videhub.utils.AvatarHelper.isVideoThumbnail(it)
                                    }
                                )
                            }
                        }

                        if (isPaginating) {
                            item(key = "sub_paginating_indicator") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color(0xFFAAAAAA)
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

/**
 * Exact replica of the YouTube Channel Avatar Item shown in user's screenshot:
 * - 60dp circular avatar
 * - When selected: crisp white outer ring with dark gap
 * - When live: red circular dot indicator at bottom right
 * - Clean truncated labels for any dynamic channel name
 * - Selected label is Bold White (#FFFFFF), Unselected is #E0E0E0
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun YouTubeChannelAvatarItem(
    channel: ChannelEntity,
    isSelected: Boolean,
    isLive: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val avatarSize = 60.dp
    val hasValidAvatar = !channel.thumbnailUrl.isNullOrBlank() &&
            channel.thumbnailUrl != "none" &&
            !com.videhub.utils.AvatarHelper.isVideoThumbnail(channel.thumbnailUrl)

    var resolvedAvatarUrl by remember(channel.thumbnailUrl) {
        mutableStateOf(if (hasValidAvatar) channel.thumbnailUrl else null)
    }

    // If avatar URL is missing or fails to load, resolve authentic channel avatar in background
    LaunchedEffect(channel.channelId, resolvedAvatarUrl) {
        if (resolvedAvatarUrl.isNullOrBlank()) {
            val realAvatar = withContext(Dispatchers.IO) {
                com.videhub.utils.AvatarHelper.resolveRealChannelAvatar(channel.channelId)
            }
            if (!realAvatar.isNullOrBlank() && !com.videhub.utils.AvatarHelper.isVideoThumbnail(realAvatar)) {
                resolvedAvatarUrl = realAvatar
                withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(context).channelDao().insert(channel.copy(thumbnailUrl = realAvatar))
                }
            }
        }
    }

    val cleanName = remember(channel.name) {
        channel.name.trim()
    }

    val dynamicFontSize = remember(cleanName) {
        when {
            cleanName.length <= 9 -> 12.sp
            cleanName.length <= 12 -> 11.sp
            else -> 11.sp
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(avatarSize),
            contentAlignment = Alignment.Center
        ) {
            // Avatar Circle with Ring (shown when selected or when live, matching reference screenshot)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isSelected || isLive) {
                            Modifier
                                .border(2.dp, Color.White, CircleShape)
                                .padding(2.dp)
                                .background(Color(0xFF0F0F0F), CircleShape)
                                .padding(1.5.dp)
                        } else {
                            Modifier.padding(2.dp)
                        }
                    )
                    .clip(CircleShape)
                    .background(Color(0xFF272727)),
                contentAlignment = Alignment.Center
            ) {
                if (!resolvedAvatarUrl.isNullOrBlank()) {
                    coil.compose.SubcomposeAsyncImage(
                        model = resolvedAvatarUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = channel.name.firstOrNull()?.uppercase() ?: "?",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        },
                        error = {
                            LaunchedEffect(Unit) {
                                resolvedAvatarUrl = null
                            }
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = channel.name.firstOrNull()?.uppercase() ?: "?",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    )
                } else {
                    Text(
                        text = channel.name.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            // Red live dot indicator at bottom right if channel is currently live
            if (isLive) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-2).dp, y = (-2).dp)
                        .background(Color(0xFF0F0F0F), CircleShape)
                        .padding(2.dp)
                        .background(Color(0xFFFF0000), CircleShape)
                )
            }
        }

        Spacer(Modifier.height(7.dp))

        Text(
            text = cleanName,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else Color(0xFFD0D0D0)
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}