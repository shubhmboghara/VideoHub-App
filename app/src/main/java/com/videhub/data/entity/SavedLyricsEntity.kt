package com.videhub.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_lyrics")
data class SavedLyricsEntity(
    @PrimaryKey
    val videoId: String,
    val lyricsJson: String
)
