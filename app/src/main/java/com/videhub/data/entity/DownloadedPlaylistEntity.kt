package com.videhub.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_playlists")
data class DownloadedPlaylistEntity(
    @PrimaryKey val playlistId: String,
    val title: String,
    val thumbnailUrl: String?,
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val state: String = "DOWNLOADING", // PREPARING, DOWNLOADING, DONE, FAILED
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val currentVideoTitle: String? = null,
    val currentVideoIndex: Int = 0,
    val currentVideoProgress: Int = 0,
    val downloadedAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)
