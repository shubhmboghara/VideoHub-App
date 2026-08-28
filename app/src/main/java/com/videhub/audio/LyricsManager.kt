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
import kotlin.math.abs
import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.tasks.await

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

data class VideoChapter(val timeMs: Long, val title: String, val artist: String = "")

object LyricsManager {
    private const val TAG = "LyricsManager"
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val languageIdentifier by lazy { LanguageIdentification.getClient() }

    fun extractChapters(description: String): List<VideoChapter> {
        if (description.isBlank()) return emptyList()
        val timestamps = parseTimestampsFromDescription(description)
        if (timestamps.size < 2) return emptyList()
        
        val maxTime = timestamps.last().timeMs
        val minTime = timestamps.first().timeMs
        val avgGap = (maxTime - minTime) / (timestamps.size - 1).coerceAtLeast(1)
        
        if (avgGap < 30_000L) {
            return emptyList()
        }
        
        return timestamps.map { line ->
            val text = line.text
            var title = text
            var artist = ""
            
            val split = text.split("-", "|", "~", limit = 2)
            if (split.size == 2) {
                artist = split[0].trim()
                title = split[1].trim()
            } else {
                val bySplit = text.split(" by ", ignoreCase = true)
                if (bySplit.size == 2) {
                    title = bySplit[0].trim()
                    artist = bySplit[1].trim()
                }
            }
            VideoChapter(line.timeMs, title, artist)
        }
    }

    private val lyricsCache = ConcurrentHashMap<String, LyricsData>()
    private val EMPTY_LYRICS = LyricsData(isSynced = false, lines = emptyList(), plainLyrics = "", source = "EMPTY")

