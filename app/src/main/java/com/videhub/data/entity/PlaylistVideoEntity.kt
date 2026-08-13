package com.videhub.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_videos",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"])]
)
data class PlaylistVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String? = null,
    val durationText: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val viewCount: Long = -1,
    val uploadDate: String = ""
)
