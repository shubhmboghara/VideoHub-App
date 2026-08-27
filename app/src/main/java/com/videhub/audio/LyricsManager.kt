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

    /**
     * Extracts chapters (tracklist) from a video description if it appears to be a compilation video.
     */
    fun extractChapters(description: String): List<VideoChapter> {
        if (description.isBlank()) return emptyList()
        val timestamps = parseTimestampsFromDescription(description)
        if (timestamps.size < 2) return emptyList()
        
        // Compilations usually have average track length > 30 seconds (30000 ms)
        val maxTime = timestamps.last().timeMs
        val minTime = timestamps.first().timeMs
        val avgGap = (maxTime - minTime) / (timestamps.size - 1).coerceAtLeast(1)
        
        if (avgGap < 30_000L) {
            // Gap is too small, likely timestamped lyrics, not a compilation tracklist
            return emptyList()
        }
        
        return timestamps.map { line ->
            val text = line.text
            var title = text
            var artist = ""
            
            // Common formats: "Artist - Title", "Title - Artist", "Artist - Title (Audio)"
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


    // In-memory cache to prevent duplicate requests
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
                    if (text.isNotBlank() && !isPureSoundAnnotation(text)) {
                        result.add(LyricLine(totalMs, text))
                    }
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
            "hits collection", "song collection", "medley", "discography", "soundtrack",
            "dj mix", "party mix", "continuous mix", "hour mix", "chill mix", "lofi mix",
            "1 hour", "2 hours", "3 hours", "10 hours", "1 hr", "2 hrs"
        )
        if (compilationKeywords.any { lower.contains(it) }) return true
        // If duration is longer than 12 minutes (720s), it is virtually always a compilation/mix/video
        if (durationSeconds > 720) return true
        return false
    }

    /**
     * Cleans YouTube video title and channel name into clean Track Name and Artist Name.
     */
    fun cleanTrackAndArtist(rawTitle: String, rawChannel: String): Pair<String, String> {
        var clean = rawTitle
            .replace(Regex("(?i)\\[.*?(official|video|audio|4k|hd|remix|lyrics|mv|visualizer|feat|ft|prod).*?\\]"), "")
            .replace(Regex("(?i)\\((official|video|audio|4k|hd|remix|lyrics|mv|visualizer|feat|ft|prod).*?\\)"), "")
            .replace(Regex("(?i)\\(lyrics?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?i)\\(official.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?i)\\[official.*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?i)\\|.*$"), "")
            .replace(Regex("(?i)#\\w+"), "") // strip hashtags
            .trim()

        var artist = rawChannel
            .replace(Regex("(?i)\\s*-\\s*topic$"), "")
            .replace(Regex("(?i)vevo$"), "")
            .replace(Regex("(?i)official$"), "")
            .trim()
            
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
        } else if (clean.contains(" — ")) {
            val parts = clean.split(" — ", limit = 2)
            artist = parts[0].trim()
            track = parts[1].trim()
        } else if (clean.contains(": ")) {
            val parts = clean.split(": ", limit = 2)
            artist = parts[0].trim()
            track = parts[1].trim()
        }

        // Clean up remaining brackets, quotes, and punctuation
        track = track
            .replace(Regex("(?i)\\b(official music video|official video|official audio|music video|lyric video|lyrics|audio|mv)\\b"), "")
            .replace(Regex("^[\"']|[\"']$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        artist = artist
            .replace(Regex("^[\"']|[\"']$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        return Pair(track, artist)
    }

    /**
     * Extracts Song and Artist metadata from description if present.
     * Parses YouTube "Provided to YouTube by", "Music in this video", publisher copyright, and artist credits.
     */
    fun extractMetadataFromDescription(description: String): Pair<String, String>? {
        if (description.isBlank()) return null
        
        var song: String? = null
        var artist: String? = null
        
        val lines = description.lines().map { it.trim() }.filter { it.isNotBlank() }
        
        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase()
            
            // 1. Standard key-value pairs
            if (lower.startsWith("song:") || lower.startsWith("track:") || lower.startsWith("title:") || lower.startsWith("music:") || lower.startsWith("canción:") || lower.startsWith("chanson:")) {
                val value = line.substring(line.indexOf(":") + 1).trim()
                if (value.isNotBlank() && song == null) song = value
            }
            if (lower.startsWith("artist:") || lower.startsWith("singer:") || lower.startsWith("band:") || lower.startsWith("creator:") || lower.startsWith("artista:") || lower.startsWith("artiste:")) {
                val value = line.substring(line.indexOf(":") + 1).trim()
                if (value.isNotBlank() && artist == null) artist = value
            }
            
            // 2. YouTube Auto-generated "Provided to YouTube by" format
            if (lower.contains("provided to youtube by")) {
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1]
                    if (nextLine.contains("·") || nextLine.contains("-") || nextLine.contains("•")) {
                        val parts = nextLine.split("·", "-", "•").map { it.trim() }
                        if (parts.size >= 2) {
                            if (song == null) song = parts[0]
                            if (artist == null) artist = parts[1]
                        }
                    } else {
                        if (song == null) song = nextLine
                        if (artist == null && i + 2 < lines.size) {
                            val candidateArtist = lines[i + 2]
                            if (!candidateArtist.lowercase().contains("℗") && !candidateArtist.lowercase().contains("released on")) {
                                artist = candidateArtist
                            }
                        }
                    }
                }
            }
            
            // 3. "Music in this video" block / Publisher credits
            if (lower == "music in this video" || lower == "song" || lower.startsWith("music in this video:")) {
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1]
                    if (!nextLine.lowercase().startsWith("learn more") && !nextLine.lowercase().startsWith("listen") && !nextLine.lowercase().startsWith("https://")) {
                        if (song == null) song = nextLine
                    }
                }
            }

            // 4. "Performed by", "Composed by", "Vocals:"
            if (lower.startsWith("performed by:") || lower.startsWith("performed by") || lower.startsWith("vocals by:") || lower.startsWith("singer -")) {
                val parts = line.split(":", "-").map { it.trim() }
                if (parts.size >= 2 && artist == null) {
                    artist = parts[1]
                }
            }
            
            // 5. Common text patterns "Title - Artist" or "Title by Artist" when preceded by keywords like 'now playing'
            if (lower.startsWith("now playing:") || lower.startsWith("playing:")) {
                val value = line.substring(line.indexOf(":") + 1).trim()
                val parts = value.split("-", " by ").map { it.trim() }
                if (parts.size >= 2) {
                    if (song == null) song = parts[0]
                    if (artist == null) artist = parts[1]
                } else {
                    if (song == null) song = value
                }
            }
            
            // 6. Look for quotes: "Song Title" by Artist
            val quoteMatch = Regex("\"([^\"]+)\"\\s+by\\s+(.+)").find(line)
            if (quoteMatch != null) {
                if (song == null) song = quoteMatch.groupValues[1].trim()
                if (artist == null) artist = quoteMatch.groupValues[2].trim()
            }
        }
        
        // Clean up common emojis and artifacts
        song = song?.replace(Regex("[🎵🎶🎧]"), "")?.replace(Regex("^[\"']|[\"']$"), "")?.trim()?.takeIf { it.isNotBlank() }
        artist = artist?.replace(Regex("[🎵🎶🎧]"), "")?.replace(Regex("^[\"']|[\"']$"), "")?.trim()?.takeIf { it.isNotBlank() }
        
        if (song != null || artist != null) {
            return Pair(song ?: "", artist ?: "")
        }
        return null
    }

    /**
     * Converts YouTube subtitle/closed-caption tracks into Synced Lyrics.
     */
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

    private fun isPureSoundAnnotation(text: String): Boolean {
        val lower = text.lowercase().trim()
        val noiseTags = listOf(
            "[music]", "(music)", "♪", "♪♪", "[applause]", "(applause)",
            "[cheering]", "[sound]", "[instrumental]", "---", "***", "[laughter]"
        )
        return noiseTags.contains(lower) || (lower.startsWith("[") && lower.endsWith("]") && lower.length < 16)
    }

    /**
     * Extracts either timestamped lyrics or plain lyrics from YouTube description text.
     */
    fun fromDescription(description: String, durationSeconds: Long = 0): LyricsData? {
        if (description.isBlank()) return null

        // 1. Try parsing timestamped cues/lyrics in description (e.g. "01:23 Verse 1" or "0:15 Song Title")
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

        // 2. Try parsing plain text lyrics block from description (supports multi-language keywords)
        val plainLyricsText = extractLyricsTextFromDescription(description)
        if (!plainLyricsText.isNullOrBlank()) {
            val rawLines = plainLyricsText.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !isPureSoundAnnotation(it) }

            if (rawLines.size >= 4) {
                // Approximate smooth line sync if video duration is known
                val linePacingMs = if (durationSeconds > 10) {
                    ((durationSeconds * 1000L) / (rawLines.size + 1)).coerceIn(2000L, 6000L)
                } else {
                    3000L
                }

                val lines = rawLines.mapIndexed { index, line ->
                    LyricLine(timeMs = index * linePacingMs, text = line)
                }

                return LyricsData(
                    isSynced = durationSeconds > 10,
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
                if (label.isNotBlank() && !label.startsWith("http") && !isPureSoundAnnotation(label)) {
                    result.add(LyricLine(timeMs = totalMs, text = label))
                }
            }
        }

        return result.sortedBy { it.timeMs }
    }

    /**
     * Extracts plain text lyrics section from video description across English, Spanish,
     * Hindi, French, Russian, German, Japanese, Korean, and other common languages.
     */
    private fun extractLyricsTextFromDescription(description: String): String? {
        val lower = description.lowercase()
        val lyricMarkers = listOf(
            // English
            "lyrics:", "lyrics :", "[lyrics]", "(lyrics)", "--- lyrics ---", "=== lyrics ===",
            "song lyrics:", "full lyrics:", "lyrics below:", "lyrics in description", "read lyrics:",
            "lyrics -", "lyrics ♫", "\nlyrics\n", "lyrics\n", "lyrics : \n", "lyrics:\n",
            "lyrics by", "written by:", "track lyrics:",
            // Spanish / Portuguese
            "letra:", "letras:", "letra da música:", "letra de la canción:", "\nletras\n", "letras\n",
            "letra :", "letras :",
            // French
            "paroles:", "paroles :", "paroles de la chanson:", "paroles : \n",
            // Italian
            "testo:", "testo :", "testo della canzone:",
            // German
            "songtext:", "songtext :", "songtexte:", "songtexte :",
            // Indonesian / Malay / Tagalog
            "lirik:", "lirik :", "lirik lagu:", "mga liriko:",
            // Russian / Ukrainian / Slavic
            "текст песни:", "слова песни:", "текст:", "текст :",
            // Hindi / Punjabi / Gujarati / Bengali
            "बोल:", "गीत:", "গান:", "गीत के बोल:", "લિરિક્સ:", "লিরিক্স:",
            // Japanese / Chinese / Korean
            "歌詞:", "歌詞 :", "가사:", "가사 :", "歌词:", "歌词 :"
        )

        val blocks = mutableListOf<String>()

        // 1. Extract based on explicit markers
        for (marker in lyricMarkers) {
            var idx = lower.indexOf(marker)
            while (idx != -1) {
                val sub = description.substring(idx + marker.length)
                val block = extractLyricsBlock(sub)
                if (block.isNotBlank()) {
                    blocks.add(block)
                }
                idx = lower.indexOf(marker, idx + 1)
            }
        }

        // 2. Fallback: Check for verse/chorus structure markers
        if (blocks.isEmpty() || blocks.maxOfOrNull { it.length } ?: 0 < 40) {
            val structureMarkers = listOf(
                "[verse 1]", "verse 1:", "verse 1\n", "[verse]", "verse:\n",
                "[intro]", "intro:", "intro\n",
                "[chorus]", "chorus:", "chorus\n", "[chorus 1]",
                "[hook]", "hook:", "hook\n",
                "[refrain]", "refrain:",
                "[strophe 1]", "strophe 1:",
                "[estribillo]", "estribillo:"
            )
            for (marker in structureMarkers) {
                var idx = lower.indexOf(marker)
                while (idx != -1) {
                    val sub = description.substring(idx) // keep the marker
                    val block = extractLyricsBlock(sub)
                    if (block.isNotBlank()) {
                        blocks.add(block)
                    }
                    idx = lower.indexOf(marker, idx + 1)
                }
            }
        }

        val bestBlock = blocks.maxByOrNull { it.length } ?: ""
        return if (bestBlock.length > 40) bestBlock else null
    }

    private fun extractLyricsBlock(sub: String): String {
        val collectedLines = mutableListOf<String>()
        var consecutiveEmpty = 0

        for (rawLine in sub.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                consecutiveEmpty++
                if (consecutiveEmpty >= 3) break // allow up to 2 empty lines (e.g. verse spacing)
                collectedLines.add("")
            } else {
                consecutiveEmpty = 0
                val lowerLine = line.lowercase()
                // Stop if we hit promotional links, copyright, credits, store links or social channels
                if (lowerLine.startsWith("http://") || lowerLine.startsWith("https://") ||
                    lowerLine.startsWith("www.") || lowerLine.startsWith("follow ") || lowerLine.startsWith("follow:") ||
                    lowerLine.startsWith("subscribe") || lowerLine.startsWith("produced by") ||
                    lowerLine.startsWith("directed by") || lowerLine.startsWith("music video by") ||
                    lowerLine.startsWith("copyright") || lowerLine.startsWith("all rights reserved") ||
                    lowerLine.startsWith("(c)") || lowerLine.startsWith("(p)") || lowerLine.startsWith("©") || lowerLine.startsWith("℗") ||
                    lowerLine.startsWith("stream / download") || lowerLine.startsWith("connect with") ||
                    lowerLine.startsWith("listen on") || lowerLine.startsWith("buy on") ||
                    lowerLine.startsWith("socials:") || lowerLine.startsWith("social media:") ||
                    lowerLine.startsWith("official merchandise:") || lowerLine.startsWith("merch:")) {
                    break
                }
                collectedLines.add(line)
            }
        }

        while (collectedLines.isNotEmpty() && collectedLines.last().isBlank()) {
            collectedLines.removeAt(collectedLines.lastIndex)
        }
        
        // Remove leading empty lines
        while (collectedLines.isNotEmpty() && collectedLines.first().isBlank()) {
            collectedLines.removeAt(0)
        }

        return collectedLines.joinToString("\n").trim()
    }

    /**
     * Calculates token-based word similarity between two strings (0.0 to 1.0).
     */
    private fun calculateWordSimilarity(s1: String, s2: String): Double {
        val words1 = s1.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").split("\\s+".toRegex()).filter { it.isNotBlank() && it.length > 1 }.toSet()
        val words2 = s2.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").split("\\s+".toRegex()).filter { it.isNotBlank() && it.length > 1 }.toSet()
        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        return intersection.toDouble() / union.toDouble()
    }

    /**
     * Deep validation to ensure external LRCLIB results strictly match the target song.
     * Prevents invalid or random lyrics from ever showing.
     */
    private fun isLrclibMatchValid(
        candidateTrack: String,
        candidateArtist: String,
        candidateDurationSec: Long,
        targetTrack: String,
        targetArtist: String,
        targetDurationSec: Long
    ): Boolean {
        // 1. Duration check: If target duration is known, candidate duration MUST be within ±18s
        if (targetDurationSec > 25 && candidateDurationSec > 0) {
            val diff = abs(targetDurationSec - candidateDurationSec)
            if (diff > 18) {
                Log.d(TAG, "LRCLIB rejected: Duration mismatch (Target: $targetDurationSec, Candidate: $candidateDurationSec, diff: $diff)")
                return false
            }
        }

        // 2. Track Title Similarity check
        val trackSim = calculateWordSimilarity(candidateTrack, targetTrack)
        val cleanCand = candidateTrack.lowercase().replace(Regex("[^a-z0-9]"), "")
        val cleanTarget = targetTrack.lowercase().replace(Regex("[^a-z0-9]"), "")
        val directSubstring = cleanCand.isNotEmpty() && cleanTarget.isNotEmpty() &&
                (cleanCand.contains(cleanTarget) || cleanTarget.contains(cleanCand))

        if (trackSim < 0.45 && !directSubstring) {
            Log.d(TAG, "LRCLIB rejected: Title similarity too low ($trackSim) for '$candidateTrack' vs '$targetTrack'")
            return false
        }

        // 3. Artist Similarity check (if targetArtist is specified and not generic)
        val cleanTargetArt = targetArtist.lowercase().replace(Regex("[^a-z0-9]"), "")
        val genericArtists = listOf("variousartists", "vevo", "topic", "records", "music", "channel", "")
        if (cleanTargetArt.isNotEmpty() && !genericArtists.contains(cleanTargetArt)) {
            val artistSim = calculateWordSimilarity(candidateArtist, targetArtist)
            val cleanCandArt = candidateArtist.lowercase().replace(Regex("[^a-z0-9]"), "")
            val artSubstring = cleanCandArt.isNotEmpty() && (cleanCandArt.contains(cleanTargetArt) || cleanTargetArt.contains(cleanCandArt))
            if (artistSim < 0.25 && !artSubstring) {
                Log.d(TAG, "LRCLIB rejected: Artist mismatch ($artistSim) for '$candidateArtist' vs '$targetArtist'")
                return false
            }
        }

        return true
    }

    /**
     * Main prioritized lyrics resolver:
     * 1. Official YouTube Closed Captions (CC)
     * 2. YouTube Video Description (Timestamped or lyrics text block)
     * 3. Cleaned Metadata & LRCLIB API Search with Deep Validation (skips if compilation/mix)
     * 4. Strict "No-Show" policy: returns null if no validated lyrics are found.
     */
    suspend fun getLyrics(
        title: String,
        channel: String,
        durationSeconds: Long = 0,
        captions: List<CaptionLine3> = emptyList(),
        description: String? = null
    ): LyricsData? = withContext(Dispatchers.IO) {
        val cacheKey = "$title|$channel|$durationSeconds"
        val cached = lyricsCache[cacheKey]
        if (cached != null) {
            return@withContext if (cached === EMPTY_LYRICS) null else cached
        }

        // 1. PRIORITY 1: Video Description Timestamped Lyrics (Gold Standard)
        var descLyrics: LyricsData? = null
        if (!description.isNullOrBlank()) {
            descLyrics = fromDescription(description, durationSeconds)
            if (descLyrics != null && descLyrics.source.contains("Timestamped")) {
                lyricsCache[cacheKey] = descLyrics
                return@withContext descLyrics
            }
        }

        // 2. Try LRCLIB Search
        val (cleanTrackOriginal, cleanArtistOriginal) = cleanTrackAndArtist(title, channel)
        var lrclibLyrics = fetchLrclibExact(cleanTrackOriginal, cleanArtistOriginal, durationSeconds)

        if (lrclibLyrics == null) {
            val query = "$cleanArtistOriginal $cleanTrackOriginal".trim()
            lrclibLyrics = fetchLrclibSearch(query, cleanTrackOriginal, cleanArtistOriginal, durationSeconds)
        }

        if (lrclibLyrics == null && cleanTrackOriginal.isNotBlank()) {
            lrclibLyrics = fetchLrclibSearch(cleanTrackOriginal, cleanTrackOriginal, cleanArtistOriginal, durationSeconds)
        }

        // Fallback to description metadata if LRCLIB failed
        if (lrclibLyrics == null && !description.isNullOrBlank()) {
            val meta = extractMetadataFromDescription(description)
            if (meta != null) {
                val cleanTrackDesc = if (meta.first.isNotBlank()) meta.first else cleanTrackOriginal
                val cleanArtistDesc = if (meta.second.isNotBlank()) meta.second else cleanArtistOriginal
                
                if (cleanTrackDesc != cleanTrackOriginal || cleanArtistDesc != cleanArtistOriginal) {
                    lrclibLyrics = fetchLrclibExact(cleanTrackDesc, cleanArtistDesc, durationSeconds)
                    if (lrclibLyrics == null) {
                        lrclibLyrics = fetchLrclibSearch("$cleanArtistDesc $cleanTrackDesc".trim(), cleanTrackDesc, cleanArtistDesc, durationSeconds)
                    }
                }
            }
        }

        // PRIORITY 2: LRCLIB Synced Lyrics
        if (lrclibLyrics != null && lrclibLyrics.isSynced) {
            lyricsCache[cacheKey] = lrclibLyrics
            return@withContext lrclibLyrics
        }

        // PRIORITY 3: Video Description Plain Lyrics
        if (descLyrics != null && descLyrics.lines.isNotEmpty()) {
            lyricsCache[cacheKey] = descLyrics
            return@withContext descLyrics
        }

        // PRIORITY 4: Official YouTube Subtitles / Captions (CC)
        // (This acts as a fallback if the uploader didn't put lyrics in description and LRCLIB didn't have synced lyrics)
        if (captions.isNotEmpty()) {
            val ccLyrics = fromSubtitles(captions)
            if (ccLyrics != null && ccLyrics.lines.isNotEmpty()) {
                lyricsCache[cacheKey] = ccLyrics
                return@withContext ccLyrics
            }
        }

        // PRIORITY 5: LRCLIB Plain Lyrics
        if (lrclibLyrics != null && lrclibLyrics.lines.isNotEmpty()) {
            lyricsCache[cacheKey] = lrclibLyrics
            return@withContext lrclibLyrics
        }

        // Strict No-Fake policy: if null, store empty marker and return null
        lyricsCache[cacheKey] = EMPTY_LYRICS
        return@withContext null
    }

    private fun fetchLrclibExact(track: String, artist: String, durationSec: Long): LyricsData? {
        try {
            if (track.isBlank()) return null
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
            val json = JSONObject(body)
            
            val candTrack = json.optString("trackName", "")
            val candArtist = json.optString("artistName", "")
            val candDuration = json.optLong("duration", 0L)

            if (!isLrclibMatchValid(candTrack, candArtist, candDuration, track, artist, durationSec)) {
                return null
            }

            return parseLrclibJson(json)
        } catch (e: Exception) {
            Log.d(TAG, "LRCLIB exact lookup failed: ${e.message}")
            return null
        }
    }

    private fun fetchLrclibSearch(
        query: String,
        targetTrack: String,
        targetArtist: String,
        targetDurationSec: Long = 0
    ): LyricsData? {
        try {
            if (query.isBlank()) return null
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

            // Prioritize items with syncedLyrics that pass strict validation
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val synced = item.optString("syncedLyrics")
                val candTrack = item.optString("trackName", "")
                val candArtist = item.optString("artistName", "")
                val itemDur = item.optLong("duration", 0L)

                if (synced.isNotBlank() && isLrclibMatchValid(candTrack, candArtist, itemDur, targetTrack, targetArtist, targetDurationSec)) {
                    val parsed = parseLrclibJson(item)
                    if (parsed != null) return parsed
                }
            }

            // Fallback to plain lyrics only if strict validation passes
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val candTrack = item.optString("trackName", "")
                val candArtist = item.optString("artistName", "")
                val itemDur = item.optLong("duration", 0L)

                if (isLrclibMatchValid(candTrack, candArtist, itemDur, targetTrack, targetArtist, targetDurationSec)) {
                    val parsed = parseLrclibJson(item)
                    if (parsed != null) return parsed
                }
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

        if (syncedLyrics.isNotBlank() && syncedLyrics != "null") {
            val lines = parseLrc(syncedLyrics)
            if (lines.size >= 2) {
                return LyricsData(
                    isSynced = true,
                    lines = lines,
                    plainLyrics = plainLyrics,
                    source = "LRCLIB"
                )
            }
        }

        if (plainLyrics.isNotBlank() && plainLyrics != "null") {
            val cleanLines = plainLyrics.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !isPureSoundAnnotation(it) }

            if (cleanLines.size >= 4) {
                val lines = cleanLines.mapIndexed { index, line ->
                    LyricLine(timeMs = index * 3000L, text = line)
                }
                return LyricsData(
                    isSynced = false,
                    lines = lines,
                    plainLyrics = plainLyrics,
                    source = "LRCLIB (Plain)"
                )
            }
        }

        return null
    }
}
