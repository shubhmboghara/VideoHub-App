package com.videhub.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.*
import androidx.core.content.FileProvider
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import com.videhub.QueueManager
import com.videhub.PlayQueueItem
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

object MediaMetadataHelper {
    data class AudioMetadata(
        val durationMs: Long = 0L,
        val embeddedPicture: ByteArray? = null
    )

    private val cache = java.util.concurrent.ConcurrentHashMap<String, AudioMetadata>()

    fun getMetadata(filePath: String): AudioMetadata {
        if (filePath.isBlank()) return AudioMetadata()
        return cache.getOrPut(filePath) {
            try {
                val f = File(filePath)
                if (!f.exists() || !f.isFile) return@getOrPut AudioMetadata()
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(filePath)
                val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val pic = mmr.embeddedPicture
                mmr.release()
                AudioMetadata(dur, pic)
            } catch (_: Exception) {
                AudioMetadata()
            }
        }
    }
}

fun extractYouTubeId(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val clean = raw.trim()
    if (clean.length == 11 && !clean.contains("/") && !clean.contains(".") && !clean.contains("?") && !clean.contains("&")) {
        return clean
    }
    val regex = Regex("""(?:v=|/v/|/embed/|youtu\.be/|/shorts/)([a-zA-Z0-9_-]{11})""")
    return regex.find(clean)?.groupValues?.get(1)
}

fun resolveThumbnailModel(
    rawThumbnailUrl: String?,
    videoId: String?,
    filePath: String
): Any {
    if (!rawThumbnailUrl.isNullOrBlank()) {
        if (rawThumbnailUrl.startsWith("http://") || rawThumbnailUrl.startsWith("https://")) {
            return rawThumbnailUrl
        }
        val cleanPath = if (rawThumbnailUrl.startsWith("file://")) rawThumbnailUrl.removePrefix("file://") else rawThumbnailUrl
        val f = File(cleanPath)
        if (f.exists() && f.isFile && f.length() > 0L) {
            return f
        }
    }
    // Local path is dead or missing (e.g. from previous app update): recover thumbnail via YouTube ID
    val ytId = extractYouTubeId(videoId)
        ?: extractYouTubeId(rawThumbnailUrl)
        ?: extractYouTubeId(filePath)
        ?: extractYouTubeId(File(filePath).nameWithoutExtension)
    if (!ytId.isNullOrBlank()) {
        return "https://i.ytimg.com/vi/$ytId/hqdefault.jpg"
    }
    val localFile = File(filePath)
    if (localFile.exists() && localFile.isFile && localFile.length() > 0L) {
        return localFile
    }
    return android.R.drawable.ic_media_play
}

