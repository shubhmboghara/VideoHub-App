package com.videhub.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DownloadProgress(
    val notificationId: Int = 0,
    val title: String = "",
    val progress: Int = -1,
    val isComplete: Boolean = false,
    val isError: Boolean = false,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val thumbnailUrl: String? = null,
    val videoId: String = "",
    val playlistId: String? = null,
    val isAudioOnly: Boolean = false,
    val quality: String? = null
)

object DownloadProgressTracker {
    private val _activeDownloads = MutableStateFlow<Map<Int, DownloadProgress>>(emptyMap())
    val activeDownloads: StateFlow<Map<Int, DownloadProgress>> = _activeDownloads.asStateFlow()

    fun updateProgress(
        id: Int,
        title: String,
        progress: Int,
        isComplete: Boolean = false,
        isError: Boolean = false,
        downloadedBytes: Long = 0L,
        totalBytes: Long = 0L,
        thumbnailUrl: String? = null,
        videoId: String = "",
        playlistId: String? = null,
        isAudioOnly: Boolean? = null,
        quality: String? = null
    ) {
        _activeDownloads.update { current ->
            val updated = current.toMutableMap()
            val existing = current[id]
            val finalThumbnail = thumbnailUrl ?: existing?.thumbnailUrl
            val finalVideoId = if (videoId.isNotEmpty()) videoId else (existing?.videoId ?: "")
            val finalPlaylistId = playlistId ?: existing?.playlistId
            val finalIsAudioOnly = isAudioOnly ?: existing?.isAudioOnly ?: false
            val finalQuality = quality ?: existing?.quality
            
            if (isComplete && progress == 100) {
                updated[id] = DownloadProgress(
                    notificationId = id,
                    title = title,
                    progress = 100,
                    isComplete = true,
                    isError = false,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    thumbnailUrl = finalThumbnail,
                    videoId = finalVideoId,
                    playlistId = finalPlaylistId,
                    isAudioOnly = finalIsAudioOnly,
                    quality = finalQuality
                )
            } else {
                updated[id] = DownloadProgress(
                    notificationId = id,
                    title = title,
                    progress = progress,
                    isComplete = isComplete,
                    isError = isError,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    thumbnailUrl = finalThumbnail,
                    videoId = finalVideoId,
                    playlistId = finalPlaylistId,
                    isAudioOnly = finalIsAudioOnly,
                    quality = finalQuality
                )
            }
            updated
        }
    }

    fun removeDownload(id: Int) {
        _activeDownloads.update { current ->
            val updated = current.toMutableMap()
            updated.remove(id)
            updated
        }
    }
}
