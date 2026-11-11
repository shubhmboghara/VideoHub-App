package com.videhub.viewmodel

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videhub.data.AppDatabase
import com.videhub.data.WatchLaterSessionManager
import com.videhub.data.entity.DownloadedPlaylistEntity
import com.videhub.data.entity.DownloadedVideoEntity
import com.videhub.data.entity.WatchLaterEntity
import com.videhub.service.WatchLaterDownloadWorker
import com.videhub.service.WatchLaterSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class WatchLaterViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val playlistDao = db.downloadedPlaylistDao()
    private val videoDao = db.downloadedVideoDao()
    private val watchLaterDao = db.watchLaterDao()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val savedVideos: StateFlow<List<WatchLaterEntity>> = watchLaterDao.getAll()
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedPlaylist: StateFlow<DownloadedPlaylistEntity?> = playlistDao
        .getPlaylistByIdFlow(WatchLaterSyncManager.WATCH_LATER_ID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val downloadedVideos: StateFlow<List<DownloadedVideoEntity>> = playlistDao
        .getVideosForPlaylist(WatchLaterSyncManager.WATCH_LATER_ID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isSessionValid: StateFlow<Boolean> = WatchLaterSessionManager
        .isUserSignedInFlow(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private var lastDeletedVideo: DownloadedVideoEntity? = null

    fun syncOrDownload(isAudioOnly: Boolean = false) {
        viewModelScope.launch {
            WatchLaterSyncManager.syncWatchLater(
                context = getApplication(),
                isAudioOnly = isAudioOnly
            )
        }
    }

    fun pauseDownload() {
        viewModelScope.launch {
            WatchLaterDownloadWorker.cancel(getApplication())
            playlistDao.updatePlaylistState(WatchLaterSyncManager.WATCH_LATER_ID, "PAUSED")
        }
    }

    fun resumeDownload() {
        viewModelScope.launch {
            playlistDao.updatePlaylistState(WatchLaterSyncManager.WATCH_LATER_ID, "DOWNLOADING")
            WatchLaterDownloadWorker.enqueue(getApplication(), replace = true)
        }
    }

    fun retryDownload() {
        viewModelScope.launch {
            WatchLaterSyncManager.syncWatchLater(getApplication())
        }
    }

    fun signInAndSync() {
        viewModelScope.launch {
            WatchLaterSessionManager.signIn(getApplication())
            WatchLaterSyncManager.syncWatchLater(getApplication())
        }
    }

    fun retrySingleVideo(videoId: String) {
        viewModelScope.launch {
            videoDao.updateVideoStateWithHeartbeat(
                videoId = videoId,
                state = "QUEUED",
                errorMessage = null,
                lastUpdatedAt = System.currentTimeMillis()
            )
            playlistDao.updatePlaylistState(WatchLaterSyncManager.WATCH_LATER_ID, "DOWNLOADING")
            WatchLaterDownloadWorker.enqueue(getApplication(), replace = true)
        }
    }

    fun removeVideoFromDownloads(video: DownloadedVideoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            lastDeletedVideo = video
            val downloadDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (!video.fileName.startsWith("PENDING_")) {
                val f = File(downloadDir, video.fileName)
                if (f.exists()) f.delete()
                val part = File(downloadDir, "${video.fileName}.part")
                if (part.exists()) part.delete()
            }
            videoDao.deleteDownload(video)
            playlistDao.deletePlaylistVideoCrossRef(WatchLaterSyncManager.WATCH_LATER_ID, video.videoId)
        }
    }

    fun deleteVideoFileOnly(video: DownloadedVideoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val downloadDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val f = File(downloadDir, video.fileName)
            if (f.exists()) f.delete()
            val part = File(downloadDir, "${video.fileName}.part")
            if (part.exists()) part.delete()

            videoDao.updateVideoStateWithHeartbeat(
                videoId = video.videoId,
                state = "QUEUED",
                errorMessage = null,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
    }

    fun undoRemoveVideo() {
        val videoToRestore = lastDeletedVideo ?: return
        viewModelScope.launch(Dispatchers.IO) {
            videoDao.insertDownload(videoToRestore)
            playlistDao.insertPlaylistVideoCrossRef(
                com.videhub.data.entity.DownloadedPlaylistVideoCrossRef(
                    WatchLaterSyncManager.WATCH_LATER_ID,
                    videoToRestore.videoId
                )
            )
            lastDeletedVideo = null
        }
    }

    fun removeAllDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            WatchLaterDownloadWorker.cancel(getApplication())
            val videos = videoDao.getPlaylistVideosSync(WatchLaterSyncManager.WATCH_LATER_ID)
            val downloadDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            videos.forEach { v ->
                val f = File(downloadDir, v.fileName)
                if (f.exists()) f.delete()
                val part = File(downloadDir, "${v.fileName}.part")
                if (part.exists()) part.delete()
                videoDao.deleteDownload(v)
            }
            playlistDao.deletePlaylist(WatchLaterSyncManager.WATCH_LATER_ID)
        }
    }

    fun removeFromWatchLaterSaved(videoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            watchLaterDao.deleteById(videoId)
            // Mark as noLongerInWatchLater if downloaded
            videoDao.updateNoLongerInWatchLater(videoId, true)
        }
    }
}