fun getYouTubeLink(entity: DownloadedVideoEntity?, file: File): String {
    val id = extractYouTubeId(entity?.videoId)
        ?: extractYouTubeId(entity?.thumbnailUrl)
        ?: extractYouTubeId(file.nameWithoutExtension)
    return if (!id.isNullOrBlank()) {
        "https://www.youtube.com/watch?v=$id"
    } else if (entity?.videoId?.startsWith("http") == true) {
        entity.videoId
    } else {
        "https://www.youtube.com/results?search_query=${android.net.Uri.encode(cleanTitle(entity?.title ?: file.nameWithoutExtension))}"
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
    val allDownloads by db.downloadedVideoDao().getAllDownloads().collectAsStateWithLifecycle(initialValue = emptyList())
    val playlistDownloads by db.downloadedVideoDao().getPlaylistDownloads().collectAsStateWithLifecycle(initialValue = emptyList())
    val activeSingleDownloadsMap by DownloadProgressTracker.activeDownloads.collectAsStateWithLifecycle(initialValue = emptyMap())
    var showWatchLaterMenu by remember { mutableStateOf(false) }

    val activeDownloads: List<DownloadProgress> = remember(activeSingleDownloadsMap, allDownloads) {
        val inMemory = activeSingleDownloadsMap.values
            .filter { it.playlistId == null && !it.isComplete }
            .associateBy { it.videoId }

        val dbPending = allDownloads
            .filter { it.playlistId == null && it.state in listOf("DOWNLOADING", "QUEUED", "PAUSED", "FAILED") }
            .map { entity ->
                val existing = inMemory[entity.videoId]
                existing ?: DownloadProgress(
                    notificationId = (entity.videoId.hashCode() and 0x7FFFFFFF),
                    title = entity.title,
                    progress = entity.progress,
                    isComplete = entity.state == "DONE",
                    isError = entity.state == "FAILED",
                    downloadedBytes = entity.bytesDownloaded,
                    totalBytes = entity.totalBytes,
                    thumbnailUrl = entity.thumbnailUrl,
                    videoId = entity.videoId,
                    playlistId = entity.playlistId,
                    isAudioOnly = entity.isAudioOnly,
                    errorMessage = entity.errorMessage,
                    lastUpdatedAt = entity.lastUpdatedAt
                )
            }

        val combinedMap = dbPending.associateBy { it.videoId }.toMutableMap()
        inMemory.forEach { (vId, p) -> combinedMap[vId] = p }
        combinedMap.values.sortedBy { it.notificationId }
    }

    var filesOnDisk by remember { mutableStateOf<List<File>>(emptyList()) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var itemToDelete by remember { mutableStateOf<DownloadItemData?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedItemForDetails by remember { mutableStateOf<DownloadItemData?>(null) }
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
            val exts = setOf("mp4", "webm", "m4a", "mp3", "mkv", "aac", "opus", "ogg", "flac")

            // Auto-heal legacy files that were saved without extension or with quality suffixes like _360p, _Video, _Audio
            listOfNotNull(dir1, dir2).forEach { dir ->
                if (dir.exists()) {
                    val allDiskFiles = dir.listFiles { f -> f.isFile && !f.name.endsWith(".tmp") && !f.name.endsWith(".download") && !f.name.endsWith(".part") } ?: emptyArray()
                    for (file in allDiskFiles) {
                        val ext = file.extension.lowercase()
                        if (ext !in exts) {
                            val matchingEntity = allDownloads.find {
                                it.fileName == file.name ||
                                (it.videoId.isNotBlank() && file.name.contains(it.videoId)) ||
                                (it.title.isNotBlank() && file.name.startsWith(it.title.take(15).replace(Regex("[^a-zA-Z0-9]"), "_"), ignoreCase = true))
                            }
                            val targetExt = if (matchingEntity?.isAudioOnly == true) ".m4a" else ".mp4"
                            val healedFile = File(file.parentFile, "${file.nameWithoutExtension}$targetExt")
                            if (!healedFile.exists() && file.renameTo(healedFile)) {
                                if (matchingEntity != null) {
                                    db.downloadedVideoDao().updateFileName(matchingEntity.videoId, healedFile.name)
                                }
                            }
                        }
                    }
                }
            }

            val f1 = dir1?.takeIf { it.exists() }?.listFiles { f -> f.isFile && f.extension.lowercase() in exts && !f.name.endsWith(".tmp") && !f.name.endsWith(".download") && !f.name.endsWith(".part") }?.toList() ?: emptyList()
            val f2 = dir2.takeIf { it.exists() }?.listFiles { f -> f.isFile && f.extension.lowercase() in exts && !f.name.endsWith(".tmp") && !f.name.endsWith(".download") && !f.name.endsWith(".part") }?.toList() ?: emptyList()

            // Also check all completed single entities from DB and ensure their files are in the list
            val directEntitiesFiles = allDownloads.filter { it.playlistId == null && it.state == "DONE" }.mapNotNull { entity ->
                val cf1 = dir1?.let { File(it, entity.fileName) }?.takeIf { it.exists() && it.isFile && it.length() > 0L }
                val cf2 = dir2.takeIf { it.exists() }?.let { File(it, entity.fileName) }?.takeIf { it.exists() && it.isFile && it.length() > 0L }
                cf1 ?: cf2
            }

            val merged = (f1 + f2 + directEntitiesFiles).distinctBy { it.absolutePath }
            withContext(Dispatchers.Main) {
                filesOnDisk = merged
            }
        }
    }

    LaunchedEffect(Unit) { loadFiles() }

    // Automatically reload files on disk whenever downloads state changes or completes
    LaunchedEffect(allDownloads.map { Triple(it.videoId, it.state, it.fileName) }) {
        loadFiles()
    }
    LaunchedEffect(activeSingleDownloadsMap.size) {
        loadFiles()
    }

    // Repair missing or dead local thumbnails automatically in background
    LaunchedEffect(allDownloads) {
        if (allDownloads.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                allDownloads.forEach { entity ->
                    val rawUrl = entity.thumbnailUrl
                    val needsRepair = rawUrl.isNullOrBlank() ||
                            (rawUrl.startsWith("file://") && !File(rawUrl.removePrefix("file://")).exists())
                    if (needsRepair) {
                        val localThumb = ThumbnailDownloader.downloadThumbnail(
                            context,
                            entity.videoId,
                            rawUrl
                        )
                        if (!localThumb.isNullOrBlank() && localThumb != rawUrl) {
                            db.downloadedVideoDao().updateThumbnailUrl(entity.videoId, localThumb)
                        }
                    }
                }
            }
        }
    }

    // Filter out files that belong to active downloads or playlist downloads so partial files NEVER appear in finished lists
    val activeVideoIds = remember(activeDownloads) { activeDownloads.map { it.videoId }.toSet() }
    val activeFileNames = remember(activeDownloads) { activeDownloads.map { "${it.title}.mp4" }.toSet() }

    val displayItems = remember(filesOnDisk, allDownloads, playlistDownloads, activeVideoIds, activeFileNames) {
        val playlistFileNames = playlistDownloads.map { it.fileName }.toSet()
        val dir1 = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val dir2 = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        // 1. First, include ALL completed single downloads directly from Room DB
        val completedSingleEntities = allDownloads.filter {
            it.playlistId == null && it.state == "DONE" && !activeVideoIds.contains(it.videoId)
        }

        val itemsFromDb = completedSingleEntities.mapNotNull { entity ->
            val directFile1 = dir1?.let { File(it, entity.fileName) }?.takeIf { it.exists() && it.isFile && it.length() > 0L }
            val directFile2 = dir2.takeIf { it.exists() }?.let { File(it, entity.fileName) }?.takeIf { it.exists() && it.isFile && it.length() > 0L }
            val matchedFromDisk = directFile1 ?: directFile2 ?: filesOnDisk.find { f ->
                f.name == entity.fileName ||
                f.name.equals(entity.fileName, ignoreCase = true) ||
                (entity.videoId.isNotBlank() && f.name.contains(entity.videoId)) ||
                (entity.title.isNotBlank() && f.nameWithoutExtension.startsWith(entity.title.take(20).replace(Regex("[^a-zA-Z0-9]"), "_"), ignoreCase = true))
            }
            if (matchedFromDisk != null) {
                DownloadItemData(matchedFromDisk, entity)
            } else {
                null
            }
        }

        val handledFilePaths = itemsFromDb.map { it.file.absolutePath }.toSet()
        val handledFileNames = itemsFromDb.map { it.file.name }.toSet()

        // 2. Also include any remaining files on disk that are not active or in playlists
        val remainingDiskItems = filesOnDisk.filter { file ->
            !handledFilePaths.contains(file.absolutePath) &&
            !handledFileNames.contains(file.name) &&
            !playlistFileNames.contains(file.name) &&
            !activeFileNames.contains(file.name)
        }.mapNotNull { file ->
            val entity = allDownloads.find {
                it.fileName == file.name ||
                it.fileName.equals(file.name, ignoreCase = true) ||
                (it.videoId.isNotBlank() && file.name.contains(it.videoId)) ||
                (it.title.isNotBlank() && file.nameWithoutExtension.startsWith(it.title.take(20).replace(Regex("[^a-zA-Z0-9]"), "_"), ignoreCase = true))
            }
            if (entity != null && (entity.state != "DONE" || activeVideoIds.contains(entity.videoId))) {
                null
            } else {
                DownloadItemData(file, entity)
            }
        }

        (itemsFromDb + remainingDiskItems).distinctBy { it.file.absolutePath }
    }

    val videos = remember(displayItems, deletedItems) {
        displayItems.filter { item ->
            !FileUtils.isAudio(item.dbEntity, item.file.absolutePath) &&
            !deletedItems.contains(item.file.absolutePath) &&
            !deletedItems.contains(item.file.name) &&
            (item.dbEntity?.videoId == null || !deletedItems.contains(item.dbEntity.videoId))
        }
    }

    val audios = remember(displayItems, deletedItems) {
        displayItems.filter { item ->
            FileUtils.isAudio(item.dbEntity, item.file.absolutePath) &&
            !deletedItems.contains(item.file.absolutePath) &&
            !deletedItems.contains(item.file.name) &&
            (item.dbEntity?.videoId == null || !deletedItems.contains(item.dbEntity.videoId))
        }
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
    if (showDeleteDialog && (fileToDelete != null || itemToDelete != null)) {
        val targetFile = fileToDelete ?: itemToDelete?.file
        val targetEntity = itemToDelete?.dbEntity
            ?: allDownloads.find { download ->
                targetFile != null && (
                    download.fileName == targetFile.name ||
                    download.fileName.equals(targetFile.name, ignoreCase = true) ||
                    (download.videoId.isNotBlank() && targetFile.name.contains(download.videoId)) ||
                    (download.title.isNotBlank() && targetFile.nameWithoutExtension.contains(download.title.take(15)))
                )
            }
        val displayTitle = targetEntity?.title ?: cleanTitle(targetFile?.nameWithoutExtension ?: "")
        val fileSizeText = formatMB(targetFile?.length() ?: 0L)

        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; fileToDelete = null; itemToDelete = null },
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
                    text = "Are you sure you want to delete '$displayTitle' ($fileSizeText)?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val f = targetFile
                        val entity = targetEntity

                        showDeleteDialog = false
                        fileToDelete = null
                        itemToDelete = null

                        // Mark in-memory deleted set immediately to instantly hide from UI
                        val idsToAdd = mutableSetOf<String>()
                        if (f != null) {
                            idsToAdd.add(f.absolutePath)
                            idsToAdd.add(f.name)
                            idsToAdd.add(f.nameWithoutExtension)
                        }
                        if (entity != null) {
                            if (entity.videoId.isNotBlank()) idsToAdd.add(entity.videoId)
                            if (entity.fileName.isNotBlank()) idsToAdd.add(entity.fileName)
                        }
                        deletedItems = deletedItems + idsToAdd

                        scope.launch(Dispatchers.IO) {
                            try {
                                // 1. Thorough physical file deletion across all download directories
                                val dir1 = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                val dir2 = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                val thumbnailDir = File(context.filesDir, "thumbnails")

                                listOfNotNull(dir1, dir2, context.filesDir, context.cacheDir).forEach { dir ->
                                    if (dir.exists()) {
                                        dir.listFiles()?.forEach { match ->
                                            val isMatch = (f != null && (match.name == f.name || match.nameWithoutExtension == f.nameWithoutExtension)) ||
                                                    (entity != null && entity.videoId.isNotBlank() && match.name.contains(entity.videoId)) ||
                                                    (entity != null && entity.fileName.isNotBlank() && match.name == entity.fileName)
                                            if (isMatch) {
                                                try {
                                                    if (!match.delete()) {
                                                        try {
                                                            java.io.FileOutputStream(match).use { it.write(ByteArray(0)) }
                                                            match.delete()
                                                        } catch (_: Exception) {}
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                }

                                if (entity != null && thumbnailDir.exists()) {
                                    val thumbFile = File(thumbnailDir, "${entity.videoId}.jpg")
                                    if (thumbFile.exists()) try { thumbFile.delete() } catch (_: Exception) {}
                                }

                                // 2. Delete database records in Room across all matching keys
                                if (entity != null) {
                                    db.downloadedVideoDao().deleteDownload(entity)
                                    if (entity.videoId.isNotBlank()) {
                                        db.downloadedVideoDao().deleteDownloadByVideoIdAndPlaylist(entity.videoId, entity.playlistId ?: "")
                                        db.downloadedVideoDao().deletePlaceholderByVideoId(entity.videoId)
                                        db.savedLyricsDao().deleteLyrics(entity.videoId)
                                        if (!entity.playlistId.isNullOrBlank()) {
                                            db.downloadedPlaylistDao().deletePlaylistVideoCrossRef(entity.playlistId, entity.videoId)
                                        }
                                    }
                                    if (entity.fileName.isNotBlank()) {
                                        db.downloadedVideoDao().deleteDownloadByFileName(entity.fileName)
                                    }
                                }
                                if (f != null) {
                                    db.downloadedVideoDao().deleteDownloadByFileName(f.name)
                                    db.downloadedVideoDao().deleteDownloadByFileName(f.nameWithoutExtension)
                                }

                                // 3. Reload files on disk & update UI state on Main thread
                                withContext(Dispatchers.Main) {
                                    loadFiles()
                                    Toast.makeText(context, "Deleted '$displayTitle'", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Error deleting file: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false; fileToDelete = null; itemToDelete = null }) {
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
                        com.videhub.utils.DownloadProgressTracker.markCancelledByUser(progressItem.videoId)
                        androidx.work.WorkManager.getInstance(context).cancelUniqueWork(
                            com.videhub.service.DownloadWorker.getUniqueWorkName(progressItem.videoId, progressItem.playlistId)
                        )
                        com.videhub.service.DownloadService.cancelDownload(context, progressItem.notificationId)

                        DownloadProgressTracker.removeDownload(progressItem.notificationId)
                        com.videhub.viewmodel.DownloadViewModel.removeActiveDownload(progressItem.videoId)

                        scope.launch(Dispatchers.IO) {
                            try {
                                val tempDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                tempDir?.listFiles { f -> f.name.contains(progressItem.videoId) && f.name.endsWith(".part") }?.forEach { it.delete() }
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

    if (selectedItemForDetails != null) {
        DownloadItemDetailsSheet(
            item = selectedItemForDetails!!,
            onDismiss = { selectedItemForDetails = null },
            onPlay = { isMusic ->
                val target = selectedItemForDetails ?: return@DownloadItemDetailsSheet
                selectedItemForDetails = null
                onVideoClick(
                    target.file.absolutePath,
                    target.dbEntity?.title ?: cleanTitle(target.file.nameWithoutExtension),
                    target.dbEntity?.thumbnailUrl ?: "",
                    isMusic
                )
            },
            onDelete = {
                itemToDelete = selectedItemForDetails
                fileToDelete = selectedItemForDetails?.file
                selectedItemForDetails = null
                showDeleteDialog = true
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            DropdownMenuItem(
                                text = { Text("Clean & Repair Storage", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    showSortMenu = false
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            var cleanedCount = 0
                                            val dir1 = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                            val dir2 = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                            listOfNotNull(dir1, dir2).forEach { dir ->
                                                if (dir.exists()) {
                                                    dir.listFiles { f -> f.length() == 0L || f.name.endsWith(".tmp") || f.name.endsWith(".part") }?.forEach {
                                                        if (it.delete()) cleanedCount++
                                                    }
                                                }
                                            }
                                            withContext(Dispatchers.Main) {
                                                loadFiles()
                                                Toast.makeText(context, if (cleanedCount > 0) "Cleaned $cleanedCount broken files" else "Storage healthy!", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Storage repair completed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
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
                                com.videhub.service.DownloadWorker.enqueueDownload(
                                    context = context,
                                    videoId = progress.videoId,
                                    title = progress.title,
                                    thumbnailUrl = progress.thumbnailUrl ?: "",
                                    isAudioOnly = progress.isAudioOnly,
                                    quality = progress.quality,
                                    playlistId = progress.playlistId,
                                    replace = true
                                )
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
                                if (playlist.playlistId.equals("WL", ignoreCase = true)) {
                                    val wlEntity = remember(playlist) {
                                        com.videhub.data.entity.DownloadedPlaylistEntity(
                                            playlistId = playlist.playlistId,
                                            title = playlist.playlistName ?: "Watch Later",
                                            thumbnailUrl = playlist.coverThumbnailUrl,
                                            totalCount = playlist.totalVideos,
                                            completedCount = playlist.videoCount,
                                            state = playlist.state,
                                            progress = playlist.progress,
                                            downloadedBytes = playlist.downloadedBytes,
                                            totalBytes = playlist.totalBytes,
                                            currentVideoIndex = playlist.currentVideoIndex,
                                            currentVideoProgress = playlist.currentVideoProgress
                                        )
                                    }
                                    com.videhub.ui.components.WatchLaterCard(
                                        playlist = wlEntity,
                                        onClick = {
                                            navController.navigate("watch_later")
                                        },
                                        onMoreClick = {
                                            showWatchLaterMenu = true
                                        },
                                        modifier = Modifier.width(220.dp)
                                    )
                                } else {
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
                            videoId = item.dbEntity?.videoId,
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
                            onMoreClick = { selectedItemForDetails = item }
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
                            videoId = item.dbEntity?.videoId,
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
                            onMoreClick = { selectedItemForDetails = item }
                        )
                    }
                }
            }
        }
    }

    if (showWatchLaterMenu) {
        ModalBottomSheet(
            onDismissRequest = { showWatchLaterMenu = false },
            containerColor = Color(0xFF222222),
            contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showWatchLaterMenu = false
                            scope.launch(Dispatchers.IO) {
                                val items = db.watchLaterDao().getAllOnce()
                                if (items.isNotEmpty()) {
                                    items.reversed().forEach { item ->
                                        QueueManager.playNext(
                                            PlayQueueItem(
                                                url = item.videoId,
                                                title = item.title,
                                                uploaderName = item.channelName,
                                                thumbnailUrl = item.thumbnailUrl
                                            )
                                        )
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Added Watch Later videos next in queue", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Watch Later is empty", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Play next in queue",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ── Music Thumbnail Composable ────────────────────────────────────────────────
@Composable
fun MusicThumbnail(
    url: String?,
    modifier: Modifier = Modifier,
    audioFilePath: String? = null,
    videoId: String? = null
) {
    val context = LocalContext.current
    var isError by remember(url, audioFilePath, videoId) { mutableStateOf(false) }

    val resolvedModel: Any? = remember(url, audioFilePath, videoId, isError) {
        if (isError) null
        else {
            val model = resolveThumbnailModel(url, videoId, audioFilePath ?: "")
            if (model is Int) null else model
        }
    }

    Box(
        modifier = modifier
            .size(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (resolvedModel != null) {
            // Blurred-background layer (Android 12+ / API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(resolvedModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(16.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                )
            }

            // Foreground layer - keeps full 16:9 thumbnail centered without clipping edges!
            AsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(resolvedModel)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { isError = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .align(Alignment.Center)
            )

            // Music Badge Overlay on bottom-right corner to give it a distinct music album look
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Music Track",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        } else {
            // Vinyl Disc / Music Gradient Placeholder when url is null or failing
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
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
                        url = item.thumbnailUrl,
                        videoId = item.videoId
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
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

                        val isStalled = !item.isComplete && !item.isError && !isPaused && (System.currentTimeMillis() - item.lastUpdatedAt > 60_000L)

                        val statusText = if (isPaused) {
                            "Paused"
                        } else if (item.isError) {
                            item.errorMessage ?: "Failed • Tap to retry"
                        } else if (isStalled) {
                            "Stalled • Tap to retry"
                        } else if (item.totalBytes > 0) {
                            "${formatMB(item.downloadedBytes)} / ${formatMB(item.totalBytes)}"
                        } else if (item.progress >= 0) {
                            "${item.progress}%"
                        } else {
                            "Connecting..."
                        }

                        Text(
                            text = statusText,
                            fontSize = 13.sp,
                            color = if (item.isError || isStalled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (item.isError || isStalled) Modifier.clickable { onRetry() } else Modifier
                        )
                    }
                }

                val isStalledCard = !item.isComplete && !item.isError && !isPaused && (System.currentTimeMillis() - item.lastUpdatedAt > 60_000L)

                Spacer(modifier = Modifier.width(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (item.isError || isStalledCard) {
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
    val isDownloading = playlist.state.equals("DOWNLOADING", ignoreCase = true) || 
        playlist.state.equals("PREPARING", ignoreCase = true) || 
        (playlist.totalVideos > 0 && playlist.videoCount < playlist.totalVideos)
    val isPreparing = playlist.state.equals("PREPARING", ignoreCase = true) || (isDownloading && playlist.totalVideos <= 0)

    val progressPercent = if (playlist.totalVideos > 0) {
        val currItemProg = playlist.currentVideoProgress.coerceIn(0, 100) / 100f
        ((playlist.videoCount.toFloat() + currItemProg) / playlist.totalVideos.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val overallProgInt = (progressPercent * 100).toInt().coerceIn(0, 100)

    val subtitleText = when {
        isPreparing -> "Preparing…"
        isDownloading -> {
            val currIdx = if (playlist.currentVideoIndex > 0) playlist.currentVideoIndex else (playlist.videoCount + 1).coerceAtMost(playlist.totalVideos)
            "Downloading $currIdx of ${playlist.totalVideos} • $overallProgInt%"
        }
        playlist.state.equals("FAILED", ignoreCase = true) -> "Download failed"
        else -> "${com.videhub.utils.FormatHelper.formatCount(playlist.videoCount.toLong())} videos • ${formatMB(playlist.totalBytes.takeIf { it > 0 } ?: playlist.downloadedBytes)}"
    }

    com.videhub.ui.components.PlaylistCard(
        title = playlist.playlistName ?: "Unknown Playlist",
        subtitle = subtitleText,
        thumbnailUrl = playlist.coverThumbnailUrl,
        onClick = onClick,
        modifier = modifier.width(220.dp),
        downloadProgress = if (isDownloading) progressPercent else null,
        state = playlist.state,
        currentTrackIndex = if (playlist.currentVideoIndex > 0) playlist.currentVideoIndex else (playlist.videoCount + 1),
        totalCount = playlist.totalVideos,
        itemProgress = playlist.currentVideoProgress
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
    videoId: String? = null,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val meta = remember(audioFilePath) {
        MediaMetadataHelper.getMetadata(audioFilePath)
    }
    val durationStr = remember(meta.durationMs) {
        formatDuration(meta.durationMs / 1000)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        MusicThumbnail(
            url = thumbnailUrl,
            audioFilePath = audioFilePath,
            videoId = videoId
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(5.dp))
            val metaLine = buildString {
                append("♪ ")
                append(if (artist.isNotBlank()) artist else "Music")
                if (durationStr.isNotBlank()) {
                    append(" • ")
                    append(durationStr)
                }
                append(" • ")
                append(fileSize)
            }
            Text(
                text = metaLine,
                fontSize = 13.sp,
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
                contentDescription = "Options and link for $title",
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
    videoId: String? = null,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val meta = remember(videoFilePath) {
        MediaMetadataHelper.getMetadata(videoFilePath)
    }
    val durationStr = remember(meta.durationMs) {
        formatDuration(meta.durationMs / 1000)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .width(148.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            val modelToUse = remember(thumbnailUrl, videoFilePath, videoId) {
                resolveThumbnailModel(thumbnailUrl, videoId, videoFilePath)
            }
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(modelToUse)
                    .crossfade(true)
                    .error(android.R.drawable.ic_media_play)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Duration badge at bottom-right
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            ) {
                Text(
                    text = if (durationStr.isNotBlank()) durationStr else "HD",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(5.dp))
            val metaLine = if (channel.isNotBlank()) "$channel • $fileSize" else fileSize
            Text(
                text = metaLine,
                fontSize = 13.sp,
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
                contentDescription = "Options and link for $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Download Item Details BottomSheet ─────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadItemDetailsSheet(
    item: DownloadItemData,
    onDismiss: () -> Unit,
    onPlay: (isMusic: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val isAudio = FileUtils.isAudio(item.dbEntity, item.file.absolutePath)
    val title = item.dbEntity?.title ?: cleanTitle(item.file.nameWithoutExtension)
    val artist = cleanChannelName(item.dbEntity?.channelName ?: if (isAudio) "Music" else "Video")
    val fullLink = remember(item) { getYouTubeLink(item.dbEntity, item.file) }
    val meta = remember(item.file.absolutePath) {
        MediaMetadataHelper.getMetadata(item.file.absolutePath)
    }
    val durationStr = remember(meta.durationMs) { formatDuration(meta.durationMs / 1000) }
    val fileSize = formatMB(item.file.length())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header: Artwork + Full Title + Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isAudio) {
                    MusicThumbnail(
                        url = item.dbEntity?.thumbnailUrl,
                        audioFilePath = item.file.absolutePath,
                        videoId = item.dbEntity?.videoId,
                        modifier = Modifier.size(80.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        val model = remember(item) {
                            resolveThumbnailModel(item.dbEntity?.thumbnailUrl, item.dbEntity?.videoId, item.file.absolutePath)
                        }
                        AsyncImage(
                            model = model,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = artist,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(
                            if (durationStr.isNotBlank()) durationStr else null,
                            fileSize,
                            if (isAudio) "Audio (M4A)" else "Video (MP4)"
                        ).joinToString(" • "),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))



            // Actions: Play & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onDismiss()
                        onPlay(isAudio)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isAudio) "Play Music" else "Play Video")
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onDelete()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
            }
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

