package com.videhub.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle


import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.videhub.extractor.ExtractorHelper
import com.videhub.ui.components.VideoCard
import com.videhub.ui.components.VideoCardShimmer
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import android.widget.Toast
import com.videhub.data.AppDatabase
import com.videhub.data.entity.ChannelEntity
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import com.videhub.ui.components.AnimatedSubscribeButton
import com.videhub.ui.components.CompactPlaylistResultItem
import com.videhub.ui.components.OnlinePlaylistItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    sharedViewModel: com.videhub.viewmodel.MainViewModel,
    onVideoClick: (String, String, String) -> Unit,
    onChannelClick: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(sharedViewModel.exploreQueryCache) }
    
    // Check if we have cached results
    var videoResults by remember { mutableStateOf<List<StreamInfoItem>>(
        sharedViewModel.exploreVideosCache?.filterIsInstance<StreamInfoItem>() ?: emptyList()
    ) }
    var channelResults by remember { mutableStateOf<List<ChannelInfoItem>>(
        sharedViewModel.exploreVideosCache?.filterIsInstance<ChannelInfoItem>() ?: emptyList()
    ) }
    var playlistResults by remember { mutableStateOf<List<PlaylistInfoItem>>(
        sharedViewModel.exploreVideosCache?.filterIsInstance<PlaylistInfoItem>() ?: emptyList()
    ) }
    
    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0: All, 1: Videos, 2: Playlists, 3: Channels
    var isPlaylistSearchLoading by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var playlistDialogVideo by remember { mutableStateOf<StreamInfoItem?>(null) }
    var pagingSource by remember { mutableStateOf<com.videhub.extractor.ListExtractorPagingSource?>(sharedViewModel.explorePagingSourceCache) }
    var isPaginating by remember { mutableStateOf(false) }

    var isRefreshing by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    
    val displayedVideos = videoResults
    
    val db = remember { AppDatabase.getDatabase(context) }
    val searchHistoryDao = db.searchHistoryDao()
    val searchHistory by searchHistoryDao.getAllSearchHistory().collectAsStateWithLifecycle(initialValue = emptyList())
    val searchCacheDao = db.searchCacheDao()

    LaunchedEffect(Unit) {
        if (sharedViewModel.exploreVideosCache == null) {
            val cached = searchCacheDao.getAll()
            if (cached.isNotEmpty()) {
                val cachedChannels = cached.filter { it.type == "channel" }.map {
                    org.schabi.newpipe.extractor.channel.ChannelInfoItem(1, it.url, it.channelName)
                }
                val cachedPlaylists = cached.filter { it.type == "playlist" }.map {
                    val p = org.schabi.newpipe.extractor.playlist.PlaylistInfoItem(1, it.url, it.title)
                    p.uploaderName = it.channelName
                    p.thumbnails = listOf(org.schabi.newpipe.extractor.Image(it.thumbnailUrl, 50, 50, org.schabi.newpipe.extractor.Image.ResolutionLevel.UNKNOWN))
                    p.streamCount = it.duration
                    p
                }
                val cachedVideos = cached.filter { it.type == "video" }.map {
                    val item = org.schabi.newpipe.extractor.stream.StreamInfoItem(1, it.url, it.title, org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM)
                    item.uploaderName = it.channelName
                    item.thumbnails = listOf(org.schabi.newpipe.extractor.Image(it.thumbnailUrl, 50, 50, org.schabi.newpipe.extractor.Image.ResolutionLevel.UNKNOWN))
                    item.viewCount = it.viewCount
                    item.duration = it.duration
                    item
                }
                channelResults = cachedChannels
                playlistResults = cachedPlaylists
                videoResults = cachedVideos
                query = cached.first().query
            }
        }
    }
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = sharedViewModel.exploreScrollIndexCache,
        initialFirstVisibleItemScrollOffset = sharedViewModel.exploreScrollOffsetCache
    )

    val currentQuery by rememberUpdatedState(query)
    val currentChannelResults by rememberUpdatedState(channelResults)
    val currentPlaylistResults by rememberUpdatedState(playlistResults)
    val currentVideoResults by rememberUpdatedState(videoResults)
    val currentPagingSource by rememberUpdatedState(pagingSource)

    DisposableEffect(listState) {
        onDispose {
            sharedViewModel.exploreQueryCache = currentQuery
            sharedViewModel.exploreScrollIndexCache = listState.firstVisibleItemIndex
            sharedViewModel.exploreScrollOffsetCache = listState.firstVisibleItemScrollOffset
            val combinedList = mutableListOf<InfoItem>()
            combinedList.addAll(currentChannelResults)
            combinedList.addAll(currentPlaylistResults)
            combinedList.addAll(currentVideoResults)
            sharedViewModel.exploreVideosCache = combinedList
            sharedViewModel.explorePagingSourceCache = currentPagingSource
        }
    }

    fun refresh() {
        if (isRefreshing || isLoading || isSearching || isPaginating) return
        if (query.isNotBlank() && (videoResults.isNotEmpty() || channelResults.isNotEmpty() || playlistResults.isNotEmpty())) {
            searchJob?.cancel()
            searchJob = scope.launch {
                videoResults = emptyList()
                channelResults = emptyList()
                playlistResults = emptyList()
                sharedViewModel.exploreVideosCache = emptyList()
                isSearching = true
                isRefreshing = true
                errorText = null
                try {
                    val (source, searchPlaylists) = coroutineScope {
                        val sourceDeferred = async(Dispatchers.IO) {
                            ExtractorHelper.getSearchPagingSource(query)
                        }
                        val playlistsDeferred = async(Dispatchers.IO) {
                            try {
                                ExtractorHelper.searchPlaylists(query)
                            } catch (_: Exception) {
                                emptyList<PlaylistInfoItem>()
                            }
                        }
                        Pair(sourceDeferred.await(), playlistsDeferred.await())
                    }
                    pagingSource = source
                    val items = withContext(Dispatchers.IO) {
                        source.loadInitial()
                    }
                    val exactChannels = items.filterIsInstance<ChannelInfoItem>().filter {
                        it.name.equals(query.trim(), ignoreCase = true)
                    }
                    val otherChannels = items.filterIsInstance<ChannelInfoItem>().filter {
                        !it.name.equals(query.trim(), ignoreCase = true)
                    }
                    channelResults = exactChannels + otherChannels

                    val combinedPlaylists = mutableListOf<PlaylistInfoItem>()
                    // If channel found, try fetching channel's official playlists
                    val mainChannel = exactChannels.firstOrNull() ?: channelResults.firstOrNull()
                    if (mainChannel != null && !mainChannel.url.isNullOrBlank()) {
                        try {
                            val chLists = withContext(Dispatchers.IO) {
                                ExtractorHelper.getChannelPlaylists(mainChannel.url)
                            }
                            combinedPlaylists.addAll(chLists)
                        } catch (_: Exception) {}
                    }
                    combinedPlaylists.addAll(searchPlaylists)
                    combinedPlaylists.addAll(items.filterIsInstance<PlaylistInfoItem>())

                    playlistResults = combinedPlaylists.distinctBy { it.url }
                    videoResults = items.filterIsInstance<StreamInfoItem>()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorText = "Search refresh failed: ${e.message}"
                } finally {
                    isSearching = false
                    isRefreshing = false
                }
            }
        }
    }

    fun doSearch() {
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = scope.launch {
            searchHistoryDao.insertSearch(com.videhub.data.entity.SearchHistoryEntity(query.trim()))
            isSearching = true
            errorText = null
            videoResults = emptyList()
            channelResults = emptyList()
            playlistResults = emptyList()
            try {
                val (source, searchPlaylists) = coroutineScope {
                    val sourceDeferred = async(Dispatchers.IO) {
                        ExtractorHelper.getSearchPagingSource(query)
                    }
                    val playlistsDeferred = async(Dispatchers.IO) {
                        try {
                            ExtractorHelper.searchPlaylists(query)
                        } catch (_: Exception) {
                            emptyList<PlaylistInfoItem>()
                        }
                    }
                    Pair(sourceDeferred.await(), playlistsDeferred.await())
                }
                pagingSource = source
                val items = withContext(Dispatchers.IO) {
                    source.loadInitial()
                }
                val exactChannels = items.filterIsInstance<ChannelInfoItem>().filter {
                    it.name.equals(query.trim(), ignoreCase = true)
                }
                val otherChannels = items.filterIsInstance<ChannelInfoItem>().filter {
                    !it.name.equals(query.trim(), ignoreCase = true)
                }
                channelResults = exactChannels + otherChannels

                val combinedPlaylists = mutableListOf<PlaylistInfoItem>()
                // If channel found, fetch channel's official playlists
                val mainChannel = exactChannels.firstOrNull() ?: channelResults.firstOrNull()
                if (mainChannel != null && !mainChannel.url.isNullOrBlank()) {
                    try {
                        val chLists = withContext(Dispatchers.IO) {
                            ExtractorHelper.getChannelPlaylists(mainChannel.url)
                        }
                        combinedPlaylists.addAll(chLists)
                    } catch (_: Exception) {}
                }
                combinedPlaylists.addAll(searchPlaylists)
                combinedPlaylists.addAll(items.filterIsInstance<PlaylistInfoItem>())

                playlistResults = combinedPlaylists.distinctBy { it.url }
                videoResults = items.filterIsInstance<StreamInfoItem>()
                
                // Save to SearchCache
                val cacheEntities = mutableListOf<com.videhub.data.entity.SearchCacheEntity>()
                var order = 0
                channelResults.forEach { 
                    cacheEntities.add(com.videhub.data.entity.SearchCacheEntity(
                        id = it.url ?: "",
                        query = query,
                        type = "channel",
                        url = it.url ?: "",
                        title = "",
                        thumbnailUrl = "",
                        channelName = it.name ?: "",
                        orderIndex = order++
                    ))
                }
                playlistResults.forEach {
                    cacheEntities.add(com.videhub.data.entity.SearchCacheEntity(
                        id = it.url ?: "",
                        query = query,
                        type = "playlist",
                        url = it.url ?: "",
                        title = it.name ?: "",
                        thumbnailUrl = it.thumbnails?.firstOrNull()?.url ?: "",
                        channelName = it.uploaderName ?: "",
                        viewCount = 0,
                        duration = it.streamCount,
                        orderIndex = order++
                    ))
                }
                videoResults.forEach { 
                    cacheEntities.add(com.videhub.data.entity.SearchCacheEntity(
                        id = it.url ?: "",
                        query = query,
                        type = "video",
                        url = it.url ?: "",
                        title = it.name ?: "",
                        thumbnailUrl = it.thumbnails?.firstOrNull()?.url ?: "",
                        channelName = it.uploaderName ?: "",
                        viewCount = it.viewCount,
                        duration = it.duration,
                        orderIndex = order++
                    ))
                }
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    searchCacheDao.clearAll()
                    searchCacheDao.insertAll(cacheEntities)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                errorText = "Search failed: ${e.message}"
            } finally {
                isSearching = false
            }
        }
    }

    // Load extra playlists when playlists tab selected and none present
    LaunchedEffect(selectedFilterIndex, query) {
        if (selectedFilterIndex == 2 && query.isNotBlank() && playlistResults.isEmpty() && !isSearching && !isPlaylistSearchLoading) {
            isPlaylistSearchLoading = true
            try {
                val lists = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ExtractorHelper.searchPlaylists(query)
                }
                if (lists.isNotEmpty()) {
                    playlistResults = lists
                }
            } catch (e: Exception) {
                // Ignore fallback playlist search error
            } finally {
                isPlaylistSearchLoading = false
            }
        }
    }

    fun loadNextPage() {
        if (isPaginating || pagingSource?.hasMore != true) return
        scope.launch {
            isPaginating = true
            try {
                val items = pagingSource?.loadNextPage() ?: emptyList()
                channelResults = channelResults + items.filterIsInstance<ChannelInfoItem>()
                playlistResults = playlistResults + items.filterIsInstance<PlaylistInfoItem>()
                videoResults = videoResults + items.filterIsInstance<StreamInfoItem>()
            } catch (e: Exception) {}
            isPaginating = false
        }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    
    Column(modifier = Modifier.fillMaxSize().clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null
    ) {
        focusManager.clearFocus()
    }) {
        val placeholderOffset by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isSearchFocused || query.isNotEmpty()) -4f else 0f,
            label = "placeholderOffset"
        )
        val placeholderScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isSearchFocused || query.isNotEmpty()) 0.9f else 1f,
            label = "placeholderScale"
        )
        
        // Search bar
        androidx.compose.material3.Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
                .height(48.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search, 
                    contentDescription = "Search", 
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty() && !isSearchFocused) {
                        Text(
                            "Search YouTube", 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = { newValue -> query = newValue },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = { 
                                focusManager.clearFocus()
                                if (query.isNotBlank()) doSearch() 
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocusRequester)
                            .onFocusChanged { focusState ->
                                isSearchFocused = focusState.isFocused
                            }
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { 
                            query = "" 
                            videoResults = emptyList()
                            channelResults = emptyList()
                            searchFocusRequester.requestFocus()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear, 
                            contentDescription = "Clear", 
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
        if (query.isNotBlank() && (videoResults.isNotEmpty() || channelResults.isNotEmpty() || playlistResults.isNotEmpty())) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf("All", "Videos", "Playlists", "Channels")
                filters.forEachIndexed { index, title ->
                    FilterChip(
                        selected = selectedFilterIndex == index,
                        onClick = { selectedFilterIndex = index },
                        label = { Text(title) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refresh() },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            when {
                (isLoading || isSearching) && !isRefreshing -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(5) { VideoCardShimmer() }
                }
                errorText != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(errorText ?: "Failed to load content", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { doSearch() }) { Text("Retry") }
                }
                isSearchFocused && searchHistory.any { it.query.contains(query, ignoreCase = true) } -> {
                    val filteredHistory = remember(query, searchHistory) { if (query.isBlank()) searchHistory else searchHistory.filter { it.query.contains(query, ignoreCase = true) } }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (query.isBlank()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Search History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { scope.launch { searchHistoryDao.clearAll() } }) {
                                        Text("Clear")
                                    }
                                }
                            }
                        }
                        items(filteredHistory.size, key = { index -> "history_${filteredHistory[index].query}_$index" }) { index ->
                            val history = filteredHistory[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        query = history.query
                                        isSearchFocused = false
                                        focusManager.clearFocus()
                                        doSearch()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .animateItem(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(history.query, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = { scope.launch { searchHistoryDao.deleteSearch(history.query) } }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                videoResults.isNotEmpty() || channelResults.isNotEmpty() || playlistResults.isNotEmpty() -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (selectedFilterIndex) {
                            0 -> {
                                // ── "All" Tab ──────────────────────────────────────────
                                // Channels section
                                if (channelResults.isNotEmpty()) {
                                    item {
                                        Text(
                                            "Channels",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                    itemsIndexed(channelResults, key = { index, channel -> "ch_${channel.url}_$index" }) { index, channel ->
                                        ChannelResultItem(
                                            channel = channel,
                                            onClick = { onChannelClick(channel.url ?: "") },
                                            onPlaylistsClick = { selectedFilterIndex = 2 }
                                        )
                                    }
                                    item { androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                                }

                                // Playlists section
                                if (playlistResults.isNotEmpty()) {
                                    item {
                                        val firstChannel = channelResults.firstOrNull()
                                        val playlistSectionTitle = if (firstChannel != null && !firstChannel.name.isNullOrBlank()) {
                                            "Playlists • by ${firstChannel.name}"
                                        } else {
                                            "Playlists & Mixes"
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                playlistSectionTitle,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (playlistResults.size > 4) {
                                                TextButton(
                                                    onClick = { selectedFilterIndex = 2 },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        "View all (${playlistResults.size})",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    itemsIndexed(playlistResults.take(6), key = { index, pl -> "pl_${pl.url}_$index" }) { index, playlist ->
                                        CompactPlaylistResultItem(
                                            playlist = playlist,
                                            onClick = { onPlaylistClick(playlist.url ?: "") },
                                            onChannelClick = onChannelClick
                                        )
                                    }
                                    item { androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                                }

                                // Videos section
                                if (displayedVideos.isNotEmpty()) {
                                    if (channelResults.isNotEmpty() || playlistResults.isNotEmpty()) {
                                        item {
                                            Text(
                                                "Videos",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                    items(
                                        count = displayedVideos.size,
                                        key = { index -> "${displayedVideos[index].url ?: index.toString()}_$index" }
                                    ) { index ->
                                        val video = displayedVideos[index]
                                        
                                        if (index == displayedVideos.size - 1 && !isPaginating && pagingSource?.hasMore == true) {
                                            LaunchedEffect(Unit) {
                                                loadNextPage()
                                            }
                                        }
                                        
                                        VideoCard(
                                            item = video,
                                            onClick = onVideoClick,
                                            onChannelClick = onChannelClick,
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                            1 -> {
                                // ── "Videos" Tab ───────────────────────────────────────
                                if (displayedVideos.isEmpty()) {
                                    item {
                                        com.videhub.ui.components.EmptyState(
                                            icon = Icons.Default.Search,
                                            title = "No videos",
                                            message = "No videos found for this search."
                                        )
                                    }
                                } else {
                                    items(
                                        count = displayedVideos.size,
                                        key = { index -> "vid_${displayedVideos[index].url ?: index.toString()}_$index" }
                                    ) { index ->
                                        val video = displayedVideos[index]
                                        if (index == displayedVideos.size - 1 && !isPaginating && pagingSource?.hasMore == true) {
                                            LaunchedEffect(Unit) {
                                                loadNextPage()
                                            }
                                        }
                                        VideoCard(
                                            item = video,
                                            onClick = onVideoClick,
                                            onChannelClick = onChannelClick,
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                            2 -> {
                                // ── "Playlists" Tab ────────────────────────────────────
                                if (isPlaylistSearchLoading) {
                                    items(4) { VideoCardShimmer() }
                                } else if (playlistResults.isEmpty()) {
                                    item {
                                        com.videhub.ui.components.EmptyState(
                                            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                            title = "No playlists",
                                            message = "No playlists found for this query."
                                        )
                                    }
                                } else {
                                    items(
                                        count = playlistResults.size,
                                        key = { index -> "tab_pl_${playlistResults[index].url ?: index.toString()}_$index" }
                                    ) { index ->
                                        val playlist = playlistResults[index]
                                        OnlinePlaylistItemCard(
                                            playlist = playlist,
                                            onClick = { onPlaylistClick(playlist.url ?: "") },
                                            onChannelClick = onChannelClick
                                        )
                                    }
                                }
                            }
                            3 -> {
                                // ── "Channels" Tab ─────────────────────────────────────
                                if (channelResults.isEmpty()) {
                                    item {
                                        com.videhub.ui.components.EmptyState(
                                            icon = Icons.Default.Search,
                                            title = "No channels",
                                            message = "No channels found for this query."
                                        )
                                    }
                                } else {
                                    itemsIndexed(channelResults, key = { index, channel -> "tab_ch_${channel.url}_$index" }) { index, channel ->
                                        ChannelResultItem(
                                            channel = channel,
                                            onClick = { onChannelClick(channel.url ?: "") }
                                        )
                                    }
                                }
                            }
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
                query.isNotBlank() && !isSearching -> {
                    com.videhub.ui.components.EmptyState(
                        icon = Icons.Default.Search,
                        title = "No results found",
                        message = "Try searching for something else."
                    )
                }
                else -> {
                    com.videhub.ui.components.EmptyState(
                        icon = Icons.Default.Search,
                        title = "Search YouTube",
                        message = "Search for videos and channels"
                    )
                }
            }
        } // End PullToRefreshBox
        
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

@Composable
private fun ChannelResultItem(
    channel: ChannelInfoItem,
    onClick: () -> Unit,
    onPlaylistsClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    var isSubscribed by remember { mutableStateOf(false) }
    
    val channelId = channel.url ?: ""

    LaunchedEffect(channelId) {
        if (channelId.isNotBlank()) {
            isSubscribed = db.channelDao().isSubscribed(channelId)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = androidx.compose.material3.ripple()
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val avatar = channel.thumbnails?.firstOrNull()?.url
        if (!avatar.isNullOrBlank()) {
            AsyncImage(
                model = avatar,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = channel.name?.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name ?: "Unknown Channel",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            channel.subscriberCount.takeIf { it > 0 }?.let {
                Text(
                    text = "${formatCount(it)} subscribers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (channelId.isNotBlank()) {
            AnimatedSubscribeButton(
                isSubscribed = isSubscribed,
                onClick = {
                    scope.launch {
                        if (isSubscribed) {
                            db.channelDao().deleteById(channelId)
                            isSubscribed = false
                            Toast.makeText(context, "Unsubscribed", Toast.LENGTH_SHORT).show()
                        } else {
                            db.channelDao().insert(
                                ChannelEntity(
                                    channelId = channelId,
                                    name = channel.name ?: "Unknown Channel",
                                    thumbnailUrl = avatar ?: "none"
                                )
                            )
                            isSubscribed = true
                            Toast.makeText(context, "Subscribed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

private fun formatCount(count: Long): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}
