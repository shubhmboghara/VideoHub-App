package com.videhub.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_metadata")
data class VideoMetadataEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channelName: String,
    val viewCount: Long,
    val duration: Long,
    val streamUrl: String?,
    val audioUrl: String?,
    val cachedAt: Long = System.currentTimeMillis()
)
