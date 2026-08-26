package com.videhub.audio

import android.util.Log
import com.videhub.ui.components.CaptionLine3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class LyricsData(
    val isSynced: Boolean,
    val lines: List<LyricLine>,
    val plainLyrics: String = "",
    val source: String = "LRCLIB"
)

object LyricsManager {
    private const val TAG = "LyricsManager"
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // In-memory cache
    private val lyricsCache = ConcurrentHashMap<String, LyricsData>()
    private val EMPTY_LYRICS = LyricsData(isSynced = false, lines = emptyList(), plainLyrics = "", source = "EMPTY")

    /**
     * Parses standard LRC timestamped text into ordered LyricLines.
     */
    fun parseLrc(lrcContent: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        if (lrcContent.isBlank()) return result

        val linePattern = Pattern.compile("(?:\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\])+(.*)")
        val tagPattern = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]")

        lrcContent.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") || 
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") || trimmed.startsWith("[length:") ||
                trimmed.startsWith("[offset:")) {
                return@forEach
            }

            val matcher = linePattern.matcher(trimmed)
            if (matcher.matches()) {
                val text = matcher.group(matcher.groupCount())?.trim() ?: ""
                val tagMatcher = tagPattern.matcher(trimmed)
                while (tagMatcher.find()) {
                    val min = tagMatcher.group(1)?.toLongOrNull() ?: 0L
                    val sec = tagMatcher.group(2)?.toLongOrNull() ?: 0L
                    val fracStr = tagMatcher.group(3) ?: "0"
                    val ms = when (fracStr.length) {
                        1 -> fracStr.toLong() * 100
                        2 -> fracStr.toLong() * 10
                        3 -> fracStr.toLong()
                        else -> fracStr.take(3).toLong()
                    }
                    val totalMs = (min * 60 * 1000) + (sec * 1000) + ms
                    result.add(LyricLine(totalMs, text))
                }
            }
        }

        return result.sortedBy { it.timeMs }
    }

    /**
     * Detects if the video is a multi-song compilation, mix, mashup, or jukebox
     * where querying single-track external LRC databases would return incorrect lyrics.
     */
    fun isCompilationOrMedley(title: String, durationSeconds: Long = 0): Boolean {
        val lower = title.lowercase()
        val compilationKeywords = listOf(
            "mashup", "compilation", "jukebox", "top 10", "top 20", "top 50", "top 100",
            "best of", "audio jukebox", "full album", "full songs", "all songs",
            "workout mix", "gym mix", "non stop", "non-stop", "mega mix", "megamix",
            "hits collection", "song collection", "medley", "discography", "soundtrack"
        )
        if (compilationKeywords.any { lower.contains(it) }) return true
        // If duration is longer than 15 minutes (900s), it is virtually always a compilation/mix
        if (durationSeconds > 900) return true
        return false
    }

    /**
     * Cleans YouTube video title and channel name into clean Track Name and Artist Name.
     */
    fun cleanTrackAndArtist(rawTitle: String, rawChannel: String): Pair<String, String> {
        var clean = rawTitle
            .replace(Regex("(?i)\\[.*?(official|video|audio|4k|hd|remix|lyrics|mv|visualizer).*?\\]"), "")
            .replace(Regex("(?i)\\(.*?(official|video|audio|4k|hd|remix|lyrics|mv|visualizer|feat|ft).*?\\)"), "")
            .replace(Regex("(?i)\\|.*$"), "")
            .trim()

        var artist = rawChannel.replace(Regex("(?i)- Topic$"), "").replace(Regex("(?i)VEVO$"), "").trim()
        var track = clean

        // Check if title has "Artist - Track"
        if (clean.contains(" - ")) {
            val parts = clean.split(" - ", limit = 2)
            artist = parts[0].trim()
            track = parts[1].trim()
        } else if (clean.contains(" – ")) {
            val parts = clean.split(" – ", limit = 2)
            artist = parts[0].trim()
            track = parts[1].trim()
        } else if (clean.contains(": ")) {
            val parts = clean.split(": ", limit = 2)
            artist = parts[0].trim()
            track = parts[1].trim()
        }

        // Strip extra punctuation and quotes
        track = track.replace(Regex("^[\"']|[\"']$"), "").trim()
        artist = artist.replace(Regex("^[\"']|[\"']$"), "").trim()

        return Pair(track, artist)
    }

    /**
     * Converts YouTube subtitle/closed-caption tracks into Synced Lyrics.
     */
    fun fromSubtitles(captionLines: List<CaptionLine3>): LyricsData? {
        if (captionLines.isEmpty()) return null

        val filtered = captionLines.mapNotNull { line ->
            val text = line.nativeText.trim()
            // Filter out empty lines or pure sound annotations
            if (text.isBlank() || isPureSoundAnnotation(text)) null
            else LyricLine(timeMs = line.startMillis, text = text)
        }

        if (filtered.isEmpty()) return null
        return LyricsData(
            isSynced = true,
            lines = filtered,
            plainLyrics = filtered.joinToString("\n") { it.text },
            source = "YouTube Captions (CC)"
        )
    }

    private fun isPureSoundAnnotation(text: String): Boolean {
        val lower = text.lowercase().trim()
        val noiseTags = listOf(
            "[music]", "(music)", "♪", "♪♪", "[applause]", "(applause)",
            "[cheering]", "[sound]", "[instrumental]", "---", "***"
        )
        return noiseTags.contains(lower) || (lower.startsWith("[") && lower.endsWith("]") && lower.length < 15)
    }

    /**
     * Extracts either timestamped lyrics or plain lyrics from YouTube description text.
     */
    fun fromDescription(description: String): LyricsData? {
        if (description.isBlank()) return null

        // 1. Try parsing timestamps in description (e.g. "01:23 Verse 1" or "0:15 Song Title")
        val timestampedLines = parseTimestampsFromDescription(description)
        if (timestampedLines.size >= 3) {
            return LyricsData(
                isSynced = true,
                lines = timestampedLines,
                plainLyrics = timestampedLines.joinToString("\n") { it.text },
                source = "Video Description (Timestamped)"
            )
        }

        // 2. Try parsing plain text lyrics block from description
        val plainLyricsText = extractLyricsTextFromDescription(description)
        if (!plainLyricsText.isNullOrBlank()) {
            val lines = plainLyricsText.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .mapIndexed { index, line ->
                    LyricLine(timeMs = index * 3000L, text = line)
                }

            if (lines.size >= 4) {
                return LyricsData(
                    isSynced = false,
                    lines = lines,
                    plainLyrics = plainLyricsText,
                    source = "Video Description"
                )
            }
        }

        return null
    }

    /**
     * Parses timestamped lines in description text (e.g. 01:23 Title / Lyrics line).
     */
    private fun parseTimestampsFromDescription(description: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        val timeRegex = Regex("(?:\\[|\\()?\\b(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})\\b(?:\\]|\\))?[\\s\\-:]*(.*)")

        description.lines().forEach { line ->
            val trimmed = line.trim()
            val match = timeRegex.find(trimmed)
            if (match != null) {
                val hoursStr = match.groups[1]?.value
                val minsStr = match.groups[2]?.value ?: "0"
                val secsStr = match.groups[3]?.value ?: "0"
                val label = match.groups[4]?.value?.trim() ?: ""

                val hours = hoursStr?.toLongOrNull() ?: 0L
                val mins = minsStr.toLongOrNull() ?: 0L
                val secs = secsStr.toLongOrNull() ?: 0L

                val totalMs = (hours * 3600 + mins * 60 + secs) * 1000L
                if (label.isNotBlank() && !label.startsWith("http")) {
                    result.add(LyricLine(timeMs = totalMs, text = label))
                }
            }
        }

        return result.sortedBy { it.timeMs }
    }

    /**
     * Extracts plain text lyrics section from video description.
     */
    private fun extractLyricsTextFromDescription(description: String): String? {
        val lower = description.lowercase()
        val lyricMarkers = listOf(
            "lyrics:", "lyrics :", "[lyrics]", "(lyrics)", "--- lyrics ---",
            "song lyrics:", "full lyrics:", "lyrics below:"
        )

        var startIndex = -1
        var markerLength = 0
        for (marker in lyricMarkers) {
            val idx = lower.indexOf(marker)
            if (idx != -1 && (startIndex == -1 || idx < startIndex)) {
                startIndex = idx
                markerLength = marker.length
            }
        }

        if (startIndex == -1) return null

        val sub = description.substring(startIndex + markerLength)
        val collectedLines = mutableListOf<String>()
        var consecutiveEmpty = 0

        for (rawLine in sub.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                consecutiveEmpty++
                if (consecutiveEmpty >= 3) break
                collectedLines.add("")
            } else {
                consecutiveEmpty = 0
                val lowerLine = line.lowercase()
                // Stop if we hit promotional links, copyright or credits
                if (lowerLine.startsWith("http://") || lowerLine.startsWith("https://") ||
                    lowerLine.startsWith("follow") || lowerLine.startsWith("subscribe") ||
                    lowerLine.startsWith("produced by") || lowerLine.startsWith("copyright") ||
                    lowerLine.startsWith("stream / download")) {
                    break
                }
                collectedLines.add(line)
            }
        }

        while (collectedLines.isNotEmpty() && collectedLines.last().isBlank()) {
            collectedLines.removeAt(collectedLines.lastIndex)
        }

        val result = collectedLines.joinToString("\n").trim()
        return if (result.length > 30) result else null
    }

    /**
     * Main prioritized lyrics resolver:
     * 1. Official YouTube Closed Captions (CC)
     * 2. YouTube Video Description (Timestamped or lyrics text)
     * 3. Cleaned Metadata & LRCLIB API Search (skips if compilation/mix)
     * 4. Plain text fallback
     */
    suspend fun getLyrics(
        title: String,
        channel: String,
        durationSeconds: Long = 0,
        captions: List<CaptionLine3> = emptyList(),
        description: String? = null
    ): LyricsData? = withContext(Dispatchers.IO) {
        val cacheKey = "$title|$channel"
        val cached = lyricsCache[cacheKey]
        if (cached != null) {
            return@withContext if (cached === EMPTY_LYRICS) null else cached
        }

        // 1. PRIORITY 1: Official YouTube Subtitles / Captions (CC)
        if (captions.isNotEmpty()) {
            val ccLyrics = fromSubtitles(captions)
            if (ccLyrics != null && ccLyrics.lines.isNotEmpty()) {
                lyricsCache[cacheKey] = ccLyrics
                return@withContext ccLyrics
            }
        }

        // 2. PRIORITY 2: Video Description (Timestamped or Lyrics block)
        if (!description.isNullOrBlank()) {
            val descLyrics = fromDescription(description)
            if (descLyrics != null && descLyrics.lines.isNotEmpty()) {
                lyricsCache[cacheKey] = descLyrics
                return@withContext descLyrics
            }
        }

        // Check if video is a compilation / medley / mix
        // Avoid single-track LRC searches on multi-song video mixes
        if (isCompilationOrMedley(title, durationSeconds)) {
            Log.d(TAG, "Compilation/Mix detected: Skipping single-track LRC search for $title")
            lyricsCache[cacheKey] = EMPTY_LYRICS
            return@withContext null
        }

        // 3. PRIORITY 3: Cleaned Metadata & LRCLIB Search
        val (cleanTrack, cleanArtist) = cleanTrackAndArtist(title, channel)

        // Try exact match
        var lyrics = fetchLrclibExact(cleanTrack, cleanArtist, durationSeconds)

        // Try search by Artist + Track
        if (lyrics == null) {
            val query = "$cleanArtist $cleanTrack".trim()
            lyrics = fetchLrclibSearch(query, durationSeconds)
        }

        // Try search by Track name only
        if (lyrics == null && cleanTrack.isNotBlank()) {
            lyrics = fetchLrclibSearch(cleanTrack, durationSeconds)
        }

        lyricsCache[cacheKey] = lyrics ?: EMPTY_LYRICS
        return@withContext lyrics
    }

    private fun fetchLrclibExact(track: String, artist: String, durationSec: Long): LyricsData? {
        try {
            val encodedTrack = URLEncoder.encode(track, "UTF-8")
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            var urlStr = "https://lrclib.net/api/get?track_name=$encodedTrack&artist_name=$encodedArtist"
            if (durationSec > 0) {
                urlStr += "&duration=$durationSec"
            }

            val request = Request.Builder()
                .url(urlStr)
                .header("User-Agent", "VideHub/1.0 (Android; Open-Source Music Client)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            return parseLrclibJson(JSONObject(body))
        } catch (e: Exception) {
            Log.d(TAG, "LRCLIB exact lookup failed: ${e.message}")
            return null
        }
    }

    private fun fetchLrclibSearch(query: String, targetDurationSec: Long = 0): LyricsData? {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://lrclib.net/api/search?q=$encodedQuery"

            val request = Request.Builder()
                .url(urlStr)
                .header("User-Agent", "VideHub/1.0 (Android; Open-Source Music Client)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val array = JSONArray(body)
            if (array.length() == 0) return null

            // Prioritize items with syncedLyrics and duration close to target
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val synced = item.optString("syncedLyrics")
                val itemDur = item.optLong("duration", 0L)
                if (synced.isNotBlank()) {
                    if (targetDurationSec <= 0 || itemDur <= 0 || Math.abs(itemDur - targetDurationSec) < 20) {
                        return parseLrclibJson(item)
                    }
                }
            }

            // Fallback to first available result with lyrics
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val parsed = parseLrclibJson(item)
                if (parsed != null) return parsed
            }

            return null
        } catch (e: Exception) {
            Log.d(TAG, "LRCLIB search lookup failed: ${e.message}")
            return null
        }
    }

    private fun parseLrclibJson(json: JSONObject): LyricsData? {
        val syncedLyrics = json.optString("syncedLyrics")
        val plainLyrics = json.optString("plainLyrics")

        if (syncedLyrics.isNotBlank()) {
            val lines = parseLrc(syncedLyrics)
            if (lines.isNotEmpty()) {
                return LyricsData(
                    isSynced = true,
                    lines = lines,
                    plainLyrics = plainLyrics,
                    source = "LRCLIB"
                )
            }
        }

        if (plainLyrics.isNotBlank()) {
            val lines = plainLyrics.lines().filter { it.isNotBlank() }.mapIndexed { index, line ->
                LyricLine(timeMs = index * 3000L, text = line.trim())
            }
            return LyricsData(
                isSynced = false,
                lines = lines,
                plainLyrics = plainLyrics,
                source = "LRCLIB (Plain)"
            )
        }

        return null
    }
}

