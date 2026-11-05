package com.videhub.ui.screens

import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.videhub.data.entity.DownloadedPlaylistEntity
import com.videhub.data.entity.DownloadedVideoEntity
import com.videhub.data.entity.WatchLaterEntity
import com.videhub.viewmodel.WatchLaterViewModel
import kotlinx.coroutines.launch
import java.io.File

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
    var showSortMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

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
        if (readyVideos.isEmpty()) {
            Toast.makeText(context, "No downloaded videos ready to play", Toast.LENGTH_SHORT).show()
            return
        }
        val listToPlay = if (shuffle) readyVideos.shuffled() else readyVideos
        com.videhub.QueueManager.clear()
        val first = listToPlay.first()
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val firstFile = File(downloadDir, first.fileName)
        val firstPlayUrl = if (firstFile.exists()) firstFile.absolutePath else first.videoId

        for (i in 1 until listToPlay.size) {
            val qItem = listToPlay[i]
            val f = File(downloadDir, qItem.fileName)
            val path = if (f.exists()) f.absolutePath else qItem.videoId
            com.videhub.QueueManager.enqueue(
                com.videhub.PlayQueueItem(
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
        com.videhub.ui.components.PlaylistDownloadQualityDialog(
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
                    Toast.makeText(context, "All Watch Later videos are up to date", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onSignInClick = {
            viewModel.signInAndSync()
            Toast.makeText(context, "Signed in! Syncing Watch Later...", Toast.LENGTH_SHORT).show()
        },
        onCancelDownload = {
            viewModel.pauseDownload()
            Toast.makeText(context, "Download paused", Toast.LENGTH_SHORT).show()
        },
        onRemoveAllDownloads = {
            viewModel.removeAllDownloads()
            Toast.makeText(context, "Removed all Watch Later downloads", Toast.LENGTH_SHORT).show()
        },
        onSortClick = { order ->
            sortOrder = order
        },
        onVideoClick = { video ->
            if (video.state == "DONE") {
                val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val targetFile = File(downloadDir, video.fileName)
                val playUrl = if (targetFile.exists()) targetFile.absolutePath else video.videoId
                onVideoClick(playUrl, video.title, video.thumbnailUrl)
            } else {
                Toast.makeText(context, "Video is not downloaded yet", Toast.LENGTH_SHORT).show()
            }
        },
        onRetryVideo = { video ->
            viewModel.retrySingleVideo(video.videoId)
        },
        onRemoveVideoFromDownloads = { video ->
            viewModel.removeVideoFromDownloads(video)
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Removed '${video.title}' from downloads",
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
            Toast.makeText(context, "Deleted file for '${video.title}'", Toast.LENGTH_SHORT).show()
        },
        snackbarHostState = snackbarHostState
    )
}

// ── Watch Later Detail Content (Stateless & Previewable) ────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchLaterDetailContent(
    playlist: DownloadedPlaylistEntity?,
    videos: List<DownloadedVideoEntity>,
    savedVideosCount: Int,
    isSessionValid: Boolean = true,
    readyVideosCount: Int = 0,
    onBackClick: () -> Unit = {},
    onPlayAllClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onPillAction: (DownloadPillAction) -> Unit = {},
    onSignInClick: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onRemoveAllDownloads: () -> Unit = {},
    onSortClick: (Int) -> Unit = {},
    onVideoClick: (DownloadedVideoEntity) -> Unit = {},
    onRetryVideo: (DownloadedVideoEntity) -> Unit = {},
    onRemoveVideoFromDownloads: (DownloadedVideoEntity) -> Unit = {},
    onDeleteVideoFile: (DownloadedVideoEntity) -> Unit = {},
    snackbarHostState: SnackbarHostState? = null
) {
    val isDownloading = playlist?.state?.equals("DOWNLOADING", ignoreCase = true) == true
    val isPreparing = playlist?.state?.equals("PREPARING", ignoreCase = true) == true ||
            (isDownloading && (playlist?.totalCount ?: 0) <= 0)
    val isFailed = playlist?.state?.equals("FAILED", ignoreCase = true) == true
    val isSignInRequired = !isSessionValid || (isFailed && playlist?.errorMessage?.contains("Sign in", ignoreCase = true) == true)

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val pillInfo = remember(savedVideosCount, playlist, videos) {
        getDownloadPillInfo(savedVideosCount, playlist, videos)
    }

    Scaffold(
        snackbarHost = {
            snackbarHostState?.let { SnackbarHost(hostState = it) }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Watch Later",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.testTag("sort_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort videos"
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Date added (default)") },
                            onClick = {
                                onSortClick(0)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Title (A-Z)") },
                            onClick = {
                                onSortClick(1)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Channel name") },
                            onClick = {
                                onSortClick(2)
                                showSortMenu = false
                            }
                        )
                    }

                    // Overflow Menu
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.testTag("overflow_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        if (isDownloading) {
                            DropdownMenuItem(
                                text = { Text("Cancel download") },
                                onClick = {
                                    onCancelDownload()
                                    showOverflowMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "Cancel download"
                                    )
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Remove all downloads") },
                                onClick = {
                                    onRemoveAllDownloads()
                                    showOverflowMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Remove all downloads"
                                    )
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (videos.isEmpty() && savedVideosCount == 0 && !isPreparing) {
            // Header + Action Pill Row are still visible, empty state message below
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (isSignInRequired) {
                    item(key = "sign_in_banner") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Sign in required",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Sign in to download Watch Later",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Watch Later is a private playlist that requires your active session.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                    )
                                }
                                Button(
                                    onClick = onSignInClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Sign In")
                                }
                            }
                        }
                    }
                }

                item(key = "summary_header") {
                    WatchLaterSummaryHeader(
                        playlist = playlist,
                        totalSaved = savedVideosCount,
                        readyCount = readyVideosCount,
                        isDownloading = isDownloading,
                        isPreparing = isPreparing
                    )
                }

                item(key = "pill_row") {
                    WatchLaterActionPillRow(
                        pillInfo = pillInfo,
                        canPlay = readyVideosCount > 0,
                        onPlayAllClick = onPlayAllClick,
                        onShuffleClick = onShuffleClick,
                        onPillAction = onPillAction
                    )
                }

                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, bottom = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkBorder,
                                        contentDescription = "No saved videos yet",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No saved videos yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Save videos to Watch Later and download them here for offline access.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // 1. Session / Sign In Required Banner (if applicable)
                if (isSignInRequired) {
                    item(key = "sign_in_banner") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Sign in required",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Sign in to download Watch Later",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Watch Later is a private playlist that requires your active session.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                    )
                                }
                                Button(
                                    onClick = onSignInClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Sign In")
                                }
                            }
                        }
                    }
                }

                // 2. Summary Line & Thin Progress Bar
                item(key = "summary_header") {
                    WatchLaterSummaryHeader(
                        playlist = playlist,
                        totalSaved = savedVideosCount.coerceAtLeast(videos.size),
                        readyCount = readyVideosCount,
                        isDownloading = isDownloading,
                        isPreparing = isPreparing
                    )
                }

                // 3. Action Pill Row (Play All, Shuffle, Download)
                item(key = "pill_row") {
                    WatchLaterActionPillRow(
                        pillInfo = pillInfo,
                        canPlay = readyVideosCount > 0,
                        onPlayAllClick = onPlayAllClick,
                        onShuffleClick = onShuffleClick,
                        onPillAction = onPillAction
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 4. Video Rows
                itemsIndexed(
                    items = videos,
                    key = { index, video -> "wl_video_${video.videoId}_$index" }
                ) { index, video ->
                    WatchLaterVideoRow(
                        video = video,
                        onClick = { onVideoClick(video) },
                        onRetry = { onRetryVideo(video) },
                        onRemoveFromDownloads = { onRemoveVideoFromDownloads(video) },
                        onDeleteFile = { onDeleteVideoFile(video) }
                    )
                }
            }
        }
    }
}

