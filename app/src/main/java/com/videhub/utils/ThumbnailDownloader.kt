package com.videhub.utils

import android.content.Context
import com.videhub.ui.screens.extractYouTubeId
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailDownloader {
    suspend fun downloadThumbnail(context: Context, videoId: String, urlString: String?): String? {
        val safeVideoId = videoId.replace(Regex("[^a-zA-Z0-9\\-_.]"), "_").trim()
        if (safeVideoId.isBlank()) return urlString

        return withContext(Dispatchers.IO) {
            try {
                val thumbnailsDir = File(context.filesDir, "thumbnails")
                if (!thumbnailsDir.exists()) {
                    thumbnailsDir.mkdirs()
                }
                
                val file = File(thumbnailsDir, "${safeVideoId}.jpg")
                if (file.exists() && file.length() > 0L) {
                    return@withContext "file://${file.absolutePath}"
                }

                // Check provided urlString
                var targetUrl: String? = urlString?.trim()
                if (!targetUrl.isNullOrBlank() && (targetUrl.startsWith("file://") || targetUrl.startsWith("/"))) {
                    val cleanPath = targetUrl.removePrefix("file://")
                    val existingFile = File(cleanPath)
                    if (existingFile.exists() && existingFile.isFile && existingFile.length() > 0L) {
                        return@withContext "file://${existingFile.absolutePath}"
                    }
                    // Local file path is dead/invalid, wipe targetUrl to trigger online/YouTube fallback
                    targetUrl = null
                }

                if (targetUrl.isNullOrBlank()) {
                    val ytId = extractYouTubeId(videoId) ?: extractYouTubeId(urlString)
                    targetUrl = if (!ytId.isNullOrBlank()) "https://i.ytimg.com/vi/$ytId/hqdefault.jpg" else null
                }

                if (targetUrl.isNullOrBlank()) return@withContext null

                val httpsUrlString = targetUrl.replace("http://", "https://")
                val url = URL(httpsUrlString)
                val connection = url.openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                connection.getInputStream().use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                
                if (file.exists() && file.length() > 0L) {
                    return@withContext "file://${file.absolutePath}"
                }
                
                urlString
            } catch (e: Exception) {
                // Retry with YouTube high quality default thumbnail URL
                val ytId = extractYouTubeId(videoId) ?: extractYouTubeId(urlString)
                if (!ytId.isNullOrBlank()) {
                    try {
                        val thumbnailsDir = File(context.filesDir, "thumbnails")
                        if (!thumbnailsDir.exists()) thumbnailsDir.mkdirs()
                        val file = File(thumbnailsDir, "${safeVideoId}.jpg")
                        val ytUrl = "https://i.ytimg.com/vi/$ytId/hqdefault.jpg"
                        val conn = URL(ytUrl).openConnection()
                        conn.connectTimeout = 10000
                        conn.readTimeout = 10000
                        conn.getInputStream().use { input ->
                            FileOutputStream(file).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (file.exists() && file.length() > 0L) {
                            return@withContext "file://${file.absolutePath}"
                        }
                    } catch (_: Exception) {}
                }
                urlString
            }
        }
    }
}
