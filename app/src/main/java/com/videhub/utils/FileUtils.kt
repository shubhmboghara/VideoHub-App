package com.videhub.utils

import com.videhub.data.entity.DownloadedVideoEntity

object FileUtils {
    fun isAudioFile(filePath: String): Boolean {
        val path = filePath.lowercase()
        return path.endsWith(".mp3") ||
                path.endsWith(".m4a") ||
                path.endsWith(".aac") ||
                path.endsWith(".opus") ||
                path.endsWith(".ogg") ||
                path.endsWith(".flac") ||
                path.endsWith(".wav") ||
                // WebM audio-only (no video stream) - basic heuristic based on name for now if not checking metadata deeply
                (path.endsWith(".webm") && path.contains("audio", ignoreCase = true)) // Just a heuristic fallback
    }

    fun isVideoFile(filePath: String): Boolean {
        val path = filePath.lowercase()
        return path.endsWith(".mp4") ||
                path.endsWith(".mkv") ||
                path.endsWith(".avi") ||
                path.endsWith(".mov") ||
                path.endsWith(".webm") && !isAudioFile(filePath)
    }

    fun isAudio(downloadedEntity: DownloadedVideoEntity?, filePath: String): Boolean {
        if (downloadedEntity?.isAudioOnly == true) return true
        if (downloadedEntity?.isAudioOnly == false) return false
        return isAudioFile(filePath)
    }
}