// ── Summary Header Line ─────────────────────────────────────────────────────

@Composable
fun WatchLaterSummaryHeader(
    playlist: DownloadedPlaylistEntity?,
    totalSaved: Int,
    readyCount: Int,
    isDownloading: Boolean,
    isPreparing: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        val totalBytes = playlist?.totalBytes?.takeIf { it > 0L } ?: playlist?.downloadedBytes ?: 0L
        val formattedSize = if (totalBytes > 0L) " • ${formatMB(totalBytes)}" else ""

        val summaryText = when {
            isPreparing -> "Preparing…"
            isDownloading -> {
                val downloadedMb = formatMB(playlist?.downloadedBytes ?: 0L)
                val totalEstMb = formatMB(playlist?.totalBytes?.takeIf { it > 0L } ?: (playlist?.downloadedBytes ?: 0L))
                "$readyCount of $totalSaved downloaded • $downloadedMb of ~$totalEstMb"
            }
            else -> {
                val plural = if (totalSaved == 1) "1 saved video" else "$totalSaved saved videos"
                "$plural$formattedSize"
            }
        }

        Text(
            text = summaryText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Thin progress bar while downloading
        if (isDownloading || isPreparing) {
            Spacer(modifier = Modifier.height(6.dp))
            val prog = if (isPreparing) 0f else {
                if (totalSaved > 0) readyCount.toFloat() / totalSaved.toFloat() else 0f
            }
            LinearProgressIndicator(
                progress = { prog.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

// ── Action Pill Row (Exact YouTube Style: White Play All, Dark Gray Shuffle & Download) ──

@Composable
fun WatchLaterActionPillRow(
    pillInfo: DownloadPillInfo,
    canPlay: Boolean,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onPillAction: (DownloadPillAction) -> Unit
) {
    val zeroElevation = ButtonDefaults.buttonElevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        focusedElevation = 0.dp,
        hoveredElevation = 0.dp,
        disabledElevation = 0.dp
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play All Pill (EXACT image_46670a8e: Solid White Pill, Solid Black Play Icon ▶, Solid Black Text)
        Button(
            onClick = onPlayAllClick,
            enabled = true,
            elevation = zeroElevation,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFFFFF),
                contentColor = Color(0xFF000000),
                disabledContainerColor = Color(0xFFFFFFFF),
                disabledContentColor = Color(0xFF000000)
            ),
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .testTag("play_all_pill")
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color(0xFF000000),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Play All",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF000000),
                maxLines = 1
            )
        }

        // Shuffle Pill (EXACT image_46670a8e: Solid Dark Gray #272727 Pill, Solid White Shuffle Icon 🔀, Solid White Text)
        Button(
            onClick = onShuffleClick,
            enabled = true,
            elevation = zeroElevation,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF272727),
                contentColor = Color(0xFFFFFFFF),
                disabledContainerColor = Color(0xFF272727),
                disabledContentColor = Color(0xFFFFFFFF)
            ),
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .testTag("shuffle_pill")
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = null,
                tint = Color(0xFFFFFFFF),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Shuffle",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFFFFF),
                maxLines = 1
            )
        }

        // Download Pill (EXACT image_46670a8e: Solid Dark Gray #272727 Pill, Solid White Download Icon ⬇, Solid White Text)
        Button(
            onClick = { onPillAction(pillInfo.action) },
            enabled = true,
            elevation = zeroElevation,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF272727),
                contentColor = Color(0xFFFFFFFF),
                disabledContainerColor = Color(0xFF272727),
                disabledContentColor = Color(0xFFFFFFFF)
            ),
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .testTag("download_pill")
        ) {
            if (pillInfo.showProgressIndicator) {
                CircularProgressIndicator(
                    progress = { pillInfo.progress },
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFFFFFFFF)
                )
            } else {
                Icon(
                    imageVector = pillInfo.icon,
                    contentDescription = null,
                    tint = Color(0xFFFFFFFF),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = pillInfo.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFFFFF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Video Row (16:9 Thumbnail, 140dp wide, 12dp corners) ─────────────────────

@Composable
fun WatchLaterVideoRow(
    video: DownloadedVideoEntity,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    onRemoveFromDownloads: () -> Unit,
    onDeleteFile: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val isDownloading = video.state == "DOWNLOADING"
    val isQueued = video.state == "QUEUED"
    val isDone = video.state == "DONE"
    val isFailed = video.state == "FAILED"
    val isStalled = isDownloading && (System.currentTimeMillis() - video.lastUpdatedAt > 60_000L)

    val metadataText = buildString {
        if (video.channelName.isNotBlank() && video.channelName != "Unknown Channel") {
            append(video.channelName)
        }
        if (video.duration.isNotBlank()) {
            if (isNotEmpty()) append(" • ")
            append(video.duration)
        }
        if (video.bytesDownloaded > 0L) {
            if (isNotEmpty()) append(" • ")
            append(formatMB(video.bytesDownloaded))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 16:9 Thumbnail ~140dp wide, 12dp corners
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (video.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = "Thumbnail for ${video.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Duration badge at bottom right
            if (video.duration.isNotBlank()) {
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
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            // Stalled overlay indicator
            if (isStalled) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "Stalled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // Info Column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (metadataText.isNotBlank()) {
                Text(
                    text = metadataText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // "No longer in Watch Later" tag
            if (video.noLongerInWatchLater) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "No longer in Watch Later",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (isStalled) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Stalled • Tap to retry",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onRetry() }
                )
            }
        }

        // Trailing: overflow menu (plus download progress or retry if needed)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when {
                isStalled || isFailed -> {
                    IconButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry download",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                isDownloading -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
                        CircularProgressIndicator(
                            progress = { (video.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${video.progress}%",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Video options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        onClick = {
                            onRemoveFromDownloads()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Remove"
                            )
                        }
                    )
                }
            }
        }
    }
}

// ── Previews for Watch Later Detail Screen in All Required States ────────────

@Preview(name = "Watch Later Detail - Preparing", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun WatchLaterDetailPreparingPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        WatchLaterDetailContent(
            playlist = DownloadedPlaylistEntity(
                playlistId = "WL",
                title = "Watch Later",
                thumbnailUrl = null,
                totalCount = 0,
                state = "PREPARING"
            ),
            videos = emptyList(),
            savedVideosCount = 3,
            isSessionValid = true
        )
    }
}

