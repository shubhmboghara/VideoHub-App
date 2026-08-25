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

    private val queryModifiers = listOf(
        "trending", "popular", "new", "top", "viral", "mix", "latest", "featured", "highlights", "best"
    )

    private val commonStopWords = setOf(
        "the", "and", "a", "an", "in", "on", "at", "for", "with", "about", "against", "between",
        "into", "through", "during", "before", "after", "above", "below", "to", "from", "up",
        "down", "of", "off", "over", "under", "again", "further", "then", "once", "here",
        "there", "when", "where", "why", "how", "all", "any", "both", "each", "few", "more",
        "most", "other", "some", "such", "no", "nor", "not", "only", "own", "same", "so",
        "than", "too", "very", "s", "t", "can", "will", "just", "don", "should", "now",
        "video", "official", "audio", "full", "hd", "4k", "music", "feat", "ft", "lyrics"
    )

    suspend fun getPartialRecommendedFeed(
        db: AppDatabase,
        context: Context,
        cachedSubscribedVideos: List<StreamInfoItem>
    ): List<InfoItem> = getRecommendedFeed(db, context, isRefresh = true)

    /**
     * Comprehensive recommendation engine that aggregates signals from:
     * 1. Watch History (HistoryDao)
     * 2. Liked Videos (LikedVideoDao)
     * 3. Watch Later (WatchLaterDao)
     * 4. User Playlists & Playlist Videos (PlaylistDao)
     * 5. Subscribed Channels (ChannelDao)
     * 6. Search Queries (SearchHistoryDao)
     * 7. User Explicit Interests (SettingsManager)
     */
    suspend fun getRecommendedFeed(
        db: AppDatabase,
        context: Context,
        isRefresh: Boolean = false
    ): List<InfoItem> = withContext(Dispatchers.IO) {
        val channels = db.channelDao().getAllOnce()
        val history = db.historyDao().getAllHistoryOnce()
        val liked = db.likedVideoDao().getAllOnce()
        val watchLater = db.watchLaterDao().getAllOnce()
        val playlistVideos = db.playlistDao().getAllPlaylistVideosOnce()
        val userPlaylists = db.playlistDao().getAllPlaylistsOnce()
        val searchHistory = db.searchHistoryDao().getAllSearchHistoryOnce()
        val userInterests = try {
            SettingsManager.getUserInterests(context).first()
        } catch (_: Exception) {
            emptyList()
        }

        // 1. Fetch latest videos from subscribed channels in parallel
        val subscribedVideos = mutableListOf<StreamInfoItem>()
        val selectedChannels = if (channels.size > 12) channels.shuffled().take(12) else channels
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
                        val channelVideos = ExtractorHelper.getChannelVideos(info, maxPages = 1)
                            .filterIsInstance<StreamInfoItem>()
                            .let { if (isRefresh) it.shuffled() else it }
                            .take(4)

                        // Attach avatar for UI consistency
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

        // 2. Fetch related videos based on rich local seeds (Liked, History, Watch Later, Playlists)
        val seedUrls = mutableListOf<String>()
        // Give heavy priority to liked and watch later
        seedUrls.addAll(liked.take(6).map { it.videoId })
        seedUrls.addAll(watchLater.take(6).map { it.videoId })
        seedUrls.addAll(playlistVideos.take(6).map { it.videoId })
        seedUrls.addAll(history.take(6).map { it.videoId })

        val uniqueSeedUrls = seedUrls.filter { it.isNotBlank() }.distinct().shuffled().take(5)
        val relatedToLocal = mutableListOf<StreamInfoItem>()
        if (uniqueSeedUrls.isNotEmpty()) {
            coroutineScope {
                val deferredRelated = uniqueSeedUrls.map { url ->
                    async {
                        try {
                            val info = ExtractorHelper.getStreamInfo(url, true)
                            info.relatedItems?.filterIsInstance<StreamInfoItem>()?.let {
                                if (isRefresh) it.shuffled() else it
                            }?.take(8) ?: emptyList()
                        } catch (_: Exception) {
                            emptyList<StreamInfoItem>()
                        }
                    }
                }
                relatedToLocal.addAll(deferredRelated.awaitAll().flatten())
            }
        }

        // 3. Extract weighted topic keywords from history, liked, playlists, searches, and explicit interests
        val keywordWeights = mutableMapOf<String, Int>()

        // Explicit user interests get maximum initial boost
        userInterests.forEach { interest ->
            val clean = interest.trim().lowercase()
            if (clean.isNotBlank()) {
                keywordWeights[clean] = (keywordWeights[clean] ?: 0) + 15
            }
        }

        // Liked video titles
        liked.forEach {
            extractKeywords(it.title).forEach { kw ->
                keywordWeights[kw] = (keywordWeights[kw] ?: 0) + 4
            }
            if (it.channelName.isNotBlank()) {
                val chan = it.channelName.trim().lowercase()
                keywordWeights[chan] = (keywordWeights[chan] ?: 0) + 5
            }
        }

        // Watch Later titles
        watchLater.forEach {
            extractKeywords(it.title).forEach { kw ->
                keywordWeights[kw] = (keywordWeights[kw] ?: 0) + 3
            }
        }

        // Playlist names & videos
        userPlaylists.forEach {
            val plName = it.name.trim().lowercase()
            if (plName.isNotBlank() && plName != "favorites") {
                keywordWeights[plName] = (keywordWeights[plName] ?: 0) + 4
            }
        }
        playlistVideos.forEach {
            extractKeywords(it.title).forEach { kw ->
                keywordWeights[kw] = (keywordWeights[kw] ?: 0) + 3
            }
        }

        // Recent search history
        searchHistory.forEach {
            val query = it.query.trim().lowercase()
            if (query.isNotBlank()) {
                keywordWeights[query] = (keywordWeights[query] ?: 0) + 4
            }
        }

        // History video titles
        history.take(20).forEach {
            extractKeywords(it.title).forEach { kw ->
                keywordWeights[kw] = (keywordWeights[kw] ?: 0) + 2
            }
        }

        val topTopics = keywordWeights.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(6)

        val topicVideos = mutableListOf<StreamInfoItem>()
        if (topTopics.isNotEmpty()) {
            coroutineScope {
                val deferredTopics = topTopics.map { topic ->
                    async {
                        try {
                            val modifier = queryModifiers.shuffled().first()
                            val topicQuery = "$topic $modifier"
                            val searchResult = ExtractorHelper.searchYouTube(topicQuery, pages = 1)
                            searchResult.relatedItems.filterIsInstance<StreamInfoItem>()
                                .let { if (isRefresh) it.shuffled() else it }
                                .take(8)
                        } catch (_: Exception) {
                            emptyList<StreamInfoItem>()
                        }
                    }
                }
                topicVideos.addAll(deferredTopics.awaitAll().flatten())
            }
        }

        // 4. Fetch general trending videos as discovery floor
        val generalTrendingVideos = mutableListOf<StreamInfoItem>()
        try {
            val trending = ExtractorHelper.getTrending()
            val trendingItems = trending.filterIsInstance<StreamInfoItem>()
            if (isRefresh) {
                generalTrendingVideos.addAll(trendingItems.shuffled().take(15))
            } else {
                generalTrendingVideos.addAll(trendingItems.take(15))
            }
        } catch (_: Exception) {}

        // Combine into candidate pool and remove duplicates
        val candidatePool = (subscribedVideos + relatedToLocal + topicVideos + generalTrendingVideos)
            .filter { it.url?.isNotBlank() == true }
            .distinctBy { it.url ?: "" }

        // Local data sets for multi-factor scoring
        val topLikedChannels = liked.groupBy { it.channelName }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(8)
            .map { it.key.lowercase() }
            .toSet()

        val historyVideoIds = history.map { it.videoId }.toSet()
        val historyChannelNames = history.map { it.channelName.lowercase() }.toSet()
        val subscribedChannelNames = channels.map { it.name.lowercase() }.toSet()
        val subscribedChannelIds = channels.map { it.channelId }.toSet()
        val watchLaterVideoIds = watchLater.map { it.videoId }.toSet()

        // 5. Multi-factor scoring
        val scoredItems = candidatePool.map { video ->
            var score = 0
            val uploaderName = (video.uploaderName ?: "").lowercase()
            val cId = video.uploaderUrl ?: ""
            val titleLower = (video.name ?: "").lowercase()

            // Subscribed channels boost (+50)
            if (subscribedChannelNames.contains(uploaderName) || subscribedChannelIds.contains(cId)) {
                score += 50
            }

            // Liked channel affinity (+35)
            if (topLikedChannels.contains(uploaderName)) {
                score += 35
            }

            // History creator affinity (+20)
            if (historyChannelNames.contains(uploaderName)) {
                score += 20
            }

            // User explicit interests matching (+35)
            val matchesUserInterest = userInterests.any { interest ->
                titleLower.contains(interest.lowercase()) || uploaderName.contains(interest.lowercase())
            }
            if (matchesUserInterest) {
                score += 35
            }

            // Dynamic weighted keywords match (+25)
            val matchesKeywords = topTopics.any { topic -> titleLower.contains(topic) }
            if (matchesKeywords) {
                score += 25
            }

            // Boost videos in watch later intent (+30)
            if (watchLaterVideoIds.contains(video.url ?: "")) {
                score += 30
            }

            // Freshness / Discovery jitter
            score += Random.nextInt(0, 30)

            // Penalize already watched
            if (historyVideoIds.contains(video.url ?: "")) {
                score -= 1000
            }

            Pair(video, score)
        }

        // 6. Sort descending, filter out heavily penalized items, cap and return
        scoredItems.filter { it.second > -500 }
            .sortedByDescending { it.second }
            .take(60)
            .map { it.first }
    }

    /**
     * Highly personalized Shorts feed driven by:
     * - User Interests (selected topics & custom keywords)
     * - Watch History
     * - Liked Videos
     * - Watch Later & Playlists
     * - Subscribed Channels
     * - Recent Search History
     */
    suspend fun getPersonalizedShortsFeed(
        db: AppDatabase,
        context: Context,
        maxItems: Int = 50
    ): List<StreamInfoItem> = withContext(Dispatchers.IO) {
        val channels = db.channelDao().getAllOnce()
        val history = db.historyDao().getAllHistoryOnce()
        val liked = db.likedVideoDao().getAllOnce()
        val watchLater = db.watchLaterDao().getAllOnce()
        val playlists = db.playlistDao().getAllPlaylistsOnce()
        val playlistVideos = db.playlistDao().getAllPlaylistVideosOnce()
        val searchHistory = db.searchHistoryDao().getAllSearchHistoryOnce()
        val userInterests = try {
            SettingsManager.getUserInterests(context).first()
        } catch (_: Exception) {
            emptyList()
        }

        // Build focused Shorts query seeds based on all user signals
        val shortsQueries = mutableListOf<String>()

        // 1. Explicit user interests
        userInterests.shuffled().take(4).forEach { interest ->
            shortsQueries.add("$interest shorts")
            shortsQueries.add("#shorts $interest")
        }

        // 2. Liked video creators & keywords
        val topLikedCreators = liked.map { it.channelName }.filter { it.isNotBlank() }.distinct().shuffled().take(3)
        topLikedCreators.forEach { creator ->
            shortsQueries.add("$creator shorts")
        }

        // 3. Playlists topics
        playlists.filter { it.name.isNotBlank() && it.name != "Favorites" }.shuffled().take(2).forEach { pl ->
            shortsQueries.add("${pl.name} shorts")
        }

        // 4. Recent search queries
        searchHistory.take(3).forEach {
            shortsQueries.add("${it.query} shorts")
        }

        // 5. Watch history top creators
        val historyCreators = history.map { it.channelName }.filter { it.isNotBlank() }.distinct().shuffled().take(3)
        historyCreators.forEach { creator ->
            shortsQueries.add("$creator #shorts")
        }

        // 6. Subscribed channels
        channels.shuffled().take(3).forEach { chan ->
            shortsQueries.add("${chan.name} shorts")
        }

        // Fallback popular seeds if queries are sparse
        if (shortsQueries.size < 5) {
            listOf("viral shorts", "trending shorts", "top shorts", "funny shorts", "tech shorts", "gaming shorts").forEach {
                shortsQueries.add(it)
            }
        }

        val selectedQueries = shortsQueries.distinct().shuffled().take(6)
        val collectedShorts = mutableListOf<StreamInfoItem>()

        coroutineScope {
            val deferredList = selectedQueries.map { query ->
                async {
                    try {
                        ExtractorHelper.getShortsFeed(query, maxPages = 2)
                    } catch (_: Exception) {
                        emptyList<StreamInfoItem>()
                    }
                }
            }
            deferredList.awaitAll().forEach {
                collectedShorts.addAll(it)
            }
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

            if (userSubscribedChannels.contains(uploader)) score += 40
            if (userLikedChannels.contains(uploader)) score += 30

            userInterests.forEach { interest ->
                if (title.contains(interest.lowercase()) || uploader.contains(interest.lowercase())) {
                    score += 25
                }
            }

            // Slight random jitter for varied ordering
            score += Random.nextInt(0, 20)

            // Penalize already watched
            if (historyWatchedUrls.contains(short.url ?: "")) {
                score -= 200
            }

            Pair(short, score)
        }.sortedByDescending { it.second }.map { it.first }

        if (ranked.isNotEmpty()) ranked.take(maxItems) else ExtractorHelper.getShortsFeed("trending", maxPages = 3)
    }

    suspend fun getRecommendedPlaylists(
        db: AppDatabase,
        context: Context,
        limit: Int = 12
    ): List<RecommendedPlaylistInfo> = withContext(Dispatchers.IO) {
        try {
            val history = db.historyDao().getAllHistoryOnce()
            val liked = db.likedVideoDao().getAllOnce()
            val watchLater = db.watchLaterDao().getAllOnce()
            val channels = db.channelDao().getAllOnce()
            val userPlaylists = db.playlistDao().getAllPlaylistsOnce()
            val userInterests = try {
                SettingsManager.getUserInterests(context).first()
            } catch (_: Exception) {
                emptyList()
            }

            // 1. Extract taste seeds (top artists, creators, channels)
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
                .take(4)

            // 2. Build search seed list
            data class QuerySeed(val query: String, val reason: String, val isMix: Boolean)
            val querySeeds = mutableListOf<QuerySeed>()

            // User explicit interests for playlists
            userInterests.take(3).forEach { interest ->
                querySeeds.add(QuerySeed("$interest playlist", "Based on your interest: $interest", false))
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

            // Add standard popular fallbacks
            val fallbackSeeds = listOf(
                QuerySeed("Top Hits Music Mix", "Trending YouTube Mix", true),
                QuerySeed("Best Lo-Fi Chill Beats Playlist", "Relax & Focus Playlists", false),
                QuerySeed("Trending Podcasts Playlist", "Recommended Podcasts", false),
                QuerySeed("Viral Music Hits Mix", "Trending Mixes", true)
            )

            for (fallback in fallbackSeeds) {
                if (querySeeds.size < 6) {
                    querySeeds.add(fallback)
                }
            }

            // 4. Fetch playlists in parallel
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
