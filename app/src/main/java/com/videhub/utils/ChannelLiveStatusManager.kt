package com.videhub.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.concurrent.ConcurrentHashMap

/**
 * Top-stream information for an active channel live stream
 */
data class ChannelTopLiveStream(
    val videoUrl: String,
    val title: String,
    val thumbnailUrl: String,
    val viewCount: Long
)

/**
 * ChannelLiveStatusManager
 *
 * Centralized registry tracking which YouTube channels currently have ongoing live streams.
 * If a creator has multiple active live streams, it identifies the stream with the highest
 * concurrent watcher count and provides instantaneous lookups and click routing across all
 * screens (Home, Search, Subscriptions, Channel).
 */
object ChannelLiveStatusManager {
    // Channel Identifier (URL or lowercase channel name) -> Top live stream details
    private val liveStreamMap = ConcurrentHashMap<String, ChannelTopLiveStream>()
    
    // Channel Identifier -> Boolean (active live stream exists)
    private val _liveChannelKeys = MutableStateFlow<Set<String>>(emptySet())
    val liveChannelKeys: StateFlow<Set<String>> = _liveChannelKeys.asStateFlow()

    // Cache of channels already queried recently (to avoid repeated network thrashing)
    private val queriedChannels = ConcurrentHashMap<String, Long>()
    private val inFlightChecks = ConcurrentHashMap.newKeySet<String>()
    private val liveCheckSemaphore = kotlinx.coroutines.sync.Semaphore(2)
    private const val CACHE_EXPIRY_MS = 45_000L // 45 seconds

    /**
     * Check if a channel currently has an active live stream
     */
    fun isChannelLive(channelUrl: String?, channelName: String?): Boolean {
        val keys = _liveChannelKeys.value
        if (!channelUrl.isNullOrBlank()) {
            val normalizedUrl = normalizeKey(channelUrl)
            if (normalizedUrl in keys || channelUrl in keys) return true
        }
        if (!channelName.isNullOrBlank()) {
            val normalizedName = normalizeKey(channelName)
            if (normalizedName in keys || channelName.lowercase() in keys) return true
        }
        return false
    }

    /**
     * Get the highest-viewed live stream for a channel, if live
     */
    fun getTopLiveStream(channelUrl: String?, channelName: String?): ChannelTopLiveStream? {
        if (!channelUrl.isNullOrBlank()) {
            liveStreamMap[normalizeKey(channelUrl)]?.let { return it }
            liveStreamMap[channelUrl]?.let { return it }
        }
        if (!channelName.isNullOrBlank()) {
            liveStreamMap[normalizeKey(channelName)]?.let { return it }
            liveStreamMap[channelName.lowercase()]?.let { return it }
        }
        return null
    }

    /**
     * Register a known live stream (e.g. from search, home feed, or channel streams)
     */
    fun registerLiveStream(
        channelUrl: String?,
        channelName: String?,
        videoUrl: String,
        title: String,
        thumbnailUrl: String,
        viewCount: Long
    ) {
        val stream = ChannelTopLiveStream(videoUrl, title, thumbnailUrl, viewCount)
        val keysToAdd = mutableListOf<String>()

        if (!channelUrl.isNullOrBlank()) {
            val key = normalizeKey(channelUrl)
            val existing = liveStreamMap[key]
            if (existing == null || viewCount >= existing.viewCount) {
                liveStreamMap[key] = stream
                liveStreamMap[channelUrl] = stream
            }
            keysToAdd.add(key)
            keysToAdd.add(channelUrl)
        }

        if (!channelName.isNullOrBlank()) {
            val nameKey = normalizeKey(channelName)
            val existing = liveStreamMap[nameKey]
            if (existing == null || viewCount >= existing.viewCount) {
                liveStreamMap[nameKey] = stream
                liveStreamMap[channelName.lowercase()] = stream
            }
            keysToAdd.add(nameKey)
            keysToAdd.add(channelName.lowercase())
        }

        if (keysToAdd.isNotEmpty()) {
            _liveChannelKeys.value = _liveChannelKeys.value + keysToAdd
        }
    }

