package com.videhub.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.videhub.data.entity.SearchCacheEntity

@Dao
interface SearchCacheDao {
    @Query("SELECT * FROM search_cache ORDER BY orderIndex ASC")
    suspend fun getAll(): List<SearchCacheEntity>

    @Query("DELETE FROM search_cache")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SearchCacheEntity>)
}
