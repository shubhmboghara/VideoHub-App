package com.videhub.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.videhub.data.dao.*
import com.videhub.data.entity.*

@Database(
    entities = [
        ChannelEntity::class, 
        PlaylistEntity::class, 
        PlaylistVideoEntity::class, 
        HistoryEntity::class,
        LikedVideoEntity::class,
        DownloadedVideoEntity::class,
        WatchLaterEntity::class,
        SearchHistoryEntity::class,
        SavedLyricsEntity::class,
        DownloadedPlaylistEntity::class,
        DownloadedPlaylistVideoCrossRef::class,
        WatchProgressEntity::class,
        FeedCacheEntity::class,
        SearchCacheEntity::class,
        VideoMetadataEntity::class
    ],
    version = 30,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun likedVideoDao(): LikedVideoDao
    abstract fun downloadedVideoDao(): DownloadedVideoDao
    abstract fun downloadedPlaylistDao(): DownloadedPlaylistDao
    abstract fun watchLaterDao(): WatchLaterDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun savedLyricsDao(): SavedLyricsDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun feedCacheDao(): FeedCacheDao
    abstract fun searchCacheDao(): SearchCacheDao
    abstract fun videoMetadataDao(): VideoMetadataDao

    companion object {
        private const val DB_NAME = "videhub_database"
        private const val TAG = "AppDatabase"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `saved_lyrics` (`videoId` TEXT NOT NULL, `lyricsJson` TEXT NOT NULL, PRIMARY KEY(`videoId`))")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfNotExists(db, "downloaded_videos", "lyrics", "TEXT")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `new_downloaded_videos` (
                        `fileName` TEXT NOT NULL,
                        `videoId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `thumbnailUrl` TEXT NOT NULL,
                        `channelName` TEXT NOT NULL,
                        `viewCount` INTEGER NOT NULL,
                        `uploadDate` TEXT NOT NULL,
                        `isAudioOnly` INTEGER NOT NULL,
                        `downloadedAt` INTEGER NOT NULL,
                        `lyrics` TEXT,
                        PRIMARY KEY(`fileName`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `new_downloaded_videos` (`fileName`, `videoId`, `title`, `thumbnailUrl`, `channelName`, `viewCount`, `uploadDate`, `isAudioOnly`, `downloadedAt`, `lyrics`)
                    SELECT `fileName`, `videoId`, `title`, `thumbnailUrl`, `channelName`, `viewCount`, `uploadDate`, `isAudioOnly`, `downloadedAt`, `lyrics` FROM `downloaded_videos`
                """.trimIndent())
                db.execSQL("DROP TABLE IF EXISTS `downloaded_videos`")
                db.execSQL("ALTER TABLE `new_downloaded_videos` RENAME TO `downloaded_videos`")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `search_cache` (`id` TEXT NOT NULL, `query` TEXT NOT NULL, `type` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `viewCount` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `video_metadata` (`videoId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `viewCount` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `streamUrl` TEXT, `audioUrl` TEXT, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`))")
            }
        }

        private fun addColumnIfNotExists(db: SupportSQLiteDatabase, table: String, column: String, columnDef: String) {
            var hasColumn = false
            try {
                db.query("PRAGMA table_info(`$table`)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    if (nameIdx >= 0) {
                        while (cursor.moveToNext()) {
                            if (cursor.getString(nameIdx).equals(column, ignoreCase = true)) {
                                hasColumn = true
                                break
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}

            if (!hasColumn) {
                try {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $columnDef")
                } catch (_: Throwable) {}
            }
        }

        private fun migrateToLatestSchema(db: SupportSQLiteDatabase) {
            // 1. Ensure playlists table has customCoverUrl column
            addColumnIfNotExists(db, "playlists", "customCoverUrl", "TEXT")

            // 2. Ensure downloaded_videos has all latest columns
            addColumnIfNotExists(db, "downloaded_videos", "lyrics", "TEXT")
            addColumnIfNotExists(db, "downloaded_videos", "playlistId", "TEXT")
            addColumnIfNotExists(db, "downloaded_videos", "playlistName", "TEXT")
            addColumnIfNotExists(db, "downloaded_videos", "playlistIndex", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "downloaded_videos", "state", "TEXT NOT NULL DEFAULT 'DONE'")
            addColumnIfNotExists(db, "downloaded_videos", "progress", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "downloaded_videos", "bytesDownloaded", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "downloaded_videos", "totalBytes", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "downloaded_videos", "duration", "TEXT NOT NULL DEFAULT ''")
            addColumnIfNotExists(db, "downloaded_videos", "errorMessage", "TEXT")
            addColumnIfNotExists(db, "downloaded_videos", "lastUpdatedAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "downloaded_videos", "partialFilePath", "TEXT")
            addColumnIfNotExists(db, "downloaded_videos", "noLongerInWatchLater", "INTEGER NOT NULL DEFAULT 0")

            // 3. Ensure search_cache table exists
            db.execSQL("CREATE TABLE IF NOT EXISTS `search_cache` (`id` TEXT NOT NULL, `query` TEXT NOT NULL, `type` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `viewCount` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, PRIMARY KEY(`id`))")

            // 4. Ensure video_metadata table exists with all required columns
            db.execSQL("CREATE TABLE IF NOT EXISTS `video_metadata` (`videoId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `viewCount` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `streamUrl` TEXT, `audioUrl` TEXT, `uploaderUrl` TEXT, `description` TEXT, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`))")
            addColumnIfNotExists(db, "video_metadata", "uploaderUrl", "TEXT")
            addColumnIfNotExists(db, "video_metadata", "description", "TEXT")

            // 5. Ensure downloaded_playlists exists
            db.execSQL("CREATE TABLE IF NOT EXISTS `downloaded_playlists` (`playlistId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT, `totalCount` INTEGER NOT NULL, `completedCount` INTEGER NOT NULL, `state` TEXT NOT NULL, `progress` INTEGER NOT NULL, `downloadedBytes` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL, `currentVideoTitle` TEXT, `currentVideoIndex` INTEGER NOT NULL, `currentVideoProgress` INTEGER NOT NULL, `downloadedAt` INTEGER NOT NULL, `lastUpdatedAt` INTEGER NOT NULL, `errorMessage` TEXT, PRIMARY KEY(`playlistId`))")

            // 6. Ensure downloaded_playlist_video_cross_ref exists
            db.execSQL("CREATE TABLE IF NOT EXISTS `downloaded_playlist_video_cross_ref` (`playlistId` TEXT NOT NULL, `videoId` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `videoId`), FOREIGN KEY(`playlistId`) REFERENCES `downloaded_playlists`(`playlistId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloaded_playlist_video_cross_ref_videoId` ON `downloaded_playlist_video_cross_ref` (`videoId`)")

            // 7. Ensure watch_progress exists
            db.execSQL("CREATE TABLE IF NOT EXISTS `watch_progress` (`videoId` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `watchedAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`))")

            // 8. Ensure feed_cache exists
            db.execSQL("CREATE TABLE IF NOT EXISTS `feed_cache` (`videoId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `channelAvatarUrl` TEXT, `viewCount` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `publishedText` TEXT NOT NULL, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`))")

            // 9. Ensure saved_lyrics exists
            db.execSQL("CREATE TABLE IF NOT EXISTS `saved_lyrics` (`videoId` TEXT NOT NULL, `lyricsJson` TEXT NOT NULL, PRIMARY KEY(`videoId`))")
        }

        private fun createMigration(from: Int, to: Int): Migration {
            return object : Migration(from, to) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    migrateToLatestSchema(db)
                }
            }
        }

        private val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_21_22,
            MIGRATION_22_23,
            createMigration(20, 30),
            createMigration(21, 30),
            createMigration(22, 30),
            createMigration(23, 30),
            createMigration(24, 30),
            createMigration(25, 30),
            createMigration(26, 30),
            createMigration(27, 30),
            createMigration(28, 30),
            createMigration(29, 30)
        )

        private fun buildDatabaseInstance(appContext: Context): AppDatabase {
            return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                DB_NAME
            )
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigration(true)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createAndValidateDatabase(context).also { INSTANCE = it }
            }
        }

        private fun createAndValidateDatabase(context: Context): AppDatabase {
            val appContext = context.applicationContext

            // 1. Attempt normal initialization with all migrations and fallback
            try {
                val db = buildDatabaseInstance(appContext)
                // Proactively test opening the database to verify schema and identity hash immediately
                db.openHelper.writableDatabase
                return db
            } catch (t: Throwable) {
                Log.e(TAG, "Database initialization/migration failed: ${t.message}. Performing auto-recovery.", t)
                try {
                    INSTANCE?.close()
                } catch (_: Throwable) {}
                INSTANCE = null
            }

            // 2. Auto-recovery: If an existing user's phone has an incompatible hash or corrupt database,
            // clean up the files so the app can launch fresh without crashing.
            try {
                appContext.deleteDatabase(DB_NAME)
            } catch (t: Throwable) {
                Log.e(TAG, "Error deleting corrupted database file: ${t.message}", t)
            }

            // 3. Build a fresh clean database instance
            val freshDb = buildDatabaseInstance(appContext)
            try {
                freshDb.openHelper.writableDatabase
            } catch (t: Throwable) {
                Log.e(TAG, "Error opening fresh database: ${t.message}", t)
            }
            return freshDb
        }
    }
}
