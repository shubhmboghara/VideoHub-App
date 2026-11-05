package com.videhub.data

import android.content.Context
import android.os.Environment
import android.widget.Toast
import com.videhub.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object DataBackupManager {

    private const val BACKUP_FILE_NAME = "videhub_local_backup.json"
    private const val BACKUP_DIR_NAME = "VideoHub_Backup"

    private fun getBackupFiles(context: Context): List<File> {
        val files = mutableListOf<File>()

        // 1. Private external files dir
        val extDir = context.getExternalFilesDir(null)
        if (extDir != null) {
            files.add(File(extDir, BACKUP_FILE_NAME))
        }

        // 2. Public Download directory (survives app reinstall)
        val publicDownloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), BACKUP_DIR_NAME)
        if (!publicDownloadDir.exists()) {
            try { publicDownloadDir.mkdirs() } catch (_: Exception) {}
        }
        files.add(File(publicDownloadDir, BACKUP_FILE_NAME))

        return files
    }

    suspend fun createBackup(context: Context, silent: Boolean = true): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)

                val rootJson = JSONObject()
                rootJson.put("version", 1)
                rootJson.put("timestamp", System.currentTimeMillis())

                // 1. Subscriptions
                val channels = db.channelDao().getAllSync()
                val channelsArray = JSONArray()
                for (ch in channels) {
                    val obj = JSONObject()
                    obj.put("channelId", ch.channelId)
                    obj.put("name", ch.name)
                    obj.put("thumbnailUrl", ch.thumbnailUrl ?: "")
                    channelsArray.put(obj)
                }
                rootJson.put("channels", channelsArray)

                // 2. Custom Playlists & Videos
                val playlists = db.playlistDao().getAllPlaylistsOnce()
                val allPVideos = db.playlistDao().getAllPlaylistVideosOnce()
                val playlistsArray = JSONArray()
                for (pl in playlists) {
                    val plObj = JSONObject()
                    plObj.put("id", pl.id)
                    plObj.put("name", pl.name)
                    plObj.put("createdAt", pl.createdAt)

                    val pVideos = allPVideos.filter { it.playlistId == pl.id }
                    val pVidArray = JSONArray()
                    for (pv in pVideos) {
                        val vObj = JSONObject()
                        vObj.put("videoId", pv.videoId)
                        vObj.put("title", pv.title)
                        vObj.put("channelName", pv.channelName)
                        vObj.put("thumbnailUrl", pv.thumbnailUrl ?: "")
                        vObj.put("durationText", pv.durationText ?: "")
                        vObj.put("addedAt", pv.addedAt)
                        pVidArray.put(vObj)
                    }
                    plObj.put("videos", pVidArray)
                    playlistsArray.put(plObj)
                }
                rootJson.put("playlists", playlistsArray)

                // 3. History
                val historyList = db.historyDao().getAllHistory().first()
                val historyArray = JSONArray()
                for (h in historyList) {
                    val obj = JSONObject()
                    obj.put("videoId", h.videoId)
                    obj.put("title", h.title)
                    obj.put("channelName", h.channelName)
                    obj.put("thumbnailUrl", h.thumbnailUrl ?: "")
                    obj.put("durationText", h.durationText ?: "")
                    obj.put("timestamp", h.timestamp)
                    historyArray.put(obj)
                }
                rootJson.put("history", historyArray)

                // 4. Liked Videos
                val likedList = db.likedVideoDao().getAll().first()
                val likedArray = JSONArray()
                for (lv in likedList) {
                    val obj = JSONObject()
                    obj.put("videoId", lv.videoId)
                    obj.put("title", lv.title)
                    obj.put("channelName", lv.channelName)
                    obj.put("thumbnailUrl", lv.thumbnailUrl)
                    obj.put("likedAt", lv.likedAt)
                    likedArray.put(obj)
                }
                rootJson.put("liked", likedArray)

                // 5. Watch Later
                val watchLaterList = db.watchLaterDao().getAllOnce()
                val wlArray = JSONArray()
                for (wl in watchLaterList) {
                    val obj = JSONObject()
                    obj.put("videoId", wl.videoId)
                    obj.put("title", wl.title)
                    obj.put("channelName", wl.channelName)
                    obj.put("thumbnailUrl", wl.thumbnailUrl)
                    obj.put("addedAt", wl.addedAt)
                    wlArray.put(obj)
                }
                rootJson.put("watchLater", wlArray)

                // 6. Downloaded Metadata
                val downloads = db.downloadedVideoDao().getAllDownloads().first()
                val dlArray = JSONArray()
                for (d in downloads) {
                    val obj = JSONObject()
                    obj.put("fileName", d.fileName)
                    obj.put("videoId", d.videoId)
                    obj.put("title", d.title)
                    obj.put("thumbnailUrl", d.thumbnailUrl)
                    obj.put("channelName", d.channelName)
                    obj.put("duration", d.duration)
                    obj.put("playlistId", d.playlistId ?: "")
                    obj.put("playlistName", d.playlistName ?: "")
                    obj.put("state", d.state)
                    obj.put("isAudioOnly", d.isAudioOnly)
                    obj.put("bytesDownloaded", d.bytesDownloaded)
                    obj.put("totalBytes", d.totalBytes)
                    dlArray.put(obj)
                }
                rootJson.put("downloads", dlArray)

                // Write to target backup files
                val jsonString = rootJson.toString(2)
                for (backupFile in getBackupFiles(context)) {
                    try {
                        backupFile.parentFile?.mkdirs()
                        backupFile.writeText(jsonString)
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

                val jsonText = targetFile.readText()
                if (jsonText.isBlank()) return@withContext false
                val rootObj = JSONObject(jsonText)
                val db = AppDatabase.getDatabase(context)

                // 1. Channels
                if (rootObj.has("channels")) {
                    val arr = rootObj.getJSONArray("channels")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        db.channelDao().insert(
                            ChannelEntity(
                                channelId = obj.getString("channelId"),
                                name = obj.getString("name"),
                                thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }

                // 2. Playlists
                if (rootObj.has("playlists")) {
                    val arr = rootObj.getJSONArray("playlists")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val name = obj.getString("name")
                        val plId = db.playlistDao().insertPlaylist(
                            PlaylistEntity(name = name, createdAt = obj.optLong("createdAt", System.currentTimeMillis()))
                        ).toInt()

                        if (obj.has("videos")) {
                            val vArr = obj.getJSONArray("videos")
                            for (j in 0 until vArr.length()) {
                                val vObj = vArr.getJSONObject(j)
                                db.playlistDao().insertVideo(
                                    PlaylistVideoEntity(
                                        playlistId = plId,
                                        videoId = vObj.getString("videoId"),
                                        title = vObj.getString("title"),
                                        channelName = vObj.optString("channelName", "Unknown"),
                                        thumbnailUrl = vObj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                                        durationText = vObj.optString("durationText").takeIf { it.isNotBlank() },
                                        addedAt = vObj.optLong("addedAt", System.currentTimeMillis())
                                    )
                                )
                            }
                        }
                    }
                }

                // 3. History
                if (rootObj.has("history")) {
                    val arr = rootObj.getJSONArray("history")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        db.historyDao().insert(
                            HistoryEntity(
                                videoId = obj.getString("videoId"),
                                title = obj.getString("title"),
                                channelName = obj.optString("channelName", "Unknown"),
                                thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                                durationText = obj.optString("durationText").takeIf { it.isNotBlank() },
                                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                }

                // 4. Liked Videos
                if (rootObj.has("liked")) {
                    val arr = rootObj.getJSONArray("liked")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        db.likedVideoDao().insert(
                            LikedVideoEntity(
                                videoId = obj.getString("videoId"),
                                title = obj.getString("title"),
                                channelName = obj.optString("channelName", "Unknown"),
                                thumbnailUrl = obj.optString("thumbnailUrl", ""),
                                likedAt = obj.optLong("likedAt", System.currentTimeMillis())
                            )
                        )
                    }
                }

                // 5. Watch Later
                if (rootObj.has("watchLater")) {
                    val arr = rootObj.getJSONArray("watchLater")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        db.watchLaterDao().insert(
                            WatchLaterEntity(
                                videoId = obj.getString("videoId"),
                                title = obj.getString("title"),
                                channelName = obj.optString("channelName", "Unknown"),
                                thumbnailUrl = obj.optString("thumbnailUrl", ""),
                                addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                            )
                        )
                    }
                }

                // 6. Downloads
                if (rootObj.has("downloads")) {
                    val arr = rootObj.getJSONArray("downloads")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        db.downloadedVideoDao().insertDownload(
                            DownloadedVideoEntity(
                                fileName = obj.getString("fileName"),
                                videoId = obj.getString("videoId"),
                                title = obj.getString("title"),
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

                if (!silent) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Restored subscriptions, playlists & history!", Toast.LENGTH_SHORT).show()
                    }
                }
                true
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
