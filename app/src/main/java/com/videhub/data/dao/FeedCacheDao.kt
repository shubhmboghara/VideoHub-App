package com.videhub.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.videhub.data.entity.FeedCacheEntity

@Dao
interface FeedCacheDao {
    @Query("SELECT * FROM feed_cache ORDER BY cachedAt DESC")
    suspend fun getAll(): List<FeedCacheEntity>

    @Query("DELETE FROM feed_cache")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FeedCacheEntity>)
}
