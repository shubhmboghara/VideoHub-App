package com.videhub.data.dao

import androidx.room.*
import com.videhub.data.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels")
    fun getAll(): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(channel: ChannelEntity)

    @Delete
    suspend fun delete(channel: ChannelEntity)

    @Query("SELECT * FROM channels WHERE channelId = :id")
    suspend fun getById(id: String): ChannelEntity?
    
    @Query("SELECT * FROM channels")
    suspend fun getAllSync(): List<ChannelEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM channels WHERE channelId = :channelId)")
    suspend fun isSubscribed(channelId: String): Boolean

    @Query("DELETE FROM channels WHERE channelId = :channelId")
    suspend fun deleteById(channelId: String)

    @Query("SELECT * FROM channels ORDER BY name ASC")
    suspend fun getAllOnce(): List<ChannelEntity>
}
