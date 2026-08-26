package com.videhub.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.videhub.data.AppDatabase
import com.videhub.data.entity.ChannelEntity
import com.videhub.data.entity.SearchCacheEntity
import com.videhub.data.entity.SearchHistoryEntity
import com.videhub.extractor.ExtractorHelper
import com.videhub.extractor.ListExtractorPagingSource
import com.videhub.ui.components.AddToPlaylistDialog
import com.videhub.ui.components.AnimatedSubscribeButton
import com.videhub.ui.components.CompactPlaylistResultItem
import com.videhub.ui.components.EmptyState
import com.videhub.ui.components.OnlinePlaylistItemCard
import com.videhub.ui.components.ShortSearchCard
import com.videhub.ui.components.ShortsSearchShelf
import com.videhub.ui.components.ShortsShelfShimmer
import com.videhub.ui.components.VideoCard
import com.videhub.ui.components.VideoCardShimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

private data class ExploreCategory(
    val title: String,
    val query: String,
    val icon: ImageVector,
    val gradientColors: List<Color>
)

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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    var query by remember { mutableStateOf(sharedViewModel.exploreQueryCache) }

    var videoResults by remember {
        mutableStateOf<List<StreamInfoItem>>(
            sharedViewModel.exploreVideosCache?.filterIsInstance<StreamInfoItem>() ?: emptyList()
        )
    }
    var shortsResults by remember {
        mutableStateOf<List<StreamInfoItem>>(emptyList())
    }
    var channelResults by remember {
        mutableStateOf<List<ChannelInfoItem>>(
            sharedViewModel.exploreVideosCache?.filterIsInstance<ChannelInfoItem>() ?: emptyList()
        )
    }
    var playlistResults by remember {
        mutableStateOf<List<PlaylistInfoItem>>(
            sharedViewModel.exploreVideosCache?.filterIsInstance<PlaylistInfoItem>() ?: emptyList()
        )
    }

    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0: All, 1: Videos, 2: Shorts, 3: Playlists, 4: Channels
    var isPlaylistSearchLoading by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var playlistDialogVideo by remember { mutableStateOf<StreamInfoItem?>(null) }
    var pagingSource by remember { mutableStateOf<ListExtractorPagingSource?>(sharedViewModel.explorePagingSourceCache) }
    var isPaginating by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    val db = remember { AppDatabase.getDatabase(context) }
    val searchHistoryDao = db.searchHistoryDao()
    val searchHistory by searchHistoryDao.getAllSearchHistory().collectAsStateWithLifecycle(initialValue = emptyList())
    val searchCacheDao = db.searchCacheDao()

    // Live search suggestions with debounce
    LaunchedEffect(query) {
        if (query.trim().length >= 2) {
            delay(180)
            try {
                val fetched = withContext(Dispatchers.IO) {
                    ExtractorHelper.getSearchSuggestions(query.trim())
                }
                suggestions = fetched
            } catch (_: Exception) {
                suggestions = emptyList()
            }
        } else {
            suggestions = emptyList()
        }
    }

    // Load initial cache if available
    LaunchedEffect(Unit) {
        if (sharedViewModel.exploreVideosCache == null) {
            val cached = searchCacheDao.getAll()
            if (cached.isNotEmpty()) {
                val cachedChannels = cached.filter { it.type == "channel" }.map {
                    ChannelInfoItem(1, it.url, it.channelName)
                }
                val cachedPlaylists = cached.filter { it.type == "playlist" }.map {
                    val p = PlaylistInfoItem(1, it.url, it.title)
                    p.uploaderName = it.channelName
                    p.thumbnails = listOf(
                        org.schabi.newpipe.extractor.Image(
                            it.thumbnailUrl,
                            50,
                            50,
                            org.schabi.newpipe.extractor.Image.ResolutionLevel.UNKNOWN
                        )
                    )
                    p.streamCount = it.duration
                    p
                }
                val cachedVideos = cached.filter { it.type == "video" }.map {
                    val item = StreamInfoItem(
                        1,
                        it.url,
                        it.title,
                        org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM
                    )
                    item.uploaderName = it.channelName
                    item.thumbnails = listOf(
                        org.schabi.newpipe.extractor.Image(
                            it.thumbnailUrl,
                            50,
                            50,
                            org.schabi.newpipe.extractor.Image.ResolutionLevel.UNKNOWN
                        )
                    )
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

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = sharedViewModel.exploreScrollIndexCache,
        initialFirstVisibleItemScrollOffset = sharedViewModel.exploreScrollOffsetCache
    )

    fun doSearch(customQuery: String? = null) {
        val targetQuery = (customQuery ?: query).trim()
        if (targetQuery.isBlank()) return
        query = targetQuery
        focusManager.clearFocus()
        keyboardController?.hide()
        isSearchFocused = false
        searchJob?.cancel()
        searchJob = scope.launch {
            searchHistoryDao.insertSearch(SearchHistoryEntity(targetQuery))
            isSearching = true
            errorText = null
            videoResults = emptyList()
            shortsResults = emptyList()
            channelResults = emptyList()
            playlistResults = emptyList()
            try {
                val (source, searchPlaylists, searchShorts) = coroutineScope {
                    val sourceDeferred = async(Dispatchers.IO) {
                        ExtractorHelper.getSearchPagingSource(targetQuery)
                    }
                    val playlistsDeferred = async(Dispatchers.IO) {
                        try {
                            ExtractorHelper.searchPlaylists(targetQuery)
                        } catch (_: Exception) {
                            emptyList<PlaylistInfoItem>()
                        }
                    }
                    val shortsDeferred = async(Dispatchers.IO) {
                        try {
                            ExtractorHelper.getShortsFeed(targetQuery, maxPages = 1)
                        } catch (_: Exception) {
                            emptyList<StreamInfoItem>()
                        }
                    }
                    Triple(sourceDeferred.await(), playlistsDeferred.await(), shortsDeferred.await())
                }
                pagingSource = source
                val items = withContext(Dispatchers.IO) {
                    source.loadInitial()
                }
                val exactChannels = items.filterIsInstance<ChannelInfoItem>().filter {
                    it.name.equals(targetQuery, ignoreCase = true)
                }
                val otherChannels = items.filterIsInstance<ChannelInfoItem>().filter {
                    !it.name.equals(targetQuery, ignoreCase = true)
                }
                channelResults = exactChannels + otherChannels

                val combinedPlaylists = mutableListOf<PlaylistInfoItem>()
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

                // Separate Shorts from standard long-form videos
                val allStreams = items.filterIsInstance<StreamInfoItem>()
                val (extractedShorts, extractedLongVideos) = allStreams.partition { stream ->
                    val dur = stream.duration
                    val name = stream.name ?: ""
                    val url = stream.url ?: ""
                    val isShortDuration = dur in 1..80
                    val isShortTagged = name.contains("#short", ignoreCase = true) || url.contains("/shorts/")
                    isShortTagged || isShortDuration
                }

                shortsResults = (searchShorts + extractedShorts).distinctBy { it.url }
                videoResults = extractedLongVideos

                // Cache for fast return
                val cacheEntities = mutableListOf<SearchCacheEntity>()
                var order = 0
                channelResults.forEach {
                    cacheEntities.add(
                        SearchCacheEntity(
                            id = it.url ?: "",
                            query = targetQuery,
                            type = "channel",
                            url = it.url ?: "",
                            title = "",
                            thumbnailUrl = "",
                            channelName = it.name ?: "",
                            orderIndex = order++
                        )
                    )
                }
                playlistResults.forEach {
                    cacheEntities.add(
                        SearchCacheEntity(
                            id = it.url ?: "",
                            query = targetQuery,
                            type = "playlist",
                            url = it.url ?: "",
                            title = it.name ?: "",
                            thumbnailUrl = it.thumbnails?.firstOrNull()?.url ?: "",
                            channelName = it.uploaderName ?: "",
                            viewCount = 0,
                            duration = it.streamCount,
                            orderIndex = order++
                        )
                    )
                }
                videoResults.forEach {
                    cacheEntities.add(
                        SearchCacheEntity(
                            id = it.url ?: "",
                            query = targetQuery,
                            type = "video",
                            url = it.url ?: "",
                            title = it.name ?: "",
                            thumbnailUrl = it.thumbnails?.firstOrNull()?.url ?: "",
                            channelName = it.uploaderName ?: "",
                            viewCount = it.viewCount,
                            duration = it.duration,
                            orderIndex = order++
                        )
                    )
                }
                scope.launch(Dispatchers.IO) {
                    searchCacheDao.clearAll()
                    searchCacheDao.insertAll(cacheEntities)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                errorText = "Search failed: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                isSearching = false
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
            } catch (_: Exception) {}
            isPaginating = false
        }
    }

    val exploreCategories = remember {
        listOf(
            ExploreCategory("Trending", "trending", Icons.Default.Whatshot, listOf(Color(0xFFFF5722), Color(0xFFFF9800))),
            ExploreCategory("Music", "music hits", Icons.Default.MusicNote, listOf(Color(0xFF9C27B0), Color(0xFFE91E63))),
            ExploreCategory("Gaming", "gaming highlights", Icons.Default.SportsEsports, listOf(Color(0xFF3F51B5), Color(0xFF2196F3))),
            ExploreCategory("Podcasts", "popular podcast", Icons.Default.Podcasts, listOf(Color(0xFF009688), Color(0xFF4CAF50))),
            ExploreCategory("Movies", "movie trailers", Icons.Default.Movie, listOf(Color(0xFFE65100), Color(0xFFF57C00))),
            ExploreCategory("Tech", "technology reviews", Icons.Default.Computer, listOf(Color(0xFF1976D2), Color(0xFF00BCD4))),
            ExploreCategory("Sports", "sports highlights", Icons.Default.SportsSoccer, listOf(Color(0xFF388E3C), Color(0xFF8BC34A))),
            ExploreCategory("Learning", "science tutorials", Icons.Default.School, listOf(Color(0xFF5D4037), Color(0xFF8D6E63)))
        )
    }

    val hasResults = videoResults.isNotEmpty() || shortsResults.isNotEmpty() || channelResults.isNotEmpty() || playlistResults.isNotEmpty()
    val isShowingSuggestions = isSearchFocused && (query.isNotBlank() || searchHistory.isNotEmpty())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
                keyboardController?.hide()
                isSearchFocused = false
            }
    ) {
        // ── Top Bar with Modern Material 3 SearchBar ────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                // Search Input Box
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .shadow(
                            elevation = if (isSearchFocused) 4.dp else 1.dp,
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                    ) {
                        // Leading Icon: Back arrow if focused, Search icon otherwise
                        if (isSearchFocused) {
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    isSearchFocused = false
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Text Field & Placeholder
                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search YouTube, channels, playlists...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { newValue -> query = newValue },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
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

                        // Trailing Clear Button
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    query = ""
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Filter Chips Row (Visible when results or query active)
                if (hasResults || query.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        val filters = listOf(
                            Triple("All", Icons.Default.AutoAwesome, 0),
                            Triple("Videos", Icons.Default.PlayArrow, 1),
                            Triple("Shorts", Icons.Default.FlashOn, 2),
                            Triple("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay, 3),
                            Triple("Channels", Icons.Default.AccountCircle, 4)
                        )
                        items(filters) { (title, icon, index) ->
                            FilterChip(
                                selected = selectedFilterIndex == index,
                                onClick = { selectedFilterIndex = index },
                                label = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selectedFilterIndex == index) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }

        // ── Main Content Area ───────────────────────────────────────────────
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (query.isNotBlank()) doSearch()
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            when {
                // Loading Shimmer
                (isLoading || isSearching) && !isRefreshing -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                    ) {
                        item {
                            ShortsShelfShimmer()
                        }
                        items(4) {
                            VideoCardShimmer()
                        }
                    }
                }

                // Error State
                errorText != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Search Encountered an Issue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorText ?: "Failed to fetch search results.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { doSearch() },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry Search")
                        }
                    }
                }

                // Live Suggestions & Search History Overlay
                isShowingSuggestions -> {
                    val filteredHistory = remember(query, searchHistory) {
                        if (query.isBlank()) searchHistory else searchHistory.filter {
                            it.query.contains(query, ignoreCase = true)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        // Search History Section Header
                        if (filteredHistory.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Recent Searches",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    TextButton(
                                        onClick = { scope.launch { searchHistoryDao.clearAll() } },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Clear all", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            // Recent Search Items
                            items(
                                items = filteredHistory,
                                key = { "hist_${it.query}" }
                            ) { history ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            doSearch(history.query)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = history.query,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    // Append Arrow: Sets text into search bar without executing immediately
                                    IconButton(
                                        onClick = {
                                            query = history.query
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Append to search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch { searchHistoryDao.deleteSearch(history.query) }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Live Autocomplete Suggestions
                        if (suggestions.isNotEmpty()) {
                            val historyQueries = filteredHistory.map { it.query.lowercase() }.toSet()
                            val cleanSuggestions = suggestions.filter { it.lowercase() !in historyQueries }

                            if (cleanSuggestions.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Suggestions",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                items(
                                    items = cleanSuggestions,
                                    key = { "sugg_$it" }
                                ) { suggestion ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                doSearch(suggestion)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = suggestion,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        IconButton(
                                            onClick = {
                                                query = suggestion
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Append to search",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Active Results Content
                hasResults -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        when (selectedFilterIndex) {
                            0 -> {
                                // ── "All" Tab ───────────────────────────────────────
                                // Top Matching Channel Highlight Card
                                if (channelResults.isNotEmpty()) {
                                    val topChannel = channelResults.first()
                                    item(key = "featured_channel_header") {
                                        Text(
                                            text = "Channel",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                    item(key = "featured_channel_card") {
                                        FeaturedChannelCard(
                                            channel = topChannel,
                                            onClick = { onChannelClick(topChannel.url ?: "") }
                                        )
                                    }
                                    // Other channels if any
                                    if (channelResults.size > 1) {
                                        itemsIndexed(
                                            channelResults.drop(1).take(3),
                                            key = { index, ch -> "sub_ch_${ch.url}_$index" }
                                        ) { _, channel ->
                                            ChannelResultItem(
                                                channel = channel,
                                                onClick = { onChannelClick(channel.url ?: "") }
                                            )
                                        }
                                    }
                                    item {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                // Top Videos (first 2) before Shorts shelf
                                val topVideos = videoResults.take(2)
                                val remainingVideos = videoResults.drop(2)

                                if (topVideos.isNotEmpty()) {
                                    item(key = "top_videos_section_header") {
                                        Text(
                                            text = "Videos",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                    itemsIndexed(
                                        topVideos,
                                        key = { index, item -> item.url ?: "top_vid_$index" },
                                        contentType = { _, _ -> "video_card" }
                                    ) { _, video ->
                                        VideoCard(
                                            item = video,
                                            onClick = onVideoClick,
                                            onChannelClick = onChannelClick
                                        )
                                    }
                                }

                                // YouTube-style Shorts Search Shelf
                                if (shortsResults.isNotEmpty()) {
                                    item(key = "shorts_search_shelf") {
                                        ShortsSearchShelf(
                                            shorts = shortsResults,
                                            onShortClick = onVideoClick,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            onViewAllClick = { selectedFilterIndex = 2 }
                                        )
                                    }
                                }

                                // Remaining Videos Section
                                if (remainingVideos.isNotEmpty()) {
                                    if (topVideos.isEmpty() && shortsResults.isNotEmpty()) {
                                        item(key = "remaining_videos_section_header") {
                                            Text(
                                                text = "More Videos",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                    itemsIndexed(
                                        remainingVideos,
                                        key = { index, item -> item.url ?: "rem_vid_$index" },
                                        contentType = { _, _ -> "video_card" }
                                    ) { index, video ->
                                        if (index == remainingVideos.size - 1 && !isPaginating && pagingSource?.hasMore == true) {
                                            LaunchedEffect(Unit) {
                                                loadNextPage()
                                            }
                                        }
                                        VideoCard(
                                            item = video,
                                            onClick = onVideoClick,
                                            onChannelClick = onChannelClick
                                        )
                                    }
                                }

                                // Playlists Section
                                if (playlistResults.isNotEmpty()) {
                                    item(key = "playlists_section_header") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Playlists",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (playlistResults.size > 3) {
                                                TextButton(
                                                    onClick = { selectedFilterIndex = 3 },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
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

                                    // Compact Playlists
                                    itemsIndexed(
                                        playlistResults.take(4),
                                        key = { index, pl -> "pl_${pl.url}_$index" }
                                    ) { _, playlist ->
                                        CompactPlaylistResultItem(
                                            playlist = playlist,
                                            onClick = { onPlaylistClick(playlist.url ?: "") },
                                            onChannelClick = onChannelClick
                                        )
                                    }
                                }
                            }

                            1 -> {
                                // ── "Videos" Tab ────────────────────────────────────
                                if (videoResults.isEmpty()) {
                                    item {
                                        EmptyState(
                                            icon = Icons.Default.PlayCircleOutline,
                                            title = "No videos found",
                                            message = "No video results found for '$query'."
                                        )
                                    }
                                } else {
                                    itemsIndexed(
                                        videoResults,
                                        key = { index, item -> item.url ?: "vid_tab_$index" },
                                        contentType = { _, _ -> "video_card" }
                                    ) { index, video ->
                                        if (index == videoResults.size - 1 && !isPaginating && pagingSource?.hasMore == true) {
                                            LaunchedEffect(Unit) {
                                                loadNextPage()
                                            }
                                        }
                                        VideoCard(
                                            item = video,
                                            onClick = onVideoClick,
                                            onChannelClick = onChannelClick
                                        )
                                    }
                                }
                            }

                            2 -> {
                                // ── "Shorts" Tab ────────────────────────────────────
                                if (shortsResults.isEmpty()) {
                                    item {
                                        EmptyState(
                                            icon = Icons.Default.FlashOn,
                                            title = "No Shorts found",
                                            message = "No Shorts found for '$query'."
                                        )
                                    }
                                } else {
                                    itemsIndexed(
                                        shortsResults.chunked(2),
                                        key = { idx, pair -> "short_pair_${pair.firstOrNull()?.url}_$idx" }
                                    ) { _, pair ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            pair.forEach { short ->
                                                ShortSearchCard(
                                                    short = short,
                                                    onClick = {
                                                        val thumb = short.thumbnails?.firstOrNull()?.url ?: ""
                                                        onVideoClick(short.url ?: "", short.name ?: "Short", thumb)
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            if (pair.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }

                            3 -> {
                                // ── "Playlists" Tab ─────────────────────────────────
                                if (playlistResults.isEmpty()) {
                                    item {
                                        EmptyState(
                                            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                            title = "No playlists found",
                                            message = "No playlists matching '$query'."
                                        )
                                    }
                                } else {
                                    itemsIndexed(
                                        playlistResults,
                                        key = { index, pl -> "tab_pl_${pl.url}_$index" }
                                    ) { _, playlist ->
                                        OnlinePlaylistItemCard(
                                            playlist = playlist,
                                            onClick = { onPlaylistClick(playlist.url ?: "") },
                                            onChannelClick = onChannelClick
                                        )
                                    }
                                }
                            }

                            4 -> {
                                // ── "Channels" Tab ──────────────────────────────────
                                if (channelResults.isEmpty()) {
                                    item {
                                        EmptyState(
                                            icon = Icons.Default.PersonSearch,
                                            title = "No channels found",
                                            message = "No channel results found for '$query'."
                                        )
                                    }
                                } else {
                                    itemsIndexed(
                                        channelResults,
                                        key = { index, channel -> "tab_ch_${channel.url}_$index" }
                                    ) { _, channel ->
                                        ChannelResultItem(
                                            channel = channel,
                                            onClick = { onChannelClick(channel.url ?: "") }
                                        )
                                    }
                                }
                            }
                        }

                        // Pagination Loader
                        if (isPaginating) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(26.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.5.dp
                                    )
                                }
                            }
                        }
                    }
                }

                // Initial / Empty Query Discovery Landing Screen
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        // Recent Searches Chips
                        if (searchHistory.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Recent Searches",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    TextButton(
                                        onClick = { scope.launch { searchHistoryDao.clearAll() } },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Clear all", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            item {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    items(searchHistory.take(8)) { history ->
                                        SuggestionChip(
                                            onClick = { doSearch(history.query) },
                                            label = {
                                                Text(
                                                    text = history.query,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            icon = {
                                                Icon(
                                                    Icons.Default.History,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }

                        // Explore Topics Header
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Explore Topics",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Explore Topics 2-Column Grid
                        val chunkedCategories = exploreCategories.chunked(2)
                        items(chunkedCategories) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { cat ->
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(80.dp)
                                            .clickable {
                                                doSearch(cat.query)
                                            },
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(14.dp)
                                        ) {
                                            // Background decorative icon accent
                                            Icon(
                                                imageVector = cat.icon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .align(Alignment.BottomEnd)
                                            )
                                            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                                                Icon(
                                                    imageVector = cat.icon,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = cat.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            // Helpful Search Tips Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = "Search Tip",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Search for your favorite creators, video titles, or paste channel links directly.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        } // End PullToRefreshBox

        // Add to Playlist Dialog
        playlistDialogVideo?.let { video ->
            AddToPlaylistDialog(
                videoUrl = video.url ?: "",
                title = video.name ?: "",
                thumbnailUrl = video.thumbnails?.firstOrNull()?.url ?: "",
                channelName = video.uploaderName ?: "",
                onDismiss = { playlistDialogVideo = null }
            )
        }
    }
}

/**
 * Featured High-Priority Channel Card for top search matches
 */
@Composable
private fun FeaturedChannelCard(
    channel: ChannelInfoItem,
    onClick: () -> Unit
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val avatar = channel.thumbnails?.firstOrNull()?.url
            if (!avatar.isNullOrBlank()) {
                AsyncImage(
                    model = avatar,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = channel.name?.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name ?: "Unknown Channel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subCount = channel.subscriberCount
                val countText = if (subCount > 0) "${formatCount(subCount)} subscribers" else "Channel"
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!channel.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = channel.description ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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
}

/**
 * Standard Channel Row Item for Channel lists
 */
@Composable
private fun ChannelResultItem(
    channel: ChannelInfoItem,
    onClick: () -> Unit
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
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                    .size(50.dp)
                    .clip(CircleShape)
            )
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = channel.name?.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name ?: "Unknown Channel",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
