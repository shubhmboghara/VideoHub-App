package com.videhub.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.videhub.ui.theme.AppTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.videhub.ui.components.CompactPlaylistResultItem
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.videhub.data.AppDatabase
import com.videhub.data.entity.ChannelEntity
import com.videhub.ui.components.AnimatedSubscribeButton
import com.videhub.extractor.ExtractorHelper
import com.videhub.ui.components.VideoCard
import com.videhub.ui.components.VideoRowShimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@Composable
fun ChannelInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isLink: Boolean = false
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .then(
                if (isLink) {
                    Modifier.clickable {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(text))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else Modifier
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ChannelHeaderShimmer() {
    val brush = com.videhub.ui.components.shimmerBrush()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        // Banner (110dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(brush)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar (72dp) on the left
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(22.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(14.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(14.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(brush)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(brush)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChannelScreen(
    sharedViewModel: com.videhub.viewmodel.MainViewModel,
    channelId: String,
    onBack: () -> Unit,
    onVideoClick: (String, String, String) -> Unit,
    onPlaylistClick: (String) -> Unit = {},
    onAboutClick: () -> Unit,
    onSearchClick: () -> Unit = {}
) {
    var showChannelMenu by remember { mutableStateOf(false) }
    // Determine if we should load from cache
    val isSameChannel = sharedViewModel.currentChannelId == channelId
    var channelInfo by remember { mutableStateOf<ChannelInfo?>(if (isSameChannel) sharedViewModel.channelInfoCache else null) }
    var channelAboutInfo by remember { mutableStateOf<com.videhub.extractor.ChannelAboutInfo?>(if (isSameChannel) sharedViewModel.channelAboutInfoCache else null) }
    var channelVideos by remember { mutableStateOf<List<StreamInfoItem>>(if (isSameChannel) sharedViewModel.channelVideosCache ?: emptyList() else emptyList()) }
    var channelPlaylists by remember { mutableStateOf<List<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>>(if (isSameChannel) sharedViewModel.channelPlaylistsCache ?: emptyList() else emptyList()) }
    var channelLiveStreams by remember { mutableStateOf<List<StreamInfoItem>>(if (isSameChannel) sharedViewModel.channelLiveStreamsCache ?: emptyList() else emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Home, 1: Videos, 2: Live, 3: Playlists
    var isPlaylistsLoading by remember { mutableStateOf(false) }
    var playlistsError by remember { mutableStateOf<String?>(null) }
    var isLiveLoading by remember { mutableStateOf(false) }
    var liveError by remember { mutableStateOf<String?>(null) }
    var isHeaderLoading by remember { mutableStateOf(channelInfo == null) }
    var headerError by remember { mutableStateOf<String?>(null) }
    var isVideoListLoading by remember { mutableStateOf(channelVideos.isEmpty() && channelInfo == null) }
    var videoListError by remember { mutableStateOf<String?>(null) }
    
    var isSubscribed by remember { mutableStateOf(false) }
    var playlistDialogVideo by remember { mutableStateOf<StreamInfoItem?>(null) }
    var selectedPlaylistForMenu by remember { mutableStateOf<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem?>(null) }
    var pagingSource by remember { mutableStateOf<com.videhub.extractor.ListExtractorPagingSource?>(if (isSameChannel) sharedViewModel.channelPagingSourceCache else null) }
    var isPaginating by remember { mutableStateOf(false) }
    var headerRetryTrigger by remember { mutableIntStateOf(0) }
    var videoRetryTrigger by remember { mutableIntStateOf(0) }
    var playlistRetryTrigger by remember { mutableIntStateOf(0) }
    var liveRetryTrigger by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = if (isSameChannel) sharedViewModel.channelScrollIndexCache else 0,
        initialFirstVisibleItemScrollOffset = if (isSameChannel) sharedViewModel.channelScrollOffsetCache else 0
    )

    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    val currentChannelInfo by rememberUpdatedState(channelInfo)
    val currentChannelAboutInfo by rememberUpdatedState(channelAboutInfo)
    val currentChannelVideos by rememberUpdatedState(channelVideos)
    val currentChannelPlaylists by rememberUpdatedState(channelPlaylists)
    val currentChannelLiveStreams by rememberUpdatedState(channelLiveStreams)
    val currentPagingSource by rememberUpdatedState(pagingSource)

    DisposableEffect(channelId) {
        onDispose {
            sharedViewModel.currentChannelId = channelId
            sharedViewModel.channelInfoCache = currentChannelInfo
            sharedViewModel.channelAboutInfoCache = currentChannelAboutInfo
            sharedViewModel.channelVideosCache = currentChannelVideos
            sharedViewModel.channelPlaylistsCache = currentChannelPlaylists
            sharedViewModel.channelLiveStreamsCache = currentChannelLiveStreams
            sharedViewModel.channelPagingSourceCache = currentPagingSource
            sharedViewModel.channelScrollIndexCache = listState.firstVisibleItemIndex
            sharedViewModel.channelScrollOffsetCache = listState.firstVisibleItemScrollOffset
        }
    }

    fun refresh() {
        if (isRefreshing || isHeaderLoading || isVideoListLoading || isPlaylistsLoading || isLiveLoading || isPaginating) return
        // "new render" - clear cache and show shimmer
        channelVideos = emptyList()
        channelPlaylists = emptyList()
        channelLiveStreams = emptyList()
        channelInfo = null
        channelAboutInfo = null
        sharedViewModel.channelVideosCache = emptyList()
        sharedViewModel.channelPlaylistsCache = null
        sharedViewModel.channelLiveStreamsCache = null
        sharedViewModel.channelInfoCache = null
        sharedViewModel.channelAboutInfoCache = null
        isHeaderLoading = true
        isVideoListLoading = true
        isRefreshing = true
        headerRetryTrigger++
        videoRetryTrigger++
        playlistRetryTrigger++
        liveRetryTrigger++
    }

    LaunchedEffect(channelId, headerRetryTrigger) {
        if (isSameChannel && channelInfo != null && !isRefreshing) {
            isHeaderLoading = false
            withContext(Dispatchers.IO) {
                isSubscribed = db.channelDao().isSubscribed(channelInfo!!.url)
            }
            return@LaunchedEffect
        }
        if (!isRefreshing) isHeaderLoading = true
        headerError = null
        try {
            val actualUrl = when {
                channelId.startsWith("http") -> channelId
                channelId.startsWith("@") -> "https://www.youtube.com/$channelId"
                else -> "https://www.youtube.com/channel/$channelId"
            }
            val (info, aboutInfo) = kotlinx.coroutines.coroutineScope {
                 
                                  
                val infoDeferred = async(Dispatchers.IO) { ExtractorHelper.getChannelInfo(actualUrl) }
                val aboutDeferred = async(Dispatchers.IO) { ExtractorHelper.getChannelAboutInfo(actualUrl) }
                Pair(infoDeferred.await(), aboutDeferred.await())
            }
            withContext(Dispatchers.IO) {
                isSubscribed = db.channelDao().isSubscribed(info.url)
            }
            channelInfo = info
            channelAboutInfo = aboutInfo
            sharedViewModel.currentChannelId = channelId
            sharedViewModel.channelInfoCache = info
            sharedViewModel.channelAboutInfoCache = aboutInfo
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            headerError = e.message ?: "Failed to load channel"
            videoListError = "Cannot load videos" // Cannot load videos without channel info
            isVideoListLoading = false
        } finally {
            isHeaderLoading = false
            if (isRefreshing && headerError != null) {
                isRefreshing = false
            }
        }
    }

    var initialVideoLoadDone by remember { mutableStateOf(false) }

    LaunchedEffect(channelInfo, videoRetryTrigger) {
        if (channelInfo == null) return@LaunchedEffect
        
        if (isSameChannel && !initialVideoLoadDone && channelVideos.isNotEmpty() && !isRefreshing) {
            isVideoListLoading = false
            initialVideoLoadDone = true
            return@LaunchedEffect
        }
        initialVideoLoadDone = true
        
        if (!isRefreshing) isVideoListLoading = true
        videoListError = null
        try {
            val videos = withContext(Dispatchers.IO) {
                val source = ExtractorHelper.getChannelPagingSource(channelInfo!!)
                pagingSource = source
                source?.loadInitial()?.filterIsInstance<StreamInfoItem>() ?: emptyList()
            }
            channelVideos = videos
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            videoListError = e.message ?: "Failed to load videos"
        } finally {
            isVideoListLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(selectedTab, channelInfo, liveRetryTrigger) {
        if (selectedTab == 2 && channelInfo != null) {
            if (channelLiveStreams.isNotEmpty() && !isRefreshing) return@LaunchedEffect
            isLiveLoading = true
            liveError = null
            try {
                val streams = ExtractorHelper.getChannelLiveStreams(channelInfo!!.url)
                channelLiveStreams = streams
                sharedViewModel.channelLiveStreamsCache = streams
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                liveError = e.message ?: "Failed to load live streams"
            } finally {
                isLiveLoading = false
            }
        }
    }

    LaunchedEffect(selectedTab, channelInfo, playlistRetryTrigger) {
        if (selectedTab == 3 && channelInfo != null) {
            if (channelPlaylists.isNotEmpty() && !isRefreshing) return@LaunchedEffect
            isPlaylistsLoading = true
            playlistsError = null
            try {
                val lists = ExtractorHelper.getChannelPlaylists(channelInfo!!.url)
                channelPlaylists = lists
                sharedViewModel.channelPlaylistsCache = lists
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                playlistsError = e.message ?: "Failed to load playlists"
            } finally {
                isPlaylistsLoading = false
            }
        }
    }

    fun loadNextPage() {
        if (isPaginating || pagingSource?.hasMore != true) return
        scope.launch {
            isPaginating = true
            try {
                val items = pagingSource?.loadNextPage()?.filterIsInstance<StreamInfoItem>() ?: emptyList()
                channelVideos = channelVideos + items
            } catch (e: Exception) {}
            isPaginating = false
        }
    }

    val showTopBarTitle by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showTopBarTitle,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut()
                    ) {
                        Text(
                            text = channelInfo?.name ?: "Channel",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    Box {
                        IconButton(
                            onClick = { showChannelMenu = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showChannelMenu,
                            onDismissRequest = { showChannelMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(id = com.videhub.R.drawable.ic_share_youtube),
                                        contentDescription = "Share channel",
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showChannelMenu = false
                                    val shareUrl = channelInfo?.url?.ifBlank { null }
                                        ?: when {
                                            channelId.startsWith("http") -> channelId
                                            channelId.startsWith("@") -> "https://www.youtube.com/$channelId"
                                            else -> "https://www.youtube.com/channel/$channelId"
                                        }
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, shareUrl)
                                        type = "text/plain"
                                    }
                                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Share channel")
                                    context.startActivity(shareIntent)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "About channel"
                                    )
                                },
                                onClick = {
                                    showChannelMenu = false
                                    sharedViewModel.currentChannelId = channelId
                                    sharedViewModel.channelInfoCache = channelInfo
                                    sharedViewModel.channelAboutInfoCache = channelAboutInfo
                                    onAboutClick()
                                }
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0.dp)
            )
        }
    ) { paddingValues ->
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refresh() },
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = Triple(isHeaderLoading, isVideoListLoading, headerError != null),
                transitionSpec = {
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)).togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)))
                },
                label = "ChannelScreenTransition"
            ) { (headerLoading, videoLoading, hasHeaderError) ->
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                // Collapsing Header Item: Banner, Avatar, Name, Subscriber count, Subscribe button, Description
                item(key = "channel_collapsing_header") {
                    if (headerLoading) {
                        ChannelHeaderShimmer()
                    } else if (hasHeaderError) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = headerError ?: "Failed to load channel info",
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                headerRetryTrigger++
                            }) {
                                Text("Retry")
                            }
                        }
                    } else if (channelInfo != null) {
                        val info = channelInfo!!
                        val bannerUrl = try { info.banners.firstOrNull()?.url } catch (e: Exception) { null }
                        val avatarUrl = try { info.avatars.firstOrNull()?.url } catch (e: Exception) { null }
                        val subText = com.videhub.utils.FormatHelper.formatSubscriberCount(info.subscriberCount)
                        val streamCount = try {
                            info.javaClass.getMethod("getStreamCount").invoke(info) as? Long
                        } catch (e: Exception) { null }
                        val statsText = if (streamCount != null && streamCount > 0) {
                            val formattedVideos = "${com.videhub.utils.FormatHelper.formatCount(streamCount)} videos"
                            "$subText • $formattedVideos"
                        } else {
                            subText
                        }

                        val handle = remember(info) {
                            if (info.url.contains("@")) {
                                info.url.substringAfter("@", "").substringBefore("/").ifBlank { null }
                            } else null
                        }

                        val descriptionSummary = remember(info.description) {
                            val raw = info.description ?: ""
                            val clean = raw.lines().firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: ""
                            if (clean.length > 40) clean.take(37) + "..." else clean
                        }

                        ChannelHeaderSection(
                            bannerUrl = bannerUrl,
                            avatarUrl = avatarUrl,
                            name = info.name,
                            handle = handle,
                            isVerified = info.isVerified,
                            statsText = statsText,
                            descriptionSummary = descriptionSummary,
                            isSubscribed = isSubscribed,
                            onSubscribeClick = {
                                scope.launch(Dispatchers.IO) {
                                    val avatar = com.videhub.utils.AvatarHelper.getBestAvatarUrl(info.avatars) ?: ""
                                    if (isSubscribed) {
                                        db.channelDao().deleteById(info.url)
                                        isSubscribed = false
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Unsubscribed", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        db.channelDao().insert(
                                            ChannelEntity(
                                                channelId = info.url,
                                                name = info.name,
                                                thumbnailUrl = avatar ?: ""
                                            )
                                        )
                                        isSubscribed = true
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Subscribed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onAboutClick = {
                                sharedViewModel.currentChannelId = channelId
                                sharedViewModel.channelInfoCache = channelInfo
                                sharedViewModel.channelAboutInfoCache = channelAboutInfo
                                onAboutClick()
                            }
                        )
                    }
                }

                // Sticky tabs: Pin Home, Videos, Live, Playlists tabs under the top bar while scrolling
                stickyHeader {
                    ChannelTabsSection(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }

                // Tab 0: Home Tab (For You carousel + Videos section, no "Our members")
                if (selectedTab == 0) {
                    if (videoLoading) {
                        items(4) {
                            com.videhub.ui.components.VideoCardShimmer()
                        }
                    } else if (channelVideos.isEmpty()) {
                        item {
                            com.videhub.ui.components.EmptyState(
                                icon = Icons.Default.PlayArrow,
                                title = "No videos",
                                message = "This channel hasn't uploaded any videos yet."
                            )
                        }
                    } else {
                        // "For you" horizontal shelf
                        val forYouVideos = channelVideos.take(8)
                        if (forYouVideos.isNotEmpty()) {
                            item(key = "for_you_section") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp, bottom = 8.dp)
                                ) {
                                    Text(
                                        text = "For you",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(
                                            count = forYouVideos.size,
                                            key = { idx -> "for_you_${forYouVideos[idx].url ?: idx}" }
                                        ) { idx ->
                                            val video = forYouVideos[idx]
                                            ChannelForYouCard(
                                                video = video,
                                                onClick = {
                                                    val url = video.url ?: ""
                                                    if (url.isNotBlank()) {
                                                        onVideoClick(url, video.name ?: "", video.thumbnails?.firstOrNull()?.url ?: "")
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // "Videos >" section
                        item(key = "videos_section_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTab = 1 }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Videos",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "View all videos",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        val homeVideos = channelVideos.drop(if (channelVideos.size > 8) 4 else 0).take(10)
                        items(
                            count = homeVideos.size,
                            key = { idx -> "home_vid_${homeVideos[idx].url ?: idx}" }
                        ) { idx ->
                            val video = homeVideos[idx]
                            com.videhub.ui.components.VideoRowItem(
                                videoUrl = video.url ?: "",
                                title = video.name ?: "",
                                uploaderName = video.uploaderName ?: "",
                                thumbnailUrl = video.thumbnails?.firstOrNull()?.url,
                                duration = video.duration,
                                viewCount = video.viewCount,
                                uploadDate = video.textualUploadDate ?: "",
                                uploaderUrl = video.uploaderUrl,
                                uploaderAvatarUrl = try { video.uploaderAvatars?.firstOrNull()?.url } catch (e: Exception) { null },
                                onChannelClick = {},
                                onClick = {
                                    val url = video.url ?: ""
                                    if (url.isNotBlank()) {
                                        onVideoClick(url, video.name ?: "", video.thumbnails?.firstOrNull()?.url ?: "")
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Tab 1: Videos Tab (Vertical list, no sorting chips)
                else if (selectedTab == 1) {
                    if (videoLoading) {
                        items(5) {
                            com.videhub.ui.components.VideoCardShimmer()
                        }
                    } else if (videoListError != null) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (channelInfo == null && headerError != null) {
                                    Text(
                                        text = "Cannot load videos until channel loads",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = {
                                        headerRetryTrigger++
                                    }) {
                                        Text("Retry")
                                    }
                                } else {
                                    Text(
                                        text = videoListError ?: "Failed to load videos",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = {
                                        videoRetryTrigger++
                                    }) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    } else if (!headerLoading && !hasHeaderError) {
                        if (channelVideos.isEmpty()) {
                            item {
                                com.videhub.ui.components.EmptyState(
                                    icon = Icons.Default.PlayArrow,
                                    title = "No videos",
                                    message = "This channel hasn't uploaded any videos yet."
                                )
                            }
                        } else {
                            items(
                                count = channelVideos.size,
                                key = { index -> "${channelVideos[index].url ?: index.toString()}_$index" }
                            ) { index ->
                                val video = channelVideos[index]
                                
                                if (index == channelVideos.size - 1 && !isPaginating && pagingSource?.hasMore == true) {
                                    LaunchedEffect(Unit) {
                                        loadNextPage()
                                    }
                                }
                                
                                com.videhub.ui.components.VideoRowItem(
                                    videoUrl = video.url ?: "",
                                    title = video.name ?: "",
                                    uploaderName = video.uploaderName ?: "",
                                    thumbnailUrl = video.thumbnails?.firstOrNull()?.url,
                                    duration = video.duration,
                                    viewCount = video.viewCount,
                                    uploadDate = video.textualUploadDate ?: "",
                                    uploaderUrl = video.uploaderUrl,
                                    uploaderAvatarUrl = try { video.uploaderAvatars?.firstOrNull()?.url } catch (e: Exception) { null },
                                    onChannelClick = {},
                                    onClick = {
                                        val url = video.url ?: ""
                                        if (url.isNotBlank()) {
                                            onVideoClick(url, video.name ?: "", video.thumbnails?.firstOrNull()?.url ?: "")
                                        }
                                    }
                                )
                            }
                            
                            if (isPaginating) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
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
                
                // Tab 2: Live Tab (Live streams, running, upcoming, and past completed)
                else if (selectedTab == 2) {
                    if (isLiveLoading) {
                        items(4) {
                            com.videhub.ui.components.VideoCardShimmer()
                        }
                    } else if (liveError != null) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = liveError ?: "Failed to load live streams",
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = {
                                    liveRetryTrigger++
                                }) {
                                    Text("Retry")
                                }
                            }
                        }
                    } else if (channelLiveStreams.isEmpty()) {
                        item {
                            com.videhub.ui.components.EmptyState(
                                icon = Icons.Default.Sensors,
                                title = "No live streams",
                                message = "This channel has no live streams or scheduled events."
                            )
                        }
                    } else {
                        items(
                            count = channelLiveStreams.size,
                            key = { index -> "live_${channelLiveStreams[index].url ?: index}" }
                        ) { index ->
                            val stream = channelLiveStreams[index]
                            ChannelLiveStreamItem(
                                video = stream,
                                onClick = {
                                    val url = stream.url ?: ""
                                    if (url.isNotBlank()) {
                                        onVideoClick(url, stream.name ?: "", stream.thumbnails?.firstOrNull()?.url ?: "")
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Tab 3: Playlists Tab (Compact playlist rows matching Image 4)
                else if (selectedTab == 3) {
                    if (isPlaylistsLoading) {
                        items(4) {
                            com.videhub.ui.components.VideoCardShimmer()
                        }
                    } else if (playlistsError != null) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = playlistsError ?: "Failed to load playlists",
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = {
                                    playlistRetryTrigger++
                                }) {
                                    Text("Retry")
                                }
                            }
                        }
                    } else if (channelPlaylists.isEmpty()) {
                        item {
                            com.videhub.ui.components.EmptyState(
                                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                title = "No playlists",
                                message = "This channel has not created any public playlists."
                            )
                        }
                    } else {
                        items(
                            count = channelPlaylists.size,
                            key = { index -> "${channelPlaylists[index].url ?: index.toString()}_$index" }
                        ) { index ->
                            val playlist = channelPlaylists[index]
                            CompactPlaylistResultItem(
                                playlist = playlist,
                                onClick = {
                                    val url = playlist.url ?: ""
                                    if (url.isNotBlank()) {
                                        onPlaylistClick(url)
                                    }
                                },
                                onMoreClick = {
                                    selectedPlaylistForMenu = playlist
                                }
                            )
                        }
                    }
                }
            }
            } // close AnimatedContent
        }
        
        playlistDialogVideo?.let { video ->
            com.videhub.ui.components.AddToPlaylistDialog(
                videoUrl = video.url ?: "",
                title = video.name ?: "",
                thumbnailUrl = video.thumbnails?.firstOrNull()?.url ?: "",
                channelName = video.uploaderName ?: "",
                onDismiss = { playlistDialogVideo = null }
            )
        }

        selectedPlaylistForMenu?.let { playlistItem ->
            com.videhub.ui.components.PlaylistActionBottomSheet(
                playlist = playlistItem,
                onDismiss = { selectedPlaylistForMenu = null }
            )
        }
    }
}

@Composable
fun ChannelForYouCard(
    video: StreamInfoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(220.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val thumbUrl = video.thumbnails?.firstOrNull()?.url
                if (!thumbUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = video.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (video.duration > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = com.videhub.utils.FormatHelper.formatDuration(video.duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = video.name ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val viewsPart = if (video.viewCount > 0) "${com.videhub.utils.FormatHelper.formatCount(video.viewCount)} views" else null
            val datePart = video.textualUploadDate?.ifBlank { null }
            val meta = listOfNotNull(viewsPart, datePart).joinToString(" • ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ChannelLiveStreamItem(
    video: StreamInfoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUpcoming = video.textualUploadDate?.contains("Scheduled", ignoreCase = true) == true ||
            video.textualUploadDate?.contains("Premieres", ignoreCase = true) == true ||
            video.streamType?.name?.contains("UPCOMING", ignoreCase = true) == true

    val isRunningLive = !isUpcoming && (
            video.streamType?.name?.contains("LIVE", ignoreCase = true) == true && video.duration <= 0 ||
            video.textualUploadDate?.contains("watching", ignoreCase = true) == true ||
            (video.duration <= 0 && video.textualUploadDate.isNullOrBlank())
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val thumbUrl = video.thumbnails?.firstOrNull()?.url
            if (!thumbUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = video.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (isRunningLive) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFCC0000),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else if (isUpcoming) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "Upcoming",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else if (video.duration > 0) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = com.videhub.utils.FormatHelper.formatDuration(video.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = video.name ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            val metadataText = when {
                isRunningLive -> {
                    if (video.viewCount > 0) "${com.videhub.utils.FormatHelper.formatCount(video.viewCount)} watching" else "LIVE"
                }
                isUpcoming -> {
                    video.textualUploadDate?.ifBlank { "Upcoming" } ?: "Upcoming"
                }
                else -> {
                    val viewsPart = if (video.viewCount > 0) "${com.videhub.utils.FormatHelper.formatCount(video.viewCount)} views" else null
                    val datePart = video.textualUploadDate?.ifBlank { null }
                    listOfNotNull(viewsPart, datePart).joinToString(" • ")
                }
            }

            Text(
                text = metadataText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ChannelHeaderSection(
    bannerUrl: String?,
    avatarUrl: String?,
    name: String,
    handle: String?,
    isVerified: Boolean,
    statsText: String,
    descriptionSummary: String,
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        // Banner height about 110dp
        if (!bannerUrl.isNullOrBlank()) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = "Channel banner",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Row: Avatar (72dp) on the left, Channel info on the right (Matches Image 1 & 6)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar on the left
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Channel avatar",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = name.firstOrNull()?.toString()?.uppercase() ?: "?",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details on the right
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified channel",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                if (!handle.isNullOrBlank()) {
                    Text(
                        text = if (handle.startsWith("@")) handle else "@$handle",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                if (statsText.isNotBlank()) {
                    Text(
                        text = statsText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Bio / About line with "...more" (aligned start, horizontal padding 16dp)
        if (descriptionSummary.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAboutClick() }
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = androidx.compose.ui.text.buildAnnotatedString {
                        append(descriptionSummary)
                        append(" ")
                        pushStyle(
                            androidx.compose.ui.text.SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        append("...more")
                        pop()
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Full width pill Subscribe button (Image 1 & 6)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            ChannelSubscribeButton(
                isSubscribed = isSubscribed,
                onClick = onSubscribeClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelTabsSection(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        val tabTitles = listOf("Home", "Videos", "Live", "Playlists")
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {}
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .defaultMinSize(minHeight = 48.dp),
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
fun ChannelSubscribeButton(
    isSubscribed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isSubscribed) {
        FilledTonalButton(
            onClick = onClick,
            shape = RoundedCornerShape(19.dp),
            modifier = modifier.height(38.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = "Subscribed notifications",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Subscribed",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    } else {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(19.dp),
            modifier = modifier.height(38.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)
        ) {
            Text(
                text = "Subscribe",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Channel Header Expanded", showBackground = true, backgroundColor = 0xFF0F0F0F)
@Composable
fun ChannelHeaderExpandedPreview() {
    AppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        // Title hidden when header is expanded
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {},
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    windowInsets = WindowInsets(0.dp)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                ChannelHeaderSection(
                    bannerUrl = null,
                    avatarUrl = null,
                    name = "Rom Rom Ji",
                    handle = "RomRomJi",
                    isVerified = true,
                    statsText = "629k subscribers • 456 videos",
                    descriptionSummary = "Rom Rom ji",
                    isSubscribed = true,
                    onSubscribeClick = {},
                    onAboutClick = {}
                )
                ChannelTabsSection(
                    selectedTab = 0,
                    onTabSelected = {}
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Channel Header Collapsed", showBackground = true, backgroundColor = 0xFF0F0F0F)
@Composable
fun ChannelHeaderCollapsedPreview() {
    AppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Rom Rom Ji",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {},
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    windowInsets = WindowInsets(0.dp)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Header is scrolled away, sticky tabs pinned under top bar
                ChannelTabsSection(
                    selectedTab = 1,
                    onTabSelected = {}
                )
            }
        }
    }
}

