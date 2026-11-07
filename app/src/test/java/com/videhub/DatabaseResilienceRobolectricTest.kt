package com.videhub

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.videhub.data.AppDatabase
import com.videhub.data.entity.PlaylistEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseResilienceRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppDatabase.resetInstanceForTesting()
        context.deleteDatabase("videhub_database")
    }

    @After
    fun tearDown() {
        AppDatabase.resetInstanceForTesting()
        context.deleteDatabase("videhub_database")
    }

    @Test
    fun `legacy database with mismatched identity hash f3379ba387f9ce80ccd09a2a8baff514 heals and preserves user playlists`() = runBlocking {
        val dbFile = context.getDatabasePath("videhub_database")
        dbFile.parentFile?.mkdirs()

        // Simulate an older user device that has version 25 (or 30) with the old hash f3379ba387f9ce80ccd09a2a8baff514
        // and the old playlists table WITHOUT customCoverUrl
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqliteDb ->
            sqliteDb.execSQL("CREATE TABLE `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            sqliteDb.execSQL("INSERT INTO `playlists` (`id`, `name`, `createdAt`) VALUES (1, 'My Old Playlist', 1700000000)")
            sqliteDb.execSQL("CREATE TABLE `watch_later` (`videoId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`))")
            sqliteDb.execSQL("INSERT INTO `watch_later` (`videoId`, `title`, `thumbnailUrl`, `channelName`, `addedAt`) VALUES ('vid_123', 'Saved Video', 'https://example.com/thumb.jpg', 'Channel A', 1700000001)")
            sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            sqliteDb.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'f3379ba387f9ce80ccd09a2a8baff514')")
            sqliteDb.version = 30 // Even if version number is identical to current version!
        }

        // Must NOT throw IllegalStateException: Room cannot verify the data integrity
        val db = AppDatabase.getDatabase(context)
        val playlists = db.playlistDao().getAllPlaylistsOnce()
        assertEquals(1, playlists.size)
        assertEquals("My Old Playlist", playlists[0].name)

        // Verify we can also insert new playlists with customCoverUrl
        db.playlistDao().insertPlaylist(PlaylistEntity(name = "New Playlist", customCoverUrl = "https://example.com/cover.png"))
        val updatedPlaylists = db.playlistDao().getAllPlaylistsOnce()
        assertEquals(2, updatedPlaylists.size)
    }

    @Test
    fun `database with ALTER TABLE DEFAULT values heals cleanly without Room TableInfo mismatch`() = runBlocking {
        val dbFile = context.getDatabasePath("videhub_database")
        dbFile.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqliteDb ->
            sqliteDb.execSQL("CREATE TABLE `downloaded_videos` (`fileName` TEXT NOT NULL, `videoId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `viewCount` INTEGER NOT NULL DEFAULT 0, `uploadDate` TEXT NOT NULL DEFAULT '', `isAudioOnly` INTEGER NOT NULL DEFAULT 0, `downloadedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`fileName`))")
            sqliteDb.execSQL("INSERT INTO `downloaded_videos` (`fileName`, `videoId`, `title`, `thumbnailUrl`, `channelName`) VALUES ('song.mp3', 'v999', 'My Song', 'thumb', 'Artist')")
            sqliteDb.version = 24
        }

        val db = AppDatabase.getDatabase(context)
        val downloaded = db.downloadedVideoDao().getDownloadByFileName("song.mp3")
        assertNotNull(downloaded)
        assertEquals("My Song", downloaded?.title)
    }

    @Test
    fun `corrupted database file recovers automatically without crashing`() = runBlocking {
        val dbFile = context.getDatabasePath("videhub_database")
        dbFile.parentFile?.mkdirs()
        dbFile.writeBytes("NOT A VALID SQLITE FILE CORRUPT BYTES".toByteArray())

        val db = AppDatabase.getDatabase(context)
        db.playlistDao().insertPlaylist(PlaylistEntity(name = "Recovered Playlist"))
        val playlists = db.playlistDao().getAllPlaylistsOnce()
        assertEquals(1, playlists.size)
        assertEquals("Recovered Playlist", playlists[0].name)
    }
}
