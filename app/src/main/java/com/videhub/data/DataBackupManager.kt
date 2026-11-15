package com.videhub.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.videhub.data.entity.ChannelEntity
import com.videhub.data.entity.DownloadedVideoEntity
import com.videhub.data.entity.HistoryEntity
import com.videhub.data.entity.LikedVideoEntity
import com.videhub.data.entity.PlaylistEntity
import com.videhub.data.entity.PlaylistVideoEntity
import com.videhub.data.entity.SearchHistoryEntity
import com.videhub.data.entity.WatchLaterEntity
import com.videhub.data.entity.WatchProgressEntity
import com.videhub.ui.theme.AppThemeMode
import com.videhub.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupSummary(
    val subscriptionsCount: Int = 0,
    val playlistsCount: Int = 0,
    val playlistVideosCount: Int = 0,
    val historyCount: Int = 0,
    val watchLaterCount: Int = 0,
    val likedVideosCount: Int = 0,
    val searchHistoryCount: Int = 0,
    val watchProgressCount: Int = 0,
    val hasPreferences: Boolean = false,
    val exportedAtMs: Long = 0L,
    val formatVersion: Int = 2
) {
    val totalRecords: Int
        get() = subscriptionsCount + playlistsCount + historyCount + watchLaterCount + likedVideosCount + searchHistoryCount
}

enum class ImportMode {
    MERGE,
    REPLACE
}

data class ExportResult(
    val success: Boolean,
    val summary: BackupSummary = BackupSummary(),
    val errorMessage: String? = null
)

data class ImportResult(
    val success: Boolean,
    val subscriptionsImported: Int = 0,
    val playlistsImported: Int = 0,
    val playlistVideosImported: Int = 0,
    val historyImported: Int = 0,
    val watchLaterImported: Int = 0,
    val likedVideosImported: Int = 0,
    val searchHistoryImported: Int = 0,
    val watchProgressImported: Int = 0,
    val preferencesRestored: Boolean = false,
    val errorMessage: String? = null
) {
    fun buildSummaryMessage(): String {
        val parts = buildList {
            if (subscriptionsImported > 0) add("$subscriptionsImported subscriptions")
            if (playlistsImported > 0) add("$playlistsImported playlists")
            if (historyImported > 0) add("$historyImported history")
            if (watchLaterImported > 0) add("$watchLaterImported watch later")
            if (likedVideosImported > 0) add("$likedVideosImported liked")
        }
        return if (parts.isEmpty()) {
            "Backup imported — your library is already up to date"
        } else {
            "Imported ${parts.joinToString(", ")}"
        }
    }
}

object DataBackupManager {

    private const val BACKUP_FILE_NAME = "videhub_local_backup.json"
    private const val BACKUP_DIR_NAME = "VideoHub_Backup"
    private const val CURRENT_BACKUP_VERSION = 2

