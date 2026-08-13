package com.videhub.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailDownloader {
    suspend fun downloadThumbnail(context: Context, videoId: String, urlString: String?): String? {
        if (urlString.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val thumbnailsDir = File(context.filesDir, "thumbnails")
                if (!thumbnailsDir.exists()) {
                    thumbnailsDir.mkdirs()
                }
                
                // Use a safe file name based on videoId to avoid invalid characters
                val safeVideoId = videoId.replace(Regex("[^a-zA-Z0-9\\-_.]"), "_").trim()
                val file = File(thumbnailsDir, "${safeVideoId}.jpg")
                
                if (file.exists()) {
                    return@withContext "file://${file.absolutePath}"
                }

                val httpsUrlString = urlString.replace("http://", "https://")
                val url = URL(httpsUrlString)
                val connection = url.openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                connection.getInputStream().use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                
                android.util.Log.d("ThumbnailDownloader", "Successfully downloaded thumbnail to ${file.absolutePath}")
                "file://${file.absolutePath}"
            } catch (e: Exception) {
                android.util.Log.e("ThumbnailDownloader", "Failed to download thumbnail: ${e.message}")
                e.printStackTrace()
                urlString // Fallback to original url
            }
        }
    }
}
