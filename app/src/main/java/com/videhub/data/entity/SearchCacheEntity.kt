package com.videhub.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_cache")
data class SearchCacheEntity(
    @PrimaryKey val id: String, // Just use a generic ID or the query itself
    val query: String,
    val type: String, // "video" or "channel"
    val url: String,
    val title: String,
    val thumbnailUrl: String,
    val channelName: String,
    val viewCount: Long = 0,
    val duration: Long = 0,
    val orderIndex: Int
)
