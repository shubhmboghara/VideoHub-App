package com.videhub.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.videhub.data.entity.DownloadedPlaylistEntity
import com.videhub.data.entity.DownloadedPlaylistVideoCrossRef
import com.videhub.data.entity.DownloadedVideoEntity
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
        SELECT p.playlistId, p.title as playlistName, 
               (SELECT COUNT(*) FROM downloaded_videos v INNER JOIN downloaded_playlist_video_cross_ref c ON v.videoId = c.videoId WHERE c.playlistId = p.playlistId AND v.fileName NOT LIKE 'PENDING_%') as videoCount,
               (SELECT COUNT(*) FROM downloaded_playlist_video_cross_ref c2 WHERE c2.playlistId = p.playlistId) as totalVideos,
               COALESCE(
                   NULLIF(p.thumbnailUrl, ''),
                   (SELECT v.thumbnailUrl FROM downloaded_videos v INNER JOIN downloaded_playlist_video_cross_ref c ON v.videoId = c.videoId WHERE c.playlistId = p.playlistId AND v.thumbnailUrl IS NOT NULL AND v.thumbnailUrl != '' LIMIT 1)
               ) as coverThumbnailUrl
        FROM downloaded_playlists p
        ORDER BY p.downloadedAt DESC
    """)
    fun getDownloadedPlaylistsSummary(): Flow<List<com.videhub.data.model.PlaylistDownloadSummary>>

    @Query("SELECT * FROM downloaded_playlists WHERE playlistId = :playlistId")
    suspend fun getPlaylistById(playlistId: String): DownloadedPlaylistEntity?

    @Query("SELECT * FROM downloaded_playlists WHERE playlistId = :playlistId")
    fun getPlaylistByIdFlow(playlistId: String): Flow<DownloadedPlaylistEntity?>

    @Transaction
    @Query("""
        SELECT v.* FROM downloaded_videos v
        INNER JOIN downloaded_playlist_video_cross_ref crossRef ON v.videoId = crossRef.videoId
        WHERE crossRef.playlistId = :playlistId
        ORDER BY v.downloadedAt ASC
    """)
    fun getVideosForPlaylist(playlistId: String): Flow<List<DownloadedVideoEntity>>



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
