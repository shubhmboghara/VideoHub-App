package com.videhub.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_entities")
data class HistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String? = null,
    val durationText: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val viewCount: Long = -1,
    val uploadDate: String = ""
)
