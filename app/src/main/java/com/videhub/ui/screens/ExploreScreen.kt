package com.videhub.ui.screens

import androidx.activity.compose.BackHandler
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
    onPlaylistClick: (String) -> Unit = {},
    onBack: () -> Unit = {}
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

    LaunchedEffect(videoResults) {
        if (videoResults.isNotEmpty()) {
            videoResults.forEach { video ->
                com.videhub.utils.ChannelLiveStatusManager.processStreamItem(video)
            }
        }
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

    fun clearSearchAndResults() {
        query = ""
        videoResults = emptyList()
        shortsResults = emptyList()
        channelResults = emptyList()
        playlistResults = emptyList()
        errorText = null
        sharedViewModel.exploreQueryCache = ""
        sharedViewModel.exploreVideosCache = null
        sharedViewModel.explorePagingSourceCache = null
        scope.launch(Dispatchers.IO) {
            searchCacheDao.clearAll()
        }
    }

    val hasResultsState = videoResults.isNotEmpty() || shortsResults.isNotEmpty() || channelResults.isNotEmpty() || playlistResults.isNotEmpty()

    BackHandler {
        if (isSearchFocused) {
            focusManager.clearFocus()
            keyboardController?.hide()
            isSearchFocused = false
        } else if (query.isNotBlank() || hasResultsState) {
            clearSearchAndResults()
        } else {
            onBack()
        }
    }

    // Live search suggestions with smart query normalization and debounce
    LaunchedEffect(query) {
        val cleanQuery = com.videhub.ml.SmartMediaAnalyzer.normalizeSearchQuery(query)
        if (cleanQuery.length >= 2) {
            delay(180)
            try {
                val fetched = withContext(Dispatchers.IO) {
                    ExtractorHelper.getSearchSuggestions(cleanQuery)
                }
                suggestions = fetched
            } catch (_: Exception) {
                suggestions = emptyList()
            }
        } else {
            suggestions = emptyList()
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

            var searchQuery = targetQuery.trim('\'', '"', ' ')

            // Check if query is a YouTube video URL or video ID link
            val isVideoLink = searchQuery.contains("youtu.be/") ||
                    searchQuery.contains("youtube.com/watch") ||
                    searchQuery.contains("youtube.com/shorts/") ||
                    searchQuery.startsWith("https://youtu") ||
                    searchQuery.startsWith("http://youtu") ||
                    searchQuery.startsWith("https://www.youtube") ||
                    searchQuery.startsWith("https://youtube") ||
                    (searchQuery.length in 10..15 && !searchQuery.contains(" ") && (searchQuery.contains("-") || searchQuery.contains("_")))

            if (isVideoLink) {
                try {
                    var extractedUrl = searchQuery
                    if (!extractedUrl.startsWith("http")) {
                        val httpIdx = extractedUrl.indexOf("http")
                        if (httpIdx != -1) {
                            extractedUrl = extractedUrl.substring(httpIdx)
                        } else if (extractedUrl.contains("youtu.be/")) {
                            extractedUrl = "https://" + extractedUrl.substring(extractedUrl.indexOf("youtu.be/"))
                        } else if (extractedUrl.contains("youtube.com/")) {
                            extractedUrl = "https://" + extractedUrl.substring(extractedUrl.indexOf("youtube.com/"))
                        } else if (!extractedUrl.contains("/")) {
                            extractedUrl = "https://www.youtube.com/watch?v=$extractedUrl"
                        }
                    }

                    val info = withContext(Dispatchers.IO) {
                        ExtractorHelper.getStreamInfo(extractedUrl)
                    }
                    val item = StreamInfoItem(
                        1,
                        info.url ?: extractedUrl,
                        info.name ?: "YouTube Video",
                        org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM
                    )
                    item.uploaderName = info.uploaderName ?: "YouTube"
                    item.thumbnails = info.thumbnails
                    item.viewCount = info.viewCount
                    item.duration = info.duration
                    videoResults = listOf(item)
                    isSearching = false
                    return@launch
                } catch (_: Exception) {
                    val cleanKeywords = searchQuery
                        .replace(Regex("https?://\\S+"), "")
                        .replace(Regex("[^a-zA-Z0-9 ]"), " ")
                        .trim()
                    if (cleanKeywords.isNotBlank()) {
                        searchQuery = cleanKeywords
                    }
                }
            }

            try {
                val (source, searchPlaylists) = coroutineScope {
                    val sourceDeferred = async(Dispatchers.IO) {
                        ExtractorHelper.getSearchPagingSource(searchQuery)
                    }
                    val playlistsDeferred = async(Dispatchers.IO) {
                        try {
                            ExtractorHelper.searchPlaylists(targetQuery)
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

                // Exclude any shorts completely
                val allStreams = items.filterIsInstance<StreamInfoItem>()
                videoResults = allStreams.filter { stream ->
                    val dur = stream.duration
                    val name = stream.name ?: ""
                    val url = stream.url ?: ""
                    val isShortDuration = dur in 1..80
                    val isShortTagged = name.contains("#short", ignoreCase = true) || url.contains("/shorts/")
                    !(isShortTagged || isShortDuration)
                }
                shortsResults = emptyList()

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
        modifier = Modifier.fillMaxSize()
    ) {
        // ── Top Bar with Modern Material 3 SearchBar ────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                // YouTube-style Search Bar Layout: Back Button -> Search Input Pill -> Voice Search Mic Button
                val voiceSearchLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == android.app.Activity.RESULT_OK) {
                        val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                        if (!spokenText.isNullOrBlank()) {
                            query = spokenText
                            doSearch(spokenText)
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    // 1. Back Arrow (Left, outside search input box)
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            isSearchFocused = false
                            if (query.isNotBlank() || hasResultsState) {
                                clearSearchAndResults()
                            }
                            onBack()
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 2. Search Input Box Pill (Middle, rounded capsule container)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = "Search YouTube",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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

                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        clearSearchAndResults()
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

                    Spacer(modifier = Modifier.width(8.dp))

                    // 3. Voice Search Mic Button (Right, circular button outside input pill)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(46.dp)
                    ) {
                        IconButton(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search YouTube")
                                    }
                                    voiceSearchLauncher.launch(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Voice search not supported on device", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Search",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Filter Chips Row (Visible when results or query active)
                if (hasResults || query.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        val filters = listOf(
                            Triple("All", Icons.Default.AutoAwesome, 0),
                            Triple("Videos", Icons.Default.PlayArrow, 1),
                            Triple("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay, 2),
                            Triple("Channels", Icons.Default.AccountCircle, 3)
                        )
                        items(filters) { (title, icon, index) ->
                            FilterChip(
                                selected = selectedFilterIndex == index,
                                onClick = { selectedFilterIndex = index },
                                label = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selectedFilterIndex == index) FontWeight.Bold else FontWeight.SemiBold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                shape = RoundedCornerShape(18.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.height(36.dp)
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
                // Loading Shimmer matching selected search tab
                isLoading || isSearching || isRefreshing -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                    ) {
                        when (selectedFilterIndex) {
                            3 -> {
                                items(6, key = { "explore_channel_shimmer_$it" }) {
                                    com.videhub.ui.components.ChannelResultCardShimmer()
                                }
                            }
                            0 -> {
                                item(key = "explore_top_channel_shimmer") {
                                    com.videhub.ui.components.ChannelResultCardShimmer()
                                }
                                items(4, key = { "explore_all_video_shimmer_$it" }) {
                                    VideoCardShimmer()
                                }
                            }
                            else -> {
                                items(5, key = { "explore_video_shimmer_$it" }) {
                                    VideoCardShimmer()
                                }
                            }
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

                            // Recent Search Items (Exact YouTube UI: Clock icon, query text, diagonal insert arrow)
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
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = Color(0xFFAAAAAA),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(18.dp))
                                    Text(
                                        text = history.query,
                                        fontSize = 15.5.sp,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Normal,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    // Diagonal insert arrow: sets text into search bar without executing immediately
                                    IconButton(
                                        onClick = {
                                            query = history.query
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = com.videhub.R.drawable.ic_arrow_insert),
                                            contentDescription = "Insert into search",
                                            tint = Color(0xFFAAAAAA),
                                            modifier = Modifier.size(19.dp)
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
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                painter = androidx.compose.ui.res.painterResource(id = com.videhub.R.drawable.ic_arrow_insert),
                                                contentDescription = "Insert into search",
                                                tint = Color(0xFFAAAAAA),
                                                modifier = Modifier.size(19.dp)
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
                                // Top Matching Channels
                                if (channelResults.isNotEmpty()) {
                                    item(key = "featured_channel_header") {
                                        Text(
                                            text = "Channel",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                    itemsIndexed(
                                        channelResults.take(3),
                                        key = { index, ch -> "ch_${ch.url}_$index" }
                                    ) { _, channel ->
                                        ChannelResultItem(
                                            channel = channel,
                                            onClick = { onChannelClick(channel.url ?: "") },
                                            onVideoClick = onVideoClick
                                        )
                                    }
                                    item {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                // Videos Section
                                if (videoResults.isNotEmpty()) {
                                    item(key = "videos_section_header") {
                                        Text(
                                            text = "Videos",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                    itemsIndexed(
                                        videoResults,
                                        key = { index, item -> item.url ?: "all_vid_$index" },
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
                                                    onClick = { selectedFilterIndex = 2 },
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

                            3 -> {
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
                                            onClick = { onChannelClick(channel.url ?: "") },
                                            onVideoClick = onVideoClick
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

                // Initial / Empty Query Landing Screen (Recent Searches only, no Explore Topics grid)
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

                            items(searchHistory) { history ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { doSearch(history.query) }
                                        .padding(horizontal = 4.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = Color(0xFFAAAAAA),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(18.dp))
                                    Text(
                                        text = history.query,
                                        fontSize = 15.5.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            query = history.query
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = com.videhub.R.drawable.ic_arrow_insert),
                                            contentDescription = "Insert into search",
                                            tint = Color(0xFFAAAAAA),
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
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
 * Exact YouTube Search Channel Card Item as shown in user attachment:
 * - Dark rounded container (#1E1E1E / 16.dp corners)
 * - 64dp Avatar with red live ring and LIVE pill badge
 * - Channel title in bold white
 * - Subscriber count & description snippet
 * - Pill subscribe button (white background, black text when not subscribed; dark grey when subscribed)
 */
@Composable
private fun ChannelResultItem(
    channel: ChannelInfoItem,
    onClick: () -> Unit,
    onVideoClick: ((String, String, String) -> Unit)? = null
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

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val avatar = channel.thumbnails?.firstOrNull()?.url
            val liveKeys by com.videhub.utils.ChannelLiveStatusManager.liveChannelKeys.collectAsState()
            val isChannelBroadcastingLive = remember(liveKeys, channelId, channel.name) {
                com.videhub.utils.ChannelLiveStatusManager.isChannelLive(channelId, channel.name)
            }
            LaunchedEffect(channelId, channel.name) {
                if (channelId.isNotBlank()) {
                    com.videhub.utils.ChannelLiveStatusManager.checkChannelLiveStatus(channelId, channel.name)
                }
            }

            com.videhub.ui.components.LiveAvatar(
                avatarUrl = avatar,
                channelName = channel.name ?: "?",
                isLive = isChannelBroadcastingLive,
                size = 64.dp,
                onClick = {
                    val topLive = com.videhub.utils.ChannelLiveStatusManager.getTopLiveStream(channelId, channel.name)
                    if (isChannelBroadcastingLive && topLive != null && topLive.videoUrl.isNotBlank() && onVideoClick != null) {
                        onVideoClick(topLive.videoUrl, topLive.title, topLive.thumbnailUrl.ifBlank { avatar ?: "" })
                    } else {
                        onClick()
                    }
                }
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name ?: "Unknown Channel",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subCount = channel.subscriberCount
                val countText = if (subCount > 0) com.videhub.utils.FormatHelper.formatSubscriberCount(subCount) else "Channel"
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = Color(0xFFAAAAAA),
                    maxLines = 1
                )
                if (!channel.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = channel.description ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = Color(0xFF888888),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (channelId.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isSubscribed) Color(0xFF272727) else Color.White,
                    modifier = Modifier
                        .height(34.dp)
                        .clickable {
                            scope.launch {
                                if (isSubscribed) {
                                    db.channelDao().deleteById(channelId)
                                    isSubscribed = false
                                    Toast.makeText(context, "Unsubscribed", Toast.LENGTH_SHORT).show()
                                } else {
                                    val validAvatar = avatar?.takeIf { !com.videhub.utils.AvatarHelper.isVideoThumbnail(it) }
                                        ?: com.videhub.utils.AvatarHelper.resolveRealChannelAvatar(channelId)
                                        ?: ""
                                    db.channelDao().insert(
                                        ChannelEntity(
                                            channelId = channelId,
                                            name = channel.name ?: "Unknown Channel",
                                            thumbnailUrl = validAvatar
                                        )
                                    )
                                    isSubscribed = true
                                    Toast.makeText(context, "Subscribed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = if (isSubscribed) "Subscribed" else "Subscribe",
                            color = if (isSubscribed) Color.White else Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun formatCount(count: Long): String = com.videhub.utils.FormatHelper.formatCount(count)

