package com.videhub.audio

import android.util.Log
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
    private val lyricsCache = ConcurrentHashMap<String, LyricsData?>()

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

    suspend fun getLyrics(
        title: String,
        channel: String,
        durationSeconds: Long = 0
    ): LyricsData? = withContext(Dispatchers.IO) {
        val cacheKey = "$title|$channel"
        if (lyricsCache.containsKey(cacheKey)) {
            return@withContext lyricsCache[cacheKey]
        }

        val (cleanTrack, cleanArtist) = cleanTrackAndArtist(title, channel)

        // Try exact match first
        var lyrics = fetchLrclibExact(cleanTrack, cleanArtist, durationSeconds)
        
        // If not found, try search query
        if (lyrics == null) {
            val query = "$cleanArtist $cleanTrack".trim()
            lyrics = fetchLrclibSearch(query)
        }

        // If still not found, try searching just the cleaned track title
        if (lyrics == null && cleanTrack.isNotBlank()) {
            lyrics = fetchLrclibSearch(cleanTrack)
        }

        lyricsCache[cacheKey] = lyrics
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

    private fun fetchLrclibSearch(query: String): LyricsData? {
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

            // Prioritize items with syncedLyrics
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val synced = item.optString("syncedLyrics")
                if (synced.isNotBlank()) {
                    return parseLrclibJson(item)
                }
            }

            // Fallback to first available result
            return parseLrclibJson(array.getJSONObject(0))
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

    /**
     * Converts subtitle tracks (from NewPipe or local) into Synced Lyrics format.
     */
    fun fromSubtitles(captionLines: List<com.videhub.ui.components.CaptionLine3>): LyricsData? {
        if (captionLines.isEmpty()) return null
        val lines = captionLines.map {
            LyricLine(timeMs = it.startMillis, text = it.nativeText.trim())
        }.filter { it.text.isNotBlank() }

        if (lines.isEmpty()) return null
        return LyricsData(
            isSynced = true,
            lines = lines,
            plainLyrics = lines.joinToString("\n") { it.text },
            source = "Closed Captions"
        )
    }
}
