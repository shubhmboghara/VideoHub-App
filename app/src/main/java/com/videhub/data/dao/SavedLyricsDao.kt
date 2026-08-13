package com.videhub.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.videhub.data.entity.SavedLyricsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLyricsDao {
    @Query("SELECT * FROM saved_lyrics WHERE videoId = :videoId LIMIT 1")
    suspend fun getLyrics(videoId: String): SavedLyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: SavedLyricsEntity)

    @Query("DELETE FROM saved_lyrics WHERE videoId = :videoId")
    suspend fun deleteLyrics(videoId: String)
}
