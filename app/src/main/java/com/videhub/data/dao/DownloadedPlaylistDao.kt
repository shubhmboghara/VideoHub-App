package com.videhub.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.videhub.data.entity.DownloadedPlaylistEntity
import com.videhub.data.entity.DownloadedPlaylistVideoCrossRef
import com.videhub.data.entity.DownloadedVideoEntity
import com.videhub.data.model.PlaylistDownloadSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedPlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: DownloadedPlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistVideoCrossRef(crossRef: DownloadedPlaylistVideoCrossRef)

    @Query("SELECT * FROM downloaded_playlists ORDER BY downloadedAt DESC")
    fun getAllDownloadedPlaylists(): Flow<List<DownloadedPlaylistEntity>>

    @Query("""
        SELECT p.playlistId, 
               p.title as playlistName, 
               (SELECT COUNT(*) FROM downloaded_videos v 
                INNER JOIN downloaded_playlist_video_cross_ref c ON v.videoId = c.videoId 
                WHERE c.playlistId = p.playlistId AND (v.state = 'DONE' OR (v.fileName NOT LIKE 'PENDING_%' AND v.fileName NOT LIKE 'QUEUED_%'))) as videoCount,
               CASE WHEN p.totalCount > 0 THEN p.totalCount 
                    ELSE (SELECT COUNT(*) FROM downloaded_playlist_video_cross_ref c2 WHERE c2.playlistId = p.playlistId) 
               END as totalVideos,
               COALESCE(
                   NULLIF(p.thumbnailUrl, ''),
                   (SELECT v.thumbnailUrl FROM downloaded_videos v 
                    INNER JOIN downloaded_playlist_video_cross_ref c ON v.videoId = c.videoId 
                    WHERE c.playlistId = p.playlistId AND v.thumbnailUrl IS NOT NULL AND v.thumbnailUrl != '' LIMIT 1)
               ) as coverThumbnailUrl,
               p.state as state,
               p.progress as progress,
               p.downloadedBytes as downloadedBytes,
               p.totalBytes as totalBytes,
               p.currentVideoTitle as currentVideoTitle,
               p.currentVideoIndex as currentVideoIndex,
               p.currentVideoProgress as currentVideoProgress
        FROM downloaded_playlists p
        ORDER BY p.downloadedAt DESC
    """)
    fun getDownloadedPlaylistsSummary(): Flow<List<PlaylistDownloadSummary>>

    @Query("SELECT * FROM downloaded_playlists WHERE playlistId = :playlistId")
    suspend fun getPlaylistById(playlistId: String): DownloadedPlaylistEntity?

    @Query("SELECT * FROM downloaded_playlists WHERE playlistId = :playlistId")
    fun getPlaylistByIdFlow(playlistId: String): Flow<DownloadedPlaylistEntity?>

    @Transaction
    @Query("""
        SELECT * FROM (
            SELECT v.* FROM downloaded_videos v
            INNER JOIN downloaded_playlist_video_cross_ref crossRef ON v.videoId = crossRef.videoId
            WHERE crossRef.playlistId = :playlistId
            ORDER BY 
                CASE WHEN v.state = 'DONE' THEN 1 WHEN v.state = 'DOWNLOADING' THEN 2 WHEN v.state = 'PAUSED' THEN 3 ELSE 4 END ASC,
                CASE WHEN v.fileName LIKE 'PENDING_%' THEN 2 ELSE 1 END ASC,
                v.downloadedAt DESC
        )
        GROUP BY videoId
        ORDER BY playlistIndex ASC, downloadedAt ASC
    """)
    fun getVideosForPlaylist(playlistId: String): Flow<List<DownloadedVideoEntity>>

    @Query("""
        UPDATE downloaded_playlists 
        SET completedCount = :completedCount, 
            progress = :progress, 
            downloadedBytes = :downloadedBytes, 
            totalBytes = :totalBytes, 
            state = :state, 
            currentVideoIndex = :currentVideoIndex, 
            currentVideoProgress = :currentVideoProgress, 
            currentVideoTitle = :currentVideoTitle 
        WHERE playlistId = :playlistId
    """)
    suspend fun updatePlaylistProgress(
        playlistId: String,
        completedCount: Int,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        state: String,
        currentVideoIndex: Int,
        currentVideoProgress: Int,
        currentVideoTitle: String?
    )

    @Query("UPDATE downloaded_playlists SET state = :state WHERE playlistId = :playlistId")
    suspend fun updatePlaylistState(playlistId: String, state: String)

    @Query("UPDATE downloaded_playlists SET state = :state, errorMessage = :errorMessage, lastUpdatedAt = :lastUpdatedAt WHERE playlistId = :playlistId")
    suspend fun updatePlaylistStateAndError(
        playlistId: String,
        state: String,
        errorMessage: String?,
        lastUpdatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE downloaded_playlists SET totalCount = :totalCount, thumbnailUrl = :coverUrl WHERE playlistId = :playlistId")
    suspend fun updatePlaylistMetadata(playlistId: String, totalCount: Int, coverUrl: String?)

    @Query("UPDATE downloaded_playlists SET thumbnailUrl = :thumbnailUrl WHERE playlistId = :playlistId")
    suspend fun updatePlaylistThumbnail(playlistId: String, thumbnailUrl: String)

    @Query("DELETE FROM downloaded_playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("DELETE FROM downloaded_playlist_video_cross_ref WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun deletePlaylistVideoCrossRef(playlistId: String, videoId: String)

    @Query("DELETE FROM downloaded_playlist_video_cross_ref WHERE playlistId = :playlistId AND videoId IN (SELECT videoId FROM downloaded_videos WHERE playlistId = :playlistId AND fileName LIKE 'PENDING_%')")
    suspend fun deletePendingPlaylistVideoCrossRefs(playlistId: String)

    @Query("DELETE FROM downloaded_playlist_video_cross_ref WHERE videoId IN (SELECT videoId FROM downloaded_videos WHERE fileName LIKE 'PENDING_%')")
    suspend fun deleteAllPendingPlaylistVideoCrossRefs()
}
