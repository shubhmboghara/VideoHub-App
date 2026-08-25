package com.videhub.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import com.videhub.data.AppDatabase
import com.videhub.extractor.ExtractorHelper
import com.videhub.service.PlaylistDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class PlaylistUiState(
    val isLoading: Boolean = false,
    val playlistInfo: PlaylistInfo? = null,
    val playlistUrl: String = "",
    val error: String? = null
)

data class PlaylistItemUiState(
    val item: StreamInfoItem,
    val isDownloaded: Boolean,
    val isDownloading: Boolean
)

class PlaylistViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    private val _playlistItems = MutableStateFlow<List<PlaylistItemUiState>>(emptyList())
    val playlistItems: StateFlow<List<PlaylistItemUiState>> = _playlistItems.asStateFlow()

    fun loadOnlinePlaylist(context: Context, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = PlaylistUiState(isLoading = true)
            try {
                val info = ExtractorHelper.getPlaylistInfo(url)
                _uiState.value = PlaylistUiState(playlistInfo = info, playlistUrl = url)
                
                val db = AppDatabase.getDatabase(context)
                val downloadedVideosFlow = db.downloadedVideoDao().getAllDownloads()
                
                downloadedVideosFlow.collect { downloaded ->
                    val combinedList = info.relatedItems.mapNotNull { streamItem ->
                        if (streamItem !is StreamInfoItem) return@mapNotNull null
                        val isDownloaded = downloaded.any { it.videoId == streamItem.url && !it.fileName.startsWith("PENDING_") }
                        val isDownloading = downloaded.any { it.videoId == streamItem.url && it.fileName.startsWith("PENDING_") }
                        PlaylistItemUiState(streamItem, isDownloaded, isDownloading)
                    }
                    _playlistItems.value = combinedList
                }
            } catch (e: Exception) {
                _uiState.value = PlaylistUiState(error = e.message)
            }
        }
    }

    fun downloadEntirePlaylist(context: Context, isAudioOnly: Boolean = false) {
        val info = _uiState.value.playlistInfo ?: return
        val items = _playlistItems.value.map { it.item }

        if (items.isEmpty()) return

        // ✅ Use Service directly — more reliable than WorkManager for large downloads
        val intent = android.content.Intent(context, com.videhub.service.PlaylistDownloadService::class.java).apply {
            putStringArrayListExtra("urls", java.util.ArrayList(items.map { it.url }))
            putStringArrayListExtra("titles", java.util.ArrayList(items.map { it.name }))
            putStringArrayListExtra("thumbnails", java.util.ArrayList(items.map { it.thumbnails.firstOrNull()?.url ?: "" }))
            putExtra("playlistId", info.url ?: _uiState.value.playlistUrl)
            putExtra("playlistName", info.name)
            putExtra("playlistThumbnail", info.thumbnails.firstOrNull()?.url ?: items.firstOrNull()?.thumbnails?.firstOrNull()?.url ?: "")
            putExtra("isAudioOnly", isAudioOnly)
        }

        androidx.core.content.ContextCompat.startForegroundService(context, intent)
        android.widget.Toast.makeText(context, "Playlist download started...", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun saveOnlinePlaylistToLocal(context: Context, customName: String? = null, onComplete: () -> Unit = {}) {
        val info = _uiState.value.playlistInfo ?: return
        val items = _playlistItems.value.map { it.item }
        if (items.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val playlistName = customName?.takeIf { it.isNotBlank() } ?: info.name ?: "Imported Playlist"
            val playlistId = db.playlistDao().insertPlaylist(
                com.videhub.data.entity.PlaylistEntity(name = playlistName)
            )

            items.forEach { item ->
                db.playlistDao().insertVideo(
                    com.videhub.data.entity.PlaylistVideoEntity(
                        playlistId = playlistId.toInt(),
                        videoId = item.url ?: "",
                        title = item.name ?: "Unknown",
                        channelName = item.uploaderName ?: "",
                        thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: ""
                    )
                )
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Saved to My Playlists!", android.widget.Toast.LENGTH_SHORT).show()
                onComplete()
            }
        }
    }

    fun downloadSingleVideo(context: Context, videoUrl: String, title: String, thumbnailUrl: String, isAudioOnly: Boolean = false) {
        val intent = android.content.Intent(context, com.videhub.service.DownloadService::class.java).apply {
            putExtra("url", videoUrl)
            putExtra("title", title)
            putExtra("thumbnailUrl", thumbnailUrl)
            putExtra("isAudioOnly", isAudioOnly)
        }
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
        android.widget.Toast.makeText(context, "Download started...", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun createWorkerRequest(playlistUrl: String, playlistTitle: String, playlistThumbnail: String?, item: StreamInfoItem) =
        OneTimeWorkRequestBuilder<PlaylistDownloadWorker>()
            .addTag("playlist_download_$playlistUrl")
            .addTag(item.url)
            .setInputData(
                Data.Builder()
                    .putString("playlistId", playlistUrl)
                    .putString("playlistTitle", playlistTitle)
                    .putString("playlistThumbnail", playlistThumbnail ?: "")
                    .putString("videoUrl", item.url)
                    .putString("videoTitle", item.name)
                    .build()
            )
            .build()
}
