package com.videhub.data.repository

import android.content.Context
import com.videhub.data.AppDatabase
import com.videhub.data.SettingsManager
import com.videhub.data.entity.FeedCacheEntity
import com.videhub.extractor.ExtractorHelper
import com.videhub.extractor.ListExtractorPagingSource
import com.videhub.recommendation.RecommendationEngine
import com.videhub.recommendation.RecommendedPlaylistInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.random.Random

/**
 * Repository layer responsible for managing and filtering the Home Screen Feed,
 * Personalized Shorts Feed, and Recommended Playlists based on user interests
 * and NewPipeExtractor API queries.
 */
class FeedRepository(
    private val db: AppDatabase,
    private val context: Context
) {

    companion object {
        private val sessionSeenUrls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private var globalRefreshCount = 0
    }

    private val commonStopWords = setOf(
        "the", "and", "a", "an", "in", "on", "at", "for", "with", "about", "against", "between",
        "into", "through", "during", "before", "after", "above", "below", "to", "from", "up",
        "down", "in", "out", "off", "over", "under", "again", "further", "then", "once", "here",
        "there", "when", "where", "why", "how", "all", "any", "both", "each", "few", "more",
        "most", "other", "some", "such", "no", "nor", "not", "only", "own", "same", "so",
        "than", "too", "very", "s", "t", "can", "will", "just", "don", "should", "now",
        "video", "official", "audio", "full", "hd", "4k", "feat", "ft", "lyrics", "shorts", "#shorts"
    )

    /**
     * Retrieves the personalized Home Screen feed.
     * Extracts video streams for every user interest using NewPipeExtractor,
     * filters and ranks items based on interest relevance, creator affinity,
     * and performs balanced round-robin interleaving so that all interests are represented.
     */
    suspend fun getHomeFeed(
        isRefresh: Boolean = false,
        refreshCount: Int = 0
    ): List<InfoItem> = withContext(Dispatchers.IO) {
        if (isRefresh) {
            globalRefreshCount++
        }
        val currentRefresh = if (refreshCount > 0) refreshCount else globalRefreshCount

        val userInterests = try {
            SettingsManager.getUserInterests(context).first()
                .map { it.trim() }
                .filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }

        val channels = db.channelDao().getAllOnce()
        val history = db.historyDao().getAllHistoryOnce()
        val liked = db.likedVideoDao().getAllOnce()
        val watchLater = db.watchLaterDao().getAllOnce()
        val playlistVideos = db.playlistDao().getAllPlaylistVideosOnce()

        val historyVideoIds = history.map { it.videoId }.toSet()
        val watchLaterVideoIds = watchLater.map { it.videoId }.toSet()
        val subscribedChannelNames = channels.map { it.name.lowercase() }.toSet()
        val subscribedChannelIds = channels.map { it.channelId }.toSet()
        val topLikedChannels = liked.groupBy { it.channelName }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(6)
            .map { it.key.lowercase() }
            .toSet()

        // 1. Direct Extraction for each individual user interest
        val interestPools = mutableMapOf<String, List<StreamInfoItem>>()
        val directInterestUrls = mutableSetOf<String>()

        val interestModifiers = listOf(
            "latest",
            "new uploads",
            "trending today",
            "popular this week",
            "highlights",
            "best moments",
            "top releases",
            "must watch"
        )

        if (userInterests.isNotEmpty()) {
            coroutineScope {
                val deferredList = userInterests.map { interest ->
                    async {
                        val collected = mutableListOf<StreamInfoItem>()
                        try {
                            if (isRefresh) {
                                val modIndex1 = Math.abs(currentRefresh + interest.hashCode()) % interestModifiers.size
                                val modIndex2 = Math.abs(currentRefresh + interest.hashCode() + 3) % interestModifiers.size
                                val q1 = "$interest ${interestModifiers[modIndex1]}"
                                val q2 = "$interest ${interestModifiers[modIndex2]}"

                                val s1 = ExtractorHelper.searchYouTube(q1, pages = 2)
                                collected.addAll(s1.relatedItems.filterIsInstance<StreamInfoItem>())
                                val s2 = ExtractorHelper.searchYouTube(q2, pages = 1)
                                collected.addAll(s2.relatedItems.filterIsInstance<StreamInfoItem>())
                            } else {
                                // Primary direct search for the interest
                                val primarySearch = ExtractorHelper.searchYouTube(interest, pages = 2)
                                val primaryItems = primarySearch.relatedItems.filterIsInstance<StreamInfoItem>()
                                collected.addAll(primaryItems)

                                // Variant query for initial population
                                val secondarySearch = ExtractorHelper.searchYouTube("$interest popular", pages = 1)
                                val secondaryItems = secondarySearch.relatedItems.filterIsInstance<StreamInfoItem>()
                                collected.addAll(secondaryItems)
                            }
                        } catch (_: Exception) {}

                        // Filter by interest relevance with freshness scoring
                        val filtered = filterAndRankForSingleInterest(
                            interest = interest,
                            items = collected,
                            historyIds = historyVideoIds,
                            isRefresh = isRefresh
                        )
                        interest to filtered
                    }
                }

                deferredList.awaitAll().forEach { (interest, list) ->
                    interestPools[interest] = list
                    list.forEach { item ->
                        item.url?.let { directInterestUrls.add(it) }
                    }
                }
            }
        }

        // 2. Fetch updates from subscribed channels (shuffled to rotate creators)
        val subscribedVideos = mutableListOf<StreamInfoItem>()
        val selectedChannels = if (channels.size > 8) {
            channels.shuffled().take(8)
        } else {
            channels
        }
        if (selectedChannels.isNotEmpty()) {
            coroutineScope {
                val deferredChannels = selectedChannels.map { channel ->
                    async {
                        try {
                            val url = when {
                                channel.channelId.startsWith("http") -> channel.channelId
                                channel.channelId.startsWith("@") -> "https://www.youtube.com/${channel.channelId}"
                                else -> "https://www.youtube.com/channel/${channel.channelId}"
                            }
                            val info = ExtractorHelper.getChannelInfo(url)
                            val channelVideos = ExtractorHelper.getChannelVideos(info, maxPages = if (isRefresh) 2 else 1)
                                .filterIsInstance<StreamInfoItem>()
                                .let { if (isRefresh) it.shuffled() else it }
                                .take(4)

                            channelVideos.forEach { latest ->
                                if (channel.thumbnailUrl?.isNotBlank() == true && channel.thumbnailUrl != "none") {
                                    latest.uploaderAvatars = listOf(
                                        org.schabi.newpipe.extractor.Image(
                                            channel.thumbnailUrl, 50, 50,
                                            org.schabi.newpipe.extractor.Image.ResolutionLevel.UNKNOWN
                                        )
                                    )
                                }
                            }
                            channelVideos
                        } catch (_: Exception) {
                            emptyList<StreamInfoItem>()
                        }
                    }
                }
                subscribedVideos.addAll(deferredChannels.awaitAll().flatten())
            }
        }

        // 3. Fetch related recommendations based on watch seeds
        val seedUrls = mutableListOf<String>()
        seedUrls.addAll(liked.take(6).map { it.videoId })
        seedUrls.addAll(watchLater.take(6).map { it.videoId })
        seedUrls.addAll(playlistVideos.take(6).map { it.videoId })
        seedUrls.addAll(history.take(6).map { it.videoId })

        val uniqueSeedUrls = seedUrls.filter { it.isNotBlank() }.distinct().shuffled().take(4)
        val relatedVideos = mutableListOf<StreamInfoItem>()
        if (uniqueSeedUrls.isNotEmpty()) {
            coroutineScope {
                val deferredSeeds = uniqueSeedUrls.map { url ->
                    async {
                        try {
                            val info = ExtractorHelper.getStreamInfo(url, true)
                            info.relatedItems?.filterIsInstance<StreamInfoItem>()?.let {
                                if (isRefresh) it.shuffled() else it
                            }?.take(6) ?: emptyList()
                        } catch (_: Exception) {
                            emptyList<StreamInfoItem>()
                        }
                    }
                }
                relatedVideos.addAll(deferredSeeds.awaitAll().flatten())
            }
        }

        // 4. Baseline trending and diverse discovery feeds
        val discoveryQueries = listOf(
            listOf("trending videos today", "viral videos this week", "new popular videos"),
            listOf("latest music releases", "trending music hits 2026", "new music videos"),
            listOf("trending gaming videos", "new game releases gameplay", "top gaming moments"),
            listOf("latest science and technology breakthroughs", "new tech reviews", "future tech"),
            listOf("breaking news today", "world news highlights", "interesting mini documentaries"),
            listOf("viral creative videos", "best internet videos", "popular uploads this week")
        )
        val activeDiscoveryGroup = discoveryQueries[Math.abs(currentRefresh) % discoveryQueries.size]

        val trendingVideos = mutableListOf<StreamInfoItem>()
        coroutineScope {
            val trendingKioskDeferred = async {
                try {
                    val trending = ExtractorHelper.getTrending()
                    trending.filterIsInstance<StreamInfoItem>()
                } catch (_: Exception) {
                    emptyList<StreamInfoItem>()
                }
            }

            val discoverySearchDeferred = async {
                val collectedDiscovery = mutableListOf<StreamInfoItem>()
                activeDiscoveryGroup.forEach { q ->
                    try {
                        val search = ExtractorHelper.searchYouTube(q, pages = 1)
                        collectedDiscovery.addAll(search.relatedItems.filterIsInstance<StreamInfoItem>())
                    } catch (_: Exception) {}
                }
                collectedDiscovery
            }

            val kioskItems = trendingKioskDeferred.await()
            val discoveryItems = discoverySearchDeferred.await()

            if (isRefresh) {
                trendingVideos.addAll((discoveryItems.shuffled() + kioskItems.shuffled()).distinctBy { it.url })
            } else {
                trendingVideos.addAll((kioskItems + discoveryItems).distinctBy { it.url })
            }
        }

        // 5. Compose the final feed using Interest-First Balanced Interleaving
        val finalFeed = mutableListOf<StreamInfoItem>()
        val seenInCurrentFeed = mutableSetOf<String>()

        fun addIfUnique(item: StreamInfoItem?): Boolean {
            if (item == null) return false
            val u = item.url ?: return false
            if (u.isBlank() || seenInCurrentFeed.contains(u)) return false
            seenInCurrentFeed.add(u)
            finalFeed.add(item)
            return true
        }

        if (userInterests.isNotEmpty()) {
            // Round-robin interleaving across all user interests
            val maxPoolSize = interestPools.values.maxOfOrNull { it.size } ?: 0
            var otherSubIndex = 0
            var otherRelatedIndex = 0

            for (index in 0 until maxPoolSize) {
                // For each round, take 1 video from each interest pool
                for (interest in userInterests) {
                    val pool = interestPools[interest] ?: emptyList()
                    if (index < pool.size) {
                        addIfUnique(pool[index])
                    }
                }

                // Every 2 rounds, mix in one subscribed or related video to preserve discovery
                if (index % 2 == 1) {
                    if (otherSubIndex < subscribedVideos.size) {
                        addIfUnique(subscribedVideos[otherSubIndex++])
                    } else if (otherRelatedIndex < relatedVideos.size) {
                        addIfUnique(relatedVideos[otherRelatedIndex++])
                    }
                }
            }

            // Fill remaining tail with subscribed and related videos
            subscribedVideos.forEach { addIfUnique(it) }
            relatedVideos.forEach { addIfUnique(it) }
            trendingVideos.forEach { addIfUnique(it) }
        } else {
            // No explicit interests: rank by affinity score & freshness
            val candidatePool = (subscribedVideos + relatedVideos + trendingVideos)
                .filter { it.url?.isNotBlank() == true }
                .distinctBy { it.url ?: "" }

            val scoredItems = candidatePool.map { video ->
                var score = 0
                val uploaderName = (video.uploaderName ?: "").lowercase()
                val cId = video.uploaderUrl ?: ""
                val videoUrl = video.url ?: ""

                if (subscribedChannelNames.contains(uploaderName) || subscribedChannelIds.contains(cId)) {
                    score += 60
                }
                if (topLikedChannels.contains(uploaderName)) {
                    score += 35
                }
                if (watchLaterVideoIds.contains(videoUrl)) {
                    score += 30
                }

                // Substantial freshness boost for unseen videos upon refresh
                if (isRefresh) {
                    if (!sessionSeenUrls.contains(videoUrl)) {
                        score += 300
                    }
                    score += Random.nextInt(0, 50)
                } else {
                    score += Random.nextInt(0, 20)
                }

                if (historyVideoIds.contains(videoUrl)) {
                    score -= 1000
                }
                Pair(video, score)
            }

            val sorted = scoredItems.filter { it.second > -500 }
                .sortedByDescending { it.second }
                .map { it.first }

            finalFeed.addAll(if (sorted.isNotEmpty()) sorted else trendingVideos)
        }

        // On refresh, guarantee newly surfaced items are prioritized at the top
        val prioritizedFeed = if (isRefresh && sessionSeenUrls.isNotEmpty()) {
            val (freshItems, olderItems) = finalFeed.partition { !sessionSeenUrls.contains(it.url) }
            freshItems + olderItems
        } else {
            finalFeed
        }

        // Register rendered URLs to session memory so subsequent refreshes find fresh content
        val resultList = prioritizedFeed.take(60)
        resultList.forEach { item ->
            item.url?.let { sessionSeenUrls.add(it) }
        }

        // Cache the freshly loaded feed into local database
        if (resultList.isNotEmpty()) {
            try {
                val entities = resultList.map {
                    FeedCacheEntity(
                        videoId = it.url ?: "",
                        title = it.name ?: "",
                        thumbnailUrl = it.thumbnails?.firstOrNull()?.url ?: "",
                        channelName = it.uploaderName ?: "",
                        channelAvatarUrl = it.uploaderAvatars?.firstOrNull()?.url,
                        viewCount = it.viewCount,
                        duration = it.duration,
                        publishedText = it.uploadDate?.toString() ?: it.textualUploadDate ?: ""
                    )
                }
                db.feedCacheDao().clearAll()
                db.feedCacheDao().insertAll(entities)
            } catch (_: Exception) {}
        }

        resultList
    }

    /**
     * Filters and scores a list of video stream items specifically for a single user interest topic.
     */
    private fun filterAndRankForSingleInterest(
        interest: String,
        items: List<StreamInfoItem>,
        historyIds: Set<String>,
        isRefresh: Boolean = false
    ): List<StreamInfoItem> {
        val cleanInterest = interest.trim().lowercase()
        val interestTokens = cleanInterest.split("\\s+".toRegex())
            .filter { it.isNotBlank() && !commonStopWords.contains(it) }

        val scored = items.mapNotNull { item ->
            val url = item.url ?: return@mapNotNull null
            if (url.isBlank()) return@mapNotNull null

            val title = (item.name ?: "").lowercase()
            val uploader = (item.uploaderName ?: "").lowercase()

            var score = 100 // Base score for being from this interest query

            // Exact phrase match in title
            if (title.contains(cleanInterest)) {
                score += 250
            }

            // Exact phrase match in uploader
            if (uploader.contains(cleanInterest)) {
                score += 120
            }

            // Keyword token match
            for (token in interestTokens) {
                if (title.contains(token) || uploader.contains(token)) {
                    score += 60
                }
            }

            // Freshness bonus for unseen videos on refresh
            if (isRefresh) {
                if (!sessionSeenUrls.contains(url)) {
                    score += 350
                }
                score += Random.nextInt(0, 40)
            } else {
                score += Random.nextInt(0, 15)
            }

            // Penalize already watched
            if (historyIds.contains(url)) {
                score -= 800
            }

            Pair(item, score)
        }

        return scored
            .filter { it.second > -500 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.url ?: "" }
    }

    /**
     * Retrieves personalized Shorts feed with explicit user interest filtering.
     */
    suspend fun getPersonalizedShorts(
        maxItems: Int = 40,
        isRefresh: Boolean = false
    ): List<StreamInfoItem> = withContext(Dispatchers.IO) {
        RecommendationEngine.getPersonalizedShortsFeed(db, context, maxItems)
    }

    /**
     * Loads the next page of videos for the Home Screen feed (All tab)
     * using user interests and discovery queries.
     */
    suspend fun getHomeFeedNextPage(
        pageNumber: Int,
        existingUrls: Set<String>
    ): List<StreamInfoItem> = withContext(Dispatchers.IO) {
        val userInterests = try {
            SettingsManager.getUserInterests(context).first()
                .map { it.trim() }
                .filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }

        val collected = mutableListOf<StreamInfoItem>()
        val seen = existingUrls.toMutableSet()

        fun addIfNew(item: StreamInfoItem) {
            val u = item.url ?: return
            if (u.isNotBlank() && !seen.contains(u)) {
                seen.add(u)
                collected.add(item)
            }
        }

        if (userInterests.isNotEmpty()) {
            coroutineScope {
                userInterests.map { interest ->
                    async {
                        try {
                            val pageQuery = "$interest page $pageNumber"
                            val search = ExtractorHelper.searchYouTube(pageQuery, pages = 1)
                            search.relatedItems.filterIsInstance<StreamInfoItem>()
                        } catch (_: Exception) {
                            emptyList<StreamInfoItem>()
                        }
                    }
                }.awaitAll().flatten().forEach { addIfNew(it) }
            }
        }

        // Also fetch from broader discovery if pool is low
        if (collected.size < 15) {
            try {
                val trendingQueries = listOf("trending videos", "popular videos today", "recommended for you")
                val q = trendingQueries.getOrElse(pageNumber % trendingQueries.size) { "trending videos" }
                val search = ExtractorHelper.searchYouTube(q, pages = 1)
                search.relatedItems.filterIsInstance<StreamInfoItem>().forEach { addIfNew(it) }
            } catch (_: Exception) {}
        }

        val pageResults = collected.shuffled().take(25)
        pageResults.forEach { it.url?.let { u -> sessionSeenUrls.add(u) } }
        pageResults
    }

    /**
     * Retrieves personalized Playlists based on user interests and seed variety.
     */
    suspend fun getRecommendedPlaylists(
        isRefresh: Boolean = false,
        refreshCount: Int = 0
    ): List<RecommendedPlaylistInfo> = withContext(Dispatchers.IO) {
        RecommendationEngine.getRecommendedPlaylists(db, context, limit = 12, isRefresh = isRefresh, refreshCount = refreshCount)
    }

    /**
     * Loads feed for custom named tabs (e.g., "Music", "Gaming", "News")
     * with dynamic rotated query support on refresh.
     */
    suspend fun loadCustomTabFeed(
        tabName: String,
        isRefresh: Boolean = false,
        refreshCount: Int = 0
    ): Pair<List<StreamInfoItem>, ListExtractorPagingSource?> = withContext(Dispatchers.IO) {
        try {
            val queryVariants = when (tabName.lowercase().trim()) {
                "music" -> listOf(
                    "trending music videos",
                    "new music releases 2026",
                    "top hits today",
                    "latest official music video",
                    "popular songs live",
                    "viral music hits",
                    "top billboard songs"
                )
                "gaming" -> listOf(
                    "trending gameplay videos",
                    "latest gaming news and walkthroughs",
                    "new game releases 2026",
                    "top gaming highlights",
                    "epic esports plays",
                    "best games review"
                )
                "news" -> listOf(
                    "breaking news today",
                    "latest world news updates",
                    "top headlines today",
                    "global news report",
                    "daily news recap"
                )
                "sports" -> listOf(
                    "latest sports highlights",
                    "sports news today",
                    "best athletic moments",
                    "top match plays this week",
                    "championship highlights"
                )
                "entertainment" -> listOf(
                    "entertainment news today",
                    "new movie trailers 2026",
                    "celebrity spotlight and interviews",
                    "trending pop culture"
                )
                "tech", "technology" -> listOf(
                    "latest technology reviews",
                    "new tech gadgets 2026",
                    "future tech breakthroughs",
                    "smartphone reviews and unboxing",
                    "ai and tech news"
                )
                else -> listOf(
                    "$tabName latest uploads",
                    "$tabName new videos",
                    "$tabName trending today",
                    "$tabName popular this week",
                    "$tabName highlights",
                    "$tabName best videos"
                )
            }

            val query = if (isRefresh) {
                val effectiveRefresh = if (refreshCount > 0) refreshCount else globalRefreshCount + 1
                val idx = Math.abs(effectiveRefresh + tabName.hashCode()) % queryVariants.size
                queryVariants[idx]
            } else {
                tabName
            }

            val source = ExtractorHelper.getSearchPagingSource(query)
            val initial = source.loadInitial().filterIsInstance<StreamInfoItem>()
            val resultItems = if (isRefresh && sessionSeenUrls.isNotEmpty()) {
                val (unseen, seen) = initial.partition { !sessionSeenUrls.contains(it.url) }
                unseen + seen
            } else {
                initial
            }

            resultItems.take(30).forEach { item ->
                item.url?.let { sessionSeenUrls.add(it) }
            }

            Pair(resultItems, source)
        } catch (e: Exception) {
            Pair(emptyList(), null)
        }
    }

    /**
     * Clears all cached feed data when interests are updated.
     */
    suspend fun clearFeedCache() = withContext(Dispatchers.IO) {
        try {
            sessionSeenUrls.clear()
            db.feedCacheDao().clearAll()
        } catch (_: Exception) {}
    }
}
