package com.videhub.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val customCoverUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
