package com.videhub.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveDownloadItem(
    val videoId: String,
    val title: String,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isComplete: Boolean = false
)

object ActiveDownloadTracker {
    private val _activeDownloads = MutableStateFlow<List<ActiveDownloadItem>>(emptyList())
    val activeDownloads: StateFlow<List<ActiveDownloadItem>> = _activeDownloads.asStateFlow()

    fun add(item: ActiveDownloadItem) {
        _activeDownloads.value = _activeDownloads.value + item
    }

    fun update(
        videoId: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        _activeDownloads.value = _activeDownloads.value.map {
            if (it.videoId == videoId)
                it.copy(
                    progress = progress,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes
                )
            else it
        }
    }

    fun markComplete(videoId: String) {
        _activeDownloads.value = _activeDownloads.value.map {
            if (it.videoId == videoId)
                it.copy(
                    isComplete = true,
                    progress = 100
                )
            else it
        }
    }

    fun remove(videoId: String) {
        _activeDownloads.value = _activeDownloads.value.filter {
            it.videoId != videoId
        }
    }

    fun clearAll() {
        _activeDownloads.value = emptyList()
    }
}
