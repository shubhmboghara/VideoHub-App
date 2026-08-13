package com.videhub.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.videhub.data.entity.WatchProgressEntity

@Dao
interface WatchProgressDao {
    @Upsert
    suspend fun save(progress: WatchProgressEntity)

    @Query("SELECT * FROM watch_progress WHERE videoId = :videoId")
    suspend fun get(videoId: String): WatchProgressEntity?

    @Query("DELETE FROM watch_progress WHERE watchedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
