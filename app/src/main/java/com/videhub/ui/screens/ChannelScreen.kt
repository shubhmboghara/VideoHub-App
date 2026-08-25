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
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(32f / 9f)
                    .background(brush)
            )
            // Avatar
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.BottomCenter)
                    .offset(y = 48.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
        }
        Spacer(modifier = Modifier.height(64.dp)) // 48dp offset + 16dp spacing
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(28.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(20.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(40.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    sharedViewModel: com.videhub.viewmodel.MainViewModel,
    channelId: String,
    onBack: () -> Unit,
    onVideoClick: (String, String, String) -> Unit,
    onPlaylistClick: (String) -> Unit = {},
    onAboutClick: () -> Unit
) {
    // Determine if we should load from cache
    val isSameChannel = sharedViewModel.currentChannelId == channelId
    var channelInfo by remember { mutableStateOf<ChannelInfo?>(if (isSameChannel) sharedViewModel.channelInfoCache else null) }
    var channelAboutInfo by remember { mutableStateOf<com.videhub.extractor.ChannelAboutInfo?>(if (isSameChannel) sharedViewModel.channelAboutInfoCache else null) }
    var channelVideos by remember { mutableStateOf<List<StreamInfoItem>>(if (isSameChannel) sharedViewModel.channelVideosCache ?: emptyList() else emptyList()) }
    var channelPlaylists by remember { mutableStateOf<List<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>>(if (isSameChannel) sharedViewModel.channelPlaylistsCache ?: emptyList() else emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Videos, 1: Playlists
    var isPlaylistsLoading by remember { mutableStateOf(false) }
    var playlistsError by remember { mutableStateOf<String?>(null) }
    var isHeaderLoading by remember { mutableStateOf(channelInfo == null) }
    var headerError by remember { mutableStateOf<String?>(null) }
    var isVideoListLoading by remember { mutableStateOf(channelVideos.isEmpty() && channelInfo == null) }
    var videoListError by remember { mutableStateOf<String?>(null) }
    
    
    var isSubscribed by remember { mutableStateOf(false) }
    var playlistDialogVideo by remember { mutableStateOf<StreamInfoItem?>(null) }
    var pagingSource by remember { mutableStateOf<com.videhub.extractor.ListExtractorPagingSource?>(if (isSameChannel) sharedViewModel.channelPagingSourceCache else null) }
    var isPaginating by remember { mutableStateOf(false) }
    var headerRetryTrigger by remember { mutableIntStateOf(0) }
    var videoRetryTrigger by remember { mutableIntStateOf(0) }
    var playlistRetryTrigger by remember { mutableIntStateOf(0) }
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
    val currentPagingSource by rememberUpdatedState(pagingSource)

    DisposableEffect(channelId) {
        onDispose {
            sharedViewModel.currentChannelId = channelId
            sharedViewModel.channelInfoCache = currentChannelInfo
            sharedViewModel.channelAboutInfoCache = currentChannelAboutInfo
            sharedViewModel.channelVideosCache = currentChannelVideos
            sharedViewModel.channelPlaylistsCache = currentChannelPlaylists
            sharedViewModel.channelPagingSourceCache = currentPagingSource
            sharedViewModel.channelScrollIndexCache = listState.firstVisibleItemIndex
            sharedViewModel.channelScrollOffsetCache = listState.firstVisibleItemScrollOffset
        }
    }

    fun refresh() {
        if (isRefreshing || isHeaderLoading || isVideoListLoading || isPlaylistsLoading || isPaginating) return
        // "new render" - clear cache and show shimmer
        channelVideos = emptyList()
        channelPlaylists = emptyList()
        channelInfo = null
        channelAboutInfo = null
        sharedViewModel.channelVideosCache = emptyList()
        sharedViewModel.channelPlaylistsCache = null
        sharedViewModel.channelInfoCache = null
        sharedViewModel.channelAboutInfoCache = null
        isHeaderLoading = true
        isVideoListLoading = true
        isRefreshing = true
        headerRetryTrigger++
        videoRetryTrigger++
        playlistRetryTrigger++
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

    LaunchedEffect(selectedTab, channelInfo, playlistRetryTrigger) {
        if (selectedTab == 1 && channelInfo != null) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(channelInfo?.name ?: "Channel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                item {
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Banner
                            Box(modifier = Modifier.fillMaxWidth()) {
                                val bannerUrl = try { info.banners.firstOrNull()?.url } catch (e: Exception) { null }
                                if (!bannerUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = bannerUrl,
                                        contentDescription = "Banner",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(32f / 9f)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(32f / 9f)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                }
                                
                                // Avatar
                                val avatarUrl = try { info.avatars.firstOrNull()?.url } catch(e: Exception) { null }
                                if (avatarUrl != null) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = "Avatar",
                                        modifier = Modifier
                                            .size(80.dp)
                                            .align(Alignment.BottomCenter)
                                            .offset(y = 40.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .align(Alignment.BottomCenter)
                                            .offset(y = 40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = info.name.firstOrNull()?.toString()?.uppercase() ?: "?",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.displayMedium
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(56.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = info.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                if (info.isVerified) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                                        contentDescription = "Verified",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            val subText = com.videhub.utils.FormatHelper.formatSubscriberCount(info.subscriberCount)
                            val streamCount = try {
                                info.javaClass.getMethod("getStreamCount").invoke(info) as? Long
                            } catch (e: Exception) { null }
                            
                            val statsText = if (streamCount != null && streamCount > 0) {
                                "$subText • $streamCount videos"
                            } else {
                                subText
                            }
                            
                            Text(
                                text = statsText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            AnimatedSubscribeButton(
                                isSubscribed = isSubscribed,
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val avatar = try { info.avatars.firstOrNull()?.url } catch (e: Exception) { null }
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
                                }
                            )
                            if (!info.description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                sharedViewModel.channelInfoCache = info
                                                sharedViewModel.channelAboutInfoCache = channelAboutInfo
                                                onAboutClick()
                                            }
                                        )
                                ) {
                                    val rawDescription = info.description ?: ""
                                    val cleanDescription = androidx.compose.runtime.remember(rawDescription) {
                                        val lines = rawDescription.lines()
                                        val cleanLines = mutableListOf<String>()
                                        for (line in lines) {
                                            val words = line.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
                                            if (words.isNotEmpty() && words.all { it.startsWith("#") }) {
                                                // skipping hashtags here for brevity
                                            } else {
                                                cleanLines.add(line)
                                            }
                                        }
                                        cleanLines.joinToString("\n").trim()
                                    }

                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = cleanDescription,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Text(
                                            text = "...more",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            } else {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                sharedViewModel.channelInfoCache = info
                                                sharedViewModel.channelAboutInfoCache = channelAboutInfo
                                                onAboutClick()
                                            }
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "About channel",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "About channel",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        androidx.compose.material3.HorizontalDivider()
                        
                        PrimaryTabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            divider = {}
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { 
                                    Text(
                                        "Videos", 
                                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                    ) 
                                }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { 
                                    Text(
                                        "Playlists", 
                                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                    ) 
                                }
                            )
                        }
                        androidx.compose.material3.HorizontalDivider()

                    }
                }
                }
                
                if (selectedTab == 0) {
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
                } else if (selectedTab == 1) {
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
                            com.videhub.ui.components.OnlinePlaylistItemCard(
                                playlist = playlist,
                                onClick = {
                                    val url = playlist.url ?: ""
                                    if (url.isNotBlank()) {
                                        onPlaylistClick(url)
                                    }
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
    }
}