@Preview(name = "Watch Later Detail - Downloading", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun WatchLaterDetailDownloadingPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val sampleVideos = listOf(
            DownloadedVideoEntity(
                fileName = "vid1.mp4",
                videoId = "v1",
                title = "Learn Jetpack Compose in 2026",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                channelName = "Android Developers",
                duration = "14:20",
                bytesDownloaded = 45 * 1024 * 1024L,
                state = "DONE",
                progress = 100
            ),
            DownloadedVideoEntity(
                fileName = "PENDING_WL_1_v2",
                videoId = "v2",
                title = "Kotlin Coroutines & Flow Deep Dive",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                channelName = "JetBrains",
                duration = "22:15",
                bytesDownloaded = 18 * 1024 * 1024L,
                totalBytes = 28 * 1024 * 1024L,
                state = "DOWNLOADING",
                progress = 63
            ),
            DownloadedVideoEntity(
                fileName = "PENDING_WL_2_v3",
                videoId = "v3",
                title = "Material 3 Adaptive Layouts for Foldables and Tablets",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                channelName = "Google Design",
                duration = "08:45",
                state = "QUEUED",
                progress = 0
            )
        )
        WatchLaterDetailContent(
            playlist = DownloadedPlaylistEntity(
                playlistId = "WL",
                title = "Watch Later",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                totalCount = 3,
                completedCount = 1,
                state = "DOWNLOADING",
                currentVideoIndex = 1,
                currentVideoProgress = 63,
                downloadedBytes = 63 * 1024 * 1024L,
                totalBytes = 245 * 1024 * 1024L
            ),
            videos = sampleVideos,
            savedVideosCount = 3,
            readyVideosCount = 1,
            isSessionValid = true
        )
    }
}

