package com.videhub.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.videhub.data.dao.*
import com.videhub.data.entity.*
import java.io.File

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
        private const val CURRENT_VERSION = 30
        private const val EXPECTED_IDENTITY_HASH = "5cd88d7abbb6dec277cd1f70b530ab5e"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private data class TableSpec(
            val name: String,
            val createSql: String,
            val columnsWithFallbacks: List<Pair<String, String>>,
            val preserveData: Boolean = true,
            val postCopySql: List<String> = emptyList()
        )

        private val TABLE_SPECS = listOf(
            TableSpec(
                name = "channels",
                createSql = "CREATE TABLE IF NOT EXISTS `channels` (`channelId` TEXT NOT NULL, `name` TEXT NOT NULL, `thumbnailUrl` TEXT, PRIMARY KEY(`channelId`))",
                columnsWithFallbacks = listOf(
                    "channelId" to "''",
                    "name" to "''",
                    "thumbnailUrl" to "NULL"
                )
            ),
            TableSpec(
                name = "playlists",
                createSql = "CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `customCoverUrl` TEXT, `createdAt` INTEGER NOT NULL)",
                columnsWithFallbacks = listOf(
                    "id" to "0",
                    "name" to "'Playlist'",
                    "customCoverUrl" to "NULL",
                    "createdAt" to "0"
                )
            ),
            TableSpec(
                name = "playlist_videos",
                createSql = "CREATE TABLE IF NOT EXISTS `playlist_videos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` INTEGER NOT NULL, `videoId` TEXT NOT NULL, `title` TEXT NOT NULL, `channelName` TEXT NOT NULL, `thumbnailUrl` TEXT, `durationText` TEXT, `addedAt` INTEGER NOT NULL, `viewCount` INTEGER NOT NULL, `uploadDate` TEXT NOT NULL, FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                columnsWithFallbacks = listOf(
                    "id" to "0",
                    "playlistId" to "0",
                    "videoId" to "''",
                    "title" to "''",
                    "channelName" to "''",
                    "thumbnailUrl" to "NULL",
                    "durationText" to "NULL",
                    "addedAt" to "0",
                    "viewCount" to "-1",
                    "uploadDate" to "''"
                ),
                postCopySql = listOf(
                    "DELETE FROM `playlist_videos` WHERE `playlistId` NOT IN (SELECT `id` FROM `playlists`)",
                    "CREATE INDEX IF NOT EXISTS `index_playlist_videos_playlistId` ON `playlist_videos` (`playlistId`)"
                )
            ),
            TableSpec(
                name = "history_entities",
                createSql = "CREATE TABLE IF NOT EXISTS `history_entities` (`videoId` TEXT NOT NULL, `title` TEXT NOT NULL, `channelName` TEXT NOT NULL, `thumbnailUrl` TEXT, `durationText` TEXT, `timestamp` INTEGER NOT NULL, `viewCount` INTEGER NOT NULL, `uploadDate` TEXT NOT NULL, PRIMARY KEY(`videoId`))",
                columnsWithFallbacks = listOf(
                    "videoId" to "''",
                    "title" to "''",
                    "channelName" to "''",
                    "thumbnailUrl" to "NULL",
                    "durationText" to "NULL",
                    "timestamp" to "0",
                    "viewCount" to "-1",
                    "uploadDate" to "''"
                )
            ),
            TableSpec(
                name = "liked_videos",
                createSql = "CREATE TABLE IF NOT EXISTS `liked_videos` (`videoId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `likedAt` INTEGER NOT NULL, `viewCount` INTEGER NOT NULL, `uploadDate` TEXT NOT NULL, PRIMARY KEY(`videoId`))",
                columnsWithFallbacks = listOf(
                    "videoId" to "''",
                    "title" to "''",
                    "thumbnailUrl" to "''",
                    "channelName" to "''",
                    "likedAt" to "0",
                    "viewCount" to "-1",
                    "uploadDate" to "''"
                )
            ),
            TableSpec(
                name = "downloaded_videos",
                createSql = "CREATE TABLE IF NOT EXISTS `downloaded_videos` (`fileName` TEXT NOT NULL, `videoId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `viewCount` INTEGER NOT NULL, `uploadDate` TEXT NOT NULL, `isAudioOnly` INTEGER NOT NULL, `downloadedAt` INTEGER NOT NULL, `lyrics` TEXT, `playlistId` TEXT, `playlistName` TEXT, `playlistIndex` INTEGER NOT NULL, `state` TEXT NOT NULL, `progress` INTEGER NOT NULL, `bytesDownloaded` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL, `duration` TEXT NOT NULL, `errorMessage` TEXT, `lastUpdatedAt` INTEGER NOT NULL, `partialFilePath` TEXT, `noLongerInWatchLater` INTEGER NOT NULL, PRIMARY KEY(`fileName`))",
                columnsWithFallbacks = listOf(
                    "fileName" to "''",
                    "videoId" to "''",
                    "title" to "''",
                    "thumbnailUrl" to "''",
                    "channelName" to "''",
                    "viewCount" to "0",
                    "uploadDate" to "''",
                    "isAudioOnly" to "0",
                    "downloadedAt" to "0",
                    "lyrics" to "NULL",
                    "playlistId" to "NULL",
                    "playlistName" to "NULL",
                    "playlistIndex" to "0",
                    "state" to "'DONE'",
                    "progress" to "0",
                    "bytesDownloaded" to "0",
                    "totalBytes" to "0",
                    "duration" to "''",
                    "errorMessage" to "NULL",
                    "lastUpdatedAt" to "0",
                    "partialFilePath" to "NULL",
                    "noLongerInWatchLater" to "0"
                )
            ),
            TableSpec(
                name = "watch_later",
                createSql = "CREATE TABLE IF NOT EXISTS `watch_later` (`videoId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`))",
                columnsWithFallbacks = listOf(
                    "videoId" to "''",
                    "title" to "''",
                    "thumbnailUrl" to "''",
                    "channelName" to "''",
                    "addedAt" to "0"
                )
            ),
            TableSpec(
                name = "search_history",
                createSql = "CREATE TABLE IF NOT EXISTS `search_history` (`query` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`query`))",
                columnsWithFallbacks = listOf(
                    "query" to "''",
                    "timestamp" to "0"
                )
            ),
            TableSpec(
                name = "saved_lyrics",
                createSql = "CREATE TABLE IF NOT EXISTS `saved_lyrics` (`videoId` TEXT NOT NULL, `lyricsJson` TEXT NOT NULL, PRIMARY KEY(`videoId`))",
                columnsWithFallbacks = listOf(
                    "videoId" to "''",
                    "lyricsJson" to "''"
                )
            ),
            TableSpec(
                name = "downloaded_playlists",
                createSql = "CREATE TABLE IF NOT EXISTS `downloaded_playlists` (`playlistId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT, `totalCount` INTEGER NOT NULL, `completedCount` INTEGER NOT NULL, `state` TEXT NOT NULL, `progress` INTEGER NOT NULL, `downloadedBytes` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL, `currentVideoTitle` TEXT, `currentVideoIndex` INTEGER NOT NULL, `currentVideoProgress` INTEGER NOT NULL, `downloadedAt` INTEGER NOT NULL, `lastUpdatedAt` INTEGER NOT NULL, `errorMessage` TEXT, PRIMARY KEY(`playlistId`))",
                columnsWithFallbacks = listOf(
                    "playlistId" to "''",
                    "title" to "''",
                    "thumbnailUrl" to "NULL",
                    "totalCount" to "0",
                    "completedCount" to "0",
                    "state" to "'DONE'",
                    "progress" to "0",
                    "downloadedBytes" to "0",
                    "totalBytes" to "0",
                    "currentVideoTitle" to "NULL",
                    "currentVideoIndex" to "0",
                    "currentVideoProgress" to "0",
                    "downloadedAt" to "0",
                    "lastUpdatedAt" to "0",
                    "errorMessage" to "NULL"
                )
            ),
            TableSpec(
                name = "downloaded_playlist_video_cross_ref",
                createSql = "CREATE TABLE IF NOT EXISTS `downloaded_playlist_video_cross_ref` (`playlistId` TEXT NOT NULL, `videoId` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `videoId`), FOREIGN KEY(`playlistId`) REFERENCES `downloaded_playlists`(`playlistId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                columnsWithFallbacks = listOf(
                    "playlistId" to "''",
                    "videoId" to "''"
                ),
                postCopySql = listOf(
                    "DELETE FROM `downloaded_playlist_video_cross_ref` WHERE `playlistId` NOT IN (SELECT `playlistId` FROM `downloaded_playlists`)",
                    "CREATE INDEX IF NOT EXISTS `index_downloaded_playlist_video_cross_ref_videoId` ON `downloaded_playlist_video_cross_ref` (`videoId`)"
                )
            ),
            TableSpec(
                name = "watch_progress",
                createSql = "CREATE TABLE IF NOT EXISTS `watch_progress` (`videoId` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `watchedAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`))",
                columnsWithFallbacks = listOf(
                    "videoId" to "''",
                    "positionMs" to "0",
                    "durationMs" to "0",
                    "watchedAt" to "0"
                )
            ),
            TableSpec(
                name = "feed_cache",
                createSql = "CREATE TABLE IF NOT EXISTS `feed_cache` (`videoId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `channelAvatarUrl` TEXT, `viewCount` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `publishedText` TEXT NOT NULL, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`))",
                columnsWithFallbacks = emptyList(),
                preserveData = false
            ),
            TableSpec(
                name = "search_cache",
                createSql = "CREATE TABLE IF NOT EXISTS `search_cache` (`id` TEXT NOT NULL, `query` TEXT NOT NULL, `type` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `viewCount` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                columnsWithFallbacks = emptyList(),
                preserveData = false
            ),
            TableSpec(
                name = "video_metadata",
                createSql = "CREATE TABLE IF NOT EXISTS `video_metadata` (`videoId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `viewCount` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `streamUrl` TEXT, `audioUrl` TEXT, `uploaderUrl` TEXT, `description` TEXT, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`))",
                columnsWithFallbacks = emptyList(),
                preserveData = false
            )
        )

        /**
         * Inspects any existing SQLite file on disk BEFORE Room opens it.
         * If the database version, identity_hash, or table definitions differ from what
         * Room's generated AppDatabase_Impl expects, rebuilds every table to match the
         * exact Room DDL while preserving existing user data.
         */
        private fun healDatabaseBeforeRoomOpens(appContext: Context) {
            val dbFile: File = appContext.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return

            var sqliteDb: SQLiteDatabase? = null
            try {
                sqliteDb = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                )

                val currentVersion = sqliteDb.version
                var currentHash: String? = null
                try {
                    sqliteDb.rawQuery(
                        "SELECT identity_hash FROM room_master_table WHERE id = 42 LIMIT 1",
                        null
                    ).use { cursor ->
                        if (cursor.moveToFirst()) {
                            currentHash = cursor.getString(0)
                        }
                    }
                } catch (_: Throwable) {
                    // room_master_table might not exist yet
                }

                val needsRebuild = currentVersion != CURRENT_VERSION ||
                    currentHash != EXPECTED_IDENTITY_HASH ||
                    !allTablesMatchCleanSchema(sqliteDb)

                if (needsRebuild) {
                    Log.i(
                        TAG,
                        "Healing SQLite schema before Room open (version=$currentVersion -> $CURRENT_VERSION, hash=$currentHash -> $EXPECTED_IDENTITY_HASH)"
                    )
                    sqliteDb.execSQL("PRAGMA foreign_keys = OFF")
                    sqliteDb.beginTransaction()
                    try {
                        for (spec in TABLE_SPECS) {
                            rebuildTableLosslessly(sqliteDb, spec)
                        }
                        sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                        sqliteDb.execSQL("DELETE FROM room_master_table")
                        sqliteDb.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$EXPECTED_IDENTITY_HASH')")
                        sqliteDb.version = CURRENT_VERSION
                        sqliteDb.setTransactionSuccessful()
                    } finally {
                        sqliteDb.endTransaction()
                        sqliteDb.execSQL("PRAGMA foreign_keys = ON")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Pre-flight SQLite heal encountered unrecoverable error, deleting DB file", t)
                try {
                    sqliteDb?.close()
                } catch (_: Throwable) {}
                sqliteDb = null
                deleteDatabaseFiles(appContext)
            } finally {
                try {
                    sqliteDb?.close()
                } catch (_: Throwable) {}
            }
        }

        private fun allTablesMatchCleanSchema(sqliteDb: SQLiteDatabase): Boolean {
            for (spec in TABLE_SPECS) {
                val existingCols = mutableMapOf<String, String?>()
                try {
                    sqliteDb.rawQuery("PRAGMA table_info(`${spec.name}`)", null).use { cursor ->
                        val nameIdx = cursor.getColumnIndex("name")
                        val dfltIdx = cursor.getColumnIndex("dflt_value")
                        while (cursor.moveToNext()) {
                            if (nameIdx >= 0) {
                                val colName = cursor.getString(nameIdx)
                                val dfltVal = if (dfltIdx >= 0 && !cursor.isNull(dfltIdx)) cursor.getString(dfltIdx) else null
                                existingCols[colName] = dfltVal
                            }
                        }
                    }
                } catch (_: Throwable) {
                    return false
                }
                if (existingCols.isEmpty()) return false
                // Check all expected columns exist and have no leftover DEFAULT value from old ALTER TABLE statements
                for ((expectedCol, _) in spec.columnsWithFallbacks) {
                    if (!existingCols.containsKey(expectedCol)) return false
                    if (existingCols[expectedCol] != null) return false
                }
            }
            return true
        }

        private fun rebuildTableLosslessly(sqliteDb: SQLiteDatabase, spec: TableSpec) {
            if (!spec.preserveData) {
                sqliteDb.execSQL("DROP TABLE IF EXISTS `${spec.name}`")
                sqliteDb.execSQL(spec.createSql)
                for (sql in spec.postCopySql) {
                    sqliteDb.execSQL(sql)
                }
                return
            }

            val existingColumns = mutableSetOf<String>()
            try {
                sqliteDb.rawQuery("PRAGMA table_info(`${spec.name}`)", null).use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    if (nameIdx >= 0) {
                        while (cursor.moveToNext()) {
                            existingColumns.add(cursor.getString(nameIdx))
                        }
                    }
                }
            } catch (_: Throwable) {}

            val tempTableName = "_temp_migration_${spec.name}"
            sqliteDb.execSQL("DROP TABLE IF EXISTS `$tempTableName`")
            val tempCreateSql = spec.createSql.replaceFirst(
                "CREATE TABLE IF NOT EXISTS `${spec.name}`",
                "CREATE TABLE `$tempTableName`"
            )
            sqliteDb.execSQL(tempCreateSql)

            if (existingColumns.isNotEmpty()) {
                val targetColumns = spec.columnsWithFallbacks.joinToString(", ") { "`${it.first}`" }
                val selectExpressions = spec.columnsWithFallbacks.joinToString(", ") { (colName, fallbackExpr) ->
                    val matchedCol = existingColumns.firstOrNull { it.equals(colName, ignoreCase = true) }
                    if (matchedCol != null) {
                        if (fallbackExpr == "NULL") {
                            "`$matchedCol`"
                        } else {
                            "COALESCE(`$matchedCol`, $fallbackExpr)"
                        }
                    } else {
                        fallbackExpr
                    }
                }
                try {
                    sqliteDb.execSQL(
                        "INSERT OR IGNORE INTO `$tempTableName` ($targetColumns) SELECT $selectExpressions FROM `${spec.name}`"
                    )
                } catch (e: Throwable) {
                    Log.w(TAG, "Could not copy rows for ${spec.name}: ${e.message}")
                }
            }

            sqliteDb.execSQL("DROP TABLE IF EXISTS `${spec.name}`")
            sqliteDb.execSQL("ALTER TABLE `$tempTableName` RENAME TO `${spec.name}`")

            for (sql in spec.postCopySql) {
                sqliteDb.execSQL(sql)
            }
        }

        private fun deleteDatabaseFiles(appContext: Context) {
            try {
                val dbFile = appContext.getDatabasePath(DB_NAME)
                SQLiteDatabase.deleteDatabase(dbFile)
                appContext.deleteDatabase(DB_NAME)
            } catch (_: Throwable) {}
        }

        private fun buildDatabaseInstance(appContext: Context): AppDatabase {
            return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                DB_NAME
            )
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration(true)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createAndValidateDatabase(context).also { INSTANCE = it }
            }
        }

        /**
         * Resets the singleton instance (used in unit tests to simulate app restarts across upgrades).
         */
        internal fun resetInstanceForTesting() {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (_: Throwable) {}
                INSTANCE = null
            }
        }

        private fun createAndValidateDatabase(context: Context): AppDatabase {
            val appContext = context.applicationContext

            // Step 1: Pre-flight heal any existing SQLite file on disk before Room opens it
            healDatabaseBeforeRoomOpens(appContext)

            // Step 2: Open via Room and verify schema + read/write health immediately
            try {
                val db = buildDatabaseInstance(appContext)
                db.openHelper.writableDatabase
                return db
            } catch (t: Throwable) {
                Log.e(TAG, "Room open failed after pre-flight heal: ${t.message}. Resetting DB.", t)
                try {
                    INSTANCE?.close()
                } catch (_: Throwable) {}
                INSTANCE = null
            }

            // Step 3: Ultimate fallback — clean recreate so the app never crashes on launch
            deleteDatabaseFiles(appContext)
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
