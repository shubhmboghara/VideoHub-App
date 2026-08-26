package com.videhub.recommendation

import android.content.Context
import com.videhub.data.AppDatabase
import com.videhub.data.SettingsManager
import com.videhub.extractor.ExtractorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.random.Random

object RecommendationEngine {

    private val commonStopWords = setOf(
        "the", "and", "a", "an", "in", "on", "at", "for", "with", "about", "against", "between",
        "into", "through", "during", "before", "after", "above", "below", "to", "from", "up",
        "down", "of", "off", "over", "under", "again", "further", "then", "once", "here",
        "there", "when", "where", "why", "how", "all", "any", "both", "each", "few", "more",
        "most", "other", "some", "such", "no", "nor", "not", "only", "own", "same", "so",
        "than", "too", "very", "s", "t", "can", "will", "just", "don", "should", "now",
        "video", "official", "audio", "full", "hd", "4k", "feat", "ft", "lyrics"
    )

    suspend fun getPartialRecommendedFeed(
        db: AppDatabase,
        context: Context,
        cachedSubscribedVideos: List<StreamInfoItem>
    ): List<InfoItem> = getRecommendedFeed(db, context, isRefresh = true)

    /**
     * Comprehensive recommendation engine that delegates to FeedRepository for interest filtering,
     * affinity ranking, and NewPipeExtractor stream handling.
     */
    suspend fun getRecommendedFeed(
        db: AppDatabase,
        context: Context,
        isRefresh: Boolean = false
    ): List<InfoItem> = com.videhub.data.repository.FeedRepository(db, context).getHomeFeed(isRefresh)

    /**
     * Highly personalized Shorts feed driven by:
     * - User Interests (selected topics & custom keywords) - TOP PRIORITY
     * - Liked Videos & Subscribed Channels
     * - Watch History
     */
    suspend fun getPersonalizedShortsFeed(
        db: AppDatabase,
        context: Context,
        maxItems: Int = 50
    ): List<StreamInfoItem> = withContext(Dispatchers.IO) {
        val channels = db.channelDao().getAllOnce()
        val history = db.historyDao().getAllHistoryOnce()
        val liked = db.likedVideoDao().getAllOnce()
        val userInterests = try {
            SettingsManager.getUserInterests(context).first()
                .map { it.trim() }
                .filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }

        val directInterestShortUrls = mutableSetOf<String>()
        val collectedShorts = mutableListOf<StreamInfoItem>()

        // 1. Fetch dedicated Shorts for EVERY user interest
        if (userInterests.isNotEmpty()) {
            coroutineScope {
                val deferredInterests = userInterests.map { interest ->
                    async {
                        val list = mutableListOf<StreamInfoItem>()
                        try {
                            val items1 = ExtractorHelper.getShortsFeed(interest, maxPages = 2)
                            list.addAll(items1)
                        } catch (_: Exception) {}
                        try {
                            val items2 = ExtractorHelper.getShortsFeed("$interest shorts", maxPages = 2)
                            list.addAll(items2)
                        } catch (_: Exception) {}
                        list
                    }
                }
                deferredInterests.awaitAll().forEach { list ->
                    val valid = list.filter { it.url?.isNotBlank() == true }
                    valid.forEach { directInterestShortUrls.add(it.url ?: "") }
                    collectedShorts.addAll(valid)
                }
            }
        }

        // 2. Fetch shorts from liked creators & subscribed channels
        val creatorQueries = mutableListOf<String>()
        liked.map { it.channelName }.filter { it.isNotBlank() }.distinct().take(3).forEach {
            creatorQueries.add("$it shorts")
        }
        channels.map { it.name }.filter { it.isNotBlank() }.distinct().take(3).forEach {
            creatorQueries.add("$it shorts")
        }

        if (creatorQueries.isNotEmpty()) {
            coroutineScope {
                val deferredCreators = creatorQueries.map { query ->
                    async {
                        try {
                            ExtractorHelper.getShortsFeed(query, maxPages = 1)
                        } catch (_: Exception) {
                            emptyList<StreamInfoItem>()
                        }
                    }
                }
                deferredCreators.awaitAll().forEach { collectedShorts.addAll(it) }
            }
        }

        // 3. Fallback discovery if collected pool is small
        if (collectedShorts.size < 15) {
            try {
                val trendingShorts = ExtractorHelper.getShortsFeed("trending", maxPages = 2)
                collectedShorts.addAll(trendingShorts)
            } catch (_: Exception) {}
        }

        // Deduplicate
        val uniqueShorts = collectedShorts
            .filter { it.url?.isNotBlank() == true }
            .distinctBy { it.url }

        // Score based on user affinity
        val userLikedChannels = liked.map { it.channelName.lowercase() }.toSet()
        val userSubscribedChannels = channels.map { it.name.lowercase() }.toSet()
        val historyWatchedUrls = history.map { it.videoId }.toSet()

        val ranked = uniqueShorts.map { short ->
            var score = 0
            val uploader = (short.uploaderName ?: "").lowercase()
            val title = (short.name ?: "").lowercase()
            val shortUrl = short.url ?: ""

            // Massive boost for explicit user interest (+500)
            if (directInterestShortUrls.contains(shortUrl)) {
                score += 500
            }

            // Keyword match to user interests (+200)
            val matchesInterest = userInterests.any { interest ->
                val cleanInt = interest.lowercase()
                title.contains(cleanInt) || uploader.contains(cleanInt)
            }
            if (matchesInterest) {
                score += 200
            }

            if (userSubscribedChannels.contains(uploader)) score += 40
            if (userLikedChannels.contains(uploader)) score += 30

            // Slight random jitter for varied ordering
            score += Random.nextInt(0, 20)

            // Penalize already watched
            if (historyWatchedUrls.contains(shortUrl)) {
                score -= 500
            }

            Pair(short, score)
        }.sortedByDescending { it.second }.map { it.first }

        if (ranked.isNotEmpty()) ranked.take(maxItems) else ExtractorHelper.getShortsFeed("trending", maxPages = 3)
    }

