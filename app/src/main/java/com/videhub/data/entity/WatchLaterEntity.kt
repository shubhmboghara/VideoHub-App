package com.videhub.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_later")
data class WatchLaterEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channelName: String,
    val addedAt: Long = System.currentTimeMillis()
)
