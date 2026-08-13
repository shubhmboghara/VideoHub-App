package com.videhub.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.videhub.data.entity.WatchLaterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchLaterDao {
    @Query("SELECT * FROM watch_later ORDER BY addedAt DESC")
    fun getAll(): Flow<List<WatchLaterEntity>>

    @Query("SELECT * FROM watch_later ORDER BY addedAt DESC")
    suspend fun getAllOnce(): List<WatchLaterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE videoId = :videoId")
    suspend fun deleteById(videoId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM watch_later WHERE videoId = :videoId)")
    suspend fun isInWatchLater(videoId: String): Boolean
}
