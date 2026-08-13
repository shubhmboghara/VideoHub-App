package com.videhub.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feed_cache")
data class FeedCacheEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channelName: String,
    val channelAvatarUrl: String?,
    val viewCount: Long,
    val duration: Long,
    val publishedText: String,
    val cachedAt: Long = System.currentTimeMillis()
)
