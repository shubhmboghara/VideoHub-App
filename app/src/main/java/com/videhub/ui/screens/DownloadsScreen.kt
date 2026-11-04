package com.videhub.ui.screens

import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.videhub.data.AppDatabase
import com.videhub.data.entity.DownloadedVideoEntity
import com.videhub.data.model.PlaylistDownloadSummary
import com.videhub.service.DownloadService
import com.videhub.utils.DownloadProgress
import com.videhub.utils.DownloadProgressTracker
import com.videhub.utils.FileUtils
import com.videhub.utils.ThumbnailDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// ── State Enum for Downloads ──
enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    DONE,
    FAILED
}

data class DownloadItemData(
    val file: File,
    val dbEntity: DownloadedVideoEntity?
)

enum class DownloadsSortOption(val label: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    TITLE_ASC("Title (A-Z)"),
    SIZE_DESC("Size (Largest First)")
}

// ── Helpers ──
fun cleanTitle(raw: String): String =
    raw.replace("_", " ").replace(Regex("\\s{2,}"), " ").trim()

fun cleanChannelName(raw: String): String =
    raw.replace(Regex("\\p{C}"), "").trim()

fun formatMB(bytes: Long): String {
    val mb = bytes / (1024f * 1024f)
    return if (mb >= 1024f) {
        String.format(Locale.US, "%.1f GB", mb / 1024f)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%d:%02d", mins, secs)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    navController: androidx.navigation.NavController,
    onVideoClick: (String, String, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    // Thumbnail Caching
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val oldDownloads = db.downloadedVideoDao().getAllDownloads().first()
                for (download in oldDownloads) {
                    if (download.thumbnailUrl.startsWith("http")) {
                        val localThumb = ThumbnailDownloader.downloadThumbnail(
                            context,
                            download.videoId,
                            download.thumbnailUrl
                        )
                        if (localThumb != null && localThumb != download.thumbnailUrl) {
                            db.downloadedVideoDao().insertDownload(
                                download.copy(thumbnailUrl = localThumb)
                            )
                        }
                    }
                }
                val oldPlaylists = db.downloadedPlaylistDao().getAllDownloadedPlaylists().first()
                for (pl in oldPlaylists) {
                    if (pl.thumbnailUrl?.startsWith("http") == true) {
                        val localThumb = ThumbnailDownloader.downloadThumbnail(
                            context,
                            pl.playlistId,
                            pl.thumbnailUrl
                        )
                        if (localThumb != null && localThumb != pl.thumbnailUrl) {
                            db.downloadedPlaylistDao().updatePlaylistThumbnail(pl.playlistId, localThumb)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val downloadedPlaylists by db.downloadedPlaylistDao().getDownloadedPlaylistsSummary().collectAsStateWithLifecycle(initialValue = emptyList())
    val allDownloads by db.downloadedVideoDao().getSingleDownloads().collectAsStateWithLifecycle(initialValue = emptyList())
    val playlistDownloads by db.downloadedVideoDao().getPlaylistDownloads().collectAsStateWithLifecycle(initialValue = emptyList())
    val activeSingleDownloadsMap by DownloadProgressTracker.activeDownloads.collectAsStateWithLifecycle(initialValue = emptyMap())

    val activeDownloads: List<DownloadProgress> = remember(activeSingleDownloadsMap) {
        activeSingleDownloadsMap.values
            .filter { it.playlistId == null && !it.isComplete }
            .sortedBy { it.notificationId }
    }

    var filesOnDisk by remember { mutableStateOf<List<File>>(emptyList()) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var cancelTargetProgress by remember { mutableStateOf<DownloadProgress?>(null) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var deletedItems by remember { mutableStateOf(setOf<String>()) }
    var pausedDownloads by remember { mutableStateOf(setOf<Int>()) }

    // Search, Filter & Sort State
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(DownloadsSortOption.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) } // 0: All, 1: Videos, 2: Music, 3: Playlists

    // Auto-focus input and show keyboard when search is activated
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Reset search when exiting search mode
    fun exitSearch() {
        searchQuery = ""
        isSearchActive = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    // Intercept Back button when search is active
    BackHandler(enabled = isSearchActive) {
        exitSearch()
    }

    suspend fun loadFiles() {
        withContext(Dispatchers.IO) {
            val dir1 = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val dir2 = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val exts = setOf("mp4", "webm", "m4a")
            val f1 = dir1?.takeIf { it.exists() }?.listFiles { f -> f.isFile && f.extension in exts && !f.name.endsWith(".tmp") && !f.name.endsWith(".download") }?.toList() ?: emptyList()
            val f2 = dir2.takeIf { it.exists() }?.listFiles { f -> f.isFile && f.extension in exts && !f.name.endsWith(".tmp") && !f.name.endsWith(".download") }?.toList() ?: emptyList()
            val merged = (f1 + f2).distinctBy { it.absolutePath }
            withContext(Dispatchers.Main) {
                filesOnDisk = merged
            }
        }
    }

    LaunchedEffect(Unit) { loadFiles() }

    // Filter out files that belong to active downloads or playlist downloads so partial files NEVER appear in finished lists
    val activeVideoIds = remember(activeDownloads) { activeDownloads.map { it.videoId }.toSet() }
    val activeFileNames = remember(activeDownloads) { activeDownloads.map { "${it.title}.mp4" }.toSet() }

    val displayItems = remember(filesOnDisk, allDownloads, playlistDownloads, activeVideoIds, activeFileNames) {
        val playlistFileNames = playlistDownloads.map { it.fileName }.toSet()
        filesOnDisk.filter { file ->
            !playlistFileNames.contains(file.name) &&
            !activeFileNames.contains(file.name)
        }.mapNotNull { file ->
            val entity = allDownloads.find { it.fileName == file.name }
            if (entity != null && activeVideoIds.contains(entity.videoId)) {
                // Currently actively downloading - MUST NOT appear in finished list
                null
            } else {
                DownloadItemData(file, entity)
            }
        }
    }

    val videos = remember(displayItems, deletedItems) {
        displayItems.filter { !FileUtils.isAudio(it.dbEntity, it.file.absolutePath) && !deletedItems.contains(it.file.absolutePath) }
    }

    val audios = remember(displayItems, deletedItems) {
        displayItems.filter { FileUtils.isAudio(it.dbEntity, it.file.absolutePath) && !deletedItems.contains(it.file.absolutePath) }
    }

    // Filtered & Sorted items
    val filteredVideos = remember(videos, searchQuery, sortOption) {
        val filtered = if (searchQuery.isBlank()) videos else videos.filter { item ->
            val title = item.dbEntity?.title ?: cleanTitle(item.file.nameWithoutExtension)
            val channel = item.dbEntity?.channelName ?: ""
            title.contains(searchQuery, ignoreCase = true) || channel.contains(searchQuery, ignoreCase = true)
        }
        when (sortOption) {
            DownloadsSortOption.DATE_DESC -> filtered.sortedByDescending { it.dbEntity?.downloadedAt ?: it.file.lastModified() }
            DownloadsSortOption.DATE_ASC -> filtered.sortedBy { it.dbEntity?.downloadedAt ?: it.file.lastModified() }
            DownloadsSortOption.TITLE_ASC -> filtered.sortedBy { (it.dbEntity?.title ?: cleanTitle(it.file.nameWithoutExtension)).lowercase() }
            DownloadsSortOption.SIZE_DESC -> filtered.sortedByDescending { it.file.length() }
        }
    }

    val filteredAudios = remember(audios, searchQuery, sortOption) {
        val filtered = if (searchQuery.isBlank()) audios else audios.filter { item ->
            val title = item.dbEntity?.title ?: cleanTitle(item.file.nameWithoutExtension)
            val artist = item.dbEntity?.channelName ?: ""
            title.contains(searchQuery, ignoreCase = true) || artist.contains(searchQuery, ignoreCase = true)
        }
        when (sortOption) {
            DownloadsSortOption.DATE_DESC -> filtered.sortedByDescending { it.dbEntity?.downloadedAt ?: it.file.lastModified() }
            DownloadsSortOption.DATE_ASC -> filtered.sortedBy { it.dbEntity?.downloadedAt ?: it.file.lastModified() }
            DownloadsSortOption.TITLE_ASC -> filtered.sortedBy { (it.dbEntity?.title ?: cleanTitle(it.file.nameWithoutExtension)).lowercase() }
            DownloadsSortOption.SIZE_DESC -> filtered.sortedByDescending { it.file.length() }
        }
    }

    val filteredPlaylists = remember(downloadedPlaylists, searchQuery, sortOption) {
        val filtered = if (searchQuery.isBlank()) downloadedPlaylists else downloadedPlaylists.filter {
            (it.playlistName ?: "").contains(searchQuery, ignoreCase = true)
        }
        when (sortOption) {
            DownloadsSortOption.DATE_DESC, DownloadsSortOption.DATE_ASC -> filtered
            DownloadsSortOption.TITLE_ASC -> filtered.sortedBy { (it.playlistName ?: "").lowercase() }
            DownloadsSortOption.SIZE_DESC -> filtered.sortedByDescending { it.totalVideos }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog && fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; fileToDelete = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Text(
                    text = "Delete Download?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${cleanTitle(fileToDelete?.nameWithoutExtension ?: "")}' (${formatMB(fileToDelete?.length() ?: 0L)})?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        fileToDelete?.let { f ->
                            if (f.exists()) f.delete()
                            deletedItems = deletedItems + f.absolutePath
                            scope.launch { db.downloadedVideoDao().deleteDownloadByFileName(f.name) }
                            Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
                        }
                        showDeleteDialog = false
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false; fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Cancel Active Download Confirmation Dialog
    if (showCancelConfirmDialog && cancelTargetProgress != null) {
        val progressItem = cancelTargetProgress!!
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false; cancelTargetProgress = null },
            title = { Text("Cancel Download?", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("Are you sure you want to cancel downloading '${cleanTitle(progressItem.title)}'?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cancelIntent = android.content.Intent(context, DownloadService::class.java).apply {
                            action = "CANCEL_${progressItem.notificationId}"
                        }
                        context.startService(cancelIntent)

                        DownloadProgressTracker.removeDownload(progressItem.notificationId)
                        com.videhub.viewmodel.DownloadViewModel.removeActiveDownload(progressItem.videoId)

                        scope.launch(Dispatchers.IO) {
                            try {
                                db.downloadedVideoDao().deletePlaceholderByVideoId(progressItem.videoId)
                            } catch (_: Exception) {}
                        }
                        Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                        showCancelConfirmDialog = false
                        cancelTargetProgress = null
                    }
                ) {
                    Text("Cancel Download", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmDialog = false; cancelTargetProgress = null }) {
                    Text("Keep Download")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search downloads...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(searchFocusRequester)
                                    )
                                }

                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Downloads",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearchActive) {
                                exitSearch()
                            } else {
                                onBackPressedDispatcher?.onBackPressed()
                            }
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search downloads")
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort downloads")
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DownloadsSortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        sortOption = option
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortOption == option) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        val hasNoItems = displayItems.isEmpty() && activeDownloads.isEmpty() && downloadedPlaylists.isEmpty()
        val hasNoSearchResults = searchQuery.isNotBlank() && filteredVideos.isEmpty() && filteredAudios.isEmpty() && filteredPlaylists.isEmpty()

        if (hasNoItems || hasNoSearchResults) {
            val infiniteTransition = rememberInfiniteTransition(label = "empty_state")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .size(76.dp)
                            .scale(scale)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (hasNoSearchResults) Icons.Default.SearchOff else Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (hasNoSearchResults) "No Results Found" else "No Downloads Yet",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (hasNoSearchResults)
                            "No downloads matched \"$searchQuery\". Try checking spelling or searching for a different title."
                        else
                            "Videos and music you download will appear here for offline playback.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    if (hasNoSearchResults) {
                        Spacer(Modifier.height(20.dp))
                        OutlinedButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Clear Search")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Category Filter Chips (Hidden when search is active)
                if (!isSearchActive) {
                    item(key = "category_filter_chips") {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val categories = listOf(
                                Pair("All (${filteredVideos.size + filteredAudios.size + filteredPlaylists.size})", 0),
                                Pair("Videos (${filteredVideos.size})", 1),
                                Pair("Music (${filteredAudios.size})", 2),
                                Pair("Playlists (${filteredPlaylists.size})", 3)
                            )
                            items(categories) { (label, index) ->
                                FilterChip(
                                    selected = selectedCategoryIndex == index,
                                    onClick = { selectedCategoryIndex = index },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 13.sp,
                                            fontWeight = if (selectedCategoryIndex == index) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }

                // Downloading Section
                if (activeDownloads.isNotEmpty() && !isSearchActive) {
                    item(key = "section_downloading_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DownloadForOffline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Downloading · ${activeDownloads.size}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    itemsIndexed(
                        activeDownloads,
                        key = { index, progress -> "active_${progress.notificationId}_$index" }
                    ) { _, progress ->
                        val isPaused = pausedDownloads.contains(progress.notificationId)
                        ActiveDownloadCard(
                            item = progress,
                            isPaused = isPaused,
                            onTogglePause = {
                                pausedDownloads = if (isPaused) {
                                    pausedDownloads - progress.notificationId
                                } else {
                                    pausedDownloads + progress.notificationId
                                }
                            },
                            onCancel = {
                                cancelTargetProgress = progress
                                showCancelConfirmDialog = true
                            },
                            onRetry = {
                                val retryIntent = android.content.Intent(context, DownloadService::class.java).apply {
                                    putExtra("title", progress.title)
                                    putExtra("videoId", progress.videoId)
                                    putExtra("thumbnailUrl", progress.thumbnailUrl)
                                    putExtra("isAudioOnly", progress.isAudioOnly)
                                    putExtra("quality", progress.quality)
                                }
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    context.startForegroundService(retryIntent)
                                } else {
                                    context.startService(retryIntent)
                                }
                                Toast.makeText(context, "Retrying download...", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // Downloaded Playlists Section
                if ((selectedCategoryIndex == 0 || selectedCategoryIndex == 3) && filteredPlaylists.isNotEmpty()) {
                    item(key = "section_playlists_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Playlists · ${filteredPlaylists.size}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(
                                filteredPlaylists,
                                key = { index, playlist -> "pl_${playlist.playlistId}_$index" }
                            ) { _, playlist ->
                                PlaylistDownloadCard(
                                    playlist = playlist,
                                    onClick = {
                                        navController.navigate(com.videhub.navigation.Screen.DownloadedPlaylistDetail.createRoute(playlist.playlistId))
                                    }
                                )
                            }
                        }
                    }
                }

                // Music Section (Finished Items Only)
                if ((selectedCategoryIndex == 0 || selectedCategoryIndex == 2) && filteredAudios.isNotEmpty()) {
                    item(key = "section_music_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Music · ${filteredAudios.size}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    itemsIndexed(
                        filteredAudios,
                        key = { index, item -> "aud_${item.file.absolutePath}_$index" }
                    ) { _, item ->
                        SpotifyMusicItem(
                            title = item.dbEntity?.title ?: cleanTitle(item.file.nameWithoutExtension),
                            artist = cleanChannelName(item.dbEntity?.channelName ?: "Unknown Channel"),
                            thumbnailUrl = item.dbEntity?.thumbnailUrl,
                            fileSize = formatMB(item.file.length()),
                            audioFilePath = item.file.absolutePath,
                            onClick = {
                                com.videhub.QueueManager.clear()
                                val validItems = filteredAudios
                                val currentIndex = validItems.indexOf(item)
                                if (currentIndex in 0 until validItems.lastIndex) {
                                    for (i in (currentIndex + 1)..validItems.lastIndex) {
                                        val qItem = validItems[i]
                                        com.videhub.QueueManager.enqueue(
                                            com.videhub.PlayQueueItem(
                                                url = qItem.file.absolutePath,
                                                title = qItem.dbEntity?.title ?: cleanTitle(qItem.file.nameWithoutExtension),
                                                uploaderName = qItem.dbEntity?.channelName ?: "Unknown Channel",
                                                thumbnailUrl = qItem.dbEntity?.thumbnailUrl ?: ""
                                            )
                                        )
                                    }
                                }
                                onVideoClick(
                                    item.file.absolutePath,
                                    item.dbEntity?.title ?: item.file.nameWithoutExtension,
                                    item.dbEntity?.thumbnailUrl ?: "",
                                    true
                                )
                            },
                            onMoreClick = { fileToDelete = item.file; showDeleteDialog = true }
                        )
                    }
                }

                // Videos Section (Finished Items Only)
                if ((selectedCategoryIndex == 0 || selectedCategoryIndex == 1) && filteredVideos.isNotEmpty()) {
                    item(key = "section_videos_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Videos · ${filteredVideos.size}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    itemsIndexed(
                        filteredVideos,
                        key = { index, item -> "vid_${item.file.absolutePath}_$index" }
                    ) { _, item ->
                        YouTubeVideoItem(
                            title = item.dbEntity?.title ?: cleanTitle(item.file.nameWithoutExtension),
                            channel = cleanChannelName(item.dbEntity?.channelName ?: ""),
                            thumbnailUrl = item.dbEntity?.thumbnailUrl,
                            fileSize = formatMB(item.file.length()),
                            videoFilePath = item.file.absolutePath,
                            onClick = {
                                com.videhub.QueueManager.clear()
                                val validItems = filteredVideos
                                val currentIndex = validItems.indexOf(item)
                                if (currentIndex in 0 until validItems.lastIndex) {
                                    for (i in (currentIndex + 1)..validItems.lastIndex) {
                                        val qItem = validItems[i]
                                        com.videhub.QueueManager.enqueue(
                                            com.videhub.PlayQueueItem(
                                                url = qItem.file.absolutePath,
                                                title = qItem.dbEntity?.title ?: cleanTitle(qItem.file.nameWithoutExtension),
                                                uploaderName = qItem.dbEntity?.channelName ?: "",
                                                thumbnailUrl = qItem.dbEntity?.thumbnailUrl ?: ""
                                            )
                                        )
                                    }
                                }
                                onVideoClick(
                                    item.file.absolutePath,
                                    item.dbEntity?.title ?: item.file.nameWithoutExtension,
                                    item.dbEntity?.thumbnailUrl ?: "",
                                    false
                                )
                            },
                            onMoreClick = { fileToDelete = item.file; showDeleteDialog = true }
                        )
                    }
                }
            }
        }
    }
}

// ── Music Thumbnail Composable ────────────────────────────────────────────────
@Composable
fun MusicThumbnail(
    url: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isError by remember(url) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(90.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank() && !isError) {
            // Blurred-background layer (Android 12+ / API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(16.dp)
                )
            }

            // Foreground layer
            AsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { isError = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// ── Active Downloading Card Component ──────────────────────────────────────────
@Composable
fun ActiveDownloadCard(
    item: DownloadProgress,
    isPaused: Boolean = false,
    onTogglePause: () -> Unit = {},
    onCancel: () -> Unit,
    onRetry: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("active_download_card_${item.notificationId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isComplete)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.isAudioOnly) {
                    MusicThumbnail(
                        url = item.thumbnailUrl
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!item.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = item.thumbnailUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = cleanTitle(item.title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val qualityText = item.quality?.takeIf { it.isNotBlank() } ?: if (item.isAudioOnly) "Audio" else "HD"
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = qualityText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        val sizeText = if (item.totalBytes > 0)
                            "${formatMB(item.downloadedBytes)} / ${formatMB(item.totalBytes)}"
                        else if (item.progress >= 0)
                            "${item.progress}%"
                        else
                            "Connecting..."

                        Text(
                            text = if (isPaused) "Paused" else sizeText,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (item.isError) {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry Download",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else if (!item.isComplete) {
                        IconButton(
                            onClick = onTogglePause,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "Resume Download" else "Pause Download",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("cancel_download_button_${item.notificationId}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Download",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (!item.isComplete && !isPaused && !item.isError) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (item.progress >= 0) (item.progress / 100f).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

// ── Downloaded Playlist Card ──────────────────────────────────────────────────
@Composable
fun PlaylistDownloadCard(
    playlist: PlaylistDownloadSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSingleDownloadsMap by DownloadProgressTracker.activeDownloads.collectAsStateWithLifecycle(initialValue = emptyMap())

    val isDownloading = playlist.videoCount < playlist.totalVideos
    val activeItems = activeSingleDownloadsMap.values.filter { it.playlistId == playlist.playlistId && !it.isComplete && it.progress > 0 }
    val partialProgress = if (activeItems.isNotEmpty() && playlist.totalVideos > 0) {
        activeItems.sumOf { (it.progress.coerceAtLeast(0).toFloat() / 100f).toDouble() }.toFloat() / playlist.totalVideos.toFloat()
    } else 0f

    val progressPercent = if (playlist.totalVideos > 0) (playlist.videoCount.toFloat() / playlist.totalVideos.toFloat()) + partialProgress else 0f

    com.videhub.ui.components.PlaylistCard(
        title = playlist.playlistName ?: "Unknown Playlist",
        subtitle = "${playlist.videoCount} / ${playlist.totalVideos} videos",
        thumbnailUrl = playlist.coverThumbnailUrl,
        onClick = onClick,
        modifier = modifier.width(220.dp),
        downloadProgress = if (isDownloading) progressPercent else null
    )
}

// ── Downloaded Music Item ────────────────────────────────────────────────────
@Composable
fun SpotifyMusicItem(
    modifier: Modifier = Modifier,
    title: String,
    artist: String,
    thumbnailUrl: String?,
    fileSize: String,
    audioFilePath: String,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        MusicThumbnail(
            url = if (!thumbnailUrl.isNullOrBlank()) thumbnailUrl else if (audioFilePath.isNotBlank()) audioFilePath else null
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "♪ ${if (artist.isNotBlank()) artist else "Music"} • $fileSize",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Downloaded Video Item ────────────────────────────────────────────────────
@Composable
fun YouTubeVideoItem(
    modifier: Modifier = Modifier,
    title: String,
    channel: String,
    thumbnailUrl: String?,
    fileSize: String,
    videoFilePath: String,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .width(128.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            val modelToUse = if (!thumbnailUrl.isNullOrBlank()) thumbnailUrl else File(videoFilePath)
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(modelToUse)
                    .error(android.R.drawable.ic_media_play)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (channel.isNotBlank()) "$channel • $fileSize" else fileSize,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────
@Preview
@Composable
fun MusicThumbnailPreview() {
    MaterialTheme {
        MusicThumbnail(url = "https://example.com/sample.jpg")
    }
}

@Preview
@Composable
fun MusicThumbnailNullPreview() {
    MaterialTheme {
        MusicThumbnail(url = null)
    }
}
