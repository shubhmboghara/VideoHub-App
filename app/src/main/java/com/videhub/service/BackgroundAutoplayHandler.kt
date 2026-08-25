package com.videhub.service

import android.content.Context
import android.os.PowerManager
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Collections
import java.util.LinkedHashSet
import java.util.regex.Pattern

object BackgroundAutoplayHandler {
    var globalAutoplayJob: Job? = null
    var relatedItemsCache: List<InfoItem> = emptyList()
    var currentStreamInfo: StreamInfo? = null

    // Track recently played URLs to prevent ping-pong loops or repeating old videos
    private val recentlyPlayedUrls: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet<String>())
    private const val MAX_RECENT_TRACKS = 200

    fun markAsRecentlyPlayed(url: String) {
        if (url.isBlank()) return
        synchronized(recentlyPlayedUrls) {
            recentlyPlayedUrls.add(url)
            if (recentlyPlayedUrls.size > MAX_RECENT_TRACKS) {
                val iterator = recentlyPlayedUrls.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
    }

    fun isRecentlyPlayed(url: String): Boolean {
        return synchronized(recentlyPlayedUrls) { recentlyPlayedUrls.contains(url) }
    }

    fun clearRecentlyPlayed() {
        synchronized(recentlyPlayedUrls) { recentlyPlayedUrls.clear() }
    }

    private val STOP_WORDS = setOf(
        "the", "and", "for", "with", "official", "video", "audio", "lyrics",
        "full", "hd", "4k", "song", "music", "feat", "ft", "prod", "by",
        "of", "in", "to", "a", "an", "on", "live", "version", "remix", "explicit"
    )

    private val EPISODE_PATTERN = Pattern.compile("""(?i)(?:episode|ep|part|pt|#|chapter|ch)\.?\s*(\d+)""")

    private fun extractEpisodeNumber(title: String): Int? {
        val matcher = EPISODE_PATTERN.matcher(title)
        return if (matcher.find()) {
            matcher.group(1)?.toIntOrNull()
        } else null
    }

    /**
     * Refined Autoplay Selection Engine:
     * 1. Extracts current video's category, channel metadata, tags, and episodic sequence.
     * 2. Incorporates user's view history and liked channels to calculate creator affinity.
     * 3. Performs multi-tier candidate evaluation (Direct Related -> Channel Catalog -> Category Search).
     * 4. Scores candidates with heavy weighting on category match, channel relationship, user history, and sequential progression.
     * 5. Falls back gracefully to generic recommendations only when specific matches are exhausted.
     */
    suspend fun findNextRecommendedCandidate(
        context: Context,
        currentUrl: String,
        isMusicMode: Boolean
    ): StreamInfoItem? = withContext(Dispatchers.IO) {
        val alreadyQueued = com.videhub.QueueManager.queue.value.map { it.url }.toSet()

        // 1. Fetch / ensure current stream info & metadata
        var streamInfo = currentStreamInfo
        if (streamInfo == null || streamInfo.url != currentUrl) {
            try {
                streamInfo = com.videhub.extractor.ExtractorHelper.getStreamInfo(currentUrl)
                currentStreamInfo = streamInfo
                relatedItemsCache = streamInfo.relatedItems ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.e("BackgroundAutoplayHandler", "Failed to fetch stream info for recommendations", e)
            }
        }

        val currentTitle = streamInfo?.name ?: ""
        val currentUploader = streamInfo?.uploaderName ?: ""
        val currentUploaderUrl = streamInfo?.uploaderUrl ?: ""
        val currentCategory = streamInfo?.category ?: if (isMusicMode) "Music" else ""
        val currentTags = streamInfo?.tags ?: emptyList()
        val currentEpisode = extractEpisodeNumber(currentTitle)

        // 2. Extract User View History & Channel Affinity
        val userHistoryChannels = mutableMapOf<String, Int>()
        val userHistoryUrls = mutableSetOf<String>()
        val userLikedChannels = mutableSetOf<String>()
        try {
            val db = com.videhub.data.AppDatabase.getDatabase(context)
            val historyList = db.historyDao().getAllHistoryOnce()
            historyList.forEach { item ->
                userHistoryUrls.add(item.videoId)
                if (item.channelName.isNotBlank()) {
                    userHistoryChannels[item.channelName.lowercase()] =
                        (userHistoryChannels[item.channelName.lowercase()] ?: 0) + 1
                }
            }
            val likedList = db.likedVideoDao().getAllOnce()
            likedList.forEach { liked ->
                if (liked.channelName.isNotBlank()) {
                    userLikedChannels.add(liked.channelName.lowercase())
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("BackgroundAutoplayHandler", "History/Liked query failed", e)
        }

        // Top 5 favorite channels from user's history
        val topHistoryChannels = userHistoryChannels.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
            .toSet()

        // Clean tokens from current title
        val titleKeywords = currentTitle.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .toSet()

        // 3. Multi-Factor Scoring Function
        fun scoreCandidate(item: StreamInfoItem): Int {
            var score = 0
            val uploader = item.uploaderName ?: ""
            val uploaderLower = uploader.lowercase()
            val name = item.name ?: ""
            val nameLower = name.lowercase()

            // --- A. Channel Metadata & Creator Relationship ---
            if (currentUploader.isNotBlank() && uploader.equals(currentUploader, ignoreCase = true)) {
                score += 120 // Same creator / artist
            } else if (currentUploader.isNotBlank() && (nameLower.contains(currentUploader.lowercase()) || uploaderLower.contains(currentUploader.lowercase()))) {
                score += 65 // Collaborator / featured
            }

            // --- B. User History & Creator Affinity ---
            if (userLikedChannels.contains(uploaderLower)) {
                score += 60 // Liked creator
            }
            if (topHistoryChannels.contains(uploaderLower)) {
                score += 50 // Frequently watched creator
            } else if (userHistoryChannels.containsKey(uploaderLower)) {
                score += 25 // Watched before in history
            }

            // Penalty for videos already in user's recent history (prefer fresh unplayed videos)
            if (userHistoryUrls.contains(item.url) || userHistoryUrls.any { histUrl -> item.url.contains(histUrl) }) {
                score -= 150
            }

            // --- C. Category & Genre Alignment ---
            if (currentCategory.isNotBlank()) {
                if (currentCategory.equals("Music", ignoreCase = true) || isMusicMode) {
                    // Check for music indicators
                    if (nameLower.contains("song") || nameLower.contains("music") || nameLower.contains("official") || nameLower.contains("audio") || nameLower.contains("video")) {
                        score += 35
                    }
                }
            }

            // Tag / Subtopic overlap
            if (currentTags.isNotEmpty()) {
                val matchingTags = currentTags.count { tag ->
                    tag.isNotBlank() && (nameLower.contains(tag.lowercase()) || uploaderLower.contains(tag.lowercase()))
                }
                score += (matchingTags.coerceAtMost(3) * 20)
            }

            // --- D. Title Semantic & Keyword Overlap ---
            val candidateKeywords = nameLower.split(Regex("[^a-zA-Z0-9]+")).toSet()
            val overlapCount = titleKeywords.count { it in candidateKeywords }
            score += (overlapCount.coerceAtMost(4) * 20)

            // --- E. Sequential Episode Progression (e.g. Part 1 -> Part 2) ---
            if (currentEpisode != null && uploader.equals(currentUploader, ignoreCase = true)) {
                val candidateEpisode = extractEpisodeNumber(name)
                if (candidateEpisode != null) {
                    if (candidateEpisode == currentEpisode + 1) {
                        score += 150 // Perfect next episode
                    } else if (candidateEpisode > currentEpisode) {
                        score += 50 // Later episode in series
                    }
                }
            }

            // --- F. Quality & Popularity Bonus ---
            if (item.viewCount > 0) {
                score += (Math.log10(item.viewCount.toDouble() + 1.0) * 4).toInt().coerceAtMost(25)
            }

            return score
        }

        // TIER 1: Evaluate Direct Related Videos from Extractor
        val directCandidates = (streamInfo?.relatedItems ?: relatedItemsCache)
            .filterIsInstance<StreamInfoItem>()
            .filter { it.url != currentUrl && it.url !in alreadyQueued && !isRecentlyPlayed(it.url) }

        if (directCandidates.isNotEmpty()) {
            val scoredDirect = directCandidates.map { it to scoreCandidate(it) }
                .sortedByDescending { it.second }

            val bestDirect = scoredDirect.firstOrNull()
            // If top candidate has high relevance (score >= 60), use it
            if (bestDirect != null && bestDirect.second >= 60) {
                return@withContext bestDirect.first
            }
        }

        // TIER 2: Channel Uploads (Creator's Catalog for Series, Musician, or Binges)
        if (currentUploaderUrl.isNotBlank()) {
            try {
                val channelVideos = com.videhub.extractor.ExtractorHelper.getChannelVideosSorted(currentUploaderUrl, "latest", 2)
                val unplayedChannelVideos = channelVideos.filter {
                    it.url != currentUrl && it.url !in alreadyQueued && !isRecentlyPlayed(it.url)
                }
                if (unplayedChannelVideos.isNotEmpty()) {
                    val scoredChannel = unplayedChannelVideos.map { it to scoreCandidate(it) }
                        .sortedByDescending { it.second }
                    val bestChannel = scoredChannel.firstOrNull()
                    if (bestChannel != null && bestChannel.second >= 50) {
                        return@withContext bestChannel.first
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("BackgroundAutoplayHandler", "Channel catalog lookup error", e)
            }
        }

        // TIER 3: Category / Genre / Contextual Search
        try {
            val searchQuery = when {
                isMusicMode && currentUploader.isNotBlank() -> "$currentUploader best songs"
                currentCategory.isNotBlank() && currentUploader.isNotBlank() -> "$currentUploader $currentCategory"
                currentUploader.isNotBlank() -> "$currentUploader"
                titleKeywords.isNotEmpty() -> "${titleKeywords.take(2).joinToString(" ")} ${currentCategory.takeIf { it.isNotBlank() } ?: ""}".trim()
                else -> currentTitle
            }

            if (searchQuery.isNotBlank()) {
                val searchResults = com.videhub.extractor.ExtractorHelper.getMoreSearchItems(searchQuery)
                    .filterIsInstance<StreamInfoItem>()
                    .filter { it.url != currentUrl && it.url !in alreadyQueued && !isRecentlyPlayed(it.url) }

                if (searchResults.isNotEmpty()) {
                    val scoredSearch = searchResults.map { it to scoreCandidate(it) }
                        .sortedByDescending { it.second }
                    val bestSearch = scoredSearch.firstOrNull()?.first ?: searchResults.first()
                    return@withContext bestSearch
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("BackgroundAutoplayHandler", "Contextual search fallback error", e)
        }

        // TIER 4: Generic Recommendations Fallback
        val fallbackPool = (streamInfo?.relatedItems ?: relatedItemsCache)
            .filterIsInstance<StreamInfoItem>()
            .filter { it.url != currentUrl && it.url !in alreadyQueued }

        if (fallbackPool.isNotEmpty()) {
            val unplayedFallback = fallbackPool.filter { !isRecentlyPlayed(it.url) }
            if (unplayedFallback.isNotEmpty()) {
                return@withContext unplayedFallback.maxByOrNull { scoreCandidate(it) } ?: unplayedFallback.first()
            }
            // If even recently played set covers everything, trim half of recently played and return best
            synchronized(recentlyPlayedUrls) {
                val toRemove = recentlyPlayedUrls.take(recentlyPlayedUrls.size / 2)
                recentlyPlayedUrls.removeAll(toRemove.toSet())
            }
            return@withContext fallbackPool.maxByOrNull { scoreCandidate(it) } ?: fallbackPool.first()
        }

        return@withContext null
    }

    fun handleAutoplay(context: Context, player: Player, isAutoplayEnabled: Boolean = true, isPrefetch: Boolean = false, skipAction: () -> com.videhub.PlayQueueItem?) {
        if (!isPrefetch) globalAutoplayJob?.cancel()
        
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VideHub:AutoplayWakeLock")
        wakeLock.acquire(30000)
        
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val wifiLock = wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "VideHub:AutoplayWifiLock")
        wifiLock.setReferenceCounted(false)
        wifiLock.acquire()

        val job = CoroutineScope(Dispatchers.IO).launch { 
            if (!isPrefetch) {
                globalAutoplayJob = coroutineContext[Job]
            }
            var playedQueueItem = false
            try {
            var nextItem = skipAction()
            while (nextItem != null) {
                val isLocal = nextItem.url.startsWith("/") || nextItem.url.startsWith("file://") || nextItem.url.startsWith("content://")
                try {
                    var fetchedStreamInfo: org.schabi.newpipe.extractor.stream.StreamInfo? = null
                    var audioUrlForMerge: String? = null
                    val uri = if (isLocal) {
                        if (nextItem.url.startsWith("/")) android.net.Uri.fromFile(java.io.File(nextItem.url)).toString() else nextItem.url
                    } else {
                        com.videhub.MiniPlayerState.setLoadingNext(true)
                        val streamInfo = withContext(Dispatchers.IO) {
                            com.videhub.extractor.ExtractorHelper.getStreamInfo(nextItem.url)
                        }
                        com.videhub.MiniPlayerState.setLoadingNext(false)
                        fetchedStreamInfo = streamInfo
                        val audioOnly = (streamInfo.audioStreams ?: emptyList()).filter { !it.content.isNullOrBlank() }
                        if (com.videhub.MiniPlayerState.isMusicMode.value && audioOnly.isNotEmpty()) {
                            audioOnly.maxByOrNull { it.averageBitrate }?.content ?: ""
                        } else {
                            val progressive = (streamInfo.videoStreams ?: emptyList()).filter { !it.content.isNullOrBlank() }
                            val videoOnly = (streamInfo.videoOnlyStreams ?: emptyList()).filter { !it.content.isNullOrBlank() }
                            val v = videoOnly.maxByOrNull { it.getResolution()?.replace("p", "")?.toIntOrNull() ?: 0 }?.content
                            if (v != null) {
                                audioUrlForMerge = (streamInfo.audioStreams ?: emptyList()).filter { !it.content.isNullOrBlank() }.maxByOrNull { it.averageBitrate }?.content
                                v
                            } else {
                                progressive.maxByOrNull { it.getResolution()?.replace("p", "")?.toIntOrNull() ?: 0 }?.content ?: ""
                            }
                        }
                    }
                    
                    if (uri.isNotBlank()) {
                        val metadata = androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(nextItem.title)
                            .setArtist(nextItem.uploaderName)
                            .setArtworkUri(android.net.Uri.parse(if (nextItem.thumbnailUrl.isNotBlank()) nextItem.thumbnailUrl else "none"))
                            .build()
                        
                        val mediaItem = androidx.media3.common.MediaItem.Builder()
                            .setMediaId(nextItem.url)
                            .setUri(uri)
                            .setMediaMetadata(metadata)
                            .build()
                            
                        if (audioUrlForMerge != null && player is androidx.media3.exoplayer.ExoPlayer) {
                            val dsf = MediaSessionManager.dataSourceFactory ?: androidx.media3.datasource.DefaultDataSource.Factory(context)
                            val videoSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dsf).createMediaSource(mediaItem)
                            val audioMediaItem = androidx.media3.common.MediaItem.fromUri(audioUrlForMerge!!)
                            val audioSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dsf).createMediaSource(audioMediaItem)
                            val mergedSource = androidx.media3.exoplayer.source.MergingMediaSource(true, videoSource, audioSource)
                            withContext(Dispatchers.Main) { if (isPrefetch) player.addMediaSource(mergedSource) else player.setMediaSource(mergedSource)
                            if (!isPrefetch) player.prepare()
                            if (!isPrefetch) player.play() }
                        } else {
                            withContext(Dispatchers.Main) { if (isPrefetch) player.addMediaItem(mediaItem) else player.setMediaItem(mediaItem)
                            if (!isPrefetch) player.prepare()
                            if (!isPrefetch) player.play() }
                        }
                        if (!isPrefetch) {
                            com.videhub.MiniPlayerState.update(nextItem.title, nextItem.uploaderName, nextItem.thumbnailUrl, com.videhub.MiniPlayerState.isMusicMode.value, nextItem.url)
                            markAsRecentlyPlayed(nextItem.url)
                            com.videhub.PlaybackHistory.addToHistory(nextItem)
                        }
                        com.videhub.ui.components.LiveCaptionsManager.clear()
                        if (com.videhub.MiniPlayerState.isMusicMode.value) {
                            val subs = fetchedStreamInfo?.subtitles
                            val tracks = subs?.mapNotNull {
                                if (it.content.isNullOrBlank() || it.languageTag.isNullOrBlank()) null
                                else com.videhub.ui.components.CaptionTrack(
                                    languageTag = it.languageTag ?: "en",
                                    displayName = java.util.Locale(it.languageTag ?: "en").displayLanguage.takeIf { d -> d.isNotBlank() } ?: it.languageTag ?: "English",
                                    url = it.content ?: "",
                                    isAutoGenerated = it.isAutoGenerated
                                )
                            } ?: emptyList()
                            com.videhub.ui.components.LiveCaptionsManager.setAvailableTracks(tracks)
                            val desc = fetchedStreamInfo?.description?.content
                            if (isLocal) {
                                com.videhub.ui.components.LiveCaptionsManager.loadCaptionsFromDb(context, nextItem.url)
                            } else {
                                com.videhub.ui.components.LiveCaptionsManager.fetchCaptions(
                                    selectedUrl = null,
                                    availableTracks = tracks,
                                    artist = nextItem.uploaderName,
                                    title = nextItem.title,
                                    description = desc,
                                    isMusicMode = true
                                )
                            }
                        }
                        
                        if (fetchedStreamInfo != null) {
                            relatedItemsCache = fetchedStreamInfo.relatedItems ?: emptyList()
                            currentStreamInfo = fetchedStreamInfo
                        }
                        kotlinx.coroutines.delay(8000)
                        playedQueueItem = true
                        break // Successfully played, stop queue fallback loop
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    nextItem = skipAction() // Try next item in queue if current one failed
                }
            }
            if (!playedQueueItem && isAutoplayEnabled) {
                // Smart Autoplay with refined multi-tier recommendation engine
                val currentUrl = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { player.currentMediaItem?.mediaId }
                android.util.Log.d("BackgroundAutoplayHandler", "currentUrl for autoplay: $currentUrl")
                if (currentUrl == null) return@launch
                
                // Track currently finishing item
                markAsRecentlyPlayed(currentUrl)

                val isCurrentLocal = currentUrl.startsWith("/") || currentUrl.startsWith("file://") || currentUrl.startsWith("content://")
                if (isCurrentLocal) {
                    val nextD = getNextDownloadedItem(context, currentUrl, com.videhub.MiniPlayerState.isMusicMode.value)
                    if (nextD != null) {
                        val nextPath = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), nextD.fileName).absolutePath
                        com.videhub.QueueManager.enqueue(com.videhub.PlayQueueItem(nextPath, nextD.title, nextD.channelName, nextD.thumbnailUrl))
                        handleAutoplay(context, player) { com.videhub.QueueManager.getNextVideo() }
                    }
                    return@launch
                }

                // Find smartest next recommendation candidate using refined algorithm
                val nextCandidate = findNextRecommendedCandidate(context, currentUrl, com.videhub.MiniPlayerState.isMusicMode.value)
                
                if (nextCandidate != null && !nextCandidate.url.isNullOrBlank()) {
                    try {
                        val streamInfo = withContext(Dispatchers.IO) {
                            com.videhub.extractor.ExtractorHelper.getStreamInfo(nextCandidate.url)
                        }
                        var audioUrlForMerge: String? = null
                        val audioOnly = (streamInfo.audioStreams ?: emptyList()).filter { !it.content.isNullOrBlank() }
                        val uri = if (com.videhub.MiniPlayerState.isMusicMode.value && audioOnly.isNotEmpty()) {
                            audioOnly.maxByOrNull { it.averageBitrate }?.content ?: ""
                        } else {
                            val progressive = (streamInfo.videoStreams ?: emptyList()).filter { !it.content.isNullOrBlank() }
                            val videoOnly = (streamInfo.videoOnlyStreams ?: emptyList()).filter { !it.content.isNullOrBlank() }
                            val v = videoOnly.maxByOrNull { it.getResolution()?.replace("p", "")?.toIntOrNull() ?: 0 }?.content
                            if (v != null) {
                                audioUrlForMerge = (streamInfo.audioStreams ?: emptyList()).filter { !it.content.isNullOrBlank() }.maxByOrNull { it.averageBitrate }?.content
                                v
                            } else {
                                progressive.maxByOrNull { it.getResolution()?.replace("p", "")?.toIntOrNull() ?: 0 }?.content ?: ""
                            }
                        }
                        
                        if (uri.isNotBlank()) {
                            val title = nextCandidate.name ?: streamInfo.name ?: ""
                            val artist = nextCandidate.uploaderName ?: streamInfo.uploaderName ?: ""
                            val thumb = nextCandidate.thumbnails?.firstOrNull()?.url ?: streamInfo.thumbnails?.firstOrNull()?.url ?: ""
                            val metadata = androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(title)
                                .setArtist(artist)
                                .setArtworkUri(android.net.Uri.parse(if (thumb.isNotBlank()) thumb else "none"))
                                .build()
                                
                            val mediaItem = androidx.media3.common.MediaItem.Builder()
                                .setMediaId(nextCandidate.url)
                                .setUri(uri)
                                .setMediaMetadata(metadata)
                                .build()
                                
                            if (audioUrlForMerge != null && player is androidx.media3.exoplayer.ExoPlayer) {
                                val dsf = MediaSessionManager.dataSourceFactory ?: androidx.media3.datasource.DefaultDataSource.Factory(context)
                                val videoSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dsf).createMediaSource(mediaItem)
                                val audioMediaItem = androidx.media3.common.MediaItem.fromUri(audioUrlForMerge!!)
                                val audioSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dsf).createMediaSource(audioMediaItem)
                                val mergedSource = androidx.media3.exoplayer.source.MergingMediaSource(true, videoSource, audioSource)
                                withContext(Dispatchers.Main) { if (isPrefetch) player.addMediaSource(mergedSource) else player.setMediaSource(mergedSource)
                                if (!isPrefetch) player.prepare()
                                if (!isPrefetch) player.play() }
                            } else {
                                withContext(Dispatchers.Main) { if (isPrefetch) player.addMediaItem(mediaItem) else player.setMediaItem(mediaItem)
                                if (!isPrefetch) player.prepare()
                                if (!isPrefetch) player.play() }
                            }
                            if (!isPrefetch) {
                                com.videhub.MiniPlayerState.update(title, artist, thumb, com.videhub.MiniPlayerState.isMusicMode.value, nextCandidate.url)
                                markAsRecentlyPlayed(nextCandidate.url)
                                com.videhub.PlaybackHistory.addToHistory(
                                    com.videhub.PlayQueueItem(nextCandidate.url, title, artist, thumb)
                                )
                                com.videhub.ui.components.LiveCaptionsManager.clear()
                            }
                            if (com.videhub.MiniPlayerState.isMusicMode.value) {
                                val subs = streamInfo.subtitles
                                val tracks = subs?.mapNotNull {
                                    if (it.content.isNullOrBlank() || it.languageTag.isNullOrBlank()) null
                                    else com.videhub.ui.components.CaptionTrack(
                                        languageTag = it.languageTag ?: "en",
                                        displayName = java.util.Locale(it.languageTag ?: "en").displayLanguage.takeIf { d -> d.isNotBlank() } ?: it.languageTag ?: "English",
                                        url = it.content ?: "",
                                        isAutoGenerated = it.isAutoGenerated
                                    )
                                } ?: emptyList()
                                com.videhub.ui.components.LiveCaptionsManager.setAvailableTracks(tracks)
                                val desc = streamInfo.description?.content
                                com.videhub.ui.components.LiveCaptionsManager.fetchCaptions(
                                    selectedUrl = null,
                                    availableTracks = tracks,
                                    artist = artist,
                                    title = title,
                                    description = desc,
                                    isMusicMode = true
                                )
                            }
                            
                            relatedItemsCache = streamInfo.relatedItems ?: emptyList()
                            currentStreamInfo = streamInfo
                            
                            kotlinx.coroutines.delay(8000)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BackgroundAutoplayHandler", "Error playing recommended candidate", e)
                    }
                }
            }
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                if (!playedQueueItem && wifiLock.isHeld) wifiLock.release()
                else CoroutineScope(Dispatchers.IO).launch {
                    kotlinx.coroutines.delay(3000)
                    if (wifiLock.isHeld) wifiLock.release()
                }
            }
        }
    }
    
    private suspend fun getNextDownloadedItem(context: Context, currentUrl: String, isMusic: Boolean): com.videhub.data.entity.DownloadedVideoEntity? {
        return withContext(Dispatchers.IO) {
            val db = com.videhub.data.AppDatabase.getDatabase(context)
            val all = db.downloadedVideoDao().getAllDownloadsSync()
            val filtered = if (isMusic) all.filter { it.isAudioOnly } else all.filter { !it.isAudioOnly }
            if (filtered.isEmpty()) return@withContext null
            val currentIndex = filtered.indexOfFirst { currentUrl.contains(it.fileName) }
            if (currentIndex != -1 && currentIndex + 1 < filtered.size) {
                filtered[currentIndex + 1]
            } else {
                filtered.first()
            }
        }
    }
}