    suspend fun getRecommendedPlaylists(
        db: AppDatabase,
        context: Context,
        limit: Int = 12,
        isRefresh: Boolean = false,
        refreshCount: Int = 0
    ): List<RecommendedPlaylistInfo> = withContext(Dispatchers.IO) {
        try {
            val history = db.historyDao().getAllHistoryOnce()
            val liked = db.likedVideoDao().getAllOnce()
            val watchLater = db.watchLaterDao().getAllOnce()
            val channels = db.channelDao().getAllOnce()
            val userPlaylists = db.playlistDao().getAllPlaylistsOnce()
            val userInterests = try {
                SettingsManager.getUserInterests(context).first()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            } catch (_: Exception) {
                emptyList()
            }

            // 1. Extract taste seeds
            val channelFrequencies = mutableMapOf<String, Int>()
            history.forEach { 
                if (it.channelName.isNotBlank()) {
                    channelFrequencies[it.channelName] = (channelFrequencies[it.channelName] ?: 0) + 1
                }
            }
            liked.forEach { 
                if (it.channelName.isNotBlank()) {
                    channelFrequencies[it.channelName] = (channelFrequencies[it.channelName] ?: 0) + 2
                }
            }
            watchLater.forEach { 
                if (it.channelName.isNotBlank()) {
                    channelFrequencies[it.channelName] = (channelFrequencies[it.channelName] ?: 0) + 1
                }
            }
            channels.forEach {
                if (it.name.isNotBlank()) {
                    channelFrequencies[it.name] = (channelFrequencies[it.name] ?: 0) + 2
                }
            }

            val topCreators = channelFrequencies.entries
                .sortedByDescending { it.value }
                .map { it.key }
                .let { if (isRefresh) it.shuffled() else it }
                .take(4)

            // 2. Build search seed list
            data class QuerySeed(val query: String, val reason: String, val isMix: Boolean)
            val querySeeds = mutableListOf<QuerySeed>()

            // User explicit interests for playlists (Top Priority)
            val selectedInterests = if (isRefresh) userInterests.shuffled() else userInterests
            selectedInterests.take(4).forEach { interest ->
                val q = if (isRefresh) "$interest best mix" else "$interest playlist"
                querySeeds.add(QuerySeed(q, "Based on your interest: $interest", isRefresh))
            }

            // User created playlists
            userPlaylists.filter { it.name.isNotBlank() && it.name != "Favorites" }.take(2).forEach { pl ->
                querySeeds.add(QuerySeed("${pl.name} playlist", "Inspired by your playlist '${pl.name}'", false))
            }

            topCreators.forEachIndexed { index, creator ->
                if (index % 2 == 0) {
                    querySeeds.add(QuerySeed("$creator mix", "Mix • $creator", true))
                } else {
                    querySeeds.add(QuerySeed("$creator playlist", "Based on $creator", false))
                }
            }

            // Add standard popular fallbacks if needed
            val fallbackPool = listOf(
                QuerySeed("Top Hits Music Mix", "Trending YouTube Mix", true),
                QuerySeed("Best Lo-Fi Chill Beats Playlist", "Relax & Focus Playlists", false),
                QuerySeed("Trending Podcasts Playlist", "Recommended Podcasts", false),
                QuerySeed("Viral Music Hits Mix", "Trending Mixes", true),
                QuerySeed("Top Workout Energy Mix", "Workout & Energy", true),
                QuerySeed("Acoustic Vibes Playlist", "Acoustic & Chill", false)
            )
            val fallbackSeeds = if (isRefresh) fallbackPool.shuffled() else fallbackPool

            for (fallback in fallbackSeeds) {
                if (querySeeds.size < 6) {
                    querySeeds.add(fallback)
                }
            }

            // Fetch playlists in parallel
            val playlistResults = mutableListOf<RecommendedPlaylistInfo>()
            coroutineScope {
                val deferredList = querySeeds.take(6).map { seed ->
                    async {
                        try {
                            val items = ExtractorHelper.searchPlaylists(seed.query).take(4)
                            items.map { item ->
                                val thumbUrl = item.thumbnails?.firstOrNull()?.url ?: ""
                                RecommendedPlaylistInfo(
                                    url = item.url ?: "",
                                    name = item.name ?: "Playlist",
                                    uploaderName = item.uploaderName ?: "YouTube",
                                    uploaderUrl = item.uploaderUrl,
                                    thumbnailUrl = thumbUrl,
                                    streamCount = item.streamCount,
                                    reason = seed.reason,
                                    isMix = seed.isMix || (item.name?.contains("mix", ignoreCase = true) == true)
                                )
                            }
                        } catch (_: Exception) {
                            emptyList<RecommendedPlaylistInfo>()
                        }
                    }
                }
                deferredList.awaitAll().forEach {
                    playlistResults.addAll(it)
                }
            }

            // Deduplicate and return
            playlistResults
                .filter { it.url.isNotBlank() && it.thumbnailUrl.isNotBlank() }
                .distinctBy { it.url }
                .take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractKeywords(title: String): List<String> {
        return title.lowercase()
            .replace(Regex("[^a-zA-Z0-9 ]"), " ")
            .split(" ")
            .map { it.trim() }
            .filter { it.length > 2 && !commonStopWords.contains(it) }
    }
}

data class RecommendedPlaylistInfo(
    val url: String,
    val name: String,
    val uploaderName: String,
    val uploaderUrl: String? = null,
    val thumbnailUrl: String,
    val streamCount: Long = -1L,
    val reason: String = "Recommended for you",
    val isMix: Boolean = false
)
