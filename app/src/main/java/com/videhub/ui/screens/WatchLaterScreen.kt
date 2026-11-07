package com.videhub.ui.screens

import android.graphics.drawable.BitmapDrawable
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.videhub.PlayQueueItem
import com.videhub.QueueManager
import com.videhub.audio.RadioManager
import com.videhub.data.entity.DownloadedPlaylistEntity
import com.videhub.data.entity.DownloadedVideoEntity
import com.videhub.ui.components.AddToPlaylistDialog
import com.videhub.ui.components.PlaylistDownloadQualityDialog
import com.videhub.viewmodel.WatchLaterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// ── State-to-Label Enum and Data Model for Download Pill ─────────────────────

enum class DownloadPillAction {
    START_DOWNLOAD,
    PAUSE,
    RESUME,
    RETRY,
    SYNC,
    UP_TO_DATE
}

data class DownloadPillInfo(
    val label: String,
    val action: DownloadPillAction,
    val icon: ImageVector,
    val showProgressIndicator: Boolean = false,
    val progress: Float = 0f
)

fun getDownloadPillInfo(
    savedVideosCount: Int,
    playlist: DownloadedPlaylistEntity?,
    downloadedVideos: List<DownloadedVideoEntity>
): DownloadPillInfo {
    val state = playlist?.state ?: "IDLE"
    val readyCount = downloadedVideos.count { it.state == "DONE" }
    val totalCount = playlist?.totalCount?.takeIf { it > 0 } ?: savedVideosCount
    val hasUnsyncedNewVideos = savedVideosCount > totalCount || downloadedVideos.size < savedVideosCount

    return when {
        state.equals("DOWNLOADING", ignoreCase = true) -> {
            val currIdx = (playlist?.currentVideoIndex ?: (readyCount + 1)).coerceAtMost(totalCount.coerceAtLeast(1))
            val prog = (playlist?.currentVideoProgress ?: 0) / 100f
            DownloadPillInfo(
                label = "Downloading $currIdx/$totalCount",
                action = DownloadPillAction.PAUSE,
                icon = Icons.Default.Pause,
                showProgressIndicator = true,
                progress = prog
            )
        }
        state.equals("PAUSED", ignoreCase = true) -> {
            DownloadPillInfo(
                label = "Resume",
                action = DownloadPillAction.RESUME,
                icon = Icons.Default.PlayArrow
            )
        }
        state.equals("FAILED", ignoreCase = true) -> {
            DownloadPillInfo(
                label = "Retry",
                action = DownloadPillAction.RETRY,
                icon = Icons.Default.Refresh
            )
        }
        hasUnsyncedNewVideos && totalCount > 0 -> {
            DownloadPillInfo(
                label = "Sync",
                action = DownloadPillAction.SYNC,
                icon = Icons.Default.Sync
            )
        }
        readyCount > 0 && readyCount >= totalCount -> {
            DownloadPillInfo(
                label = "Up to date",
                action = DownloadPillAction.UP_TO_DATE,
                icon = Icons.Default.Check
            )
        }
        else -> {
            DownloadPillInfo(
                label = "Download",
                action = DownloadPillAction.START_DOWNLOAD,
                icon = Icons.Default.Download
            )
        }
    }
}

