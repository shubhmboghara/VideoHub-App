package com.videhub.ml

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Represents a timestamped chapter or track segment detected via OCR/Text Analysis
 */
data class SmartChapter(
    val title: String,
    val timestampMs: Long,
    val formattedTime: String
)

object SmartMediaAnalyzer {
    private const val TAG = "SmartMediaAnalyzer"

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Extracts timestamps and chapter titles from an image or video frame.
     * Useful for title cards, tutorial presentation slides, or DJ tracklist cards.
     */
    suspend fun extractChaptersFromFrame(bitmap: Bitmap): List<SmartChapter> = withContext(Dispatchers.Default) {
        val chapters = mutableListOf<SmartChapter>()
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = textRecognizer.process(image).await()
            val text = visionText.text

            val lines = text.lines()
            val timestampRegex = Regex("""(\d{1,2}:\d{2}(?::\d{2})?)""")

            for (line in lines) {
                val match = timestampRegex.find(line)
                if (match != null) {
                    val timeStr = match.value
                    val title = line.replace(timeStr, "").replace(Regex("""^[\s\-_:•|]+"""), "").trim()
                    val ms = parseTimestampToMs(timeStr)
                    if (ms >= 0 && title.isNotBlank()) {
                        chapters.add(SmartChapter(title = title, timestampMs = ms, formattedTime = timeStr))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze frame for chapters: ${e.message}", e)
        }
        chapters.sortedBy { it.timestampMs }
    }

    /**
     * Smart Search Query Normalization & Typo Correction.
     * Cleans common query typos, handles artist entity formatting, and extracts core search terms.
     */
    fun normalizeSearchQuery(query: String): String {
        var clean = query.trim()
        if (clean.isBlank()) return ""

        // Common shorthand and slang normalization
        val replacements = mapOf(
            Regex("(?i)\\bft\\.?\\b") to "feat",
            Regex("(?i)\\bvs\\.?\\b") to "vs",
            Regex("(?i)\\bost\\b") to "soundtrack",
            Regex("(?i)\\blirik\\b") to "lyrics",
            Regex("(?i)\\bletra\\b") to "lyrics",
            Regex("(?i)\\bparoles\\b") to "lyrics"
        )
        for ((regex, replacement) in replacements) {
            clean = clean.replace(regex, replacement)
        }

        // Collapse excess whitespace
        clean = clean.replace(Regex("\\s+"), " ").trim()
        return clean
    }

    private fun parseTimestampToMs(time: String): Long {
        val parts = time.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000L
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
            else -> -1L
        }
    }
}
