package com.videhub.data.dao

import androidx.room.*
import com.videhub.data.entity.PlaylistEntity
import com.videhub.data.entity.PlaylistVideoEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistWithThumbnail(
    @Embedded val playlist: PlaylistEntity,
    @ColumnInfo(name = "thumbnailUrl") val thumbnailUrl: String?,
    @ColumnInfo(name = "videoCount") val videoCount: Int
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT COUNT(*) FROM playlists")
    fun getCount(): Flow<Int>
    
    @Query("""
        SELECT p.*, 
               COALESCE(p.customCoverUrl, (SELECT thumbnailUrl FROM playlist_videos pv WHERE pv.playlistId = p.id ORDER BY addedAt DESC LIMIT 1)) as thumbnailUrl,
               (SELECT COUNT(*) FROM playlist_videos pv WHERE pv.playlistId = p.id) as videoCount
        FROM playlists p 
        ORDER BY p.createdAt DESC
    """)
    fun getAllPlaylistsWithDetails(): Flow<List<PlaylistWithThumbnail>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name, customCoverUrl = :customCoverUrl WHERE id = :id")
    suspend fun updatePlaylistDetails(id: Int, name: String, customCoverUrl: String?)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: PlaylistVideoEntity)

    @Delete
    suspend fun deleteVideo(video: PlaylistVideoEntity)

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    fun getVideos(playlistId: Int): Flow<List<PlaylistVideoEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_videos WHERE playlistId = :playlistId AND videoId = :videoId LIMIT 1)")
    suspend fun isVideoInPlaylist(playlistId: Int, videoId: String): Boolean

    @Query("SELECT * FROM playlists WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getPlaylistByName(name: String): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    suspend fun getVideosOnce(playlistId: Int): List<PlaylistVideoEntity>

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun deleteVideoByPair(playlistId: Int, videoId: String)

    @Query("SELECT * FROM playlist_videos ORDER BY addedAt DESC")
    suspend fun getAllPlaylistVideosOnce(): List<PlaylistVideoEntity>

    @Query("DELETE FROM playlists WHERE id IN (:ids)")
    suspend fun deletePlaylistsByIds(ids: List<Int>)

    @Query("DELETE FROM playlists WHERE id NOT IN (SELECT MIN(id) FROM playlists GROUP BY LOWER(TRIM(name)))")
    suspend fun deduplicatePlaylistsKeepOldest()
}
