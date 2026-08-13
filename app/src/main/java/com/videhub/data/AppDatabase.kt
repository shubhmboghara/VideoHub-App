package com.videhub.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.videhub.data.dao.ChannelDao
import com.videhub.data.dao.HistoryDao
import com.videhub.data.dao.LikedVideoDao
import com.videhub.data.dao.PlaylistDao
import com.videhub.data.dao.DownloadedVideoDao
import com.videhub.data.dao.WatchLaterDao
import com.videhub.data.dao.SearchHistoryDao
import com.videhub.data.entity.ChannelEntity
import com.videhub.data.entity.HistoryEntity
import com.videhub.data.entity.LikedVideoEntity
import com.videhub.data.entity.PlaylistEntity
import com.videhub.data.entity.PlaylistVideoEntity
import com.videhub.data.entity.DownloadedVideoEntity
import com.videhub.data.entity.WatchLaterEntity
import com.videhub.data.entity.SearchHistoryEntity
import com.videhub.data.dao.WatchProgressDao
import com.videhub.data.entity.WatchProgressEntity
import com.videhub.data.dao.FeedCacheDao
import com.videhub.data.entity.FeedCacheEntity
import com.videhub.data.entity.SavedLyricsEntity
import com.videhub.data.dao.SavedLyricsDao
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

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
        com.videhub.data.entity.DownloadedPlaylistEntity::class,
        com.videhub.data.entity.DownloadedPlaylistVideoCrossRef::class,
        WatchProgressEntity::class,
        FeedCacheEntity::class,
        com.videhub.data.entity.SearchCacheEntity::class,
        com.videhub.data.entity.VideoMetadataEntity::class
    ],
    version = 23,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun likedVideoDao(): LikedVideoDao
    abstract fun downloadedVideoDao(): DownloadedVideoDao
    abstract fun downloadedPlaylistDao(): com.videhub.data.dao.DownloadedPlaylistDao
    abstract fun watchLaterDao(): WatchLaterDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun savedLyricsDao(): SavedLyricsDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun feedCacheDao(): FeedCacheDao
    abstract fun searchCacheDao(): com.videhub.data.dao.SearchCacheDao
    abstract fun videoMetadataDao(): com.videhub.data.dao.VideoMetadataDao

    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `search_cache` (`id` TEXT NOT NULL, `query` TEXT NOT NULL, `type` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `viewCount` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `saved_lyrics` (`videoId` TEXT NOT NULL, `lyricsJson` TEXT NOT NULL, PRIMARY KEY(`videoId`))")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloaded_videos` ADD COLUMN `lyrics` TEXT")
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
                db.execSQL("DROP TABLE `downloaded_videos`")
                db.execSQL("ALTER TABLE `new_downloaded_videos` RENAME TO `downloaded_videos`")
            }
        }

        
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `video_metadata` (`videoId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `viewCount` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `streamUrl` TEXT, `audioUrl` TEXT, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`))")
            }
        }
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "videhub_database"
                )
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_22_23)
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