    fun parseLrc(lrcContent: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        if (lrcContent.isBlank()) return result

        val linePattern = Pattern.compile("(?:\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\])+(.*)")
        
        lrcContent.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") || 
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") || trimmed.startsWith("[length:") ||
                trimmed.startsWith("[offset:")) {
                return@forEach
            }

            val matcher = linePattern.matcher(trimmed)
            if (matcher.matches()) {
                val mm = matcher.group(1)?.toLong() ?: 0L
                val ss = matcher.group(2)?.toLong() ?: 0L
                val msStr = matcher.group(3) ?: "0"
                val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                
                val timeMs = (mm * 60 + ss) * 1000 + ms
                val text = matcher.group(matcher.groupCount())?.trim() ?: ""
                
                if (text.isNotBlank() && !isPureSoundAnnotation(text)) {
                    result.add(LyricLine(timeMs, text))
                }
            }
        }
        return result.sortedBy { it.timeMs }
    }

    fun fromSubtitles(captionLines: List<CaptionLine3>): LyricsData? {
        if (captionLines.isEmpty()) return null

        val filtered = captionLines.mapNotNull { line ->
            val text = line.nativeText.trim()
            if (text.isBlank() || isPureSoundAnnotation(text)) null
            else LyricLine(timeMs = line.startMillis, text = text)
        }

        if (filtered.size < 2) return null
        return LyricsData(
            isSynced = true,
            lines = filtered,
            plainLyrics = filtered.joinToString("\n") { it.text },
            source = "YouTube Captions (CC)"
        )
    }

    fun isPureSoundAnnotation(text: String): Boolean {
        val lower = text.lowercase().trim()
        val noiseTags = listOf(
            "[music]", "(music)", "♪", "♪♪", "[applause]", "(applause)",
            "[cheering]", "[sound]", "[instrumental]", "---", "***", "[laughter]",
            "[lyrics]", "(lyrics)", "lyric", "lyrics", "[intro]", "[verse]", "[chorus]", "[bridge]"
        )
        if (lower.length == 1 && !lower[0].isLetterOrDigit()) return true
        return noiseTags.contains(lower) || (lower.startsWith("[") && lower.endsWith("]") && lower.length < 16)
    }

    private suspend fun scoreBlock(text: String): Int {
        var score = 0
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return 0

        score += lines.size * 2
        val lengths = lines.map { it.length }
        if (lengths.isNotEmpty()) {
            val avgLen = lengths.average()
            val variance = lengths.map { (it - avgLen) * (it - avgLen) }.average()
            if (variance < 200) score += 20
        }

        try {
            val lang = languageIdentifier.identifyLanguage(text).await()
            if (lang != "und" && lang.isNotEmpty()) {
                score += 15
            }
        } catch (e: Exception) { }

        val uniqueLines = lines.toSet().size
        if (uniqueLines < lines.size) {
            score += (lines.size - uniqueLines) * 5
        }

        if (text.contains("http") || text.contains("www.") || text.contains("@")) {
            score -= 50
        }

        return score
    }

    suspend fun fromDescription(description: String, durationSeconds: Long = 0): LyricsData? {
        if (description.isBlank()) return null

        val chapters = extractChapters(description)
        if (chapters.isEmpty()) {
            val timestampedLines = parseTimestampsFromDescription(description)
            if (timestampedLines.size >= 3) {
                return LyricsData(
                    isSynced = true,
                    lines = timestampedLines,
                    plainLyrics = timestampedLines.joinToString("\n") { it.text },
                    source = "Video Description (Timestamped)"
                )
            }
        }

        val plainLyricsText = extractLyricsTextFromDescription(description)
        if (!plainLyricsText.isNullOrBlank()) {
            val rawLines = plainLyricsText.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !isPureSoundAnnotation(it) }

            if (rawLines.size >= 2) {
                val effectiveDuration = if (durationSeconds > 10) durationSeconds else (rawLines.size * 4L).coerceAtLeast(60L)
                val linePacingMs = ((effectiveDuration * 1000L) / (rawLines.size + 1)).coerceIn(2000L, 6000L)

                val lines = rawLines.mapIndexed { index, line ->
                    LyricLine(timeMs = index * linePacingMs, text = line)
                }

                return LyricsData(
                    isSynced = true,
                    lines = lines,
                    plainLyrics = plainLyricsText,
                    source = "Video Description"
                )
            }
        }

        return null
    }

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
                if (label.isNotBlank() && !label.startsWith("http") && !isPureSoundAnnotation(label)) {
                    result.add(LyricLine(timeMs = totalMs, text = label))
                }
            }
        }

        return result.sortedBy { it.timeMs }
    }

    private suspend fun extractLyricsTextFromDescription(description: String): String? {
        if (description.isBlank()) return null
        val lower = description.lowercase()

        val lyricMarkers = listOf(
            "lyrics:", "lyrics :", "[lyrics]", "(lyrics)", "--- lyrics ---", "=== lyrics ===",
            "song lyrics:", "full lyrics:", "lyrics below:", "lyrics in description", "read lyrics:",
            "lyrics -", "lyrics ♫", "\nlyrics\n", "lyrics\n", "lyrics : \n", "lyrics:\n",
            "lyrics by", "written by:", "track lyrics:", "lyrics", "song info:", "song details:",
            "letra:", "letras:", "letra da música:", "letra de la canción:", "\nletras\n", "letras\n",
            "letra :", "letras :", "letra", "letras de canciones:",
            "paroles:", "paroles :", "paroles de la chanson:", "paroles : \n", "paroles",
            "testo:", "testo :", "testo della canzone:", "testo",
            "songtext:", "songtext :", "songtexte:", "songtexte :", "songtext",
            "lirik:", "lirik :", "lirik lagu:", "mga liriko:", "lirik",
            "текст песни:", "слова песни:", "текст:", "текст :", "текст песни",
            "बोल:", "गीत:", "গান:", "गीत के बोल:", "लिरिक:", "लिरिक्स:", "লিরিক্স:", "गीत", "लय", "धुन", "mukhda:", "antara:",
            "歌詞:", "歌詞 :", "가사:", "가사 :", "歌词:", "歌词 :", "歌詞", "가사"
        )

        val blocks = mutableListOf<String>()

        for (marker in lyricMarkers) {
            var idx = lower.indexOf(marker)
            while (idx != -1) {
                val isWordBoundary = idx == 0 || lower[idx - 1].isWhitespace() || lower[idx - 1] == '\n' || lower[idx - 1] == '[' || lower[idx - 1] == '(' || lower[idx - 1] == '-'
                if (isWordBoundary) {
                    val sub = description.substring(idx + marker.length)
                    val block = extractLyricsBlock(sub)
                    if (block.isNotBlank()) blocks.add(block)
                }
                idx = lower.indexOf(marker, idx + 1)
            }
        }

        val structureMarkers = listOf(
            "[chorus]", "chorus:", "chorus\n",
            "[verse 1]", "verse 1:", "verse 1\n",
            "[verse]", "verse:", "verse\n",
            "[hook]", "hook:", "hook\n",
            "[refrain]", "refrain:",
            "mukhda:", "antara:"
        )
        for (marker in structureMarkers) {
            var idx = lower.indexOf(marker)
            while (idx != -1) {
                val block = extractLyricsBlock(description.substring(idx))
                if (block.isNotBlank()) blocks.add(block)
                idx = lower.indexOf(marker, idx + 1)
            }
        }

        val heuristicBlocks = mutableListOf<String>()
        val lines = description.lines()
        var currentBlock = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            val len = trimmed.length
            val isProse = trimmed.contains(". ") && trimmed.split(". ").size > 3
            val isUrl = trimmed.contains("http") || trimmed.contains("www.") || trimmed.contains("bit.ly")
            
            if (len in 4..100 && !isProse && !isUrl) {
                currentBlock.add(trimmed)
            } else {
                if (currentBlock.count { it.isNotBlank() } >= 4) {
                    heuristicBlocks.add(currentBlock.joinToString("\n").trim())
                }
                currentBlock.clear()
            }
        }
        if (currentBlock.count { it.isNotBlank() } >= 4) {
            heuristicBlocks.add(currentBlock.joinToString("\n").trim())
        }

        val allCandidates = (blocks + heuristicBlocks).distinct()
        val best = allCandidates.map { it to scoreBlock(it) }
            .maxByOrNull { it.second }

        return if (best != null && best.second > 15) best.first else null
    }

    private fun extractLyricsBlock(sub: String): String {
        val collectedLines = mutableListOf<String>()
        var consecutiveEmpty = 0
        var lineCount = 0

        for (rawLine in sub.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                consecutiveEmpty++
                if (consecutiveEmpty >= 4) break 
                if (collectedLines.isNotEmpty()) {
                    collectedLines.add("")
                }
            } else {
                consecutiveEmpty = 0
                val lowerLine = line.lowercase()
                
                val isPromotional = lowerLine.startsWith("http://") || lowerLine.startsWith("https://") ||
                    lowerLine.startsWith("www.") || lowerLine.startsWith("follow ") || lowerLine.startsWith("follow:") ||
                    lowerLine.startsWith("subscribe") || lowerLine.startsWith("copyright") || 
                    lowerLine.startsWith("all rights reserved") ||
                    lowerLine.startsWith("(c)") || lowerLine.startsWith("(p)") || lowerLine.startsWith("©") || lowerLine.startsWith("℗") ||
                    lowerLine.startsWith("stream / download") || lowerLine.startsWith("connect with") ||
                    lowerLine.startsWith("listen on") || lowerLine.startsWith("buy on") ||
                    lowerLine.startsWith("socials:") || lowerLine.startsWith("social media:") ||
                    lowerLine.startsWith("official merchandise:") || lowerLine.startsWith("merch:") 

                if (isPromotional && lineCount > 2) break
                if (lineCount > 5 && (lowerLine.startsWith("audio credits") || lowerLine.startsWith("video credits") || lowerLine.startsWith("cast:"))) break

                collectedLines.add(line)
                lineCount++
            }
        }

        while (collectedLines.isNotEmpty() && collectedLines.last().isBlank()) {
            collectedLines.removeAt(collectedLines.lastIndex)
        }
        while (collectedLines.isNotEmpty() && collectedLines.first().isBlank()) {
            collectedLines.removeAt(0)
        }

        return collectedLines.joinToString("\n").trim()
    }

    private fun calculateWordSimilarity(s1: String, s2: String): Double {
        val words1 = s1.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").split("\\s+".toRegex()).filter { it.isNotBlank() && it.length > 1 }.toSet()
        val words2 = s2.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").split("\\s+".toRegex()).filter { it.isNotBlank() && it.length > 1 }.toSet()
        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        return intersection.toDouble() / union.toDouble()
    }

    private fun isLrclibMatchValid(
        candidateTrack: String,
        candidateArtist: String,
        candidateDurationSec: Long,
        targetTrack: String,
        targetArtist: String,
        targetDurationSec: Long
    ): Boolean {
        if (targetDurationSec > 25 && candidateDurationSec > 0) {
            val diff = abs(targetDurationSec - candidateDurationSec)
            if (diff > 18) return false
        }

        val trackSim = calculateWordSimilarity(candidateTrack, targetTrack)
        val cleanCand = candidateTrack.lowercase().replace(Regex("[^a-z0-9]"), "")
        val cleanTarget = targetTrack.lowercase().replace(Regex("[^a-z0-9]"), "")
        val directSubstring = cleanCand.isNotEmpty() && cleanTarget.isNotEmpty() &&
                (cleanCand.contains(cleanTarget) || cleanTarget.contains(cleanCand))

        if (trackSim < 0.45 && !directSubstring) return false

        val cleanTargetArt = targetArtist.lowercase().replace(Regex("[^a-z0-9]"), "")
        val genericArtists = listOf("variousartists", "vevo", "topic", "records", "music", "channel", "")
        if (cleanTargetArt.isNotEmpty() && !genericArtists.contains(cleanTargetArt)) {
            val artistSim = calculateWordSimilarity(candidateArtist, targetArtist)
            val cleanCandArt = candidateArtist.lowercase().replace(Regex("[^a-z0-9]"), "")
            val artSubstring = cleanCandArt.isNotEmpty() && (cleanCandArt.contains(cleanTargetArt) || cleanTargetArt.contains(cleanCandArt))
            if (artistSim < 0.25 && !artSubstring) return false
        }

        return true
    }

    suspend fun getLyrics(
        title: String,
        channel: String,
        durationSeconds: Long = 0,
        captions: List<CaptionLine3> = emptyList(),
        description: String? = null
    ): LyricsData? = withContext(Dispatchers.IO) {
        val cacheKey = "$title|$channel|$durationSeconds"

        if (captions.isNotEmpty()) {
            val ccLyrics = fromSubtitles(captions)
            if (ccLyrics != null && ccLyrics.lines.isNotEmpty()) {
                lyricsCache[cacheKey] = ccLyrics
                return@withContext ccLyrics
            }
        }

        if (!description.isNullOrBlank()) {
            val descLyrics = fromDescription(description, durationSeconds)
            if (descLyrics != null && descLyrics.lines.isNotEmpty()) {
                lyricsCache[cacheKey] = descLyrics
                return@withContext descLyrics
            }
        }

        val cached = lyricsCache[cacheKey]
        if (cached != null) {
            return@withContext if (cached === EMPTY_LYRICS) null else cached
        }

        val (cleanTrackOriginal, cleanArtistOriginal) = cleanTrackAndArtist(title, channel)
        var lrclibLyrics = fetchLrclibExact(cleanTrackOriginal, cleanArtistOriginal, durationSeconds)

        if (lrclibLyrics == null) {
            val query = "$cleanArtistOriginal $cleanTrackOriginal".trim()
            lrclibLyrics = fetchLrclibSearch(query, cleanTrackOriginal, cleanArtistOriginal, durationSeconds)
        }

        if (lrclibLyrics == null && cleanTrackOriginal.isNotBlank()) {
            lrclibLyrics = fetchLrclibSearch(cleanTrackOriginal, cleanTrackOriginal, cleanArtistOriginal, durationSeconds)
        }

        if (lrclibLyrics != null && lrclibLyrics.isSynced) {
            lyricsCache[cacheKey] = lrclibLyrics
            return@withContext lrclibLyrics
        }

        if (lrclibLyrics != null && lrclibLyrics.lines.isNotEmpty()) {
            lyricsCache[cacheKey] = lrclibLyrics
            return@withContext lrclibLyrics
        }

        lyricsCache[cacheKey] = EMPTY_LYRICS
        return@withContext null
    }

    private fun cleanTrackAndArtist(title: String, channel: String): Pair<String, String> {
        var cleanTitle = title
            .replace(Regex("(?i)\\[.*?\\]"), "")
            .replace(Regex("(?i)\\(.*?\\)"), "")
            .replace(Regex("(?i)official (video|audio|lyrics|music video)"), "")
            .replace(Regex("(?i)ft\\.|feat\\..*"), "")
            .trim()
        
        var cleanArtist = channel
            .replace(Regex("(?i) - Topic"), "")
            .replace(Regex("(?i)VEVO"), "")
            .trim()
            
        return Pair(cleanTitle, cleanArtist)
    }

    private fun fetchLrclibExact(track: String, artist: String, durationSec: Long): LyricsData? {
        try {
            if (track.isBlank()) return null
            val encodedTrack = URLEncoder.encode(track, "UTF-8")
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            var urlStr = "https://lrclib.net/api/get?track_name=$encodedTrack&artist_name=$encodedArtist"
            if (durationSec > 0) urlStr += "&duration=$durationSec"

            val request = Request.Builder()
                .url(urlStr)
                .header("User-Agent", "VideHub/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            
            if (!isLrclibMatchValid(json.optString("trackName"), json.optString("artistName"), json.optLong("duration"), track, artist, durationSec)) return null

            return parseLrclibJson(json)
        } catch (e: Exception) { return null }
    }

    private fun fetchLrclibSearch(query: String, targetTrack: String, targetArtist: String, targetDurationSec: Long = 0): LyricsData? {
        try {
            if (query.isBlank()) return null
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://lrclib.net/api/search?q=$encodedQuery"

            val request = Request.Builder().url(urlStr).header("User-Agent", "VideHub/1.0").build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val array = JSONArray(body)
            
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                if (item.optString("syncedLyrics").isNotBlank() && isLrclibMatchValid(item.optString("trackName"), item.optString("artistName"), item.optLong("duration"), targetTrack, targetArtist, targetDurationSec)) {
                    return parseLrclibJson(item)
                }
            }
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                if (isLrclibMatchValid(item.optString("trackName"), item.optString("artistName"), item.optLong("duration"), targetTrack, targetArtist, targetDurationSec)) {
                    return parseLrclibJson(item)
                }
            }
            return null
        } catch (e: Exception) { return null }
    }

    private fun parseLrclibJson(json: JSONObject): LyricsData? {
        val syncedLyrics = json.optString("syncedLyrics")
        val plainLyrics = json.optString("plainLyrics")

        if (syncedLyrics.isNotBlank() && syncedLyrics != "null") {
            val lines = parseLrc(syncedLyrics)
            if (lines.size >= 2) return LyricsData(true, lines, plainLyrics, "LRCLIB")
        }

        if (plainLyrics.isNotBlank() && plainLyrics != "null") {
            val cleanLines = plainLyrics.lines().map { it.trim() }.filter { it.isNotBlank() && !isPureSoundAnnotation(it) }
            if (cleanLines.size >= 4) {
                val lines = cleanLines.mapIndexed { index, line -> LyricLine(index * 3000L, line) }
                return LyricsData(false, lines, plainLyrics, "LRCLIB (Plain)")
            }
        }
        return null
    }
}
