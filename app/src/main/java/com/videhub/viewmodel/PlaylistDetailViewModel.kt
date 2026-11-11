package com.videhub.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videhub.data.AppDatabase
import com.videhub.data.entity.DownloadedPlaylistVideoCrossRef
import com.videhub.data.entity.PlaylistEntity
import com.videhub.data.entity.PlaylistVideoEntity
import com.videhub.service.PlaylistDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class PlaylistDownloadState {
    NONE,
    PARTIAL,
    ALL_DONE
}

data class PlaylistVideoUiItem(
    val id: String,
    val playlistVideoId: Int = 0,
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationText: String?,
    val durationSeconds: Long = 0L,
    val downloadState: String? = null, // "DONE", "DOWNLOADING", "QUEUED", "FAILED", or null
    val downloadProgress: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val localFilePath: String? = null,
    val isAudioOnly: Boolean = false,
    val originalIndex: Int = 0
)

data class PlaylistDetailUiState(
    val playlistId: String = "",
    val playlistName: String = "Playlist",
    val customCoverUrl: String? = null,
    val videos: List<PlaylistVideoUiItem> = emptyList(),
    val downloadState: PlaylistDownloadState = PlaylistDownloadState.NONE,
    val totalDurationSeconds: Long = 0L,
    val totalEstimatedBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val isLoading: Boolean = true
)

fun derivePlaylistDownloadState(videos: List<PlaylistVideoUiItem>): PlaylistDownloadState {
    if (videos.isEmpty()) return PlaylistDownloadState.NONE
    val doneCount = videos.count { it.downloadState == "DONE" && !it.localFilePath.isNullOrBlank() }
    val downloadingOrQueuedCount = videos.count {
        it.downloadState == "DOWNLOADING" || it.downloadState == "QUEUED" || it.downloadState == "PREPARING"
    }
    return when {
        doneCount == videos.size -> PlaylistDownloadState.ALL_DONE
        doneCount > 0 || downloadingOrQueuedCount > 0 -> PlaylistDownloadState.PARTIAL
        else -> PlaylistDownloadState.NONE
    }
}

fun parseDurationTextToSeconds(text: String?): Long {
    if (text.isNullOrBlank()) return 0L
    val parts = text.split(":").mapNotNull { it.trim().toLongOrNull() }
    return when (parts.size) {
        1 -> parts[0]
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> 0L
    }
}

class PlaylistDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    // Undo states for "Remove from playlist"
    private var lastRemovedFromPlaylist: Pair<Int, PlaylistVideoUiItem>? = null

    // Undo states for "Delete download" (with delayed physical file deletion)
    private var pendingDeletionJob: Job? = null
    private var lastDeletedDownloadItem: PlaylistVideoUiItem? = null
    private var pendingFileToDelete: File? = null

    fun loadPlaylist(context: Context, rawPlaylistId: String) {
        val intId = rawPlaylistId.toIntOrNull()
        val db = AppDatabase.getDatabase(context)

        viewModelScope.launch(Dispatchers.IO) {
            val playlistMetaFlow: Flow<Pair<String, String?>> = if (intId != null) {
                db.playlistDao().getAllPlaylists().map { list ->
                    val found = list.find { it.id == intId }
                    (found?.name ?: "Playlist") to found?.customCoverUrl
                }
            } else {
                db.downloadedPlaylistDao().getPlaylistByIdFlow(rawPlaylistId).map { found ->
                    (found?.title ?: "Playlist") to found?.thumbnailUrl
                }
            }

            // Combine playlist metadata Flow, playlist items Flow, and downloads Flow
            val downloadsFlow = db.downloadedVideoDao().getAllDownloads()
            val videosFlow: Flow<List<Any>> = if (intId != null) {
                db.playlistDao().getVideos(intId).map { it as List<Any> }
            } else {
                db.downloadedPlaylistDao().getVideosForPlaylist(rawPlaylistId).map { it as List<Any> }
            }

            combine(playlistMetaFlow, videosFlow, downloadsFlow) { (playlistTitle, customCover), rawVideos, downloads ->
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val mappedItems = rawVideos.mapIndexed { index, rawItem ->
                    when (rawItem) {
                        is PlaylistVideoEntity -> {
                            val download = downloads.find { it.videoId == rawItem.videoId }
                            val isDone = download?.state == "DONE"
                            val file = if (isDone && download?.fileName != null) File(downloadsDir, download.fileName) else null
                            val fileExists = file?.exists() == true && file.length() > 0

                            val durationSec = parseDurationTextToSeconds(rawItem.durationText)
                            PlaylistVideoUiItem(
                                id = "${rawItem.id}_${rawItem.videoId}",
                                playlistVideoId = rawItem.id,
                                videoId = rawItem.videoId,
                                title = rawItem.title,
                                channelName = rawItem.channelName,
                                thumbnailUrl = rawItem.thumbnailUrl,
                                durationText = rawItem.durationText,
                                durationSeconds = durationSec,
                                downloadState = if (fileExists) "DONE" else download?.state,
                                downloadProgress = download?.progress ?: 0,
                                bytesDownloaded = download?.bytesDownloaded ?: 0L,
                                totalBytes = download?.totalBytes ?: (if (fileExists) file!!.length() else 0L),
                                localFilePath = if (fileExists) file!!.absolutePath else null,
                                isAudioOnly = download?.isAudioOnly ?: false,
                                originalIndex = index
                            )
                        }
                        is com.videhub.data.entity.DownloadedVideoEntity -> {
                            val isDone = rawItem.state == "DONE"
                            val file = if (isDone) File(downloadsDir, rawItem.fileName) else null
                            val fileExists = file?.exists() == true && file.length() > 0
                            val durationSec = parseDurationTextToSeconds(rawItem.duration)

                            PlaylistVideoUiItem(
                                id = rawItem.videoId,
                                playlistVideoId = 0,
                                videoId = rawItem.videoId,
                                title = rawItem.title,
                                channelName = rawItem.channelName,
                                thumbnailUrl = rawItem.thumbnailUrl,
                                durationText = rawItem.duration,
                                durationSeconds = durationSec,
                                downloadState = if (fileExists) "DONE" else rawItem.state,
                                downloadProgress = rawItem.progress,
                                bytesDownloaded = rawItem.bytesDownloaded,
                                totalBytes = if (fileExists) file!!.length() else rawItem.totalBytes,
                                localFilePath = if (fileExists) file!!.absolutePath else null,
                                isAudioOnly = rawItem.isAudioOnly,
                                originalIndex = index
                            )
                        }
                        else -> null
                    }
                }.filterNotNull()

                val state = derivePlaylistDownloadState(mappedItems)
                val totalDuration = mappedItems.sumOf { it.durationSeconds }
                val totalBytes = mappedItems.sumOf { it.totalBytes }
                val downloadedBytes = mappedItems.sumOf { it.bytesDownloaded }

                PlaylistDetailUiState(
                    playlistId = rawPlaylistId,
                    playlistName = playlistTitle,
                    customCoverUrl = customCover,
                    videos = mappedItems,
                    downloadState = state,
                    totalDurationSeconds = totalDuration,
                    totalEstimatedBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                    isLoading = false
                )
            }.collectLatest { state ->
                _uiState.value = state
            }
        }
    }

    /**
     * Removes the video from the playlist.
     * Retains the file on disk. Saves originalIndex for undo.
     */
    fun removeFromPlaylist(
        context: Context,
        item: PlaylistVideoUiItem,
        onSuccess: () -> Unit
    ) {
        val intId = _uiState.value.playlistId.toIntOrNull()
        val rawId = _uiState.value.playlistId
        lastRemovedFromPlaylist = item.originalIndex to item

        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            if (intId != null && item.playlistVideoId > 0) {
                db.playlistDao().deleteVideo(
                    PlaylistVideoEntity(
                        id = item.playlistVideoId,
                        playlistId = intId,
                        videoId = item.videoId,
                        title = item.title,
                        channelName = item.channelName,
                        thumbnailUrl = item.thumbnailUrl,
                        durationText = item.durationText
                    )
                )
            } else {
                db.downloadedPlaylistDao().deletePlaylistVideoCrossRef(rawId, item.videoId)
            }
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    /**
     * Undoes removing a video from the playlist, restoring it at its original index.
     */
    fun undoRemoveFromPlaylist(context: Context, onSuccess: () -> Unit = {}) {
        val (originalIndex, item) = lastRemovedFromPlaylist ?: return
        val intId = _uiState.value.playlistId.toIntOrNull()
        val rawId = _uiState.value.playlistId

        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            if (intId != null) {
                db.playlistDao().insertVideo(
                    PlaylistVideoEntity(
                        id = item.playlistVideoId,
                        playlistId = intId,
                        videoId = item.videoId,
                        title = item.title,
                        channelName = item.channelName,
                        thumbnailUrl = item.thumbnailUrl,
                        durationText = item.durationText
                    )
                )
            } else {
                db.downloadedPlaylistDao().insertPlaylistVideoCrossRef(
                    DownloadedPlaylistVideoCrossRef(rawId, item.videoId)
                )
            }
            lastRemovedFromPlaylist = null
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    /**
     * Deletes the download with delayed physical file deletion until snackbar times out.
     */
    fun deleteDownloadWithDelayedUndo(
        context: Context,
        item: PlaylistVideoUiItem,
        onSuccess: () -> Unit
    ) {
        // Cancel any previous pending deletion job and finalize its file delete
        pendingDeletionJob?.cancel()
        pendingFileToDelete?.delete()

        val filePath = item.localFilePath
        val targetFile = if (!filePath.isNullOrBlank()) File(filePath) else null

        lastDeletedDownloadItem = item
        pendingFileToDelete = targetFile

        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val download = db.downloadedVideoDao().getAllDownloadsSync().find { it.videoId == item.videoId }
            if (download != null) {
                db.downloadedVideoDao().deleteDownload(download)
            }
            db.downloadedPlaylistDao().deletePlaylistVideoCrossRef(_uiState.value.playlistId, item.videoId)

            withContext(Dispatchers.Main) {
                onSuccess()
            }

            // Launch delayed physical deletion job (delays 4 seconds for snackbar)
            pendingDeletionJob = viewModelScope.launch(Dispatchers.IO) {
                delay(4000)
                targetFile?.let {
                    if (it.exists()) it.delete()
                }
                pendingFileToDelete = null
                lastDeletedDownloadItem = null
            }
        }
    }

    /**
     * Undoes deleting download within the snackbar window.
     */
    fun undoDeleteDownload(context: Context, onSuccess: () -> Unit = {}) {
        pendingDeletionJob?.cancel()
        pendingDeletionJob = null
        val item = lastDeletedDownloadItem ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val targetFile = pendingFileToDelete ?: File(downloadsDir, "${item.videoId}.mp4")

            db.downloadedVideoDao().insertDownload(
                com.videhub.data.entity.DownloadedVideoEntity(
                    fileName = targetFile.name,
                    videoId = item.videoId,
                    title = item.title,
                    thumbnailUrl = item.thumbnailUrl ?: "",
                    channelName = item.channelName,
                    state = "DONE",
                    progress = 100,
                    bytesDownloaded = item.bytesDownloaded,
                    totalBytes = item.totalBytes,
                    duration = item.durationText ?: ""
                )
            )
            db.downloadedPlaylistDao().insertPlaylistVideoCrossRef(
                DownloadedPlaylistVideoCrossRef(_uiState.value.playlistId, item.videoId)
            )

            pendingFileToDelete = null
            lastDeletedDownloadItem = null

            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    fun renamePlaylist(context: Context, newName: String, onSuccess: () -> Unit = {}) {
        renamePlaylistAndCover(context, newName, null, onSuccess)
    }

    fun renamePlaylistAndCover(context: Context, newName: String, newCoverUrl: String?, onSuccess: () -> Unit = {}) {
        val intId = _uiState.value.playlistId.toIntOrNull()
        val rawId = _uiState.value.playlistId

        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            if (intId != null) {
                db.playlistDao().updatePlaylistDetails(intId, newName.trim(), newCoverUrl)
            } else {
                db.downloadedPlaylistDao().renamePlaylist(rawId, newName.trim())
                if (!newCoverUrl.isNullOrBlank()) {
                    db.downloadedPlaylistDao().updatePlaylistThumbnail(rawId, newCoverUrl)
                }
            }
            _uiState.update {
                it.copy(
                    playlistName = newName.trim(),
                    customCoverUrl = newCoverUrl ?: it.customCoverUrl
                )
            }
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    fun deletePlaylist(
        context: Context,
        deleteFilesToo: Boolean,
        onSuccess: () -> Unit
    ) {
        val intId = _uiState.value.playlistId.toIntOrNull()
        val rawId = _uiState.value.playlistId
        val videos = _uiState.value.videos

        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            if (intId != null) {
                val entity = db.playlistDao().getAllPlaylistsOnce().find { it.id == intId }
                if (entity != null) {
                    db.playlistDao().deletePlaylist(entity)
                }
            }
            db.downloadedPlaylistDao().deletePlaylist(rawId)

            if (deleteFilesToo) {
                videos.forEach { video ->
                    video.localFilePath?.let { path ->
                        val f = File(path)
                        if (f.exists()) f.delete()
                    }
                    val dl = db.downloadedVideoDao().getAllDownloadsSync().find { it.videoId == video.videoId }
                    if (dl != null) {
                        db.downloadedVideoDao().deleteDownload(dl)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    /**
     * Downloads ONLY the videos that are not DONE yet.
     */
    fun downloadRemaining(context: Context, quality: String, isAudioOnly: Boolean = false) {
        val videosToDownload = _uiState.value.videos.filter { it.downloadState != "DONE" }
        if (videosToDownload.isEmpty()) return

        val intent = Intent(context, PlaylistDownloadService::class.java).apply {
            putStringArrayListExtra("urls", ArrayList(videosToDownload.map { it.videoId }))
            putStringArrayListExtra("titles", ArrayList(videosToDownload.map { it.title }))
            putStringArrayListExtra("thumbnails", ArrayList(videosToDownload.map { it.thumbnailUrl ?: "" }))
            putStringArrayListExtra("channels", ArrayList(videosToDownload.map { it.channelName }))
            putExtra("playlistId", _uiState.value.playlistId)
            putExtra("playlistName", _uiState.value.playlistName)
            putExtra("playlistThumbnail", videosToDownload.firstOrNull()?.thumbnailUrl ?: "")
            putExtra("isAudioOnly", isAudioOnly)
            putExtra("quality", quality)
        }
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    override fun onCleared() {
        super.onCleared()
        pendingDeletionJob?.cancel()
        pendingFileToDelete?.delete()
    }
}
