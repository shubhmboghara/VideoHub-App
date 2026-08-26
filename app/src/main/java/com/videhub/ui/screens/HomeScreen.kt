package com.videhub.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle


import android.content.Context
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.videhub.data.AppDatabase
import com.videhub.extractor.ExtractorHelper
import com.videhub.ui.components.VideoCard
import com.videhub.ui.components.VideoCardShimmer
import com.videhub.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@kotlin.OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    sharedViewModel: com.videhub.viewmodel.MainViewModel,
    onVideoClick: (String, String, String) -> Unit,
    onChannelClick: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onPlaylistClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val isDarkMode by ThemeManager.isDarkMode.collectAsStateWithLifecycle()

    // Tab colors adapt to theme
    val tabBg = MaterialTheme.colorScheme.surface
    val tabSelected = MaterialTheme.colorScheme.onSurface
    val tabUnselected = MaterialTheme.colorScheme.onSurfaceVariant
    val indicatorColor = MaterialTheme.colorScheme.onSurfaceVariant

    var selectedTab by remember { mutableIntStateOf(sharedViewModel.homeSelectedTabCache) }

    // Recommended playlists state from cache or fetched
    var recommendedPlaylists by remember {
        mutableStateOf<List<com.videhub.recommendation.RecommendedPlaylistInfo>>(
            sharedViewModel.homeRecommendedPlaylistsCache ?: emptyList()
        )
    }

    // Use state map directly for videos
    val videos = sharedViewModel.homeVideosCacheMap[selectedTab] ?: emptyList()
    var isLoading by remember { mutableStateOf(sharedViewModel.homeVideosCacheMap[selectedTab] == null) }
    var isPaginating by remember { mutableStateOf(false) }
    var pagingSource by remember { mutableStateOf<com.videhub.extractor.ListExtractorPagingSource?>(sharedViewModel.homePagingSourceMap[selectedTab]) }
    
    // We remember the scroll state for each tab so we can restore it immediately.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = sharedViewModel.homeScrollStateMap[selectedTab]?.first ?: 0,
        initialFirstVisibleItemScrollOffset = sharedViewModel.homeScrollStateMap[selectedTab]?.second ?: 0
    )
    
    val customTabsString by com.videhub.data.SettingsManager.getCustomTabs(context).collectAsStateWithLifecycle(initialValue = "Music,Gaming,News,Sports")
    val channels by db.channelDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val channelMap = remember(channels) { channels.associateBy { it.channelId } }
    val tabs = remember(customTabsString) {
        val list = mutableStateListOf("All")
        if (customTabsString.isNotEmpty()) {
            list.addAll(customTabsString.split(","))
        }
        list
    }
    
    val isOnline by remember { com.videhub.utils.NetworkUtils.getNetworkStatusFlow(context) }.collectAsStateWithLifecycle(initialValue = com.videhub.utils.NetworkUtils.isNetworkAvailable(context))
    val offlineDownloads by db.downloadedVideoDao().getAllDownloads().collectAsStateWithLifecycle(initialValue = emptyList())
    
    val feedRepository = remember(db, context) { com.videhub.data.repository.FeedRepository(db, context) }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInterestsSheet by remember { mutableStateOf(false) }
    var tabToDelete by remember { mutableIntStateOf(-1) }
    var newTabName by remember { mutableStateOf("") }
    var playlistDialogVideo by remember { mutableStateOf<StreamInfoItem?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var homeFeedPage by remember { mutableIntStateOf(1) }
    var refreshCounter by remember { mutableIntStateOf(0) }

    val onError: (String) -> Unit = { message ->
        errorText = message
        isLoading = false
    }

    fun loadNextPage() {
        if (isPaginating) return
        val current = sharedViewModel.homeVideosCacheMap[selectedTab] ?: emptyList()
        if (current.isEmpty()) return

        scope.launch {
            isPaginating = true
            try {
                if (selectedTab == 0) {
                    val existingUrls = current.mapNotNull {
                        when (it) {
                            is StreamInfoItem -> it.url
                            is com.videhub.data.entity.FeedCacheEntity -> it.videoId
                            else -> null
                        }
                    }.toSet()
                    val nextVideos = feedRepository.getHomeFeedNextPage(
                        pageNumber = ++homeFeedPage,
                        existingUrls = existingUrls
                    )
                    if (nextVideos.isNotEmpty()) {
                        sharedViewModel.homeVideosCacheMap[0] = current + nextVideos
                    }
                } else {
                    if (pagingSource?.hasMore == true) {
                        val items = pagingSource?.loadNextPage() ?: emptyList()
                        val newItems = items.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                        if (newItems.isNotEmpty()) {
                            sharedViewModel.homeVideosCacheMap[selectedTab] = current + newItems
                        }
                    } else if (pagingSource == null && selectedTab < tabs.size) {
                        // Fallback pagination for custom tabs
                        val tabName = tabs[selectedTab]
                        val (moreFeed, newSource) = feedRepository.loadCustomTabFeed(tabName)
                        pagingSource = newSource
                        sharedViewModel.homePagingSourceMap[selectedTab] = newSource
                        if (moreFeed.isNotEmpty()) {
                            sharedViewModel.homeVideosCacheMap[selectedTab] = current + moreFeed
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeScreen", "Error loading next page", e)
            } finally {
                isPaginating = false
            }
        }
    }

    // Trigger pagination when nearing end of list (within last 3 items)
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 3 && !isPaginating
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && videos.isNotEmpty() && !isLoading && !isRefreshing) {
            loadNextPage()
        }
    }

    // Save scroll state continuously or when selectedTab changes
    LaunchedEffect(selectedTab) {
        sharedViewModel.homeSelectedTabCache = selectedTab
        pagingSource = sharedViewModel.homePagingSourceMap[selectedTab]
        // We will restore the scroll state for this new tab
        val savedScroll = sharedViewModel.homeScrollStateMap[selectedTab]
        if (savedScroll != null) {
            listState.scrollToItem(savedScroll.first, savedScroll.second)
        } else {
            listState.scrollToItem(0)
        }
    }

    DisposableEffect(selectedTab) {
        onDispose {
            sharedViewModel.homeScrollStateMap[selectedTab] = Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }

    fun refresh(force: Boolean = false) {
        if (!force && (isRefreshing || isLoading || isPaginating)) return
        scope.launch {
            // "new render": clear cache and show shimmer
            sharedViewModel.homeVideosCacheMap[selectedTab] = emptyList()
            sharedViewModel.homeScrollStateMap[selectedTab] = Pair(0, 0)
            isLoading = true
            isRefreshing = true
            errorText = null
            refreshCounter++
            homeFeedPage = 1
            try {
                val safeTab = if (tabs.isNotEmpty()) selectedTab.coerceIn(0, tabs.size - 1) else 0
                val newVideos = mutableListOf<Any>()
                when (safeTab) {
                    0 -> {
                        // Concurrently refresh recommended feed videos and recommended playlists via FeedRepository
                        var freshVideos: List<InfoItem> = emptyList()
                        coroutineScope {
                            val playlistsDeferred = async(Dispatchers.IO) {
                                try {
                                    feedRepository.getRecommendedPlaylists(isRefresh = true, refreshCount = refreshCounter)
                                } catch (_: Exception) {
                                    emptyList<com.videhub.recommendation.RecommendedPlaylistInfo>()
                                }
                            }
                            val feedDeferred = async(Dispatchers.IO) {
                                feedRepository.getHomeFeed(isRefresh = true, refreshCount = refreshCounter)
                            }
                            freshVideos = feedDeferred.await()
                            val fetchedPlaylists = playlistsDeferred.await()
                            if (fetchedPlaylists.isNotEmpty()) {
                                recommendedPlaylists = fetchedPlaylists
                                sharedViewModel.homeRecommendedPlaylistsCache = fetchedPlaylists
                            }
                        }

                        if (freshVideos.isNotEmpty()) {
                            newVideos.addAll(freshVideos)
                        }
                        pagingSource = null
                    }
                    else -> {
                        if (safeTab < tabs.size) {
                            val (customFeed, source) = feedRepository.loadCustomTabFeed(
                                tabName = tabs[safeTab],
                                isRefresh = true,
                                refreshCount = refreshCounter
                            )
                            pagingSource = source
                            newVideos.addAll(customFeed)
                        }
                    }
                }
                sharedViewModel.homeVideosCacheMap[selectedTab] = newVideos
                sharedViewModel.homePagingSourceMap[selectedTab] = pagingSource
                listState.scrollToItem(0)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    android.util.Log.e("HomeScreen", "Error loading All tab", e)
                    onError("Error: ${e.message}")
                }
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (sharedViewModel.homeVideosCacheMap[selectedTab] != null && sharedViewModel.homeVideosCacheMap[selectedTab]!!.isNotEmpty()) {
            // Already cached and on the same tab
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        errorText = null
        try {
            val safeTab = if (tabs.isNotEmpty()) selectedTab.coerceIn(0, tabs.size - 1) else 0
            val newVideos = mutableListOf<Any>()
            when (safeTab) {
                0 -> {
                    pagingSource = null
                    try {
                        val cachedFeed = db.feedCacheDao().getAll()
                        if (cachedFeed.isNotEmpty()) {
                            sharedViewModel.homeVideosCacheMap[0] = cachedFeed
                            isLoading = false
                        }
                        
                        // Concurrently load recommended playlists if not cached
                        if (sharedViewModel.homeRecommendedPlaylistsCache == null) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val fetchedPlaylists = feedRepository.getRecommendedPlaylists()
                                    if (fetchedPlaylists.isNotEmpty()) {
                                        recommendedPlaylists = fetchedPlaylists
                                        sharedViewModel.homeRecommendedPlaylistsCache = fetchedPlaylists
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        val freshVideos = feedRepository.getHomeFeed(isRefresh = false)
                        if (freshVideos.isNotEmpty()) {
                            sharedViewModel.homeVideosCacheMap[0] = freshVideos
                        }
                    } catch (e: Exception) {
                        if (e !is kotlinx.coroutines.CancellationException) {
                            if (sharedViewModel.homeVideosCacheMap[0].isNullOrEmpty()) {
                                android.util.Log.e("HomeScreen", "Error loading All tab", e)
                                onError("Error: ${e.message}")
                            }
                        } else throw e
                    } finally {
                        isLoading = false
                    }
                }
                else -> {
                    if (safeTab < tabs.size) {
                        val (customFeed, source) = feedRepository.loadCustomTabFeed(tabs[safeTab])
                        pagingSource = source
                        newVideos.addAll(customFeed)
                    }
                    isLoading = false
                }
            }
            if (safeTab != 0 || newVideos.isNotEmpty()) {
                sharedViewModel.homeVideosCacheMap[selectedTab] = newVideos
            }
            sharedViewModel.homePagingSourceMap[selectedTab] = pagingSource
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("HomeScreen", "Error loading tab", e)
            onError("Error: ${e.message}")
        }
    }

    if (!isOnline) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Text(
                text = "Offline Mode",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Here are your downloaded videos & songs:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(offlineDownloads, key = { index, download -> "${download.videoId}_$index" }) { index, download ->
                    VideoCard(
                        title = download.title,
                        channelName = download.channelName,
                        thumbnailUrl = download.thumbnailUrl,
                        url = download.videoId,
                        duration = -1L,
                        viewCount = download.viewCount,
                        channelAvatarUrl = null,
                        uploadDate = download.uploadDate,
                        onClick = { _, _, _ ->
                            val localUri = android.net.Uri.fromFile(java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), download.fileName)).toString()
                            onVideoClick(localUri, download.title, download.thumbnailUrl)
                        },
                        onChannelClick = {}
                    )
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.videhub.R.drawable.ic_logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "VideoHub",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { showInterestsSheet = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(48.dp)
                        .semantics {
                            contentDescription = "Personalize interests and topics"
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onSearchClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(48.dp)
                        .semantics {
                            contentDescription = "Search videos, channels, and playlists"
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tabBg)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val safeSelectedTab = if (tabs.isNotEmpty()) selectedTab.coerceIn(0, tabs.size - 1) else 0
            val selectedChipBg = MaterialTheme.colorScheme.onSurface
            val unselectedChipBg = MaterialTheme.colorScheme.surfaceVariant
            val selectedText = MaterialTheme.colorScheme.surface
            val unselectedText = MaterialTheme.colorScheme.onSurface
            
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(count = tabs.size, key = { index -> tabs[index] }) { index ->
                    val tab = tabs[index]
                    val isSelected = safeSelectedTab == index
                    val isCustom = tab != "All"
                    
                    Surface(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .height(36.dp)
                            .semantics {
                                contentDescription = if (isSelected) "$tab tab, selected" else "$tab tab"
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) selectedChipBg else unselectedChipBg.copy(alpha = 0.5f),
                        onClick = { selectedTab = index }
                    ) {
                        Box(
                            modifier = Modifier
                                .combinedClickable(
                                    role = androidx.compose.ui.semantics.Role.Tab,
                                    onClick = { selectedTab = index },
                                    onLongClick = {
                                        if (isCustom) {
                                            tabToDelete = index
                                            showDeleteDialog = true
                                        }
                                    }
                                )
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = if (isSelected) selectedText else unselectedText
                            )
                        }
                    }
                }
                item {
                    Surface(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(36.dp)
                            .semantics {
                                contentDescription = "Add custom category"
                            },
                        shape = CircleShape,
                        color = unselectedChipBg,
                        onClick = { showAddDialog = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurface,
                title = { Text("Add Category") },
                text = {
                    OutlinedTextField(
                        value = newTabName,
                        onValueChange = { newTabName = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newTabName.isNotBlank() && !tabs.contains(newTabName.trim())) {
                                val category = newTabName.trim()
                                tabs.add(category)
                                val customTabs = tabs.filter { it != "All" }.joinToString(",")
                                scope.launch { com.videhub.data.SettingsManager.setCustomTabs(context, customTabs) }
                                selectedTab = tabs.size - 1
                            }
                            newTabName = ""
                            showAddDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { 
                            showAddDialog = false 
                            newTabName = ""
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDeleteDialog && tabToDelete in 1 until tabs.size) {
            val titleToDelete = tabs[tabToDelete]
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurface,
                title = { Text("Delete Category") },
                text = { Text("Are you sure you want to delete '$titleToDelete'?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            tabs.removeAt(tabToDelete)
                            val customTabs = tabs.filter { it != "All" }.joinToString(",")
                            scope.launch { com.videhub.data.SettingsManager.setCustomTabs(context, customTabs) }
                            
                            // Adjust selected tab safely
                            if (selectedTab >= tabs.size) {
                                selectedTab = tabs.size - 1
                            } else if (selectedTab == tabToDelete) {
                                selectedTab = 0 // Reset to home if deleted tab was selected
                            }
                            
                            showDeleteDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refresh() },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = Triple(selectedTab, isLoading && !isRefreshing, errorText != null),
                transitionSpec = {
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)).togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)))
                },
                label = "HomeScreenStateTransition"
            ) { (tab, showShimmer, hasError) ->
                when {
                    showShimmer -> LazyColumn {
                        items(5) { VideoCardShimmer() }
                    }
                    hasError -> Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(errorText ?: "Could not load content. Please try again.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            isLoading = true
                            val t = selectedTab
                            selectedTab = -1
                            selectedTab = t
                        }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Text("Retry") }
                    }
                    videos.isEmpty() -> com.videhub.ui.components.EmptyState(
                        icon = Icons.Default.Home,
                        title = "No videos found",
                        message = "Try exploring other categories.",
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(
                        count = videos.size,
                        key = { index -> 
                            val v = videos[index]
                            when (v) {
                                is StreamInfoItem -> v.url ?: "stream_$index"
                                is com.videhub.data.entity.FeedCacheEntity -> "feed_${v.videoId}"
                                is com.videhub.data.entity.HistoryEntity -> "hist_${v.videoId}"
                                is com.videhub.data.entity.WatchLaterEntity -> "wl_${v.videoId}"
                                is com.videhub.data.entity.LikedVideoEntity -> "liked_${v.videoId}"
                                is com.videhub.data.entity.DownloadedVideoEntity -> "dl_${v.videoId}"
                                is com.videhub.data.entity.PlaylistVideoEntity -> "pl_${v.videoId}"
                                else -> "item_$index"
                            }
                        },
                        contentType = { index ->
                            videos[index]::class.java.simpleName
                        }
                    ) { index ->
                        val video = videos[index]
                        
                        when (video) {
                            is StreamInfoItem -> {
                                val cId = video.uploaderUrl ?: ""
                                val avatarUrl = channelMap[cId]?.thumbnailUrl
                                VideoCard(
                                    item = video,
                                    onClick = onVideoClick,
                                    onChannelClick = onChannelClick,
                                    channelAvatarUrl = avatarUrl
                                )
                            }
                            is com.videhub.data.entity.FeedCacheEntity -> {
                                VideoCard(
                                    title = video.title,
                                    channelName = video.channelName,
                                    thumbnailUrl = video.thumbnailUrl,
                                    url = video.videoId,
                                    duration = video.duration,
                                    viewCount = video.viewCount,
                                    channelAvatarUrl = video.channelAvatarUrl,
                                    uploadDate = video.publishedText,
                                    onClick = onVideoClick,
                                    onChannelClick = onChannelClick
                                )
                            }
                            is com.videhub.data.entity.HistoryEntity -> {
                                VideoCard(
                                    title = video.title,
                                    channelName = video.channelName,
                                    thumbnailUrl = video.thumbnailUrl ?: "",
                                    url = video.videoId,
                                    duration = -1L,
                                    viewCount = video.viewCount,
                                    channelAvatarUrl = null,
                                    uploadDate = video.uploadDate,
                                    onClick = onVideoClick,
                                    onChannelClick = onChannelClick
                                )
                            }
                            is com.videhub.data.entity.WatchLaterEntity -> {
                                VideoCard(
                                    title = video.title,
                                    channelName = video.channelName,
                                    thumbnailUrl = video.thumbnailUrl,
                                    url = video.videoId,
                                    duration = -1L,
                                    viewCount = -1L,
                                    channelAvatarUrl = null,
                                    uploadDate = "",
                                    onClick = onVideoClick,
                                    onChannelClick = onChannelClick
                                )
                            }
                            is com.videhub.data.entity.LikedVideoEntity -> {
                                VideoCard(
                                    title = video.title,
                                    channelName = video.channelName,
                                    thumbnailUrl = video.thumbnailUrl,
                                    url = video.videoId,
                                    duration = -1L,
                                    viewCount = video.viewCount,
                                    channelAvatarUrl = null,
                                    uploadDate = video.uploadDate,
                                    onClick = onVideoClick,
                                    onChannelClick = onChannelClick
                                )
                            }
                            is com.videhub.data.entity.DownloadedVideoEntity -> {
                                VideoCard(
                                    title = video.title,
                                    channelName = video.channelName,
                                    thumbnailUrl = video.thumbnailUrl,
                                    url = video.videoId,
                                    duration = -1L,
                                    viewCount = video.viewCount,
                                    channelAvatarUrl = null,
                                    uploadDate = video.uploadDate,
                                    onClick = { _: String, _: String, _: String ->
                                        val localUri = android.net.Uri.fromFile(java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), video.fileName)).toString()
                                        onVideoClick(localUri, video.title, video.thumbnailUrl)
                                    },
                                    onChannelClick = onChannelClick
                                )
                            }
                            is com.videhub.data.entity.PlaylistVideoEntity -> {
                                VideoCard(
                                    title = video.title,
                                    channelName = video.channelName,
                                    thumbnailUrl = video.thumbnailUrl ?: "",
                                    url = video.videoId,
                                    duration = -1L,
                                    viewCount = video.viewCount,
                                    channelAvatarUrl = null,
                                    uploadDate = video.uploadDate,
                                    onClick = onVideoClick,
                                    onChannelClick = onChannelClick,
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        // YouTube-style Recommended Playlists & Mixes shelf injected right below the first video
                        if (selectedTab == 0 && recommendedPlaylists.isNotEmpty() && index == 0) {
                            com.videhub.ui.components.RecommendedPlaylistsShelf(
                                playlists = recommendedPlaylists,
                                onPlaylistClick = onPlaylistClick
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
                } // End of when
            } // End of AnimatedContent
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

        if (showInterestsSheet) {
            com.videhub.ui.components.InterestsBottomSheet(
                onDismiss = { showInterestsSheet = false },
                onSaved = {
                    sharedViewModel.homeVideosCacheMap.clear()
                    sharedViewModel.homeRecommendedPlaylistsCache = null
                    scope.launch(Dispatchers.IO) {
                        try {
                            db.feedCacheDao().clearAll()
                        } catch (_: Exception) {}
                        withContext(Dispatchers.Main) {
                            selectedTab = 0
                            refresh(force = true)
                        }
                    }
                }
            )
        }
    }
}


