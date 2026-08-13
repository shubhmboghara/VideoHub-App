package com.videhub.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_playlists")
data class DownloadedPlaylistEntity(
    @PrimaryKey val playlistId: String,
    val title: String,
    val thumbnailUrl: String?,
    val downloadedAt: Long = System.currentTimeMillis()
)
