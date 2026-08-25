package com.videhub.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.videhub.data.AppDatabase
import com.videhub.data.entity.ChannelEntity
import com.videhub.data.entity.LikedVideoEntity
import com.videhub.data.entity.PlaylistEntity
import com.videhub.data.entity.PlaylistVideoEntity
import com.videhub.extractor.ExtractorHelper
import com.videhub.recommendation.RecommendationEngine
import com.videhub.ui.components.InterestsBottomSheet
import com.videhub.ui.components.shimmerBrush
import com.videhub.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen(
    sharedViewModel: MainViewModel,
    onVideoClick: (String, String, String) -> Unit,
    onChannelClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var shortsList by remember { mutableStateOf<List<StreamInfoItem>>(sharedViewModel.shortsListCache ?: emptyList()) }
    var isLoading by remember { mutableStateOf(shortsList.isEmpty()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var isZoomMode by remember { mutableStateOf(true) }

    // Dialogs & Sheets
    var showInterestsSheet by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var detailsShort by remember { mutableStateOf<StreamInfoItem?>(null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var playlistShort by remember { mutableStateOf<StreamInfoItem?>(null) }
    var playlists by remember { mutableStateOf<List<PlaylistEntity>>(emptyList()) }

    fun loadPersonalizedShorts(isRefresh: Boolean = false) {
        scope.launch {
            if (isRefresh) {
                isRefreshing = true
            } else if (shortsList.isEmpty()) {
                isLoading = true
            }
            errorMessage = null
            try {
                val results = RecommendationEngine.getPersonalizedShortsFeed(db, context, maxItems = 40)
                if (results.isNotEmpty()) {
                    shortsList = results
                    sharedViewModel.shortsListCache = results
                } else if (shortsList.isEmpty()) {
                    errorMessage = "No recommended Shorts found"
                }
            } catch (e: Exception) {
                if (shortsList.isEmpty()) {
                    errorMessage = "Failed to load Shorts: ${e.message}"
                }
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    fun loadMoreShorts() {
        if (isLoadingMore || isLoading || isRefreshing) return
        scope.launch {
            isLoadingMore = true
            try {
                val more = RecommendationEngine.getPersonalizedShortsFeed(db, context, maxItems = 20)
                if (more.isNotEmpty()) {
                    val currentUrls = shortsList.mapNotNull { it.url }.toSet()
                    val newUnique = more.filter { it.url != null && !currentUrls.contains(it.url) }
                    if (newUnique.isNotEmpty()) {
                        val combined = shortsList + newUnique
                        shortsList = combined
                        sharedViewModel.shortsListCache = combined
                    }
                }
            } catch (_: Exception) {
                // Ignore background load more error
            } finally {
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (shortsList.isEmpty()) {
            loadPersonalizedShorts()
        }
    }

    val pagerState = rememberPagerState(
        initialPage = sharedViewModel.shortsCurrentIndexCache.coerceIn(0, (shortsList.size - 1).coerceAtLeast(0)),
        pageCount = { shortsList.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        sharedViewModel.shortsCurrentIndexCache = pagerState.currentPage
        if (shortsList.isNotEmpty() && pagerState.currentPage >= shortsList.size - 3) {
            loadMoreShorts()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            if (isLoading && shortsList.isEmpty()) {
                ShortsLoadingShimmer()
            } else if (errorMessage != null && shortsList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Error icon",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage ?: "Failed to load Shorts",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { loadPersonalizedShorts(isRefresh = true) },
                        modifier = Modifier.defaultMinSize(minWidth = 120.dp, minHeight = 48.dp)
                    ) {
                        Text("Retry", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    key = { page -> shortsList.getOrNull(page)?.url ?: page.toString() }
                ) { page ->
                    val shortItem = shortsList.getOrNull(page)
                    if (shortItem != null) {
                        val isPageActive = pagerState.currentPage == page
                        ShortPageItem(
                            item = shortItem,
                            isActive = isPageActive,
                            isMuted = isMuted,
                            isZoomMode = isZoomMode,
                            onToggleMute = { isMuted = !isMuted },
                            onChannelClick = onChannelClick,
                            onOpenFullPlayer = {
                                onVideoClick(
                                    shortItem.url ?: "",
                                    shortItem.name ?: "Short",
                                    shortItem.thumbnails?.firstOrNull()?.url ?: ""
                                )
                            },
                            onOpenDetails = {
                                detailsShort = shortItem
                                showDetailsSheet = true
                            },
                            onOpenPlaylist = {
                                playlistShort = shortItem
                                scope.launch {
                                    playlists = withContext(Dispatchers.IO) { db.playlistDao().getAllPlaylistsOnce() }
                                    showPlaylistDialog = true
                                }
                            }
                        )
                    }
                }

                // Top Overlay Bar
                ShortsTopBar(
                    isMuted = isMuted,
                    onToggleMute = { isMuted = !isMuted },
                    isZoomMode = isZoomMode,
                    onToggleZoom = { isZoomMode = !isZoomMode },
                    isRefreshing = isRefreshing,
                    onRefresh = { loadPersonalizedShorts(isRefresh = true) },
                    onOpenInterests = { showInterestsSheet = true }
                )
            }
        }
    }

    // Modal Interests BottomSheet
    if (showInterestsSheet) {
        InterestsBottomSheet(
            onDismiss = { showInterestsSheet = false },
            onSaved = {
                loadPersonalizedShorts(isRefresh = true)
                scope.launch {
                    snackbarHostState.showSnackbar("Recommendations updated")
                }
            }
        )
    }

    // Modal Details BottomSheet
    if (showDetailsSheet && detailsShort != null) {
        val item = detailsShort!!
        ModalBottomSheet(
            onDismissRequest = { showDetailsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = item.name ?: "Short Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics(mergeDescendants = true) {}
                ) {
                    val avatar = item.uploaderAvatars?.firstOrNull()?.url
                    if (!avatar.isNullOrBlank()) {
                        AsyncImage(
                            model = avatar,
                            contentDescription = "${item.uploaderName} channel avatar",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column {
                        Text(
                            text = item.uploaderName ?: "Unknown Channel",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Start
                        )
                        val views = item.viewCount
                        val date = item.textualUploadDate ?: item.uploadDate?.toString() ?: ""
                        val meta = listOfNotNull(
                            if (views >= 0) "$views views" else null,
                            date.ifBlank { null }
                        ).joinToString(" • ")
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            showDetailsSheet = false
                            onVideoClick(
                                item.url ?: "",
                                item.name ?: "Short",
                                item.thumbnails?.firstOrNull()?.url ?: ""
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Play in Full Player", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, item.url ?: "")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Short"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Add to Playlist Dialog
    if (showPlaylistDialog && playlistShort != null) {
        val shortToSave = playlistShort!!
        var newName by remember { mutableStateOf("") }
        var showCreateInput by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = {
                Text(
                    text = "Save Short to Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (playlists.isEmpty() && !showCreateInput) {
                        Text(
                            text = "No playlists found. Create one below:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        playlists.forEach { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) {
                                        scope.launch(Dispatchers.IO) {
                                            db.playlistDao().insertVideo(
                                                PlaylistVideoEntity(
                                                    playlistId = pl.id,
                                                    videoId = shortToSave.url ?: "",
                                                    title = shortToSave.name ?: "",
                                                    thumbnailUrl = shortToSave.thumbnails?.firstOrNull()?.url ?: "",
                                                    channelName = shortToSave.uploaderName ?: "",
                                                    durationText = if (shortToSave.duration > 0) "${shortToSave.duration}s" else null,
                                                    viewCount = shortToSave.viewCount,
                                                    uploadDate = shortToSave.textualUploadDate ?: ""
                                                )
                                            )
                                            withContext(Dispatchers.Main) {
                                                showPlaylistDialog = false
                                                snackbarHostState.showSnackbar("Saved to ${pl.name}")
                                            }
                                        }
                                    }
                                    .padding(vertical = 12.dp)
                                    .semantics(mergeDescendants = true) {},
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = pl.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (showCreateInput) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Playlist Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (showCreateInput) {
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                scope.launch(Dispatchers.IO) {
                                    val newId = db.playlistDao().insertPlaylist(PlaylistEntity(name = newName.trim())).toInt()
                                    db.playlistDao().insertVideo(
                                        PlaylistVideoEntity(
                                            playlistId = newId,
                                            videoId = shortToSave.url ?: "",
                                            title = shortToSave.name ?: "",
                                            thumbnailUrl = shortToSave.thumbnails?.firstOrNull()?.url ?: "",
                                            channelName = shortToSave.uploaderName ?: "",
                                            durationText = if (shortToSave.duration > 0) "${shortToSave.duration}s" else null,
                                            viewCount = shortToSave.viewCount,
                                            uploadDate = shortToSave.textualUploadDate ?: ""
                                        )
                                    )
                                    withContext(Dispatchers.Main) {
                                        showPlaylistDialog = false
                                        snackbarHostState.showSnackbar("Created & saved to $newName")
                                    }
                                }
                            }
                        },
                        enabled = newName.isNotBlank(),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Create & Save", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    TextButton(
                        onClick = { showCreateInput = true },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Playlist", style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPlaylistDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}

@Composable
private fun ShortPageItem(
    item: StreamInfoItem,
    isActive: Boolean,
    isMuted: Boolean,
    isZoomMode: Boolean,
    onToggleMute: () -> Unit,
    onChannelClick: (String) -> Unit,
    onOpenFullPlayer: () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenPlaylist: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val haptic = LocalHapticFeedback.current

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var isLiked by remember { mutableStateOf(false) }
    var isSubscribed by remember { mutableStateOf(false) }
    var showPauseIcon by remember { mutableStateOf(false) }
    var showHeartPop by remember { mutableStateOf(false) }
    var isTitleExpanded by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableFloatStateOf(0f) }

    val videoUrl = item.url ?: ""
    val thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: ""
    val uploaderUrl = item.uploaderUrl ?: ""
    val uploaderName = item.uploaderName ?: "Creator"

    LaunchedEffect(videoUrl) {
        if (videoUrl.isNotBlank()) {
            withContext(Dispatchers.IO) {
                isLiked = db.likedVideoDao().isLiked(videoUrl)
            }
        }
    }

    LaunchedEffect(uploaderUrl) {
        if (uploaderUrl.isNotBlank()) {
            withContext(Dispatchers.IO) {
                isSubscribed = db.channelDao().isSubscribed(uploaderUrl)
            }
        }
    }

    DisposableEffect(isActive, videoUrl) {
        if (!isActive || videoUrl.isBlank()) {
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
            return@DisposableEffect onDispose {}
        }

        val player = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            volume = if (isMuted) 0f else 1f
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        exoPlayer = player

        val loadJob = scope.launch(Dispatchers.IO) {
            try {
                val streamInfo = ExtractorHelper.getStreamInfo(videoUrl)
                val progressive = (streamInfo.videoStreams ?: emptyList()).filter { !it.content.isNullOrBlank() }
                val videoOnly = (streamInfo.videoOnlyStreams ?: emptyList()).filter { !it.content.isNullOrBlank() }
                val audioOnly = (streamInfo.audioStreams ?: emptyList()).filter { !it.content.isNullOrBlank() }

                val bestProg = progressive.maxByOrNull {
                    it.getResolution()?.replace("p", "")?.replace("fps", "")?.trim()?.toIntOrNull() ?: 0
                }?.content

                val chosenVideoUri = bestProg ?: (videoOnly.find { it.format?.mimeType == "video/mp4" } ?: videoOnly.firstOrNull())?.content
                val chosenAudioUri = if (bestProg == null) {
                    (audioOnly.find { it.format?.mimeType == "audio/mp4" } ?: audioOnly.maxByOrNull { it.averageBitrate })?.content
                } else null

                withContext(Dispatchers.Main) {
                    if (chosenVideoUri != null && isActive) {
                        val videoMediaItem = MediaItem.fromUri(chosenVideoUri)
                        if (chosenAudioUri != null) {
                            val dsf = androidx.media3.datasource.DefaultDataSource.Factory(context)
                            val videoSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dsf).createMediaSource(videoMediaItem)
                            val audioSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dsf).createMediaSource(MediaItem.fromUri(chosenAudioUri))
                            val merged = androidx.media3.exoplayer.source.MergingMediaSource(false, videoSource, audioSource)
                            player.setMediaSource(merged)
                        } else {
                            player.setMediaItem(videoMediaItem)
                        }
                        player.prepare()
                        player.play()
                    }
                }
            } catch (_: Exception) {}
        }

        onDispose {
            loadJob.cancel()
            player.removeListener(listener)
            player.stop()
            player.release()
            exoPlayer = null
        }
    }

    LaunchedEffect(isMuted, exoPlayer) {
        exoPlayer?.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(isActive, exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (isActive && kotlinx.coroutines.currentCoroutineContext().isActive) {
            val dur = player.duration
            val pos = player.currentPosition
            if (dur > 0) {
                currentProgress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            }
            delay(200)
        }
    }

    fun toggleLike() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val next = !isLiked
        isLiked = next
        scope.launch(Dispatchers.IO) {
            if (next) {
                db.likedVideoDao().insert(
                    LikedVideoEntity(
                        videoId = videoUrl,
                        title = item.name ?: "Short",
                        thumbnailUrl = thumbnailUrl,
                        channelName = uploaderName,
                        likedAt = System.currentTimeMillis(),
                        viewCount = item.viewCount,
                        uploadDate = item.textualUploadDate ?: ""
                    )
                )
            } else {
                db.likedVideoDao().deleteById(videoUrl)
            }
        }
    }

    fun toggleSubscribe() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val next = !isSubscribed
        isSubscribed = next
        scope.launch(Dispatchers.IO) {
            if (next) {
                db.channelDao().insert(
                    ChannelEntity(
                        channelId = uploaderUrl,
                        name = uploaderName,
                        thumbnailUrl = item.uploaderAvatars?.firstOrNull()?.url
                    )
                )
            } else {
                db.channelDao().deleteById(uploaderUrl)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (!isLiked) toggleLike()
                        showHeartPop = true
                        scope.launch {
                            delay(800)
                            showHeartPop = false
                        }
                    },
                    onTap = {
                        exoPlayer?.let { p ->
                            if (p.isPlaying) {
                                p.pause()
                                showPauseIcon = true
                            } else {
                                p.play()
                                showPauseIcon = false
                            }
                        }
                    }
                )
            }
    ) {
        if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = exoPlayer
                        resizeMode = if (isZoomMode) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    view.resizeMode = if (isZoomMode) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = item.name ?: "Short video preview",
                contentScale = if (isZoomMode) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }

        AnimatedVisibility(
            visible = showHeartPop,
            enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Liked",
                tint = Color.Red,
                modifier = Modifier.size(96.dp)
            )
        }

        AnimatedVisibility(
            visible = showPauseIcon,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Paused",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        // Gradient Scrim for accessibility contrast
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        // Bottom Left Info Area
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .padding(start = 16.dp, end = 8.dp, bottom = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) {
                        if (uploaderUrl.isNotBlank()) onChannelClick(uploaderUrl)
                    }
                    .semantics(mergeDescendants = true) {}
            ) {
                val avatar = item.uploaderAvatars?.firstOrNull()?.url
                if (!avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = "${uploaderName} channel logo",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "@${uploaderName.replace(" ", "")}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSubscribed) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(role = Role.Button) { toggleSubscribe() }
                        .semantics {
                            role = Role.Button
                            contentDescription = if (isSubscribed) "Unsubscribe from channel" else "Subscribe to channel"
                        }
                ) {
                    Text(
                        text = if (isSubscribed) "Subscribed" else "Subscribe",
                        color = if (isSubscribed) Color.White else MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.name ?: "",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (isTitleExpanded) 6 else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.clickable(role = Role.Button) { isTitleExpanded = !isTitleExpanded }
            )

            Spacer(modifier = Modifier.height(8.dp))

            val infiniteTransition = rememberInfiniteTransition(label = "disc")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "discRotation"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics(mergeDescendants = true) {}
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Audio track",
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(if (isPlaying) rotation else 0f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${uploaderName} • Original Sound",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
            }
        }

        // Right Side Floating Action Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ShortActionButton(
                icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                tint = if (isLiked) Color.Red else Color.White,
                label = if (isLiked) "Liked" else "Like",
                contentDescription = if (isLiked) "Unlike short" else "Like short",
                onClick = { toggleLike() }
            )

            ShortActionButton(
                icon = Icons.Outlined.PlaylistAdd,
                tint = Color.White,
                label = "Save",
                contentDescription = "Save to playlist",
                onClick = onOpenPlaylist
            )

            ShortActionButton(
                icon = Icons.Outlined.Info,
                tint = Color.White,
                label = "Info",
                contentDescription = "Show short info",
                onClick = onOpenDetails
            )

            ShortActionButton(
                icon = Icons.Outlined.Share,
                tint = Color.White,
                label = "Share",
                contentDescription = "Share short link",
                onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, videoUrl)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Short"))
                }
            )

            ShortActionButton(
                icon = Icons.Filled.SmartDisplay,
                tint = MaterialTheme.colorScheme.primary,
                label = "Player",
                contentDescription = "Open in full player view",
                onClick = onOpenFullPlayer
            )
        }

        LinearProgressIndicator(
            progress = { currentProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun ShortActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(role = Role.Button) { onClick() }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.contentDescription = contentDescription
            }
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ShortsTopBar(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    isZoomMode: Boolean,
    onToggleZoom: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenInterests: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {}
        ) {
            Icon(
                imageVector = Icons.Filled.FlashOn,
                contentDescription = "Shorts badge",
                tint = Color.Red,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Shorts",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onOpenInterests,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .semantics {
                        contentDescription = "Personalize interests and recommendations"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onToggleZoom,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .semantics {
                        contentDescription = if (isZoomMode) "Switch to fit aspect ratio" else "Switch to zoom full screen"
                    }
            ) {
                Icon(
                    imageVector = if (isZoomMode) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .semantics {
                        contentDescription = if (isMuted) "Unmute audio" else "Mute audio"
                    }
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .semantics {
                        contentDescription = "Refresh feed"
                    }
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortsLoadingShimmer() {
    val brush = shimmerBrush()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
            }
        }
    }
}
