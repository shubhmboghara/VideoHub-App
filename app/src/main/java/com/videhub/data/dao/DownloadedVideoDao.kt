package com.videhub.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.videhub.data.entity.DownloadedVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedVideoDao {
    @Query("SELECT * FROM downloaded_videos ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadedVideoEntity>>

    @Query("SELECT * FROM downloaded_videos ORDER BY downloadedAt DESC")
    suspend fun getAllDownloadsSync(): List<DownloadedVideoEntity>

    @Query("SELECT * FROM downloaded_videos WHERE playlistId IS NULL ORDER BY downloadedAt DESC")
    fun getSingleDownloads(): Flow<List<DownloadedVideoEntity>>

    @Query("SELECT * FROM downloaded_videos WHERE playlistId IS NOT NULL")
    fun getPlaylistDownloads(): Flow<List<DownloadedVideoEntity>>



    @Query("SELECT * FROM downloaded_videos WHERE playlistId = :playlistId ORDER BY playlistIndex ASC")
    fun getPlaylistVideos(playlistId: String): Flow<List<DownloadedVideoEntity>>

    @Query("SELECT * FROM downloaded_videos WHERE playlistId = :playlistId ORDER BY playlistIndex ASC")
    suspend fun getPlaylistVideosSync(playlistId: String): List<DownloadedVideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadedVideoEntity)

    @Delete
    suspend fun deleteDownload(download: DownloadedVideoEntity)

    @Query("DELETE FROM downloaded_videos WHERE videoId = :videoId AND fileName LIKE 'PENDING_%'")
    suspend fun deletePlaceholderByVideoId(videoId: String)

    @Query("DELETE FROM downloaded_videos WHERE playlistId = :playlistId AND fileName LIKE 'PENDING_%'")
    suspend fun deletePendingVideosByPlaylist(playlistId: String)

    @Query("DELETE FROM downloaded_videos WHERE fileName LIKE 'PENDING_%'")
    suspend fun deleteAllPendingVideos()
    
    @Query("SELECT * FROM downloaded_videos WHERE fileName = :fileName LIMIT 1")
    suspend fun getDownloadByFileName(fileName: String): DownloadedVideoEntity?
    
    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_videos WHERE videoId = :videoId LIMIT 1)")
    suspend fun isVideoDownloaded(videoId: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_videos WHERE videoId = :videoId AND fileName NOT LIKE 'PENDING_%' LIMIT 1)")
    suspend fun isFinalVideoDownloaded(videoId: String): Boolean
    
    @Query("DELETE FROM downloaded_videos WHERE fileName = :fileName")
    suspend fun deleteDownloadByFileName(fileName: String)
}
