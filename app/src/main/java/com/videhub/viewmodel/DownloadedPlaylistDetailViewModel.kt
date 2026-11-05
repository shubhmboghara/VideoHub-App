package com.videhub.viewmodel

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videhub.data.AppDatabase
import com.videhub.data.entity.DownloadedPlaylistVideoCrossRef
import com.videhub.data.entity.DownloadedVideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DownloadedPlaylistDetailViewModel : ViewModel() {

    // Stored for undo of "Remove from playlist"
    private var lastRemovedFromPlaylist: Pair<String, DownloadedVideoEntity>? = null

    // Stored for undo of "Delete download" (temp backup file & entity)
    private var lastDeletedDownload: Triple<String, DownloadedVideoEntity, File?>? = null

    /**
     * Removes the video from the playlist in Room, keeping the downloaded file on disk.
     */
    fun removeFromPlaylist(
        context: Context,
        playlistId: String,
        video: DownloadedVideoEntity,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.downloadedPlaylistDao().deletePlaylistVideoCrossRef(playlistId, video.videoId)
            lastRemovedFromPlaylist = playlistId to video
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    /**
     * Undoes removing a video from the playlist by re-inserting the cross reference.
     */
    fun undoRemoveFromPlaylist(context: Context, onSuccess: () -> Unit = {}) {
        val (playlistId, video) = lastRemovedFromPlaylist ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.downloadedPlaylistDao().insertPlaylistVideoCrossRef(
                DownloadedPlaylistVideoCrossRef(playlistId = playlistId, videoId = video.videoId)
            )
            lastRemovedFromPlaylist = null
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    /**
     * Deletes the download from Room and disk with undo support.
     * Renames the file temporarily to a .bak file so undo can instantly restore it.
     */
    fun deleteDownloadWithUndo(
        context: Context,
        playlistId: String,
        video: DownloadedVideoEntity,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val originalFile = File(downloadsDir, video.fileName)
            var backupFile: File? = null

            if (originalFile.exists()) {
                val tempFile = File(downloadsDir, ".bak_${System.currentTimeMillis()}_${video.fileName}")
                if (originalFile.renameTo(tempFile)) {
                    backupFile = tempFile
                } else {
                    originalFile.delete()
                }
            }

            db.downloadedPlaylistDao().deletePlaylistVideoCrossRef(playlistId, video.videoId)
            db.downloadedVideoDao().deleteDownload(video)

            // Purge previous backup if un-restored
            lastDeletedDownload?.third?.delete()
            lastDeletedDownload = Triple(playlistId, video, backupFile)

            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    /**
     * Restores a deleted download and its playlist cross-reference.
     */
    fun undoDeleteDownload(context: Context, onSuccess: () -> Unit = {}) {
        val (playlistId, video, backupFile) = lastDeletedDownload ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val originalFile = File(downloadsDir, video.fileName)

            if (backupFile != null && backupFile.exists()) {
                backupFile.renameTo(originalFile)
            }

            db.downloadedVideoDao().insertDownload(video)
            db.downloadedPlaylistDao().insertPlaylistVideoCrossRef(
                DownloadedPlaylistVideoCrossRef(playlistId = playlistId, videoId = video.videoId)
            )

            lastDeletedDownload = null
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    /**
     * Renames the downloaded playlist.
     */
    fun renamePlaylist(context: Context, playlistId: String, newTitle: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.downloadedPlaylistDao().renamePlaylist(playlistId, newTitle.trim())
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    /**
     * Deletes the playlist from Room, optionally deleting the downloaded media files.
     */
    fun deletePlaylist(
        context: Context,
        playlistId: String,
        videos: List<DownloadedVideoEntity>,
        alsoDeleteFiles: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

            videos.forEach { video ->
                if (alsoDeleteFiles) {
                    val file = File(downloadsDir, video.fileName)
                    if (file.exists()) file.delete()
                    db.downloadedVideoDao().deleteDownload(video)
                }
                db.downloadedPlaylistDao().deletePlaylistVideoCrossRef(playlistId, video.videoId)
            }
            db.downloadedPlaylistDao().deletePlaylist(playlistId)

            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Permanently delete any pending undo backup file
        lastDeletedDownload?.third?.delete()
        lastDeletedDownload = null
    }
}
