package com.videhub.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
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
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Brush
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.videhub.PlayQueueItem
import com.videhub.QueueManager
import com.videhub.data.AppDatabase
import com.videhub.ui.components.AddToPlaylistDialog
import com.videhub.ui.components.PlaylistDownloadQualityDialog
import com.videhub.viewmodel.PlaylistDetailUiState
import com.videhub.viewmodel.PlaylistDetailViewModel
import com.videhub.viewmodel.PlaylistDownloadState
import com.videhub.viewmodel.PlaylistVideoUiItem
import kotlinx.coroutines.launch
import java.util.Locale

// ── Overload for Int playlistId ──
@Composable
fun PlaylistDetailScreen(
    playlistId: Int,
    onVideoClick: (String, String, String) -> Unit,
    onSearchClick: () -> Unit = {},
    viewModel: PlaylistDetailViewModel = viewModel()
) {
    PlaylistDetailScreen(
        playlistId = playlistId.toString(),
        onVideoPlay = { url, title, thumb, _ -> onVideoClick(url, title, thumb) },
        onSearchClick = onSearchClick,
        viewModel = viewModel
    )
}

// ── Main Single PlaylistDetailScreen ──
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onVideoPlay: (url: String, title: String, thumbnail: String, isAudioOnly: Boolean) -> Unit,
    onSearchClick: () -> Unit = {},
    viewModel: PlaylistDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val snackbarHostState = remember { SnackbarHostState() }
    val db = remember { AppDatabase.getDatabase(context) }

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(context, playlistId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentSortOrder by remember { mutableStateOf("DEFAULT") }

    val sortedVideos = remember(uiState.videos, currentSortOrder) {
        when (currentSortOrder) {
            "TITLE" -> uiState.videos.sortedBy { it.title.lowercase(Locale.ROOT) }
            "DURATION" -> uiState.videos.sortedByDescending { it.durationSeconds }
            "SIZE" -> uiState.videos.sortedByDescending { it.totalBytes }
            else -> uiState.videos
        }
    }

    // Confirmation dialog states
    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    var videoToDeleteDownload by remember { mutableStateOf<PlaylistVideoUiItem?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInputText by remember { mutableStateOf("") }
    var showQualityDownloadDialog by remember { mutableStateOf(false) }
    var videoForAddToPlaylist by remember { mutableStateOf<PlaylistVideoUiItem?>(null) }

    // Save to Playlist Dialog
    videoForAddToPlaylist?.let { targetVideo ->
        AddToPlaylistDialog(
            videoUrl = targetVideo.videoId,
            title = targetVideo.title,
            thumbnailUrl = targetVideo.thumbnailUrl ?: "",
            channelName = targetVideo.channelName,
            onDismiss = { videoForAddToPlaylist = null }
        )
    }

    // Delete Playlist Confirmation Dialog
    if (showDeletePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePlaylistDialog = false },
            title = {
                Text(
                    text = "Delete playlist?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Do you want to delete '${uiState.playlistName}'? Keep your downloaded files?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Neutral option: Keep files
                    TextButton(
                        onClick = {
                            showDeletePlaylistDialog = false
                            viewModel.deletePlaylist(context, deleteFilesToo = false) {
                                onBackPressedDispatcher?.onBackPressed()
                                Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Keep files")
                    }

                    // Destructive option: Delete files too
                    Button(
                        onClick = {
                            showDeletePlaylistDialog = false
                            viewModel.deletePlaylist(context, deleteFilesToo = true) {
                                onBackPressedDispatcher?.onBackPressed()
                                Toast.makeText(context, "Playlist and files deleted", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Delete files too")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Single Download Dialog
    videoToDeleteDownload?.let { video ->
        val fileSize = formatPlaylistMB(video.totalBytes.takeIf { it > 0 } ?: video.bytesDownloaded)
        AlertDialog(
            onDismissRequest = { videoToDeleteDownload = null },
            title = {
                Text(
                    text = "Delete download?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Delete this download? Frees $fileSize.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = video
                        videoToDeleteDownload = null
                        viewModel.deleteDownloadWithDelayedUndo(
                            context = context,
                            item = target,
                            onSuccess = {
                                coroutineScope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Deleted download",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoDeleteDownload(context)
                                    }
                                }
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDeleteDownload = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Playlist Sheet
    if (showRenameDialog) {
        com.videhub.ui.components.EditPlaylistSheet(
            initialTitle = uiState.playlistName,
            initialCoverUrl = uiState.customCoverUrl?.takeIf { it.isNotBlank() }
                ?: uiState.videos.firstOrNull()?.thumbnailUrl,
            onDismiss = { showRenameDialog = false },
            onSave = { newTitle, newCoverUrl ->
                viewModel.renamePlaylistAndCover(context, newTitle, newCoverUrl)
                showRenameDialog = false
            }
        )
    }

    // Download Quality Selection Dialog
    if (showQualityDownloadDialog) {
        PlaylistDownloadQualityDialog(
            playlistTitle = uiState.playlistName,
            onDismiss = { showQualityDownloadDialog = false },
            onSelectOption = { isAudioOnly, quality ->
                viewModel.downloadRemaining(context, quality, isAudioOnly)
                Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
            }
        )
    }

    PlaylistDetailContent(
        uiState = uiState.copy(videos = sortedVideos),
        snackbarHostState = snackbarHostState,
        onBackClick = { onBackPressedDispatcher?.onBackPressed() },
        onSearchClick = onSearchClick,
        onRenameClick = {
            renameInputText = uiState.playlistName
            showRenameDialog = true
        },
        onSortClick = { sortKey ->
            currentSortOrder = sortKey
            Toast.makeText(context, "Sorted", Toast.LENGTH_SHORT).show()
        },
        onDeletePlaylistClick = { showDeletePlaylistDialog = true },
        onPlayAllClick = {
            if (sortedVideos.isNotEmpty()) {
                QueueManager.clear()
                for (i in 1 until sortedVideos.size) {
                    val v = sortedVideos[i]
                    val playUrl = v.localFilePath ?: v.videoId
                    QueueManager.enqueue(
                        PlayQueueItem(
                            url = playUrl,
                            title = v.title,
                            uploaderName = v.channelName,
                            thumbnailUrl = v.thumbnailUrl ?: "",
                            id = v.videoId
                        )
                    )
                }
                val first = sortedVideos.first()
                val firstUrl = first.localFilePath ?: first.videoId
                onVideoPlay(firstUrl, first.title, first.thumbnailUrl ?: "", first.isAudioOnly)
            }
        },
        onShuffleClick = {
            if (sortedVideos.isNotEmpty()) {
                QueueManager.clear()
                val shuffled = sortedVideos.shuffled()
                for (i in 1 until shuffled.size) {
                    val v = shuffled[i]
                    val playUrl = v.localFilePath ?: v.videoId
                    QueueManager.enqueue(
                        PlayQueueItem(
                            url = playUrl,
                            title = v.title,
                            uploaderName = v.channelName,
                            thumbnailUrl = v.thumbnailUrl ?: "",
                            id = v.videoId
                        )
                    )
                }
                val first = shuffled.first()
                val firstUrl = first.localFilePath ?: first.videoId
                onVideoPlay(firstUrl, first.title, first.thumbnailUrl ?: "", first.isAudioOnly)
            }
        },
        onDownloadPillClick = {
            if (uiState.downloadState != PlaylistDownloadState.ALL_DONE) {
                showQualityDownloadDialog = true
            }
        },
        onVideoRowClick = { clickedIndex, video ->
            QueueManager.clear()
            if (clickedIndex in 0 until sortedVideos.lastIndex) {
                for (i in (clickedIndex + 1)..sortedVideos.lastIndex) {
                    val v = sortedVideos[i]
                    val playUrl = v.localFilePath ?: v.videoId
                    QueueManager.enqueue(
                        PlayQueueItem(
                            url = playUrl,
                            title = v.title,
                            uploaderName = v.channelName,
                            thumbnailUrl = v.thumbnailUrl ?: "",
                            id = v.videoId
                        )
                    )
                }
            }
            val playUrl = video.localFilePath ?: video.videoId
            onVideoPlay(playUrl, video.title, video.thumbnailUrl ?: "", video.isAudioOnly)
        },
        onStartRadio = { video ->
            com.videhub.audio.RadioManager.startRadio(
                video.videoId, video.title, video.channelName, video.thumbnailUrl ?: ""
            )
            Toast.makeText(context, "📻 Radio started", Toast.LENGTH_SHORT).show()
        },
        onPlayNext = { video ->
            val playUrl = video.localFilePath ?: video.videoId
            QueueManager.playNext(
                PlayQueueItem(
                    url = playUrl,
                    title = video.title,
                    uploaderName = video.channelName,
                    thumbnailUrl = video.thumbnailUrl ?: "",
                    id = video.videoId
                )
            )
            Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
        },
        onAddToQueue = { video ->
            val playUrl = video.localFilePath ?: video.videoId
            val added = QueueManager.enqueue(
                PlayQueueItem(
                    url = playUrl,
                    title = video.title,
                    uploaderName = video.channelName,
                    thumbnailUrl = video.thumbnailUrl ?: "",
                    id = video.videoId
                )
            )
            Toast.makeText(context, if (added) "Added to queue" else "Already in queue", Toast.LENGTH_SHORT).show()
        },
        onSaveToWatchLater = { video ->
            coroutineScope.launch {
                db.watchLaterDao().insert(
                    com.videhub.data.entity.WatchLaterEntity(
                        videoId = video.videoId,
                        title = video.title,
                        thumbnailUrl = video.thumbnailUrl ?: "",
                        channelName = video.channelName
                    )
                )
                Toast.makeText(context, "Saved to Watch Later", Toast.LENGTH_SHORT).show()
            }
        },
        onOpenSaveToPlaylist = { video ->
            videoForAddToPlaylist = video
        },
        onDownloadSingleVideo = { video ->
            val intent = android.content.Intent(context, com.videhub.service.DownloadService::class.java).apply {
                putExtra("needsExtraction", true)
                putExtra("url", video.videoId)
                putExtra("videoId", video.videoId)
                putExtra("title", video.title)
                putExtra("thumbnailUrl", video.thumbnailUrl)
                putExtra("playlistId", playlistId)
                putExtra("playlistName", uiState.playlistName)
                putExtra("isAudioOnly", false)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Toast.makeText(context, "Downloading video...", Toast.LENGTH_SHORT).show()
        },
        onDeleteDownloadClick = { video ->
            videoToDeleteDownload = video
        },
        onRemoveFromPlaylist = { video ->
            viewModel.removeFromPlaylist(
                context = context,
                item = video,
                onSuccess = {
                    coroutineScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        val result = snackbarHostState.showSnackbar(
                            message = "Removed from '${uiState.playlistName}'",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undoRemoveFromPlaylist(context)
                        }
                    }
                }
            )
        }
    )
}

// ── 2x2 Collage Cover (Height ~200dp, thumbnails requested at 480px) ──
@Composable
fun PlaylistCollageCover(
    thumbnails: List<String>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val nonBlank = thumbnails.filter { it.isNotBlank() }
        if (nonBlank.size >= 2) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(nonBlank[0])
                                .crossfade(true)
                                .size(480)
                                .build(),
                            contentDescription = "Cover quadrant 1",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(nonBlank[1])
                                .crossfade(true)
                                .size(480)
                                .build(),
                            contentDescription = "Cover quadrant 2",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val thumb3 = nonBlank.getOrNull(2) ?: nonBlank[0]
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(thumb3)
                                .crossfade(true)
                                .size(480)
                                .build(),
                            contentDescription = "Cover quadrant 3",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val thumb4 = nonBlank.getOrNull(3) ?: nonBlank[1]
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(thumb4)
                                .crossfade(true)
                                .size(480)
                                .build(),
                            contentDescription = "Cover quadrant 4",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } else if (nonBlank.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(nonBlank[0])
                    .crossfade(true)
                    .size(480)
                    .build(),
                contentDescription = "Playlist single cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = "Empty cover",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(56.dp)
            )
        }
    }
}

// ── UI Content Scaffold ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailContent(
    uiState: PlaylistDetailUiState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onRenameClick: () -> Unit,
    onSortClick: (String) -> Unit,
    onDeletePlaylistClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onDownloadPillClick: () -> Unit,
    onVideoRowClick: (index: Int, video: PlaylistVideoUiItem) -> Unit,
    onStartRadio: (video: PlaylistVideoUiItem) -> Unit,
    onPlayNext: (video: PlaylistVideoUiItem) -> Unit,
    onAddToQueue: (video: PlaylistVideoUiItem) -> Unit,
    onSaveToWatchLater: (video: PlaylistVideoUiItem) -> Unit,
    onOpenSaveToPlaylist: (video: PlaylistVideoUiItem) -> Unit,
    onDownloadSingleVideo: (video: PlaylistVideoUiItem) -> Unit,
    onDeleteDownloadClick: (video: PlaylistVideoUiItem) -> Unit,
    onRemoveFromPlaylist: (video: PlaylistVideoUiItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCount = uiState.videos.size
    val readyVideos = uiState.videos.filter { it.downloadState == "DONE" && !it.localFilePath.isNullOrBlank() }
    val readyCount = readyVideos.size
    val isDownloading = uiState.videos.any { it.downloadState == "DOWNLOADING" || it.downloadState == "QUEUED" || it.downloadState == "PREPARING" }

    // Summary line calculations
    val totalMinutes = (uiState.totalDurationSeconds + 59) / 60
    val durationText = if (totalMinutes > 0) "$totalMinutes min" else ""
    val videoCountText = if (totalCount == 1) "1 video" else "${com.videhub.utils.FormatHelper.formatCount(totalCount.toLong())} videos"

    val summaryText = when {
        isDownloading -> {
            val countLabel = if (readyCount == 1 && totalCount == 1) "1 of 1 downloaded" else "$readyCount of $totalCount downloaded"
            val dlMb = formatPlaylistMB(uiState.downloadedBytes)
            val estMb = formatPlaylistMB(uiState.totalEstimatedBytes.coerceAtLeast(uiState.downloadedBytes))
            "$countLabel • $dlMb of ~$estMb"
        }
        uiState.downloadState == PlaylistDownloadState.ALL_DONE -> {
            val sizeMb = formatPlaylistMB(uiState.totalEstimatedBytes.coerceAtLeast(uiState.downloadedBytes))
            listOfNotNull(videoCountText, durationText.takeIf { it.isNotBlank() }, sizeMb).joinToString(" • ")
        }
        else -> {
            listOfNotNull(videoCountText, durationText.takeIf { it.isNotBlank() }).joinToString(" • ")
        }
    }

    val overallProgress = if (readyCount >= totalCount && totalCount > 0) 1f else {
        if (totalCount > 0) (readyCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) else 0f
    }

    val lazyListState = rememberLazyListState()
    val showTopBarTitle by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }

    var showSortMenu by remember { mutableStateOf(false) }
    var currentSortLabel by remember { mutableStateOf("Manual") }

    val context = LocalContext.current
    val coverThumbs = remember(uiState.videos) {
        uiState.videos.mapNotNull { it.thumbnailUrl?.takeIf { t -> t.isNotBlank() } }
    }
    val primaryCoverUrl = remember(uiState.customCoverUrl, coverThumbs) {
        uiState.customCoverUrl?.takeIf { it.isNotBlank() } ?: coverThumbs.firstOrNull()
    }
    val resolvedPrimaryCoverModel = remember(primaryCoverUrl) {
        com.videhub.ui.components.resolveCoverImageModel(primaryCoverUrl)
    }
    val (ambientLeft, ambientRight) = com.videhub.ui.components.rememberPlaylistAmbientColors(primaryCoverUrl)
    val bgColor = MaterialTheme.colorScheme.background

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(ambientLeft, ambientRight)
                        )
                    )
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    title = {
                        if (showTopBarTitle) {
                            Text(
                                text = uiState.playlistName,
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
                        IconButton(
                            onClick = onSearchClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = onDeletePlaylistClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete playlist",
                                tint = Color.White
                            )
                        }
                    }
                )
            }
        },
        modifier = modifier
    ) { padding ->
        if (uiState.isLoading) {
            com.videhub.ui.components.OnlinePlaylistShimmer(
                modifier = Modifier.padding(padding)
            )
        } else {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Header Item matching YouTube playlist structure
            item(key = "playlist_header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(ambientLeft, ambientRight)
                            )
                        )
                ) {
                    // Vertical fade overlay into background color
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.55f to bgColor.copy(alpha = 0.45f),
                                        0.85f to bgColor.copy(alpha = 0.88f),
                                        1.0f to bgColor
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Single 16:9 Rounded Cover Image (displays custom uploaded image or first video thumbnail)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1F1F1F))
                                .clickable { onRenameClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (resolvedPrimaryCoverModel != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(resolvedPrimaryCoverModel)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Playlist cover",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                    contentDescription = "Empty cover",
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Left-aligned Playlist Title
                        Text(
                            text = uiState.playlistName,
                            fontSize = 26.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Start,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Left-aligned Subtitle ("Playlist • X videos")
                        val subtitleText = "Playlist • $summaryText"
                        Text(
                            text = subtitleText,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.72f),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Progress bar while downloading
                        if (isDownloading) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { overallProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Row (Play All + Circular Icon Buttons matching screenshot)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Play All: Solid white pill button with black text & play icon
                            Button(
                                onClick = onPlayAllClick,
                                enabled = totalCount > 0,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = CircleShape,
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
                                    modifier = Modifier.size(22.dp)
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
                                color = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add videos",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Circular Icon Button: Edit / Rename & Cover Image
                            Surface(
                                onClick = onRenameClick,
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit playlist",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Circular Icon Button: Download Playlist (↓)
                            Surface(
                                onClick = onDownloadPillClick,
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download playlist",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Sort selector dropdown matching screenshot ("Manual ∨")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Box {
                                Surface(
                                    onClick = { showSortMenu = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = currentSortLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Sort dropdown",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
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
                                        onSortClick("DEFAULT")
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Title") },
                                    onClick = {
                                        currentSortLabel = "Title"
                                        onSortClick("TITLE")
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duration") },
                                    onClick = {
                                        currentSortLabel = "Duration"
                                        onSortClick("DURATION")
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("File size") },
                                    onClick = {
                                        currentSortLabel = "File size"
                                        onSortClick("SIZE")
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Empty State
            if (uiState.videos.isEmpty()) {
                item(key = "empty_state") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = "Empty playlist",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No videos in this playlist",
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
                // Video Rows matching screenshot layout & options
                itemsIndexed(uiState.videos, key = { _, video -> video.id }) { index, video ->
                    val isDone = video.downloadState == "DONE" && !video.localFilePath.isNullOrBlank()
                    val isRowDownloading = video.downloadState == "DOWNLOADING"

                    // Metadata line: "Channel • 3:45 • 19.8 MB"
                    val metaParts = mutableListOf<String>()
                    if (video.channelName.isNotBlank()) metaParts.add(video.channelName)
                    if (!video.durationText.isNullOrBlank() && video.durationText != "0:00") metaParts.add(video.durationText)
                    if (isDone && video.totalBytes > 0L) metaParts.add(formatPlaylistMB(video.totalBytes))
                    val metadataString = metaParts.joinToString(" • ")

                    var showRowMenu by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVideoRowClick(index, video) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("playlist_item_${video.videoId}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 16:9 Thumbnail, ~140dp wide, 12dp corners, duration badge bottom-right
                        Box(
                            modifier = Modifier
                                .width(135.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (!video.thumbnailUrl.isNullOrBlank()) {
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
                                        imageVector = if (video.isAudioOnly) Icons.Default.MusicNote else Icons.AutoMirrored.Filled.PlaylistPlay,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Duration badge bottom-right
                            if (!video.durationText.isNullOrBlank() && video.durationText != "0:00") {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = video.durationText,
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

                        // Right: Download State & Three-dot menu
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // State icon
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
                                        progress = { (video.downloadProgress / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Three-dot menu with ALL requested video options
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
                                        leadingIcon = {
                                            Icon(
                                                painter = androidx.compose.ui.res.painterResource(id = com.videhub.R.drawable.ic_play_next),
                                                contentDescription = "Play next in queue",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
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
                                        text = { Text("Save to Watch Later") },
                                        leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                                        onClick = {
                                            showRowMenu = false
                                            onSaveToWatchLater(video)
                                        }
                                    )
                                    if (isDone) {
                                        DropdownMenuItem(
                                            text = { Text("Delete download", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                showRowMenu = false
                                                onDeleteDownloadClick(video)
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
                                        text = { Text("Remove from '${uiState.playlistName}'", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.PlaylistRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showRowMenu = false
                                            onRemoveFromPlaylist(video)
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
}

// ── Format Bytes Utility ──
private fun formatPlaylistMB(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}

// ── Previews ──

private val previewSampleVideos = listOf(
    PlaylistVideoUiItem(
        id = "v1",
        videoId = "v1",
        title = "Linux For DevOps In One Shot | Complete Beginners to Advanced",
        channelName = "TrainWithShubham",
        thumbnailUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=480",
        durationText = "5:46:00",
        durationSeconds = 20760,
        downloadState = "DONE",
        bytesDownloaded = 19_800_000L,
        totalBytes = 19_800_000L,
        localFilePath = "/data/downloads/v1.mp4"
    ),
    PlaylistVideoUiItem(
        id = "v2",
        videoId = "v2",
        title = "DevOps - Zero To Hero (Junoon batch 9) | Welcome Session",
        channelName = "TrainWithShubham",
        thumbnailUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=480",
        durationText = "3:39:44",
        durationSeconds = 13184,
        downloadState = "DONE",
        bytesDownloaded = 24_200_000L,
        totalBytes = 24_200_000L,
        localFilePath = "/data/downloads/v2.mp4"
    ),
    PlaylistVideoUiItem(
        id = "v3",
        videoId = "v3",
        title = "Why are you REJECTED in DevOps Scenarios?",
        channelName = "TrainWithShubham",
        thumbnailUrl = "https://images.unsplash.com/photo-1461749280684-dccba630e2f6?w=480",
        durationText = "16:45",
        durationSeconds = 1005,
        downloadState = null,
        bytesDownloaded = 0L,
        totalBytes = 15_600_000L,
        localFilePath = null
    )
)

@Preview(name = "Playlist - State NONE", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PlaylistDetailNonePreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val noneVideos = previewSampleVideos.map { it.copy(downloadState = null, localFilePath = null) }
        PlaylistDetailContent(
            uiState = PlaylistDetailUiState(
                playlistId = "1",
                playlistName = "Devops",
                videos = noneVideos,
                downloadState = PlaylistDownloadState.NONE,
                totalDurationSeconds = 34949,
                totalEstimatedBytes = 59_600_000L,
                downloadedBytes = 0L
            ),
            onBackClick = {},
            onSearchClick = {},
            onRenameClick = {},
            onSortClick = {},
            onDeletePlaylistClick = {},
            onPlayAllClick = {},
            onShuffleClick = {},
            onDownloadPillClick = {},
            onVideoRowClick = { _, _ -> },
            onStartRadio = {},
            onPlayNext = {},
            onAddToQueue = {},
            onSaveToWatchLater = {},
            onOpenSaveToPlaylist = {},
            onDownloadSingleVideo = {},
            onDeleteDownloadClick = {},
            onRemoveFromPlaylist = {}
        )
    }
}
