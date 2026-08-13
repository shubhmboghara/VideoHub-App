package com.videhub.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "liked_videos")
data class LikedVideoEntity(
    @PrimaryKey
    val videoId: String,       // YouTube video URL
    val title: String,
    val thumbnailUrl: String,
    val channelName: String,
    val likedAt: Long = System.currentTimeMillis(),
    val viewCount: Long = -1,
    val uploadDate: String = ""
)
