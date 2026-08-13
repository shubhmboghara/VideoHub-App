package com.videhub.data.dao

import androidx.room.*
import com.videhub.data.entity.LikedVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedVideoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: LikedVideoEntity)

    @Query("DELETE FROM liked_videos WHERE videoId = :videoId")
    suspend fun deleteById(videoId: String)

    @Query("SELECT * FROM liked_videos ORDER BY likedAt DESC")
    fun getAll(): Flow<List<LikedVideoEntity>>

    @Query("SELECT * FROM liked_videos ORDER BY likedAt DESC")
    suspend fun getAllOnce(): List<LikedVideoEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_videos WHERE videoId = :videoId)")
    suspend fun isLiked(videoId: String): Boolean
}
