package com.videhub.ui.components

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.cancel
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CaptionLine3(
    val startMillis: Long,
    val endMillis: Long,
    val nativeText: String,
    val romanizedText: String? = null,
    val englishText: String? = null
)

object LiveCaptionsManager {
    private var currentFetchJob: kotlinx.coroutines.Job? = null
    private var currentTransliterationJob: kotlinx.coroutines.Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _captions = MutableStateFlow<List<CaptionLine3>>(emptyList())
    val captions: StateFlow<List<CaptionLine3>> = _captions.asStateFlow()

    private val _availableTracks = MutableStateFlow<List<CaptionTrack>>(emptyList())
    val availableTracks: StateFlow<List<CaptionTrack>> = _availableTracks.asStateFlow()

    private val _selectedTrack = MutableStateFlow<CaptionTrack?>(null)
    val selectedTrack: StateFlow<CaptionTrack?> = _selectedTrack.asStateFlow()

    private val _selectedLanguageCode = MutableStateFlow<String?>(null)
    val selectedLanguageCode: StateFlow<String?> = _selectedLanguageCode.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()

    private val _isMusicMode = MutableStateFlow(false)
    val isMusicMode: StateFlow<Boolean> = _isMusicMode.asStateFlow()

    private var currentOfflineJson: String? = null

    fun setMusicMode(isMusic: Boolean) {
        _isMusicMode.value = isMusic
    }

    fun setAvailableTracks(tracks: List<CaptionTrack>) {
        _availableTracks.value = tracks
    }

    fun selectTrack(track: CaptionTrack?) {
        _selectedTrack.value = track
    }

    fun loadSubtitles(subtitlesJson: String?, autoTranslate: Boolean) {
        _isError.value = false
        if (subtitlesJson.isNullOrBlank()) {
            _captions.value = emptyList()
            return
        }
        try {
            val parsed = parseSubtitlesJson(subtitlesJson)
            _captions.value = parsed
            if (autoTranslate) {
                detectAndTranslateCaptions(_captions.value.toList())
            }
        } catch (e: Exception) {
            Log.e("LiveCaptionsManager", "Failed to parse JSON subtitles", e)
            _captions.value = emptyList()
        }
    }

    private fun extractLyricsFromDescription(description: String): List<String> {
        val lowerDesc = description.lowercase()
        var index = lowerDesc.indexOf("lyrics :")
        if (index == -1) index = lowerDesc.indexOf("lyrics:")
        if (index == -1) index = lowerDesc.indexOf("lyrics\n")
            
        if (index != -1) {
            val substring = description.substring(index)
            val lines = substring.lines().drop(1) // drop the "lyrics:" line
            
            val result = mutableListOf<String>()
            var emptyCount = 0
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    emptyCount++
                    if (emptyCount >= 3) break // Assume end of lyrics if 3 empty lines
                    result.add("")
                } else {
                    emptyCount = 0
                    // stop if line looks like a promo/link
                    if (trimmed.startsWith("http") || trimmed.lowercase().contains("subscribe") || trimmed.lowercase().contains("follow")) {
                        break
                    }
                    result.add(trimmed)
                }
            }
            
            // Remove trailing empty lines
            while (result.isNotEmpty() && result.last().isEmpty()) {
                result.removeLast()
            }
            
