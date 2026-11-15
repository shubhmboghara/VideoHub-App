package com.videhub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.videhub.data.AppDatabase
import com.videhub.data.DataBackupManager
import com.videhub.data.ImportMode
import com.videhub.data.entity.ChannelEntity
import com.videhub.data.entity.DownloadedVideoEntity
import com.videhub.data.entity.HistoryEntity
import com.videhub.data.entity.LikedVideoEntity
import com.videhub.data.entity.PlaylistEntity
import com.videhub.data.entity.PlaylistVideoEntity
import com.videhub.data.entity.SearchHistoryEntity
import com.videhub.data.entity.WatchLaterEntity
import com.videhub.data.entity.WatchProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataBackupManagerTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppDatabase.resetInstanceForTesting()
        context.deleteDatabase("videhub_database")
        db = AppDatabase.getDatabase(context)
    }

    @After
    fun tearDown() {
        AppDatabase.resetInstanceForTesting()
        context.deleteDatabase("videhub_database")
    }

    @Test
    fun `export and import round trip restores subscriptions playlists history watch later and likes on new device`() = runBlocking {
        // Seed old device data
        db.channelDao().insert(
            ChannelEntity(
                channelId = "https://www.youtube.com/channel/UC_test_1",
                name = "Tech Channel",
                thumbnailUrl = "https://example.com/avatar1.jpg"
            )
        )
        val plId = db.playlistDao().insertPlaylist(
            PlaylistEntity(
                name = "Favorites",
                customCoverUrl = "https://example.com/cover.jpg",
                createdAt = 1700000000000L
            )
        ).toInt()
        db.playlistDao().insertVideo(
            PlaylistVideoEntity(
                playlistId = plId,
                videoId = "https://www.youtube.com/watch?v=vid_pl_1",
                title = "Playlist Song",
                channelName = "Music Maker",
                thumbnailUrl = "https://example.com/th1.jpg",
                durationText = "03:45",
                addedAt = 1700000001000L,
                viewCount = 12345L,
                uploadDate = "2025-01-10"
            )
        )
        db.historyDao().insert(
            HistoryEntity(
                videoId = "https://www.youtube.com/watch?v=hist_1",
                title = "Watched Documentary",
                channelName = "Doc Studio",
                thumbnailUrl = "https://example.com/th2.jpg",
                durationText = "24:10",
                timestamp = 1700000002000L,
                viewCount = 50000L,
                uploadDate = "2025-02-01"
            )
        )
        db.watchLaterDao().insert(
            WatchLaterEntity(
                videoId = "https://www.youtube.com/watch?v=wl_1",
                title = "Watch This Later",
                channelName = "Science Daily",
                thumbnailUrl = "https://example.com/th3.jpg",
                addedAt = 1700000003000L
            )
        )
        db.likedVideoDao().insert(
            LikedVideoEntity(
                videoId = "https://www.youtube.com/watch?v=like_1",
                title = "Great Tutorial",
                thumbnailUrl = "https://example.com/th4.jpg",
                channelName = "Code Academy",
                likedAt = 1700000004000L,
                viewCount = 8800L,
                uploadDate = "2025-03-01"
            )
        )
        db.searchHistoryDao().insertSearch(SearchHistoryEntity("kotlin compose", 1700000005000L))
        db.watchProgressDao().save(WatchProgressEntity("https://www.youtube.com/watch?v=hist_1", 90_000L, 600_000L, 1700000006000L))

        val (exportedJson, summary) = DataBackupManager.exportToJsonString(context)
        assertEquals(1, summary.subscriptionsCount)
        assertEquals(1, summary.playlistsCount)
        assertEquals(1, summary.playlistVideosCount)
        assertEquals(1, summary.historyCount)
        assertEquals(1, summary.watchLaterCount)
        assertEquals(1, summary.likedVideosCount)
        assertEquals(1, summary.searchHistoryCount)
        assertEquals(1, summary.watchProgressCount)

        // Inspect summary before import
        val inspected = DataBackupManager.inspectBackupJson(exportedJson)
        assertNotNull(inspected)
        assertEquals(1, inspected!!.subscriptionsCount)
        assertEquals(1, inspected.playlistsCount)
        assertEquals(1, inspected.watchLaterCount)

        // Simulate fresh new device database
        AppDatabase.resetInstanceForTesting()
        context.deleteDatabase("videhub_database")
        val newDeviceDb = AppDatabase.getDatabase(context)
        assertEquals(0, newDeviceDb.channelDao().getAllSync().size)

        val importResult = DataBackupManager.importFromJsonString(
            context = context,
            jsonText = exportedJson,
            mode = ImportMode.MERGE,
            restorePreferences = false
        )
        assertTrue(importResult.success)
        assertEquals(1, importResult.subscriptionsImported)
        assertEquals(1, importResult.playlistsImported)
        assertEquals(1, importResult.playlistVideosImported)
        assertEquals(1, importResult.historyImported)
        assertEquals(1, importResult.watchLaterImported)
        assertEquals(1, importResult.likedVideosImported)

        val restoredChannels = newDeviceDb.channelDao().getAllSync()
        assertEquals(1, restoredChannels.size)
        assertEquals("Tech Channel", restoredChannels.first().name)

        val restoredPlaylists = newDeviceDb.playlistDao().getAllPlaylistsOnce()
        assertEquals(1, restoredPlaylists.size)
        assertEquals("Favorites", restoredPlaylists.first().name)
        assertEquals("https://example.com/cover.jpg", restoredPlaylists.first().customCoverUrl)

        val restoredPlaylistVideos = newDeviceDb.playlistDao().getVideosOnce(restoredPlaylists.first().id)
        assertEquals(1, restoredPlaylistVideos.size)
        assertEquals(12345L, restoredPlaylistVideos.first().viewCount)

        val restoredHistory = newDeviceDb.historyDao().getAllHistoryOnce()
        assertEquals(1, restoredHistory.size)
        assertEquals(50000L, restoredHistory.first().viewCount)

        val restoredWatchLater = newDeviceDb.watchLaterDao().getAllOnce()
        assertEquals(1, restoredWatchLater.size)
        assertEquals("Watch This Later", restoredWatchLater.first().title)

        val restoredLiked = newDeviceDb.likedVideoDao().getAllOnce()
        assertEquals(1, restoredLiked.size)
        assertEquals("Great Tutorial", restoredLiked.first().title)

        val restoredSearch = newDeviceDb.searchHistoryDao().getAllSearchHistory().first()
        assertEquals(1, restoredSearch.size)
        assertEquals("kotlin compose", restoredSearch.first().query)

        val restoredProgress = newDeviceDb.watchProgressDao().get("https://www.youtube.com/watch?v=hist_1")
        assertNotNull(restoredProgress)
        assertEquals(90_000L, restoredProgress!!.positionMs)
    }

    @Test
    fun `merge mode deduplicates channels and playlists across repeated imports`() = runBlocking {
        db.channelDao().insert(ChannelEntity("UC_dup_1", "Dup Channel", null))
        val plId = db.playlistDao().insertPlaylist(PlaylistEntity(name = "Workout")).toInt()
        db.playlistDao().insertVideo(
            PlaylistVideoEntity(
                playlistId = plId,
                videoId = "https://www.youtube.com/watch?v=v1",
                title = "Track 1",
                channelName = "Artist"
            )
        )

        val incomingJson = """
            {
              "version": 2,
              "channels": [
                {"channelId": "https://www.youtube.com/channel/UC_dup_1", "name": "Dup Channel", "thumbnailUrl": "https://example.com/a.jpg"}
              ],
              "playlists": [
                {
                  "name": "workout",
                  "videos": [
                    {"videoId": "https://www.youtube.com/watch?v=v1", "title": "Track 1", "channelName": "Artist"},
                    {"videoId": "https://www.youtube.com/watch?v=v2", "title": "Track 2", "channelName": "Artist"}
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = DataBackupManager.importFromJsonString(context, incomingJson, mode = ImportMode.MERGE, restorePreferences = false)
        assertTrue(result.success)

        // Should NOT create duplicate channel or duplicate playlist or duplicate Track 1
        assertEquals(1, db.channelDao().getAllSync().size)
        assertEquals("https://example.com/a.jpg", db.channelDao().getAllSync().first().thumbnailUrl)
        assertEquals(1, db.playlistDao().getAllPlaylistsOnce().size)
        assertEquals(2, db.playlistDao().getVideosOnce(plId).size)
    }

    @Test
    fun `replace mode clears existing user data while preserving local downloads`() = runBlocking {
        db.channelDao().insert(ChannelEntity("https://www.youtube.com/channel/OLD", "Old Sub", null))
        db.watchLaterDao().insert(WatchLaterEntity("old_wl", "Old WL", "", "Ch", 100L))
        db.downloadedVideoDao().insertDownload(
            DownloadedVideoEntity(
                fileName = "local_song.mp3",
                videoId = "local_1",
                title = "Downloaded Song",
                thumbnailUrl = "",
                channelName = "Artist"
            )
        )

        val json = """
            {
              "version": 2,
              "channels": [{"channelId": "https://www.youtube.com/channel/NEW", "name": "New Sub", "thumbnailUrl": ""}],
              "watchLater": [{"videoId": "new_wl", "title": "New WL", "channelName": "Ch", "thumbnailUrl": ""}]
            }
        """.trimIndent()

        val result = DataBackupManager.importFromJsonString(context, json, mode = ImportMode.REPLACE, restorePreferences = false)
        assertTrue(result.success)

        val subs = db.channelDao().getAllSync()
        assertEquals(1, subs.size)
        assertEquals("New Sub", subs.first().name)

        val wl = db.watchLaterDao().getAllOnce()
        assertEquals(1, wl.size)
        assertEquals("new_wl", wl.first().videoId)

        // Local download must not be wiped on Replace import
        val downloads = db.downloadedVideoDao().getAllDownloadsSync()
        assertEquals(1, downloads.size)
        assertEquals("local_song.mp3", downloads.first().fileName)
    }

    @Test
    fun `inspectBackupJson rejects unrelated json files`() {
        assertNull(DataBackupManager.inspectBackupJson("""{"hello": "world"}"""))
        assertNull(DataBackupManager.inspectBackupJson("not json at all"))
        assertNull(DataBackupManager.inspectBackupJson(""))
        assertFalse(runBlocking {
            DataBackupManager.importFromJsonString(context, """{"hello":"world"}""").success
        })
    }
}