@Preview(name = "Watch Later Detail - Done", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun WatchLaterDetailDonePreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val sampleVideos = listOf(
            DownloadedVideoEntity(
                fileName = "vid1.mp4",
                videoId = "v1",
                title = "Learn Jetpack Compose in 2026",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                channelName = "Android Developers",
                duration = "14:20",
                bytesDownloaded = 45 * 1024 * 1024L,
                state = "DONE",
                progress = 100
            ),
            DownloadedVideoEntity(
                fileName = "vid2.mp4",
                videoId = "v2",
                title = "Kotlin Coroutines & Flow Deep Dive",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                channelName = "JetBrains",
                duration = "22:15",
                bytesDownloaded = 95 * 1024 * 1024L,
                state = "DONE",
                progress = 100
            ),
            DownloadedVideoEntity(
                fileName = "vid3.mp4",
                videoId = "v3",
                title = "Material 3 Adaptive Layouts for Foldables and Tablets",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                channelName = "Google Design",
                duration = "08:45",
                bytesDownloaded = 105 * 1024 * 1024L,
                state = "DONE",
                progress = 100
            )
        )
        WatchLaterDetailContent(
            playlist = DownloadedPlaylistEntity(
                playlistId = "WL",
                title = "Watch Later",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                totalCount = 3,
                completedCount = 3,
                state = "DONE",
                downloadedBytes = 245 * 1024 * 1024L,
                totalBytes = 245 * 1024 * 1024L
            ),
            videos = sampleVideos,
            savedVideosCount = 3,
            readyVideosCount = 3,
            isSessionValid = true
        )
    }
}