    private val verificationScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
    )

    /**
     * Strictly determines whether a StreamInfoItem is an unreleased scheduled video,
     * upcoming Premiere, or scheduled live event that cannot be played yet.
     */
    fun isScheduledOrUpcomingVideo(item: StreamInfoItem): Boolean {
        val uploadText = item.textualUploadDate ?: ""
        val descText = item.shortDescription ?: ""
        if (uploadText.contains("Scheduled", ignoreCase = true) ||
            uploadText.contains("Premieres", ignoreCase = true) ||
            uploadText.contains("Premiere", ignoreCase = true) ||
            uploadText.contains("Upcoming", ignoreCase = true) ||
            uploadText.contains("Notify me", ignoreCase = true) ||
            descText.contains("Premieres", ignoreCase = true) ||
            descText.contains("Scheduled for", ignoreCase = true)
        ) {
            return true
        }

        if (item.streamType?.name?.contains("UPCOMING", ignoreCase = true) == true) {
            return true
        }

        // Future upload/premiere timestamp
        val parsedDate = try { item.uploadDate?.offsetDateTime() } catch (_: Exception) { null }
        if (parsedDate != null && parsedDate.isAfter(java.time.OffsetDateTime.now().plusMinutes(2))) {
            return true
        }

        // On YouTube, NewPipeExtractor extracts scheduled Premieres with duration == -1L,
        // textualUploadDate == null, viewCount == -1L, and streamType == VIDEO_STREAM (or LIVE_STREAM with viewCount < 0).
        if (!item.isShortFormContent && item.duration <= 0L) {
            val isLiveType = item.streamType == StreamType.LIVE_STREAM ||
                    item.streamType == StreamType.AUDIO_LIVE_STREAM
            if (!isLiveType) {
                // A regular VIDEO_STREAM (or NONE) with no duration (<= 0) is an unreleased scheduled Premiere
                return true
            } else if (item.viewCount < 0L) {
                // An active live stream on YouTube has concurrent viewers (viewCount >= 0);
                // an upcoming scheduled live stream or premiere has viewCount == -1L.
                return true
            }
        }

        return false
    }

    /**
     * Checks if a full StreamInfo represents an unreleased scheduled/upcoming Premiere or live event.
     */
    fun isScheduledOrUpcomingStreamInfo(info: org.schabi.newpipe.extractor.stream.StreamInfo): Boolean {
        val uploadText = info.textualUploadDate ?: ""
        if (uploadText.contains("Scheduled", ignoreCase = true) ||
            uploadText.contains("Premieres", ignoreCase = true) ||
            uploadText.contains("Premiere", ignoreCase = true) ||
            uploadText.contains("Upcoming", ignoreCase = true)
        ) {
            return true
        }
        val parsedDate = try { info.uploadDate?.offsetDateTime() } catch (_: Exception) { null }
        if (parsedDate != null && parsedDate.isAfter(java.time.OffsetDateTime.now().plusMinutes(2))) {
            return true
        }
        val hasPlayableStream = !info.videoStreams.isNullOrEmpty() ||
                !info.videoOnlyStreams.isNullOrEmpty() ||
                !info.audioStreams.isNullOrEmpty() ||
                !info.hlsUrl.isNullOrBlank() ||
                !info.dashMpdUrl.isNullOrBlank()
        if (!hasPlayableStream && info.duration <= 0L) {
            return true
        }
        return false
    }

    /**
     * Filters out scheduled/upcoming unplayable videos from a list of StreamInfoItems.
     */
    fun filterPlayableVideos(items: List<StreamInfoItem>): List<StreamInfoItem> {
        return items.filterNot { isScheduledOrUpcomingVideo(it) }
    }

    /**
     * Filters out scheduled/upcoming unplayable StreamInfoItems from a mixed InfoItem list.
     */
    fun filterPlayableInfoItems(items: List<org.schabi.newpipe.extractor.InfoItem>): List<org.schabi.newpipe.extractor.InfoItem> {
        return items.filterNot { it is StreamInfoItem && isScheduledOrUpcomingVideo(it) }
    }

    /**
     * Strictly determines whether a StreamInfoItem is a candidate active live stream,
     * excluding ended streams ("Streamed ..."), upcoming/scheduled streams, and normal videos.
     */
    fun isStreamCurrentlyLive(item: StreamInfoItem): Boolean {
        if (isScheduledOrUpcomingVideo(item)) {
            return false
        }
        val uploadText = item.textualUploadDate ?: ""
        if (uploadText.contains("Streamed", ignoreCase = true) ||
            uploadText.contains("Scheduled", ignoreCase = true) ||
            uploadText.contains("Premieres", ignoreCase = true) ||
            uploadText.contains("Upcoming", ignoreCase = true) ||
            uploadText.contains("ago", ignoreCase = true)
        ) {
            return false
        }
        if (item.duration > 0L) {
            return false
        }
        if (item.isShortFormContent) {
            return false
        }
        // Active live streams report concurrent watcher count (>= 0)
        if (item.viewCount < 0L) {
            return false
        }
        return item.streamType == StreamType.LIVE_STREAM ||
                item.streamType == StreamType.AUDIO_LIVE_STREAM
    }

    /**
     * Returns true if the item is an ended/past live stream or scheduled upcoming stream
     * that should not be shown on the Channel Home tab or Subscriptions feed.
     */
    fun isEndedOrPastLiveStream(item: StreamInfoItem): Boolean {
        if (isScheduledOrUpcomingVideo(item)) {
            return true
        }
        if (item.streamType == StreamType.POST_LIVE_STREAM ||
            item.streamType == StreamType.POST_LIVE_AUDIO_STREAM
        ) {
            return true
        }
        val uploadText = item.textualUploadDate ?: ""
        if (uploadText.contains("Streamed", ignoreCase = true) ||
            uploadText.contains("Scheduled", ignoreCase = true) ||
            uploadText.contains("Premieres", ignoreCase = true) ||
            uploadText.contains("Upcoming", ignoreCase = true)
        ) {
            return true
        }
        return false
    }

    /**
     * Verifies via StreamInfo whether a candidate StreamInfoItem is genuinely live right now
     * (has an active HLS live manifest and 0 duration) and has not ended.
     */
    suspend fun verifyStreamIsLiveRightNow(item: StreamInfoItem): Boolean = withContext(Dispatchers.IO) {
        if (!isStreamCurrentlyLive(item)) return@withContext false
        val url = item.url ?: return@withContext false
        if (url.isBlank()) return@withContext false
        try {
            val info = com.videhub.extractor.ExtractorHelper.getStreamInfo(url, useCache = false)
            val uploadText = info.textualUploadDate ?: ""
            val isEndedOrUpcomingText = uploadText.contains("Streamed", ignoreCase = true) ||
                    uploadText.contains("Scheduled", ignoreCase = true) ||
                    uploadText.contains("Premieres", ignoreCase = true) ||
                    uploadText.contains("Upcoming", ignoreCase = true) ||
                    uploadText.contains("ago", ignoreCase = true)

            val stillLive = !isEndedOrUpcomingText &&
                    (info.streamType == StreamType.LIVE_STREAM ||
                            info.streamType == StreamType.AUDIO_LIVE_STREAM) &&
                    info.duration <= 0L &&
                    !info.hlsUrl.isNullOrBlank()
            if (stillLive && info.viewCount > 0L) {
                item.viewCount = info.viewCount
            }
            stillLive
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Remove a channel from active live status when its live stream has ended
     */
    fun unregisterLiveStream(
        channelUrl: String?,
        channelName: String?,
        secondaryChannelUrl: String? = null,
        videoUrl: String? = null
    ) {
        val keysToRemove = mutableSetOf<String>()

        fun collectUrlKeys(url: String?) {
            if (!url.isNullOrBlank()) {
                val key = normalizeKey(url)
                keysToRemove.add(key)
                keysToRemove.add(url)
            }
        }

        collectUrlKeys(channelUrl)
        collectUrlKeys(secondaryChannelUrl)

        if (!channelName.isNullOrBlank()) {
            val nameKey = normalizeKey(channelName)
            keysToRemove.add(nameKey)
            keysToRemove.add(channelName.lowercase())
        }

        // Also find any registered keys in liveStreamMap that point to the same videoUrl or same channel
        val targetVideoUrl = videoUrl?.takeIf { it.isNotBlank() }
        val matchingEntries = liveStreamMap.entries.filter { (k, v) ->
            k in keysToRemove || (targetVideoUrl != null && v.videoUrl == targetVideoUrl)
        }
        for ((k, v) in matchingEntries) {
            keysToRemove.add(k)
            liveStreamMap.entries.filter { it.value.videoUrl == v.videoUrl }.forEach {
                keysToRemove.add(it.key)
            }
        }

        for (k in keysToRemove) {
            liveStreamMap.remove(k)
        }

        if (keysToRemove.isNotEmpty()) {
            _liveChannelKeys.value = _liveChannelKeys.value - keysToRemove
        }
    }

    /**
     * Set the full live streams list for a channel (determines the highest watcher stream)
     */
    fun setChannelLiveStreams(
        channelUrl: String,
        channelName: String?,
        streams: List<StreamInfoItem>,
        secondaryChannelUrl: String? = null
    ) {
        val now = System.currentTimeMillis()
        queriedChannels[normalizeKey(channelUrl)] = now
        secondaryChannelUrl?.let { queriedChannels[normalizeKey(it)] = now }
        channelName?.let { queriedChannels[normalizeKey(it)] = now }

        val activeStreams = streams.filter { s -> isStreamCurrentlyLive(s) }

        if (activeStreams.isNotEmpty()) {
            val top = activeStreams.maxByOrNull { it.viewCount } ?: activeStreams.first()
            registerLiveStream(
                channelUrl = channelUrl,
                channelName = channelName ?: top.uploaderName,
                videoUrl = top.url ?: "",
                title = top.name ?: "Live Stream",
                thumbnailUrl = top.thumbnails?.firstOrNull()?.url ?: "",
                viewCount = top.viewCount
            )
            if (!secondaryChannelUrl.isNullOrBlank() && secondaryChannelUrl != channelUrl) {
                registerLiveStream(
                    channelUrl = secondaryChannelUrl,
                    channelName = channelName ?: top.uploaderName,
                    videoUrl = top.url ?: "",
                    title = top.name ?: "Live Stream",
                    thumbnailUrl = top.thumbnails?.firstOrNull()?.url ?: "",
                    viewCount = top.viewCount
                )
            }
        } else {
            unregisterLiveStream(channelUrl, channelName, secondaryChannelUrl)
        }
    }

    /**
     * Verifies candidate live streams against real-time StreamInfo and updates live status.
     * Returns the verified active top live StreamInfoItem, or null if no live stream is active.
     */
    suspend fun verifyAndSetChannelLiveStreams(
        channelUrl: String,
        secondaryChannelUrl: String?,
        channelName: String?,
        streams: List<StreamInfoItem>
    ): StreamInfoItem? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        queriedChannels[normalizeKey(channelUrl)] = now
        secondaryChannelUrl?.let { queriedChannels[normalizeKey(it)] = now }
        channelName?.let { queriedChannels[normalizeKey(it)] = now }

        val candidates = streams.filter { isStreamCurrentlyLive(it) }
            .sortedByDescending { it.viewCount }

        var verifiedTop: StreamInfoItem? = null
        for (candidate in candidates) {
            if (verifyStreamIsLiveRightNow(candidate)) {
                verifiedTop = candidate
                break
            }
        }

        if (verifiedTop != null) {
            setChannelLiveStreams(
                channelUrl = channelUrl,
                channelName = channelName ?: verifiedTop.uploaderName,
                streams = listOf(verifiedTop),
                secondaryChannelUrl = secondaryChannelUrl
            )
        } else {
            unregisterLiveStream(channelUrl, channelName, secondaryChannelUrl)
        }
        verifiedTop
    }

    /**
     * Check channel live status asynchronously via ExtractorHelper if not recently checked
     */
    suspend fun checkChannelLiveStatus(
        channelUrl: String,
        channelName: String? = null
    ): ChannelTopLiveStream? = withContext(Dispatchers.IO) {
        if (channelUrl.isBlank()) return@withContext null
        val normKey = normalizeKey(channelUrl)
        val lastCheck = queriedChannels[normKey] ?: 0L
        if (System.currentTimeMillis() - lastCheck < CACHE_EXPIRY_MS) {
            return@withContext getTopLiveStream(channelUrl, channelName)
        }
        if (!inFlightChecks.add(normKey)) {
            return@withContext getTopLiveStream(channelUrl, channelName)
        }
        queriedChannels[normKey] = System.currentTimeMillis()

        try {
            liveCheckSemaphore.acquire()
            try {
                val streams = com.videhub.extractor.ExtractorHelper.getChannelLiveStreams(channelUrl)
                verifyAndSetChannelLiveStreams(
                    channelUrl = channelUrl,
                    secondaryChannelUrl = null,
                    channelName = channelName,
                    streams = streams
                )
            } finally {
                liveCheckSemaphore.release()
            }
            return@withContext getTopLiveStream(channelUrl, channelName)
        } catch (_: Exception) {
            return@withContext null
        } finally {
            inFlightChecks.remove(normKey)
        }
    }

    /**
     * Inspect a video/stream item and verify before registering its creator as live
     */
    fun processStreamItem(item: StreamInfoItem) {
        if (isStreamCurrentlyLive(item)) {
            verificationScope.launch {
                if (verifyStreamIsLiveRightNow(item)) {
                    registerLiveStream(
                        channelUrl = item.uploaderUrl,
                        channelName = item.uploaderName,
                        videoUrl = item.url ?: "",
                        title = item.name ?: "Live Stream",
                        thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                        viewCount = item.viewCount
                    )
                } else {
                    unregisterLiveStream(
                        channelUrl = item.uploaderUrl,
                        channelName = item.uploaderName,
                        videoUrl = item.url
                    )
                }
            }
        }
    }

    private fun normalizeKey(raw: String): String {
        return raw.trim()
            .removePrefix("@")
            .removeSuffix("/")
            .substringAfterLast("/")
            .lowercase()
            .replace(" ", "")
    }
}
