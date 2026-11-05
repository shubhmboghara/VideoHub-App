package com.videhub.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_videos")
data class DownloadedVideoEntity(
    @PrimaryKey
    val fileName: String,
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channelName: String,
    val viewCount: Long = 0L,
    val uploadDate: String = "",
    val isAudioOnly: Boolean = false,
    val downloadedAt: Long = System.currentTimeMillis(),
    val lyrics: String? = null,
    val playlistId: String? = null,
    val playlistName: String? = null,
    val playlistIndex: Int = 0,
    val state: String = "DONE", // QUEUED, DOWNLOADING, DONE, FAILED
    val progress: Int = 0, // 0..100
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val duration: String = "",
    val errorMessage: String? = null,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val partialFilePath: String? = null,
    val noLongerInWatchLater: Boolean = false
)
