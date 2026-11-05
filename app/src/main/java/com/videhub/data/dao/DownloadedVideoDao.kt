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

    @Query("SELECT * FROM downloaded_videos WHERE playlistId = :playlistId ORDER BY playlistIndex ASC, downloadedAt ASC")
    fun getPlaylistVideos(playlistId: String): Flow<List<DownloadedVideoEntity>>

    @Query("SELECT * FROM downloaded_videos WHERE playlistId = :playlistId ORDER BY playlistIndex ASC, downloadedAt ASC")
    suspend fun getPlaylistVideosSync(playlistId: String): List<DownloadedVideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadedVideoEntity)

    @Delete
    suspend fun deleteDownload(download: DownloadedVideoEntity)

    @Query("UPDATE downloaded_videos SET state = :state, progress = :progress, bytesDownloaded = :bytesDownloaded, totalBytes = :totalBytes WHERE videoId = :videoId")
    suspend fun updateVideoProgress(videoId: String, state: String, progress: Int, bytesDownloaded: Long, totalBytes: Long)

    @Query("UPDATE downloaded_videos SET state = :state, progress = :progress, bytesDownloaded = :bytesDownloaded, totalBytes = :totalBytes, lastUpdatedAt = :lastUpdatedAt WHERE videoId = :videoId")
    suspend fun updateVideoProgressWithHeartbeat(videoId: String, state: String, progress: Int, bytesDownloaded: Long, totalBytes: Long, lastUpdatedAt: Long)

    @Query("UPDATE downloaded_videos SET state = :state, errorMessage = :errorMessage WHERE videoId = :videoId")
    suspend fun updateVideoState(videoId: String, state: String, errorMessage: String? = null)

    @Query("UPDATE downloaded_videos SET state = :state, errorMessage = :errorMessage, lastUpdatedAt = :lastUpdatedAt WHERE videoId = :videoId")
    suspend fun updateVideoStateWithHeartbeat(videoId: String, state: String, errorMessage: String? = null, lastUpdatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE downloaded_videos SET partialFilePath = :partialFilePath, lastUpdatedAt = :lastUpdatedAt WHERE videoId = :videoId")
    suspend fun updatePartialFilePath(videoId: String, partialFilePath: String?, lastUpdatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM downloaded_videos WHERE state = 'DOWNLOADING'")
    suspend fun getDownloadingVideosSync(): List<DownloadedVideoEntity>

    @Query("SELECT * FROM downloaded_videos WHERE state = 'DOWNLOADING' AND lastUpdatedAt < :staleThreshold")
    suspend fun getStaleDownloadsSync(staleThreshold: Long): List<DownloadedVideoEntity>

    @Query("SELECT * FROM downloaded_videos WHERE state = 'DONE' AND fileName NOT LIKE 'PENDING_%' ORDER BY downloadedAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadedVideoEntity>>

    @Query("SELECT * FROM downloaded_videos WHERE state = 'DONE' AND fileName NOT LIKE 'PENDING_%' ORDER BY downloadedAt DESC")
    suspend fun getCompletedDownloadsSync(): List<DownloadedVideoEntity>

    @Query("SELECT * FROM downloaded_videos WHERE videoId = :videoId LIMIT 1")
    suspend fun getVideoByVideoId(videoId: String): DownloadedVideoEntity?

    @Query("DELETE FROM downloaded_videos WHERE videoId = :videoId AND (fileName LIKE 'PENDING_%' OR state != 'DONE')")
    suspend fun deletePlaceholderByVideoId(videoId: String)

    @Query("DELETE FROM downloaded_videos WHERE playlistId = :playlistId AND (fileName LIKE 'PENDING_%' OR state != 'DONE')")
    suspend fun deletePendingVideosByPlaylist(playlistId: String)

    @Query("DELETE FROM downloaded_videos WHERE fileName LIKE 'PENDING_%' OR state != 'DONE'")
    suspend fun deleteAllPendingVideos()
    
    @Query("SELECT * FROM downloaded_videos WHERE fileName = :fileName LIMIT 1")
    suspend fun getDownloadByFileName(fileName: String): DownloadedVideoEntity?
    
    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_videos WHERE videoId = :videoId AND state = 'DONE' AND fileName NOT LIKE 'PENDING_%' LIMIT 1)")
    suspend fun isVideoDownloaded(videoId: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_videos WHERE videoId = :videoId AND fileName NOT LIKE 'PENDING_%' LIMIT 1)")
    suspend fun isFinalVideoDownloaded(videoId: String): Boolean
    
    @Query("DELETE FROM downloaded_videos WHERE fileName = :fileName")
    suspend fun deleteDownloadByFileName(fileName: String)

    @Query("UPDATE downloaded_videos SET noLongerInWatchLater = :noLonger WHERE videoId = :videoId")
    suspend fun updateNoLongerInWatchLater(videoId: String, noLonger: Boolean)

    @Query("SELECT * FROM downloaded_videos WHERE videoId = :videoId AND playlistId = :playlistId LIMIT 1")
    suspend fun getVideoByVideoIdAndPlaylist(videoId: String, playlistId: String): DownloadedVideoEntity?

    @Query("DELETE FROM downloaded_videos WHERE videoId = :videoId AND playlistId = :playlistId")
    suspend fun deleteDownloadByVideoIdAndPlaylist(videoId: String, playlistId: String)

    @Query("UPDATE downloaded_videos SET thumbnailUrl = :thumbnailUrl WHERE videoId = :videoId")
    suspend fun updateThumbnailUrl(videoId: String, thumbnailUrl: String)
}
