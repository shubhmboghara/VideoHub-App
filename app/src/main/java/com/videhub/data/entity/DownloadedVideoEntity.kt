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
    val viewCount: Long,
    val uploadDate: String,
    val isAudioOnly: Boolean,
    val downloadedAt: Long = System.currentTimeMillis(),
    val lyrics: String? = null,
    val playlistId: String? = null,
    val playlistName: String? = null,
    val playlistIndex: Int = 0
)