@Preview(name = "Watch Later Detail - Failed", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun WatchLaterDetailFailedPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val sampleVideos = listOf(
            DownloadedVideoEntity(
                fileName = "vid1.mp4",
                videoId = "v1",
                title = "Learn Jetpack Compose in 2026",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                channelName = "Android Developers",
                duration = "14:20",
                bytesDownloaded = 45 * 1024 * 1024L,
                state = "DONE",
                progress = 100
            ),
            DownloadedVideoEntity(
                fileName = "PENDING_WL_1_v2",
                videoId = "v2",
                title = "Kotlin Coroutines & Flow Deep Dive",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                channelName = "JetBrains",
                duration = "22:15",
                state = "FAILED",
                errorMessage = "Network timeout"
            )
        )
        WatchLaterDetailContent(
            playlist = DownloadedPlaylistEntity(
                playlistId = "WL",
                title = "Watch Later",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                totalCount = 2,
                completedCount = 1,
                state = "FAILED",
                errorMessage = "Download encountered network errors"
            ),
            videos = sampleVideos,
            savedVideosCount = 2,
            readyVideosCount = 1,
            isSessionValid = true
        )
    }
}

@Preview(name = "Watch Later Detail - Sign In Required", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun WatchLaterDetailSignInRequiredPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        WatchLaterDetailContent(
            playlist = DownloadedPlaylistEntity(
                playlistId = "WL",
                title = "Watch Later",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                totalCount = 3,
                completedCount = 0,
                state = "FAILED",
                errorMessage = "Sign in to download Watch Later"
            ),
            videos = emptyList(),
            savedVideosCount = 3,
            isSessionValid = false
        )
    }
}

@Preview(name = "Watch Later Detail - Empty", showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun WatchLaterDetailEmptyPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        WatchLaterDetailContent(
            playlist = null,
            videos = emptyList(),
            savedVideosCount = 0,
            isSessionValid = true
        )
    }
}