    fun defaultExportFileName(nowMs: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(nowMs))
        return "VideoHub_Backup_$stamp.json"
    }

    private fun normalizeChannelKey(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var key = raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removePrefix("m.")
            .trimEnd('/')
            .lowercase()
        if (key.startsWith("youtube.com/channel/")) {
            key = key.removePrefix("youtube.com/channel/")
        } else if (key.startsWith("youtube.com/@")) {
            key = key.removePrefix("youtube.com/")
        }
        return key
    }

    private fun getBackupFiles(context: Context): List<File> {
        val files = mutableListOf<File>()

        val extDir = context.getExternalFilesDir(null)
        if (extDir != null) {
            files.add(File(extDir, BACKUP_FILE_NAME))
        }

        val publicDownloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            BACKUP_DIR_NAME
        )
        if (!publicDownloadDir.exists()) {
            try { publicDownloadDir.mkdirs() } catch (_: Exception) {}
        }
        files.add(File(publicDownloadDir, BACKUP_FILE_NAME))

        return files
    }

    suspend fun getCurrentDataSummary(context: Context): BackupSummary = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val channels = db.channelDao().getAllSync()
        val playlists = db.playlistDao().getAllPlaylistsOnce()
        val playlistVideos = db.playlistDao().getAllPlaylistVideosOnce()
        val history = db.historyDao().getAllHistoryOnce()
        val watchLater = db.watchLaterDao().getAllOnce()
        val liked = db.likedVideoDao().getAllOnce()
        val searchHistory = db.searchHistoryDao().getAllSearchHistory().first()
        val watchProgress = db.watchProgressDao().getAllOnce()

        BackupSummary(
            subscriptionsCount = channels.size,
            playlistsCount = playlists.size,
            playlistVideosCount = playlistVideos.size,
            historyCount = history.size,
            watchLaterCount = watchLater.size,
            likedVideosCount = liked.size,
            searchHistoryCount = searchHistory.size,
            watchProgressCount = watchProgress.size,
            hasPreferences = true,
            exportedAtMs = System.currentTimeMillis(),
            formatVersion = CURRENT_BACKUP_VERSION
        )
    }

    fun inspectBackupJson(jsonText: String): BackupSummary? {
        if (jsonText.isBlank()) return null
        return try {
            val root = JSONObject(jsonText)
            val hasRecognizedKey = listOf(
                "channels",
                "subscriptions",
                "playlists",
                "history",
                "watchLater",
                "watch_later",
                "liked",
                "likedVideos",
                "searchHistory",
                "search_history",
                "watchProgress",
                "preferences"
            ).any { root.has(it) }
            if (!hasRecognizedKey) return null

            val channelsArr = root.optJSONArray("channels") ?: root.optJSONArray("subscriptions")
            val playlistsArr = root.optJSONArray("playlists")
            val historyArr = root.optJSONArray("history")
            val watchLaterArr = root.optJSONArray("watchLater") ?: root.optJSONArray("watch_later")
            val likedArr = root.optJSONArray("liked") ?: root.optJSONArray("likedVideos")
            val searchArr = root.optJSONArray("searchHistory") ?: root.optJSONArray("search_history")
            val progressArr = root.optJSONArray("watchProgress")

            var playlistVideosTotal = 0
            if (playlistsArr != null) {
                for (i in 0 until playlistsArr.length()) {
                    val pl = playlistsArr.optJSONObject(i) ?: continue
                    playlistVideosTotal += pl.optJSONArray("videos")?.length() ?: 0
                }
            }

            BackupSummary(
                subscriptionsCount = channelsArr?.length() ?: 0,
                playlistsCount = playlistsArr?.length() ?: 0,
                playlistVideosCount = playlistVideosTotal,
                historyCount = historyArr?.length() ?: 0,
                watchLaterCount = watchLaterArr?.length() ?: 0,
                likedVideosCount = likedArr?.length() ?: 0,
                searchHistoryCount = searchArr?.length() ?: 0,
                watchProgressCount = progressArr?.length() ?: 0,
                hasPreferences = root.optJSONObject("preferences") != null,
                exportedAtMs = root.optLong("timestamp", 0L),
                formatVersion = root.optInt("version", 1)
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun exportToJsonString(
        context: Context,
        includePreferences: Boolean = true,
        includeDownloadsMetadata: Boolean = false
    ): Pair<String, BackupSummary> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val nowMs = System.currentTimeMillis()
        val rootJson = JSONObject()
        rootJson.put("app", "VideoHub")
        rootJson.put("version", CURRENT_BACKUP_VERSION)
        rootJson.put("timestamp", nowMs)

        // 1. Subscriptions
        val channels = db.channelDao().getAllSync()
        val channelsArray = JSONArray()
        for (ch in channels) {
            channelsArray.put(
                JSONObject().apply {
                    put("channelId", ch.channelId)
                    put("name", ch.name)
                    put("thumbnailUrl", ch.thumbnailUrl ?: "")
                }
            )
        }
        rootJson.put("channels", channelsArray)

        // 2. Playlists & Videos
        val playlists = db.playlistDao().getAllPlaylistsOnce()
        val allPVideos = db.playlistDao().getAllPlaylistVideosOnce()
        val playlistsArray = JSONArray()
        for (pl in playlists) {
            val pVideos = allPVideos.filter { it.playlistId == pl.id }
            val pVidArray = JSONArray()
            for (pv in pVideos) {
                pVidArray.put(
                    JSONObject().apply {
                        put("videoId", pv.videoId)
                        put("title", pv.title)
                        put("channelName", pv.channelName)
                        put("thumbnailUrl", pv.thumbnailUrl ?: "")
                        put("durationText", pv.durationText ?: "")
                        put("addedAt", pv.addedAt)
                        put("viewCount", pv.viewCount)
                        put("uploadDate", pv.uploadDate)
                    }
                )
            }
            playlistsArray.put(
                JSONObject().apply {
                    put("id", pl.id)
                    put("name", pl.name)
                    put("customCoverUrl", pl.customCoverUrl ?: "")
                    put("createdAt", pl.createdAt)
                    put("videos", pVidArray)
                }
            )
        }
        rootJson.put("playlists", playlistsArray)

        // 3. History
        val historyList = db.historyDao().getAllHistoryOnce()
        val historyArray = JSONArray()
        for (h in historyList) {
            historyArray.put(
                JSONObject().apply {
                    put("videoId", h.videoId)
                    put("title", h.title)
                    put("channelName", h.channelName)
                    put("thumbnailUrl", h.thumbnailUrl ?: "")
                    put("durationText", h.durationText ?: "")
                    put("timestamp", h.timestamp)
                    put("viewCount", h.viewCount)
                    put("uploadDate", h.uploadDate)
                }
            )
        }
        rootJson.put("history", historyArray)

        // 4. Liked Videos
        val likedList = db.likedVideoDao().getAllOnce()
        val likedArray = JSONArray()
        for (lv in likedList) {
            likedArray.put(
                JSONObject().apply {
                    put("videoId", lv.videoId)
                    put("title", lv.title)
                    put("channelName", lv.channelName)
                    put("thumbnailUrl", lv.thumbnailUrl)
                    put("likedAt", lv.likedAt)
                    put("viewCount", lv.viewCount)
                    put("uploadDate", lv.uploadDate)
                }
            )
        }
        rootJson.put("liked", likedArray)

        // 5. Watch Later
        val watchLaterList = db.watchLaterDao().getAllOnce()
        val wlArray = JSONArray()
        for (wl in watchLaterList) {
            wlArray.put(
                JSONObject().apply {
                    put("videoId", wl.videoId)
                    put("title", wl.title)
                    put("channelName", wl.channelName)
                    put("thumbnailUrl", wl.thumbnailUrl)
                    put("addedAt", wl.addedAt)
                }
            )
        }
        rootJson.put("watchLater", wlArray)

        // 6. Search History
        val searchHistory = db.searchHistoryDao().getAllSearchHistory().first()
        val searchArray = JSONArray()
        for (sh in searchHistory) {
            searchArray.put(
                JSONObject().apply {
                    put("query", sh.query)
                    put("timestamp", sh.timestamp)
                }
            )
        }
        rootJson.put("searchHistory", searchArray)

        // 7. Watch Progress (resume position on new device)
        val progressList = db.watchProgressDao().getAllOnce()
        val progressArray = JSONArray()
        for (wp in progressList) {
            progressArray.put(
                JSONObject().apply {
                    put("videoId", wp.videoId)
                    put("positionMs", wp.positionMs)
                    put("durationMs", wp.durationMs)
                    put("watchedAt", wp.watchedAt)
                }
            )
        }
        rootJson.put("watchProgress", progressArray)

        // 8. Preferences
        if (includePreferences) {
            val prefsObj = JSONObject().apply {
                put("themeMode", SettingsManager.getThemeMode(context).first())
                put("isAmoledMode", SettingsManager.getIsAmoledMode(context).first())
                put("autoplay", SettingsManager.getAutoplay(context).first())
                put("playbackSpeed", SettingsManager.getPlaybackSpeed(context).first().toDouble())
                put("showCaptions", SettingsManager.getShowCaptions(context).first())
                put("sponsorBlockEnabled", SettingsManager.getSponsorBlockEnabled(context).first())
                put("volumeBoost", SettingsManager.getVolumeBoost(context).first())
                put("loopVideo", SettingsManager.getLoopVideo(context).first())
                put("customTabs", SettingsManager.getCustomTabs(context).first())
                put("contentLanguage", SettingsManager.getContentLanguage(context).first())
                put("contentCountry", SettingsManager.getContentCountry(context).first())
                put("userInterests", JSONArray(SettingsManager.getUserInterests(context).first()))
            }
            rootJson.put("preferences", prefsObj)
        }

        // 9. Downloaded Metadata (same-device local auto-backup only)
        if (includeDownloadsMetadata) {
            val downloads = db.downloadedVideoDao().getAllDownloadsSync()
            val dlArray = JSONArray()
            for (d in downloads) {
                dlArray.put(
                    JSONObject().apply {
                        put("fileName", d.fileName)
                        put("videoId", d.videoId)
                        put("title", d.title)
                        put("thumbnailUrl", d.thumbnailUrl)
                        put("channelName", d.channelName)
                        put("duration", d.duration)
                        put("playlistId", d.playlistId ?: "")
                        put("playlistName", d.playlistName ?: "")
                        put("state", d.state)
                        put("isAudioOnly", d.isAudioOnly)
                        put("bytesDownloaded", d.bytesDownloaded)
                        put("totalBytes", d.totalBytes)
                    }
                )
            }
            rootJson.put("downloads", dlArray)
        }

        val summary = BackupSummary(
            subscriptionsCount = channels.size,
            playlistsCount = playlists.size,
            playlistVideosCount = allPVideos.size,
            historyCount = historyList.size,
            watchLaterCount = watchLaterList.size,
            likedVideosCount = likedList.size,
            searchHistoryCount = searchHistory.size,
            watchProgressCount = progressList.size,
            hasPreferences = includePreferences,
            exportedAtMs = nowMs,
            formatVersion = CURRENT_BACKUP_VERSION
        )
        rootJson.toString(2) to summary
    }

    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        includePreferences: Boolean = true
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val (jsonString, summary) = exportToJsonString(
                context = context,
                includePreferences = includePreferences,
                includeDownloadsMetadata = false
            )
            val stream = try {
                context.contentResolver.openOutputStream(uri, "wt")
            } catch (_: Exception) {
                null
            } ?: context.contentResolver.openOutputStream(uri)
                ?: return@withContext ExportResult(false, errorMessage = "Unable to write to the selected location.")

            stream.use { out ->
                out.write(jsonString.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            ExportResult(success = true, summary = summary)
        } catch (e: Exception) {
            ExportResult(success = false, errorMessage = e.message ?: "Export failed")
        }
    }

    suspend fun createShareableBackupFile(
        context: Context,
        includePreferences: Boolean = true
    ): Pair<File, BackupSummary>? = withContext(Dispatchers.IO) {
        try {
            val (jsonString, summary) = exportToJsonString(
                context = context,
                includePreferences = includePreferences,
                includeDownloadsMetadata = false
            )
            val dir = File(context.filesDir, "backups").apply { mkdirs() }
            dir.listFiles()?.forEach { old ->
                if (old.isFile) old.delete()
            }
            val file = File(dir, defaultExportFileName())
            file.writeText(jsonString, Charsets.UTF_8)
            file to summary
        } catch (_: Exception) {
            null
        }
    }

    suspend fun inspectUri(context: Context, uri: Uri): Pair<String, BackupSummary>? = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: return@withContext null
            val summary = inspectBackupJson(text) ?: return@withContext null
            text to summary
        } catch (_: Exception) {
            null
        }
    }

    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        mode: ImportMode = ImportMode.MERGE,
        restorePreferences: Boolean = true
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: return@withContext ImportResult(false, errorMessage = "Could not open the selected file.")
            importFromJsonString(
                context = context,
                jsonText = text,
                mode = mode,
                restorePreferences = restorePreferences,
                restoreDownloadsMetadata = false
            )
        } catch (e: Exception) {
            ImportResult(false, errorMessage = e.message ?: "Import failed")
        }
    }

    suspend fun importFromJsonString(
        context: Context,
        jsonText: String,
        mode: ImportMode = ImportMode.MERGE,
        restorePreferences: Boolean = true,
        restoreDownloadsMetadata: Boolean = false
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            if (jsonText.isBlank()) {
                return@withContext ImportResult(false, errorMessage = "Backup file is empty.")
            }
            if (inspectBackupJson(jsonText) == null) {
                return@withContext ImportResult(false, errorMessage = "Selected file is not a valid VideoHub backup.")
            }

            val rootObj = JSONObject(jsonText)
            val db = AppDatabase.getDatabase(context)

            if (mode == ImportMode.REPLACE) {
                db.channelDao().clearAll()
                db.playlistDao().clearAllPlaylists()
                db.historyDao().clearAll()
                db.likedVideoDao().clearAll()
                db.watchLaterDao().clearAll()
                db.searchHistoryDao().clearAll()
                db.watchProgressDao().clearAll()
            }

            var subsCount = 0
            var playlistsCount = 0
            var playlistVideosCount = 0
            var historyCount = 0
            var likedCount = 0
            var watchLaterCount = 0
            var searchCount = 0
            var progressCount = 0
            var prefsRestored = false

            // 1. Subscriptions
            val channelsArr = rootObj.optJSONArray("channels") ?: rootObj.optJSONArray("subscriptions")
            if (channelsArr != null) {
                val existingChannels = db.channelDao().getAllSync().toMutableList()
                for (i in 0 until channelsArr.length()) {
                    val obj = channelsArr.optJSONObject(i) ?: continue
                    val channelId = obj.optString("channelId").trim()
                    val name = obj.optString("name").trim()
                    if (channelId.isBlank() && name.isBlank()) continue
                    val effectiveId = channelId.ifBlank { name }
                    val thumb = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() }

                    val incomingKey = normalizeChannelKey(effectiveId)
                    val matchedExisting = existingChannels.firstOrNull { ch ->
                        val existingKey = normalizeChannelKey(ch.channelId)
                        (incomingKey.isNotEmpty() && existingKey == incomingKey) ||
                            ch.channelId == effectiveId ||
                            (name.isNotBlank() && ch.name.equals(name, ignoreCase = true))
                    }

                    if (matchedExisting == null) {
                        val entity = ChannelEntity(
                            channelId = effectiveId,
                            name = name.ifBlank { effectiveId },
                            thumbnailUrl = thumb
                        )
                        db.channelDao().insert(entity)
                        existingChannels.add(entity)
                        subsCount++
                    } else if (matchedExisting.thumbnailUrl.isNullOrBlank() && thumb != null) {
                        val updated = matchedExisting.copy(thumbnailUrl = thumb)
                        db.channelDao().insert(updated)
                        existingChannels.remove(matchedExisting)
                        existingChannels.add(updated)
                    }
                }
            }

            // 2. Playlists & Playlist Videos (deduplicated by playlist name + videoId)
            val playlistsArr = rootObj.optJSONArray("playlists")
            if (playlistsArr != null) {
                for (i in 0 until playlistsArr.length()) {
                    val obj = playlistsArr.optJSONObject(i) ?: continue
                    val name = obj.optString("name").trim()
                    if (name.isBlank()) continue
                    val customCover = obj.optString("customCoverUrl").takeIf { it.isNotBlank() }
                    val createdAt = obj.optLong("createdAt", System.currentTimeMillis())

                    val existingPl = db.playlistDao().getPlaylistByName(name)
                    val plId = if (existingPl != null) {
                        if (existingPl.customCoverUrl.isNullOrBlank() && customCover != null) {
                            db.playlistDao().updatePlaylistDetails(existingPl.id, existingPl.name, customCover)
                        }
                        existingPl.id
                    } else {
                        playlistsCount++
                        db.playlistDao().insertPlaylist(
                            PlaylistEntity(
                                name = name,
                                customCoverUrl = customCover,
                                createdAt = createdAt
                            )
                        ).toInt()
                    }

                    val vArr = obj.optJSONArray("videos")
                    if (vArr != null) {
                        for (j in 0 until vArr.length()) {
                            val vObj = vArr.optJSONObject(j) ?: continue
                            val videoId = vObj.optString("videoId").trim()
                            val vTitle = vObj.optString("title").trim()
                            if (videoId.isBlank()) continue
                            if (!db.playlistDao().isVideoInPlaylist(plId, videoId)) {
                                db.playlistDao().insertVideo(
                                    PlaylistVideoEntity(
                                        playlistId = plId,
                                        videoId = videoId,
                                        title = vTitle.ifBlank { "Video" },
                                        channelName = vObj.optString("channelName", "Unknown"),
                                        thumbnailUrl = vObj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                                        durationText = vObj.optString("durationText").takeIf { it.isNotBlank() },
                                        addedAt = vObj.optLong("addedAt", System.currentTimeMillis()),
                                        viewCount = vObj.optLong("viewCount", -1L),
                                        uploadDate = vObj.optString("uploadDate", "")
                                    )
                                )
                                playlistVideosCount++
                            }
                        }
                    }
                }
            }

            // 3. History
            val historyArr = rootObj.optJSONArray("history")
            if (historyArr != null) {
                val existingHistoryById = db.historyDao().getAllHistoryOnce().associateBy { it.videoId }
                for (i in 0 until historyArr.length()) {
                    val obj = historyArr.optJSONObject(i) ?: continue
                    val videoId = obj.optString("videoId").trim()
                    val title = obj.optString("title").trim()
                    if (videoId.isBlank()) continue
                    val incomingTs = obj.optLong("timestamp", System.currentTimeMillis())
                    val existing = existingHistoryById[videoId]
                    val incomingThumb = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() }
                    val incomingDuration = obj.optString("durationText").takeIf { it.isNotBlank() }
                    val incomingViews = obj.optLong("viewCount", -1L)
                    val incomingUpload = obj.optString("uploadDate", "")

                    if (existing == null || incomingTs > existing.timestamp) {
                        db.historyDao().insert(
                            HistoryEntity(
                                videoId = videoId,
                                title = title.ifBlank { existing?.title ?: "Video" },
                                channelName = obj.optString("channelName", existing?.channelName ?: "Unknown"),
                                thumbnailUrl = incomingThumb ?: existing?.thumbnailUrl,
                                durationText = incomingDuration ?: existing?.durationText,
                                timestamp = incomingTs,
                                viewCount = if (incomingViews >= 0) incomingViews else (existing?.viewCount ?: -1L),
                                uploadDate = incomingUpload.ifBlank { existing?.uploadDate ?: "" }
                            )
                        )
                        if (existing == null) historyCount++
                    }
                }
            }

            // 4. Liked Videos
            val likedArr = rootObj.optJSONArray("liked") ?: rootObj.optJSONArray("likedVideos")
            if (likedArr != null) {
                val existingLikedIds = db.likedVideoDao().getAllOnce().map { it.videoId }.toHashSet()
                for (i in 0 until likedArr.length()) {
                    val obj = likedArr.optJSONObject(i) ?: continue
                    val videoId = obj.optString("videoId").trim()
                    val title = obj.optString("title").trim()
                    if (videoId.isBlank()) continue
                    if (videoId !in existingLikedIds) {
                        db.likedVideoDao().insert(
                            LikedVideoEntity(
                                videoId = videoId,
                                title = title.ifBlank { "Video" },
                                channelName = obj.optString("channelName", "Unknown"),
                                thumbnailUrl = obj.optString("thumbnailUrl", ""),
                                likedAt = obj.optLong("likedAt", System.currentTimeMillis()),
                                viewCount = obj.optLong("viewCount", -1L),
                                uploadDate = obj.optString("uploadDate", "")
                            )
                        )
                        existingLikedIds.add(videoId)
                        likedCount++
                    }
                }
            }

            // 5. Watch Later
            val watchLaterArr = rootObj.optJSONArray("watchLater") ?: rootObj.optJSONArray("watch_later")
            if (watchLaterArr != null) {
                val existingWlIds = db.watchLaterDao().getAllOnce().map { it.videoId }.toHashSet()
                for (i in 0 until watchLaterArr.length()) {
                    val obj = watchLaterArr.optJSONObject(i) ?: continue
                    val videoId = obj.optString("videoId").trim()
                    val title = obj.optString("title").trim()
                    if (videoId.isBlank()) continue
                    if (videoId !in existingWlIds) {
                        db.watchLaterDao().insert(
                            WatchLaterEntity(
                                videoId = videoId,
                                title = title.ifBlank { "Video" },
                                channelName = obj.optString("channelName", "Unknown"),
                                thumbnailUrl = obj.optString("thumbnailUrl", ""),
                                addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                            )
                        )
                        existingWlIds.add(videoId)
                        watchLaterCount++
                    }
                }
            }

            // 6. Search History
            val searchArr = rootObj.optJSONArray("searchHistory") ?: rootObj.optJSONArray("search_history")
            if (searchArr != null) {
                val existingQueries = db.searchHistoryDao().getAllSearchHistory().first()
                    .associateBy { it.query.trim().lowercase() }
                for (i in 0 until searchArr.length()) {
                    val obj = searchArr.optJSONObject(i) ?: continue
                    val query = obj.optString("query").trim()
                    if (query.isBlank()) continue
                    val ts = obj.optLong("timestamp", System.currentTimeMillis())
                    val existing = existingQueries[query.lowercase()]
                    if (existing == null || ts > existing.timestamp) {
                        db.searchHistoryDao().insertSearch(
                            SearchHistoryEntity(
                                query = query,
                                timestamp = ts
                            )
                        )
                        if (existing == null) searchCount++
                    }
                }
            }

            // 7. Watch Progress
            val progressArr = rootObj.optJSONArray("watchProgress")
            if (progressArr != null) {
                val existingProgress = db.watchProgressDao().getAllOnce().associateBy { it.videoId }
                for (i in 0 until progressArr.length()) {
                    val obj = progressArr.optJSONObject(i) ?: continue
                    val videoId = obj.optString("videoId").trim()
                    if (videoId.isBlank()) continue
                    val watchedAt = obj.optLong("watchedAt", 0L)
                    val existing = existingProgress[videoId]
                    if (existing == null || watchedAt > existing.watchedAt) {
                        db.watchProgressDao().save(
                            WatchProgressEntity(
                                videoId = videoId,
                                positionMs = obj.optLong("positionMs", 0L),
                                durationMs = obj.optLong("durationMs", 0L),
                                watchedAt = watchedAt
                            )
                        )
                        if (existing == null) progressCount++
                    }
                }
            }

            // 8. Preferences
            if (restorePreferences) {
                val prefsObj = rootObj.optJSONObject("preferences")
                if (prefsObj != null) {
                    if (prefsObj.has("themeMode")) {
                        val mode = AppThemeMode.fromString(prefsObj.optString("themeMode", "SYSTEM"))
                        ThemeManager.setThemeMode(context, mode)
                    }
                    if (prefsObj.has("isAmoledMode")) {
                        ThemeManager.setAmoledMode(context, prefsObj.optBoolean("isAmoledMode", false))
                    }
                    if (prefsObj.has("autoplay")) {
                        SettingsManager.setAutoplay(context, prefsObj.optBoolean("autoplay", true))
                    }
                    if (prefsObj.has("playbackSpeed")) {
                        val spd = prefsObj.optDouble("playbackSpeed", 1.0).toFloat().coerceIn(0.25f, 3f)
                        SettingsManager.setPlaybackSpeed(context, spd)
                    }
                    if (prefsObj.has("showCaptions")) {
                        SettingsManager.setShowCaptions(context, prefsObj.optBoolean("showCaptions", true))
                    }
                    if (prefsObj.has("sponsorBlockEnabled")) {
                        SettingsManager.setSponsorBlockEnabled(context, prefsObj.optBoolean("sponsorBlockEnabled", true))
                    }
                    if (prefsObj.has("volumeBoost")) {
                        SettingsManager.setVolumeBoost(context, prefsObj.optBoolean("volumeBoost", false))
                    }
                    if (prefsObj.has("loopVideo")) {
                        SettingsManager.setLoopVideo(context, prefsObj.optBoolean("loopVideo", false))
                    }
                    if (prefsObj.has("customTabs")) {
                        val tabs = prefsObj.optString("customTabs")
                        if (tabs.isNotBlank()) SettingsManager.setCustomTabs(context, tabs)
                    }
                    if (prefsObj.has("contentLanguage")) {
                        val lang = prefsObj.optString("contentLanguage")
                        if (lang.isNotBlank()) SettingsManager.setContentLanguage(context, lang)
                    }
                    if (prefsObj.has("contentCountry")) {
                        val country = prefsObj.optString("contentCountry")
                        if (country.isNotBlank()) SettingsManager.setContentCountry(context, country)
                    }
                    val interestsArr = prefsObj.optJSONArray("userInterests")
                    if (interestsArr != null && interestsArr.length() > 0) {
                        val interests = buildList {
                            for (k in 0 until interestsArr.length()) {
                                val item = interestsArr.optString(k).trim()
                                if (item.isNotBlank()) add(item)
                            }
                        }
                        if (interests.isNotEmpty()) {
                            SettingsManager.setUserInterests(context, interests)
                        }
                    }
                    prefsRestored = true
                }
            }

            // 9. Downloads metadata (same-device local auto-backup only)
            if (restoreDownloadsMetadata && rootObj.has("downloads")) {
                val arr = rootObj.getJSONArray("downloads")
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val fileName = obj.optString("fileName")
                    val videoId = obj.optString("videoId")
                    if (fileName.isBlank() || videoId.isBlank()) continue
                    db.downloadedVideoDao().insertDownload(
                        DownloadedVideoEntity(
                            fileName = fileName,
                            videoId = videoId,
                            title = obj.optString("title", "Video"),
                            thumbnailUrl = obj.optString("thumbnailUrl", ""),
                            channelName = obj.optString("channelName", "Unknown"),
                            duration = obj.optString("duration", ""),
                            playlistId = obj.optString("playlistId").takeIf { it.isNotBlank() },
                            playlistName = obj.optString("playlistName").takeIf { it.isNotBlank() },
                            state = obj.optString("state", "DONE"),
                            isAudioOnly = obj.optBoolean("isAudioOnly", false),
                            bytesDownloaded = obj.optLong("bytesDownloaded", 0L),
                            totalBytes = obj.optLong("totalBytes", 0L)
                        )
                    )
                }
            }

            ImportResult(
                success = true,
                subscriptionsImported = subsCount,
                playlistsImported = playlistsCount,
                playlistVideosImported = playlistVideosCount,
                historyImported = historyCount,
                watchLaterImported = watchLaterCount,
                likedVideosImported = likedCount,
                searchHistoryImported = searchCount,
                watchProgressImported = progressCount,
                preferencesRestored = prefsRestored
            )
        } catch (e: Exception) {
            ImportResult(false, errorMessage = e.message ?: "Import failed")
        }
    }

    suspend fun createBackup(context: Context, silent: Boolean = true): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val (jsonString, _) = exportToJsonString(
                    context = context,
                    includePreferences = true,
                    includeDownloadsMetadata = true
                )
                for (backupFile in getBackupFiles(context)) {
                    try {
                        backupFile.parentFile?.mkdirs()
                        backupFile.writeText(jsonString, Charsets.UTF_8)
                    } catch (_: Exception) {}
                }

                if (!silent) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup created successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
                true
            } catch (e: Exception) {
                if (!silent) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                false
            }
        }
    }

    suspend fun restoreBackup(context: Context, silent: Boolean = false): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                var targetFile: File? = null
                for (file in getBackupFiles(context)) {
                    if (file.exists() && file.length() > 0L) {
                        targetFile = file
                        break
                    }
                }

                if (targetFile == null) {
                    if (!silent) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "No backup file found to restore.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@withContext false
                }

                val jsonText = targetFile.readText(Charsets.UTF_8)
                val result = importFromJsonString(
                    context = context,
                    jsonText = jsonText,
                    mode = ImportMode.MERGE,
                    restorePreferences = false,
                    restoreDownloadsMetadata = true
                )

                if (!silent) {
                    withContext(Dispatchers.Main) {
                        val msg = if (result.success) {
                            result.buildSummaryMessage()
                        } else {
                            "Restore error: ${result.errorMessage ?: "Unknown error"}"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                result.success
            } catch (e: Exception) {
                if (!silent) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Restore error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                false
            }
        }
    }
}
