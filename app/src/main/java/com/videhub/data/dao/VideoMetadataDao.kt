package com.videhub.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.videhub.data.entity.VideoMetadataEntity

@Dao
interface VideoMetadataDao {
    @Query("SELECT * FROM video_metadata WHERE videoId = :videoId")
    suspend fun getVideoMetadata(videoId: String): VideoMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideoMetadata(metadata: VideoMetadataEntity)

    @Query("DELETE FROM video_metadata WHERE cachedAt < :timestamp")
    suspend fun deleteOldMetadata(timestamp: Long)
}