            return result
        }
        return emptyList()
    }

    private fun detectAndTranslateCaptions(lines: List<CaptionLine3>) {
        currentTransliterationJob?.cancel()
        currentTransliterationJob = scope.launch(Dispatchers.IO) {
            try {
                if (lines.isEmpty()) return@launch

                // Apply to ALL languages — not just Korean/Japanese
                val transliterator = com.ibm.icu.text.Transliterator
                    .getInstance("Any-Latin; Latin-ASCII")

                val transliteratedList = lines.map { line ->
                    try {
                        val romanized = transliterator
                            .transliterate(line.nativeText)
                            .trim()
                        // Only set romanizedText if it's meaningfully 
                        // different from the original (i.e. the original 
                        // was NOT already in Latin script)
                        val alreadyLatin = line.nativeText
                            .none { it.code > 127 && it.isLetter() }
                        line.copy(
                            romanizedText = if (alreadyLatin) null else romanized
                        )
                    } catch (e: Exception) {
                        line
                    }
                }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    _captions.value = transliteratedList
                }

                // 2. Translation (English) using ML Kit
                val fullText = transliteratedList.take(5).joinToString(" ") { it.nativeText }
                val languageIdentifier = LanguageIdentification.getClient()
                val langCode = languageIdentifier.identifyLanguage(fullText).await()

                if (langCode != "und" && langCode != "en") {
                    val sourceLang = TranslateLanguage.fromLanguageTag(langCode)
                    if (sourceLang != null) {
                        val options = TranslatorOptions.Builder()
                            .setSourceLanguage(sourceLang)
                            .setTargetLanguage(TranslateLanguage.ENGLISH)
                            .build()
                        val translator = Translation.getClient(options)
                        translator.downloadModelIfNeeded().await()

                        val translatedList = transliteratedList.map { line ->
                            try {
                                val englishText = translator.translate(line.nativeText).await()
                                line.copy(englishText = englishText)
                            } catch (e: Exception) {
                                line
                            }
                        }

                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            _captions.value = translatedList
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("LiveCaptionsManager", "Transliteration/Translation failed: ${e.message}")
            }
        }
    }
    private fun parseLrc(lrcText: String): List<CaptionLine3> {
        val lines = mutableListOf<CaptionLine3>()
        val regex = Regex("\\[(\\d+):(\\d+)\\.(\\d+)\\](.*)")
        lrcText.split("\n").forEach { line ->
            val match = regex.find(line.trim())
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val ms = match.groupValues[3].padEnd(3, '0').take(3).toLong()
                val text = match.groupValues[4].trim()
                val start = (min * 60 + sec) * 1000 + ms
                lines.add(CaptionLine3(start, start + 3000, text, null, null))
            }
        }
        // Smoothly adjust end timestamps based on next lines
        return lines.mapIndexed { index, item ->
            val end = if (index < lines.size - 1) lines[index + 1].startMillis else item.startMillis + 4000
            item.copy(endMillis = end)
        }
    }

    private fun parseSubtitlesJson(json: String): List<CaptionLine3> {
        val list = mutableListOf<CaptionLine3>()
        val array = JSONObject(json).getJSONArray("events")
        for (i in 0 until array.length()) {
            val event = array.getJSONObject(i)
            val start = event.optLong("tStartMs", 0)
            val duration = event.optLong("dDurationMs", 0)
            val segments = event.optJSONArray("segs")
            if (segments != null) {
                val textBuilder = StringBuilder()
                for (j in 0 until segments.length()) {
                    textBuilder.append(segments.getJSONObject(j).optString("utf8", ""))
                }
                val text = textBuilder.toString().trim()
                if (text.isNotEmpty()) {
                    list.add(CaptionLine3(start, start + duration, text, null, null))
                }
            }
        }
        return list
    }

    private fun parseVttOrSrt(text: String): List<CaptionLine3> {
        val lines = mutableListOf<CaptionLine3>()
        val regex = Regex("(\\d{1,2}:\\d{2}(?::\\d{2})?[.,]\\d{3})\\s*-->\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?[.,]\\d{3})")
        val matches = regex.findAll(text).toList()
        for (i in matches.indices) {
            val match = matches[i]
            val start = parseTime(match.groupValues[1])
            val end = parseTime(match.groupValues[2])
            val textStartIndex = match.range.last + 1
            val textEndIndex = if (i + 1 < matches.size) matches[i+1].range.first else text.length
            
            var content = text.substring(textStartIndex, textEndIndex)
                .lines()
                .filter { it.isNotBlank() && !it.trim().matches(Regex("^\\d+$")) && !it.trim().startsWith("WEBVTT") }
                .joinToString("\n")
                .replace(Regex("<[^>]*>"), "")
                .trim()
                
            if (content.isNotEmpty()) {
                lines.add(CaptionLine3(start, end, content, null, null))
            }
        }
        return lines
    }

    private fun parseTime(timeStr: String): Long {
        val clean = timeStr.replace(",", ".")
        val parts = clean.split(":")
        var h = 0L
        var m = 0L
        val sAndMs = parts.last().split(".")
        val s = sAndMs[0].toLong()
        val ms = if (sAndMs.size > 1) sAndMs[1].padEnd(3, '0').take(3).toLong() else 0L
        
        if (parts.size == 3) {
            h = parts[0].toLong()
            m = parts[1].toLong()
        } else if (parts.size == 2) {
            m = parts[0].toLong()
        }
        return h * 3600000 + m * 60000 + s * 1000 + ms
    }

    private fun parseXml(xmlText: String): List<CaptionLine3> {
        val lines = mutableListOf<CaptionLine3>()
        val srv1Regex = Regex("<text\\s+start=\"([\\d.]+)\"\\s+dur=\"([\\d.]+)\"[^>]*>(.*?)</text>", RegexOption.DOT_MATCHES_ALL)
        var hasSrv1 = false
        srv1Regex.findAll(xmlText).forEach { match ->
            hasSrv1 = true
            val startSec = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val durSec = match.groupValues[2].toDoubleOrNull() ?: 0.0
            var content = match.groupValues[3].replace(Regex("<[^>]*>"), "").trim()
            content = content.replace("&#39;", "'").replace("&apos;", "'").replace("&#x27;", "'").replace("&amp;apos;", "'").replace("&amp;", "&").replace("&quot;", "\"")
            content = android.text.Html.fromHtml(content, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
            if (content.isNotEmpty()) {
                val start = (startSec * 1000).toLong()
                val end = ((startSec + durSec) * 1000).toLong()
                lines.add(CaptionLine3(start, end, content, null, null))
            }
        }
        if (hasSrv1) return lines

        val srv3Regex = Regex("<p\\s+[^>]*t=\"([\\d]+)\"(?:\\s+[^>]*d=\"([\\d]+)\")?[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
        var hasSrv3 = false
        srv3Regex.findAll(xmlText).forEach { match ->
            hasSrv3 = true
            val startMs = match.groupValues[1].toLongOrNull() ?: 0L
            val durMs = match.groupValues[2].toLongOrNull() ?: 0L
            var content = match.groupValues[3].replace(Regex("<[^>]*>"), "").trim()
            content = content.replace("&#39;", "'").replace("&apos;", "'").replace("&#x27;", "'").replace("&amp;apos;", "'").replace("&amp;", "&").replace("&quot;", "\"")
            content = android.text.Html.fromHtml(content, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
            if (content.isNotEmpty()) {
                val start = startMs
                val end = startMs + durMs
                lines.add(CaptionLine3(start, end, content, null, null))
            }
        }
        if (hasSrv3) return lines

        val ttmlRegex = Regex("<p\\s+[^>]*begin=\"([^\"]+)\"\\s+[^>]*end=\"([^\"]+)\"[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
        ttmlRegex.findAll(xmlText).forEach { match ->
            val start = parseTime(match.groupValues[1])
            val end = parseTime(match.groupValues[2])
            var content = match.groupValues[3].replace(Regex("<[^>]*>"), "").trim()
            content = content.replace("&#39;", "'").replace("&apos;", "'").replace("&#x27;", "'").replace("&amp;apos;", "'").replace("&amp;", "&").replace("&quot;", "\"")
            content = android.text.Html.fromHtml(content, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
            if (content.isNotEmpty()) {
                lines.add(CaptionLine3(start, end, content, null, null))
            }
        }
        return lines
    }

    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "VideHub/1.0 (https://github.com/videhub)")
                .build()
            chain.proceed(request)
        }
        .build()

    private suspend fun tryFallbackLyrics(artist: String, title: String, description: String?, expectedLang: String? = null) {
        // Priority 2: Description Fallback
        if (!description.isNullOrBlank()) {
            val extracted = extractLyricsFromDescription(description)
            if (extracted.isNotEmpty()) {
                val lines = extracted.map { 
                     CaptionLine3(startMillis = Long.MAX_VALUE, endMillis = Long.MAX_VALUE, nativeText = it)
                }
                _captions.value = lines
                _isError.value = false
                detectAndTranslateCaptions(_captions.value.toList())
                return
            }
        }
        
        // Priority 3: LRCLIB API
        try {
            var cleanArtist = artist.replace(Regex("(?i)\\s*-\\s*topic$"), "")
                .replace(Regex("(?i)vevo$"), "")
                .replace(Regex("[\\(\\[【].*?[\\)\\]】]"), "")
                .trim()
            var cleanTitle = title.replace(Regex("[\\(\\[【].*?[\\)\\]】]"), "")
                .replace(Regex("(?i)(official.*|music video|lyrics|audio|video|mv)"), "")
                .trim()
                
            // Step 1: Try exact match first
            val exactUrl = "https://lrclib.net/api/get?" +
                "artist_name=${URLEncoder.encode(cleanArtist, "UTF-8")}" +
                "&track_name=${URLEncoder.encode(cleanTitle, "UTF-8")}"
            val exactRequest = okhttp3.Request.Builder().url(exactUrl).build()
            val exactResponse = kotlinx.coroutines.withContext(Dispatchers.IO) { 
                 client.newCall(exactRequest).execute() 
             }
            val exactBodyString = exactResponse.body?.string()
            if (exactResponse.isSuccessful) {
                val body = exactBodyString ?: ""
                if (body.isNotBlank() && body != "null" && !body.startsWith("[")) {
                    val json = org.json.JSONObject(body)
                    
                    suspend fun verifyAndAccept(lines: List<CaptionLine3>): Boolean {
                        if (expectedLang == null) {
                            _captions.value = lines
                            detectAndTranslateCaptions(_captions.value.toList())
                            return true
                        }
                        val sample = lines.take(3).joinToString(" ") { it.nativeText }.take(100)
                        if (!isTextInExpectedScript(sample, expectedLang)) return false
                        val langId = LanguageIdentification.getClient().identifyLanguage(sample).await()
                        if (langId != "und" && langId != expectedLang) return false
                        
                        _captions.value = lines
                        detectAndTranslateCaptions(_captions.value.toList())
                        return true
                    }

                    val syncedLyrics = json.optString("syncedLyrics", "")
                    if (syncedLyrics.isNotEmpty() && syncedLyrics != "null") {
                        val lines = parseLrc(syncedLyrics)
                        if (verifyAndAccept(lines)) return
                    }
                    val plainLyrics = json.optString("plainLyrics", "")
                    if (plainLyrics.isNotEmpty() && plainLyrics != "null") {
                        val lines = plainLyrics.lines().map {
                            CaptionLine3(Long.MAX_VALUE, Long.MAX_VALUE, it)
                        }
                        if (verifyAndAccept(lines)) return
                    }
                }
            }
            
            // Step 2: Fall back to search
            val queries = mutableListOf<String>()
            if (cleanTitle.contains("-")) {
                val parts = cleanTitle.split("-", limit = 2)
                if (parts.size == 2) {
                    queries.add("${parts[0].trim()} ${parts[1].trim()}")
                    queries.add(parts[1].trim())
                }
            } else if (cleanTitle.contains("|")) {
                val parts = cleanTitle.split("|", limit = 2)
                if (parts.size == 2) {
                    queries.add("$cleanArtist ${parts[0].trim()}")
                    queries.add(parts[0].trim())
                }
            }
            queries.add("$cleanArtist $cleanTitle".trim())
            queries.add(cleanTitle)
            
            for (query in queries.distinct()) {
                if (query.isBlank() || query.length < 2) continue
                
                val searchUrl = "https://lrclib.net/api/search?q=${URLEncoder.encode(query, "UTF-8")}"
                val request = okhttp3.Request.Builder().url(searchUrl).build()
                val response = kotlinx.coroutines.withContext(Dispatchers.IO) { client.newCall(request).execute() }
                
                if (response.isSuccessful) {
                    val res = response.body?.string() ?: ""
                    if (res.isNotBlank() && res != "[]") {
                        val jsonArray = org.json.JSONArray(res)
                        
                        suspend fun verifyAndAccept(lines: List<CaptionLine3>): Boolean {
                            if (expectedLang == null) {
                                _captions.value = lines
                                detectAndTranslateCaptions(_captions.value.toList())
                                return true
                            }
                            val sample = lines.take(3).joinToString(" ") { it.nativeText }.take(100)
                            if (!isTextInExpectedScript(sample, expectedLang)) return false
                            val langId = LanguageIdentification.getClient().identifyLanguage(sample).await()
                            if (langId != "und" && langId != expectedLang) return false
                            
                            _captions.value = lines
                            detectAndTranslateCaptions(_captions.value.toList())
                            return true
                        }
                        
                        for (i in 0 until jsonArray.length()) {
                            val json = jsonArray.getJSONObject(i)
                            val syncedLyrics = json.optString("syncedLyrics", "")
                            if (syncedLyrics.isNotEmpty() && syncedLyrics != "null") {
                                val lines = parseLrc(syncedLyrics)
                                if (verifyAndAccept(lines)) return
                            }
                        }
                        for (i in 0 until jsonArray.length()) {
                            val json = jsonArray.getJSONObject(i)
                            val plainLyrics = json.optString("plainLyrics", "")
                            if (plainLyrics.isNotEmpty() && plainLyrics != "null") {
                                val lines = plainLyrics.lines().map {
                                    CaptionLine3(startMillis = Long.MAX_VALUE, endMillis = Long.MAX_VALUE, nativeText = it)
                                }
                                if (verifyAndAccept(lines)) return
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("LiveCaptionsManager", "LRCLIB failed: ${e.message}")
        }
        _isError.value = true
    }

    
    suspend fun downloadAndSaveCaptions(context: android.content.Context, videoId: String, nativeUrl: String?, englishUrl: String?, artist: String, title: String, description: String?, subtitlesJson: String? = null): String? {
        try {
            val allParsed = mutableMapOf<String, List<CaptionLine3>>()

            if (!subtitlesJson.isNullOrBlank()) {
                try {
                    val arr = org.json.JSONArray(subtitlesJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val u = obj.optString("url", "")
                        val tag = obj.optString("languageTag", "")
                        val isAuto = obj.optBoolean("isAutoGenerated", false)
                        val key = if (isAuto) "$tag-auto" else tag
                        
                        if (u.isNotBlank() && tag.isNotBlank() && !allParsed.containsKey(key)) {
                            var p: List<CaptionLine3> = emptyList()
                            val formatsToTry = listOf("json3", "vtt", "")
                            for (fmt in formatsToTry) {
                                var urlToFetch = u
                                if (urlToFetch.contains("youtube.com") || urlToFetch.contains("timedtext")) {
                                    if (fmt.isNotEmpty()) {
                                        urlToFetch = if (urlToFetch.contains("fmt=")) urlToFetch.replace(Regex("fmt=[^&]*"), "fmt=$fmt") else "$urlToFetch&fmt=$fmt"
                                    }
                                }
                                val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        org.schabi.newpipe.extractor.NewPipe.getDownloader().get(urlToFetch, org.schabi.newpipe.extractor.localization.Localization.DEFAULT).responseBody()
                                    } catch (e: Exception) {
                                        val request = okhttp3.Request.Builder().url(urlToFetch).header("User-Agent", "Mozilla/5.0").build()
                                        client.newCall(request).execute().body?.string()
                                    }
                                }
                                if (json != null) {
                                    val content = json.trim()
                                    p = try {
                                        if (content.startsWith("{")) parseSubtitlesJson(content)
                                        else if (content.startsWith("WEBVTT") || content.contains("-->")) parseVttOrSrt(content)
                                        else if (content.startsWith("<?xml") || content.startsWith("<")) parseXml(content)
                                        else emptyList()
                                    } catch(e:Exception){ emptyList() }
                                    if (p.isNotEmpty()) break
                                }
                            }
                            if (p.isNotEmpty()) {
                                allParsed[key] = p
                            }
                        }
                    }
                } catch(e:Exception){}
            }

            var lrclibParsed: List<CaptionLine3> = emptyList()
            if (allParsed.isEmpty()) {
                lrclibParsed = getLrclibCaptions(artist, title, description)
            }

            if (allParsed.isNotEmpty() || lrclibParsed.isNotEmpty()) {
                val finalObj = org.json.JSONObject()
                
                if (allParsed.isNotEmpty()) {
                    for ((k, parsed) in allParsed) {
                        val jsonArray = org.json.JSONArray()
                        for (line in parsed) {
                            val obj = org.json.JSONObject()
                            obj.put("startMillis", line.startMillis)
                            obj.put("endMillis", line.endMillis)
                            obj.put("nativeText", line.nativeText)
                            line.romanizedText?.let { obj.put("romanizedText", it) }
                            line.englishText?.let { obj.put("englishText", it) }
                            jsonArray.put(obj)
                        }
                        finalObj.put(k, jsonArray)
                    }
                } else if (lrclibParsed.isNotEmpty()) {
                    val jsonArray = org.json.JSONArray()
                    for (line in lrclibParsed) {
                        val obj = org.json.JSONObject()
                        obj.put("startMillis", line.startMillis)
                        obj.put("endMillis", line.endMillis)
                        obj.put("nativeText", line.nativeText)
                        line.romanizedText?.let { obj.put("romanizedText", it) }
                        line.englishText?.let { obj.put("englishText", it) }
                        jsonArray.put(obj)
                    }
                    finalObj.put("lrclib", jsonArray)
                }
                
                val lyricsJson = finalObj.toString()
                com.videhub.data.AppDatabase.getDatabase(context).savedLyricsDao().insertLyrics(
                    com.videhub.data.entity.SavedLyricsEntity(videoId, lyricsJson)
                )
                return lyricsJson
            }
        } catch (e: Exception) {
            android.util.Log.e("LiveCaptionsManager", "Failed to download and save captions", e)
        }
        return null
    }
    
    fun loadCaptionsFromDb(context: android.content.Context, videoId: String, lyricsJson: String? = null) {
        currentFetchJob?.cancel()
        currentTransliterationJob?.cancel()
        currentFetchJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
        _isError.value = false
        _captions.value = emptyList()
        try {
            var finalJson = lyricsJson
            if (finalJson.isNullOrBlank()) {
                val downloaded = com.videhub.data.AppDatabase.getDatabase(context).downloadedVideoDao().getDownloadByFileName(videoId.substringAfterLast("/"))
                if (downloaded != null && !downloaded.lyrics.isNullOrBlank()) {
                    finalJson = downloaded.lyrics
                } else {
                    val entity = com.videhub.data.AppDatabase.getDatabase(context).savedLyricsDao().getLyrics(videoId)
                    if (entity != null) finalJson = entity.lyricsJson
                }
            }
            if (finalJson.isNullOrBlank()) {
                _captions.value = emptyList()
                return@launch
            }
            currentOfflineJson = finalJson
            
            var array: org.json.JSONArray? = null
            val finalTrimmed = finalJson.trim()
            if (finalTrimmed.startsWith("{")) {
                val obj = org.json.JSONObject(finalTrimmed)
                val keys = obj.keys()
                val keyList = mutableListOf<String>()
                val availableTracks = mutableListOf<CaptionTrack>()
                while (keys.hasNext()) {
                    val k = keys.next()
                    keyList.add(k)
                    val isAuto = k.endsWith("-auto")
                    val tag = if (isAuto) k.removeSuffix("-auto") else k
                    val loc = try { java.util.Locale(tag) } catch(e: Exception) { java.util.Locale.ENGLISH }
                    val dName = loc.getDisplayLanguage(loc).ifEmpty { tag }
                    availableTracks.add(CaptionTrack(url = "", displayName = dName, languageTag = tag, isAutoGenerated = isAuto))
                }
                _availableTracks.value = availableTracks

                val selectedKey = _selectedLanguageCode.value
                val bestKey = if (selectedKey != null) {
                    keyList.find { it == selectedKey } ?: keyList.find { it.startsWith(selectedKey) }
                } else null
                
                val fallbackKey = bestKey ?: keyList.find { it == "en" } ?: keyList.find { it.startsWith("en") } ?: keyList.firstOrNull()
                if (fallbackKey != null) {
                    array = obj.optJSONArray(fallbackKey)
                    val tag = if (fallbackKey.endsWith("-auto")) fallbackKey.removeSuffix("-auto") else fallbackKey
                    _selectedLanguageCode.value = tag
                    _selectedTrack.value = availableTracks.find { it.languageTag == tag && it.isAutoGenerated == fallbackKey.endsWith("-auto") }
                }
            } else if (finalTrimmed.startsWith("[")) {
                array = org.json.JSONArray(finalTrimmed)
            }

            if (array == null) {
                _captions.value = emptyList()
                return@launch
            }

            val list = mutableListOf<CaptionLine3>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val hasRom = obj.has("romanizedText") && !obj.isNull("romanizedText")
                val hasEng = obj.has("englishText") && !obj.isNull("englishText")
                list.add(CaptionLine3(
                    startMillis = obj.getLong("startMillis"),
                    endMillis = obj.getLong("endMillis"),
                    nativeText = obj.getString("nativeText"),
                    romanizedText = if (hasRom) obj.getString("romanizedText") else null,
                    englishText = if (hasEng) obj.getString("englishText") else null
                ))
            }
            _captions.value = list
            _isError.value = false
            if (list.isNotEmpty() && list.any { it.romanizedText.isNullOrBlank() && it.englishText.isNullOrBlank() }) {
                detectAndTranslateCaptions(list)
            }
        } catch (e: Exception) {
            android.util.Log.e("LiveCaptionsManager", "Failed to load captions from DB", e)
            _isError.value = true
        }
        }
    }

    private suspend fun getLrclibCaptions(artist: String, title: String, description: String?): List<CaptionLine3> {
        // Priority 2: Description Fallback
        if (!description.isNullOrBlank()) {
            val extracted = extractLyricsFromDescription(description)
            if (extracted.isNotEmpty()) {
                return extracted.map { 
                     CaptionLine3(startMillis = Long.MAX_VALUE, endMillis = Long.MAX_VALUE, nativeText = it)
                }
            }
        }
        
        // Priority 3: LRCLIB API
        try {
            var cleanArtist = artist.replace(Regex("(?i)\\s*-\\s*topic$"), "")
                .replace(Regex("(?i)vevo$"), "")
                .replace(Regex("[\\(\\[【].*?[\\)\\]】]"), "")
                .trim()
            var cleanTitle = title.replace(Regex("[\\(\\[【].*?[\\)\\]】]"), "")
                .replace(Regex("(?i)(official.*|music video|lyrics|audio|video|mv)"), "")
                .trim()
                
            val exactUrl = "https://lrclib.net/api/get?" +
                "artist_name=${java.net.URLEncoder.encode(cleanArtist, "UTF-8")}" +
                "&track_name=${java.net.URLEncoder.encode(cleanTitle, "UTF-8")}"
            val exactRequest = okhttp3.Request.Builder().url(exactUrl).build()
            val exactResponse = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { client.newCall(exactRequest).execute() }
            val exactBodyString = exactResponse.body?.string()
            if (exactResponse.isSuccessful && exactBodyString != null && exactBodyString.isNotBlank() && exactBodyString != "null" && !exactBodyString.startsWith("[")) {
                val json = org.json.JSONObject(exactBodyString)
                val syncedLyrics = json.optString("syncedLyrics", "")
                if (syncedLyrics.isNotEmpty() && syncedLyrics != "null") {
                    return parseLrc(syncedLyrics)
                }
                val plainLyrics = json.optString("plainLyrics", "")
                if (plainLyrics.isNotEmpty() && plainLyrics != "null") {
                    return plainLyrics.lines().map { CaptionLine3(Long.MAX_VALUE, Long.MAX_VALUE, it) }
                }
            }
            
            val queries = mutableListOf<String>()
            if (cleanTitle.contains("-")) {
                val parts = cleanTitle.split("-", limit = 2)
                if (parts.size == 2) {
                    queries.add("${parts[0].trim()} ${parts[1].trim()}")
                    queries.add(parts[1].trim())
                }
            } else if (cleanTitle.contains("|")) {
                val parts = cleanTitle.split("|", limit = 2)
                if (parts.size == 2) {
                    queries.add("$cleanArtist ${parts[0].trim()}")
                    queries.add(parts[0].trim())
                }
            }
            queries.add("$cleanArtist $cleanTitle".trim())
            queries.add(cleanTitle)
            
            for (query in queries.distinct()) {
                if (query.isBlank() || query.length < 2) continue
                
                val searchUrl = "https://lrclib.net/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
                val request = okhttp3.Request.Builder().url(searchUrl).build()
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { client.newCall(request).execute() }
                
                if (response.isSuccessful) {
                    val res = response.body?.string() ?: ""
                    if (res.isNotBlank() && res != "[]") {
                        val jsonArray = org.json.JSONArray(res)
                        for (i in 0 until jsonArray.length()) {
                            val json = jsonArray.getJSONObject(i)
                            val syncedLyrics = json.optString("syncedLyrics", "")
                            if (syncedLyrics.isNotEmpty() && syncedLyrics != "null") {
                                return parseLrc(syncedLyrics)
                            }
                        }
                        for (i in 0 until jsonArray.length()) {
                            val json = jsonArray.getJSONObject(i)
                            val plainLyrics = json.optString("plainLyrics", "")
                            if (plainLyrics.isNotEmpty() && plainLyrics != "null") {
                                return plainLyrics.lines().map { CaptionLine3(Long.MAX_VALUE, Long.MAX_VALUE, it) }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { }
        return emptyList()
    }

    fun clear() {
        currentFetchJob?.cancel()
        currentTransliterationJob?.cancel()
        _isError.value = false
        _captions.value = emptyList()
        _selectedTrack.value = null
        _selectedLanguageCode.value = null
        _availableTracks.value = emptyList()
    }

    private suspend fun detectNativeLanguage(
        title: String,
        artist: String,
        availableTracks: List<CaptionTrack>
    ): String? {
        if (availableTracks.isEmpty()) return null

        val sample = "$artist $title"
        var detectedLang = "und"
        try {
            detectedLang = com.google.mlkit.nl.languageid.LanguageIdentification.getClient().identifyLanguage(sample).await()
        } catch (e: Exception) {
            Log.e("LiveCaptionsManager", "Language ID failed", e)
        }

        var matchingTrack = availableTracks.find {
            !it.isAutoGenerated && it.languageTag.startsWith(detectedLang, ignoreCase = true)
        }
        if (matchingTrack == null) {
            matchingTrack = availableTracks.find {
                it.isAutoGenerated && it.languageTag.startsWith(detectedLang, ignoreCase = true)
            }
        }
        if (matchingTrack == null) {
            matchingTrack = availableTracks.find { !it.isAutoGenerated }
        }
        if (matchingTrack == null) {
            matchingTrack = availableTracks.firstOrNull()
        }
        return matchingTrack?.url
    }

    fun fetchCaptions(selectedUrl: String?, availableTracks: List<CaptionTrack>, artist: String, title: String, description: String?, isMusicMode: Boolean) {
        currentFetchJob?.cancel()
        currentTransliterationJob?.cancel()
        currentFetchJob = scope.launch {
            _isError.value = false
            _captions.value = emptyList()

            var finalTrackUrl = selectedUrl
            if (finalTrackUrl == null && availableTracks.isNotEmpty()) {
                val userLang = java.util.Locale.getDefault().language
                var matchingTrack = availableTracks.find {
                    !it.isAutoGenerated && it.languageTag.startsWith(userLang, ignoreCase = true)
                } ?: availableTracks.find {
                    it.isAutoGenerated && it.languageTag.startsWith(userLang, ignoreCase = true)
                }

                if (matchingTrack == null) {
                    val sample = "$artist $title"
                    var detectedLang = "und"
                    try {
                        detectedLang = com.google.mlkit.nl.languageid.LanguageIdentification.getClient().identifyLanguage(sample).await()
                    } catch (e: Exception) {
                        Log.e("LiveCaptionsManager", "Language ID failed", e)
                    }

                    matchingTrack = availableTracks.find {
                        !it.isAutoGenerated && it.languageTag.startsWith(detectedLang, ignoreCase = true)
                    }
                    if (matchingTrack == null) {
                        matchingTrack = availableTracks.find {
                            it.isAutoGenerated && it.languageTag.startsWith(detectedLang, ignoreCase = true)
                        }
                    }
                }
                if (matchingTrack == null) {
                    // Fallback to English priority
                    matchingTrack = availableTracks.find {
                        !it.isAutoGenerated && it.languageTag.startsWith("en", ignoreCase = true)
                    } ?: availableTracks.find {
                        it.isAutoGenerated && it.languageTag.startsWith("en", ignoreCase = true)
                    } ?: availableTracks.find {
                        !it.isAutoGenerated // Any manual track
                    } ?: availableTracks.firstOrNull() // Any auto track
                }
                finalTrackUrl = matchingTrack?.url
            }
            
            val trackToUse = availableTracks.find { it.url == finalTrackUrl }
            _selectedTrack.value = trackToUse
            val urlToUse = finalTrackUrl ?: selectedUrl
            _selectedLanguageCode.value = trackToUse?.languageTag ?: extractLangFromUrl(urlToUse ?: "")

            // Priority 1: Native Subtitles
            if (!urlToUse.isNullOrBlank()) {
                try {
                    var parsed: List<CaptionLine3> = emptyList()
                    val urlToFetch = urlToUse
                    
                    val json = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        try {
                            org.schabi.newpipe.extractor.NewPipe.getDownloader().get(urlToFetch, org.schabi.newpipe.extractor.localization.Localization.DEFAULT).responseBody()
                        } catch (e: Exception) {
                            val request = okhttp3.Request.Builder().url(urlToFetch).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36").build()
                            client.newCall(request).execute().body?.string()
                        }
                    }
                    
                    if (json != null) {
                        val content = json.trim()
                        if (content.startsWith("{")) {
                            try {
                                parsed = parseSubtitlesJson(content)
                            } catch (e: Exception) { }
                        } else if (content.startsWith("WEBVTT") || content.contains("-->")) {
                            try {
                                parsed = parseVttOrSrt(content)
                            } catch (e: Exception) { }
                        } else if (content.startsWith("<?xml") || content.startsWith("<")) {
                            try {
                                parsed = parseXml(content)
                            } catch (e: Exception) { }
                        }
                    }

                    if (parsed.isNotEmpty()) {
                        _captions.value = parsed
                        val track = availableTracks.find { it.url == urlToUse }
                        if (track != null && !track.languageTag.startsWith("en", ignoreCase = true)) {
                            detectAndTranslateCaptions(_captions.value.toList())
                        }
                        return@launch
                    } else {
                        Log.e("LiveCaptionsManager", "Parsed lines is empty after trying all formats.")
                    }
                } catch (e: Exception) {
                    Log.e("LiveCaptionsManager", "Failed to fetch subtitles from url", e)
                }
            }

            // Fallback chain
            tryFallbackLyrics(artist, title, description, extractLangFromUrl(urlToUse ?: ""))
        }
    }

    fun cancelCurrentTrack() {
        currentFetchJob?.cancel()
        currentTransliterationJob?.cancel()
    }

    fun release() {
        cancelCurrentTrack()
        // scope.cancel() // Don't cancel the singleton scope
    }

    private fun extractLangFromUrl(url: String): String? {
        val uri = android.net.Uri.parse(url)
        return uri.getQueryParameter("lang") ?: uri.getQueryParameter("tlang")
    }

    private fun isTextInExpectedScript(text: String, langCode: String): Boolean {
        // Fast sanity check for script mismatch based on common unicode blocks
        return when (langCode) {
            "hi", "mr", "ne" -> text.any { it.code in 0x0900..0x097F } // Devanagari
            "gu" -> text.any { it.code in 0x0A80..0x0AFF } // Gujarati
            "pa" -> text.any { it.code in 0x0A00..0x0A7F } // Gurmukhi
            "bn", "as" -> text.any { it.code in 0x0980..0x09FF } // Bengali
            "ta" -> text.any { it.code in 0x0B80..0x0BFF } // Tamil
            "te" -> text.any { it.code in 0x0C00..0x0C7F } // Telugu
            "kn" -> text.any { it.code in 0x0C80..0x0CFF } // Kannada
            "ml" -> text.any { it.code in 0x0D00..0x0D7F } // Malayalam
            "si" -> text.any { it.code in 0x0D80..0x0DFF } // Sinhala
            "th" -> text.any { it.code in 0x0E00..0x0E7F } // Thai
            "lo" -> text.any { it.code in 0x0E80..0x0EFF } // Lao
            "my" -> text.any { it.code in 0x1000..0x109F } // Myanmar
            "ka" -> text.any { it.code in 0x10A0..0x10FF } // Georgian
            "km" -> text.any { it.code in 0x1780..0x17FF } // Khmer
            "ar", "fa", "ur", "ps" -> text.any { it.code in 0x0600..0x06FF } // Arabic
            "he" -> text.any { it.code in 0x0590..0x05FF } // Hebrew
            "ja" -> text.any { it.code in 0x3040..0x309F || it.code in 0x30A0..0x30FF || it.code in 0x4E00..0x9FFF } // Hiragana/Katakana/Kanji
            "zh", "zh-CN", "zh-TW" -> text.any { it.code in 0x4E00..0x9FFF } // CJK
            "ko" -> text.any { it.code in 0xAC00..0xD7AF || it.code in 0x1100..0x11FF } // Hangul
            "el" -> text.any { it.code in 0x0370..0x03FF } // Greek
            "ru", "uk", "be", "bg", "sr", "mk" -> text.any { it.code in 0x0400..0x04FF } // Cyrillic
            "hy" -> text.any { it.code in 0x0530..0x058F } // Armenian
            "en", "es", "fr", "de", "it", "pt", "nl", "sv", "da", "no", "fi", "tr", "id", "ms", "vi", "pl", "cs", "hu", "ro", "sk", "sl", "hr" -> 
                text.any { it.code in 0x0000..0x024F } // Latin Extended
            else -> true // Unknown language -> skip script check
        }
    }

    fun switchOfflineTrack(track: CaptionTrack) {
        val finalJson = currentOfflineJson ?: return
        try {
            val finalTrimmed = finalJson.trim()
            if (finalTrimmed.startsWith("{")) {
                val obj = org.json.JSONObject(finalTrimmed)
                val key = if (track.isAutoGenerated) "${track.languageTag}-auto" else track.languageTag
                val array = obj.optJSONArray(key) ?: obj.optJSONArray(track.languageTag)
                if (array != null) {
                    val list = mutableListOf<CaptionLine3>()
                    for (i in 0 until array.length()) {
                        val o = array.getJSONObject(i)
                        list.add(CaptionLine3(
                            startMillis = o.getLong("startMillis"),
                            endMillis = o.getLong("endMillis"),
                            nativeText = o.getString("nativeText"),
                            romanizedText = if (o.has("romanizedText") && !o.isNull("romanizedText")) o.getString("romanizedText") else null,
                            englishText = if (o.has("englishText") && !o.isNull("englishText")) o.getString("englishText") else null
                        ))
                    }
                    _captions.value = list
                    _selectedLanguageCode.value = track.languageTag
                    _selectedTrack.value = track
                    if (list.isNotEmpty() && list.any { it.romanizedText.isNullOrBlank() && it.englishText.isNullOrBlank() }) {
                        scope.launch { detectAndTranslateCaptions(list) }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LiveCaptionsManager", "Failed to switch offline track", e)
        }
    }
}