// ── Unified Watch Later Screen ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchLaterScreen(
    onVideoClick: (String, String, String) -> Unit,
    onSearchClick: () -> Unit = {},
    viewModel: WatchLaterViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val savedVideos by viewModel.savedVideos.collectAsStateWithLifecycle()
    val downloadedPlaylist by viewModel.downloadedPlaylist.collectAsStateWithLifecycle()
    val downloadedVideos by viewModel.downloadedVideos.collectAsStateWithLifecycle()
    val isSessionValid by viewModel.isSessionValid.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Sort order: 0 = Default (Added date), 1 = Title A-Z, 2 = Channel
    var sortOrder by remember { mutableIntStateOf(0) }
    var videoForAddToPlaylist by remember { mutableStateOf<DownloadedVideoEntity?>(null) }

    // Save to Playlist Dialog
    videoForAddToPlaylist?.let { video ->
        AddToPlaylistDialog(
            videoUrl = video.videoId,
            title = video.title,
            thumbnailUrl = video.thumbnailUrl,
            channelName = video.channelName,
            onDismiss = { videoForAddToPlaylist = null }
        )
    }

    // Filter downloaded videos to match saved items or display full downloads
    val displayVideos = remember(savedVideos, downloadedVideos, sortOrder) {
        val merged = if (downloadedVideos.isNotEmpty()) {
            downloadedVideos
                .groupBy { it.videoId }
                .map { (_, list) ->
                    list.minByOrNull { item ->
                        when {
                            item.state == "DONE" -> 1
                            item.state == "DOWNLOADING" -> 2
                            item.state == "PAUSED" -> 3
                            !item.fileName.startsWith("PENDING_") -> 4
                            else -> 5
                        }
                    } ?: list.first()
                }
        } else {
            savedVideos.mapIndexed { idx, s ->
                DownloadedVideoEntity(
                    fileName = "SAVED_${s.videoId}",
                    videoId = s.videoId,
                    title = s.title,
                    thumbnailUrl = s.thumbnailUrl,
                    channelName = s.channelName,
                    playlistId = "WL",
                    playlistName = "Watch Later",
                    playlistIndex = idx,
                    state = "QUEUED"
                )
            }
        }

        when (sortOrder) {
            1 -> merged.sortedBy { it.title.lowercase() }
            2 -> merged.sortedBy { it.channelName.lowercase() }
            else -> merged.sortedBy { it.playlistIndex }
        }
    }

    val readyVideos = remember(displayVideos) {
        displayVideos.filter { it.state == "DONE" }
    }

    fun playAll(shuffle: Boolean = false) {
        val listToPlay = if (readyVideos.isNotEmpty()) readyVideos else displayVideos
        if (listToPlay.isEmpty()) {
            Toast.makeText(context, "No videos in Watch Later", Toast.LENGTH_SHORT).show()
            return
        }
        val orderedList = if (shuffle) listToPlay.shuffled() else listToPlay
        QueueManager.clear()
        val first = orderedList.first()
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val firstFile = File(downloadDir, first.fileName)
        val firstPlayUrl = if (firstFile.exists()) firstFile.absolutePath else first.videoId

        for (i in 1 until orderedList.size) {
            val qItem = orderedList[i]
            val f = File(downloadDir, qItem.fileName)
            val path = if (f.exists()) f.absolutePath else qItem.videoId
            QueueManager.enqueue(
                PlayQueueItem(
                    url = path,
                    title = qItem.title,
                    uploaderName = qItem.channelName,
                    thumbnailUrl = qItem.thumbnailUrl
                )
            )
        }
        onVideoClick(firstPlayUrl, first.title, first.thumbnailUrl)
    }

    var showQualityDialog by remember { mutableStateOf(false) }

    if (showQualityDialog) {
        PlaylistDownloadQualityDialog(
            playlistTitle = "Watch Later",
            onDismiss = { showQualityDialog = false },
            onSelectOption = { isAudioOnly, quality ->
                viewModel.syncOrDownload(isAudioOnly = isAudioOnly)
            }
        )
    }

    WatchLaterDetailContent(
        playlist = downloadedPlaylist,
        videos = displayVideos,
        savedVideosCount = savedVideos.size.coerceAtLeast(displayVideos.size),
        isSessionValid = isSessionValid,
        readyVideosCount = readyVideos.size,
        onBackClick = { onBackPressedDispatcher?.onBackPressed() },
        onSearchClick = onSearchClick,
        onPlayAllClick = { playAll(shuffle = false) },
        onShuffleClick = { playAll(shuffle = true) },
        onPillAction = { action ->
            when (action) {
                DownloadPillAction.START_DOWNLOAD -> showQualityDialog = true
                DownloadPillAction.PAUSE -> viewModel.pauseDownload()
                DownloadPillAction.RESUME -> viewModel.resumeDownload()
                DownloadPillAction.RETRY -> viewModel.retryDownload()
                DownloadPillAction.SYNC -> showQualityDialog = true
                DownloadPillAction.UP_TO_DATE -> {
                    Toast.makeText(context, "All Watch Later videos are downloaded", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onSortClick = { order ->
            sortOrder = order
        },
        onVideoClick = { video ->
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val targetFile = File(downloadDir, video.fileName)
            val playUrl = if (targetFile.exists()) targetFile.absolutePath else video.videoId
            onVideoClick(playUrl, video.title, video.thumbnailUrl)
        },
        onStartRadio = { video ->
            RadioManager.startRadio(video.videoId, video.title, video.channelName, video.thumbnailUrl)
            Toast.makeText(context, "📻 Radio started", Toast.LENGTH_SHORT).show()
        },
        onPlayNext = { video ->
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val targetFile = File(downloadDir, video.fileName)
            val playUrl = if (targetFile.exists()) targetFile.absolutePath else video.videoId
            QueueManager.playNext(
                PlayQueueItem(
                    url = playUrl,
                    title = video.title,
                    uploaderName = video.channelName,
                    thumbnailUrl = video.thumbnailUrl
                )
            )
            Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
        },
        onAddToQueue = { video ->
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val targetFile = File(downloadDir, video.fileName)
            val playUrl = if (targetFile.exists()) targetFile.absolutePath else video.videoId
            val added = QueueManager.enqueue(
                PlayQueueItem(
                    url = playUrl,
                    title = video.title,
                    uploaderName = video.channelName,
                    thumbnailUrl = video.thumbnailUrl
                )
            )
            Toast.makeText(context, if (added) "Added to queue" else "Already in queue", Toast.LENGTH_SHORT).show()
        },
        onOpenSaveToPlaylist = { video ->
            videoForAddToPlaylist = video
        },
        onRemoveVideoFromWatchLater = { video ->
            viewModel.removeVideoFromDownloads(video)
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Removed '${video.title}' from Watch Later",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoRemoveVideo()
                }
            }
        },
        onDeleteVideoFile = { video ->
            viewModel.deleteVideoFileOnly(video)
            Toast.makeText(context, "Deleted download for '${video.title}'", Toast.LENGTH_SHORT).show()
        },
        onDownloadSingleVideo = { video ->
            viewModel.retrySingleVideo(video.videoId)
            Toast.makeText(context, "Download started for '${video.title}'", Toast.LENGTH_SHORT).show()
        },
        snackbarHostState = snackbarHostState
    )
}

// ── Watch Later Detail Content ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchLaterDetailContent(
    playlist: DownloadedPlaylistEntity?,
    videos: List<DownloadedVideoEntity>,
    savedVideosCount: Int,
    isSessionValid: Boolean = true,
    readyVideosCount: Int = 0,
    onBackClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onPlayAllClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onPillAction: (DownloadPillAction) -> Unit = {},
    onSortClick: (Int) -> Unit = {},
    onVideoClick: (DownloadedVideoEntity) -> Unit = {},
    onStartRadio: (DownloadedVideoEntity) -> Unit = {},
    onPlayNext: (DownloadedVideoEntity) -> Unit = {},
    onAddToQueue: (DownloadedVideoEntity) -> Unit = {},
    onOpenSaveToPlaylist: (DownloadedVideoEntity) -> Unit = {},
    onRemoveVideoFromWatchLater: (DownloadedVideoEntity) -> Unit = {},
    onDeleteVideoFile: (DownloadedVideoEntity) -> Unit = {},
    onDownloadSingleVideo: (DownloadedVideoEntity) -> Unit = {},
    snackbarHostState: SnackbarHostState? = null
) {
    val context = LocalContext.current
    val isDownloading = playlist?.state?.equals("DOWNLOADING", ignoreCase = true) == true
    val isPreparing = playlist?.state?.equals("PREPARING", ignoreCase = true) == true ||
            (isDownloading && (playlist?.totalCount ?: 0) <= 0)

    val lazyListState = rememberLazyListState()
    val showTopBarTitle by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }

    var showSortMenu by remember { mutableStateOf(false) }
    var currentSortLabel by remember { mutableStateOf("Manual") }

    // Cover Thumbnails list
    val coverThumbs = remember(videos) {
        videos.mapNotNull { it.thumbnailUrl.takeIf { t -> t.isNotBlank() } }
    }
    val primaryCoverUrl = coverThumbs.firstOrNull()

    // Palette Color Extraction
    var extractedColor by remember { mutableStateOf(Color(0xFF272727)) }

    LaunchedEffect(primaryCoverUrl) {
        if (!primaryCoverUrl.isNullOrBlank()) {
            try {
                val loader = coil.Coil.imageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(primaryCoverUrl)
                    .allowHardware(false)
                    .size(300)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).generate()
                        val swatch = palette.dominantSwatch
                            ?: palette.darkVibrantSwatch
                            ?: palette.darkMutedSwatch
                            ?: palette.vibrantSwatch
                            ?: palette.mutedSwatch
                        if (swatch != null) {
                            extractedColor = Color(swatch.rgb)
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    val pillInfo = remember(savedVideosCount, playlist, videos) {
        getDownloadPillInfo(savedVideosCount, playlist, videos)
    }

    Scaffold(
        snackbarHost = {
            snackbarHostState?.let { SnackbarHost(hostState = it) }
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = extractedColor,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    if (showTopBarTitle) {
                        Text(
                            text = "Watch Later",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Search Button (redirects to search screen)
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // NOTE: NO 3-dots overflow menu in TopAppBar as explicitly requested
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Header Item matching YouTube Watch Later layout
            item(key = "watch_later_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    extractedColor.copy(alpha = 0.85f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cover Image (~200dp tall, 16dp rounded corners)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!primaryCoverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(primaryCoverUrl)
                                    .crossfade(true)
                                    .size(480)
                                    .build(),
                                contentDescription = "Watch Later cover",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = "Watch Later cover",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title: "Watch Later" (headlineMedium, bold)
                    Text(
                        text = "Watch Later",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Subtitle: "X videos" (NO "by username", NO "Private", NO "No views")
                    val videoCountLabel = if (savedVideosCount == 1) "1 video" else "${com.videhub.utils.FormatHelper.formatCount(savedVideosCount.toLong())} videos"
                    Text(
                        text = videoCountLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Progress bar while downloading
                    if (isDownloading || isPreparing) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val prog = if (isPreparing) 0f else {
                            if (savedVideosCount > 0) readyVideosCount.toFloat() / savedVideosCount.toFloat() else 0f
                        }
                        LinearProgressIndicator(
                            progress = { prog.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Row (Play All + Circular Icon Buttons)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Play All: Solid white pill button
                        Button(
                            onClick = onPlayAllClick,
                            enabled = videos.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                                disabledContainerColor = Color.White.copy(alpha = 0.4f),
                                disabledContentColor = Color.Black.copy(alpha = 0.4f)
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play all",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.Black
                            )
                        }

                        // Circular Icon Button: Add videos (+)
                        Surface(
                            onClick = onSearchClick,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add videos",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Circular Icon Button: Download (↓)
                        Surface(
                            onClick = { onPillAction(pillInfo.action) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (pillInfo.showProgressIndicator) {
                                    CircularProgressIndicator(
                                        progress = { pillInfo.progress },
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = pillInfo.icon,
                                        contentDescription = pillInfo.label,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sort selector dropdown ("Manual ∨")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box {
                            Surface(
                                onClick = { showSortMenu = true },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = currentSortLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Sort dropdown",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Manual") },
                                    onClick = {
                                        currentSortLabel = "Manual"
                                        onSortClick(0)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Title A-Z") },
                                    onClick = {
                                        currentSortLabel = "Title A-Z"
                                        onSortClick(1)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Channel name") },
                                    onClick = {
                                        currentSortLabel = "Channel name"
                                        onSortClick(2)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Empty State
            if (videos.isEmpty()) {
                item(key = "empty_state") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = "No saved videos",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No videos in Watch Later",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = onSearchClick,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add videos", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                // Video Rows
                itemsIndexed(
                    items = videos,
                    key = { index, video -> "wl_video_${video.videoId}_$index" }
                ) { index, video ->
                    val isDone = video.state == "DONE"
                    val isRowDownloading = video.state == "DOWNLOADING"

                    val metaParts = mutableListOf<String>()
                    if (video.channelName.isNotBlank() && video.channelName != "Unknown Channel") metaParts.add(video.channelName)
                    if (video.duration.isNotBlank() && video.duration != "0:00") metaParts.add(video.duration)
                    if (isDone && video.bytesDownloaded > 0L) metaParts.add(formatWatchLaterMB(video.bytesDownloaded))
                    val metadataString = metaParts.joinToString(" • ")

                    var showRowMenu by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVideoClick(video) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("wl_item_${video.videoId}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 16:9 Thumbnail, ~140dp wide, 12dp corners, duration badge
                        Box(
                            modifier = Modifier
                                .width(135.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (video.thumbnailUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(video.thumbnailUrl)
                                        .crossfade(true)
                                        .size(480)
                                        .build(),
                                    contentDescription = "Video thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Duration badge
                            if (video.duration.isNotBlank() && video.duration != "0:00") {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = video.duration,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Middle: Title & Metadata
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = metadataString.ifBlank { video.channelName },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Right: State icon & Three-dot menu
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Download state icon
                            when {
                                isDone -> {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Downloaded",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                isRowDownloading -> {
                                    CircularProgressIndicator(
                                        progress = { (video.progress / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Three-dot video options menu
                            Box {
                                IconButton(
                                    onClick = { showRowMenu = true },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = showRowMenu,
                                    onDismissRequest = { showRowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Start Song Radio (Endless Mix)") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            showRowMenu = false
                                            onStartRadio(video)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Play next in queue") },
                                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                        onClick = {
                                            showRowMenu = false
                                            onPlayNext(video)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Add to queue") },
                                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                                        onClick = {
                                            showRowMenu = false
                                            onAddToQueue(video)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Save to playlist") },
                                        leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                                        onClick = {
                                            showRowMenu = false
                                            onOpenSaveToPlaylist(video)
                                        }
                                    )
                                    if (isDone) {
                                        DropdownMenuItem(
                                            text = { Text("Delete download") },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                            onClick = {
                                                showRowMenu = false
                                                onDeleteVideoFile(video)
                                            }
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text("Download") },
                                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                            onClick = {
                                                showRowMenu = false
                                                onDownloadSingleVideo(video)
                                            }
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Remove from 'Watch Later'", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.PlaylistRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showRowMenu = false
                                            onRemoveVideoFromWatchLater(video)
                                        }
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

// ── Format Bytes Utility ─────────────────────────────────────────────────────

private fun formatWatchLaterMB(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}
