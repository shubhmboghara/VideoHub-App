package com.videhub.recommendation

import android.content.Context
import com.videhub.data.AppDatabase
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
        "trending", "popular", "new", "top", "viral", "mix", "latest", "featured", "highlights"
    )

    suspend fun getPartialRecommendedFeed(
        db: AppDatabase,
        context: Context,
        cachedSubscribedVideos: List<StreamInfoItem>
    ): List<InfoItem> = getRecommendedFeed(db, context, isRefresh = true)

    suspend fun getRecommendedFeed(
        db: AppDatabase,
        context: Context,
        isRefresh: Boolean = false
    ): List<InfoItem> = withContext(Dispatchers.IO) {
        val channels = db.channelDao().getAllOnce()
        val subscribedVideos = mutableListOf<StreamInfoItem>()
        
        // 1. Fetch latest videos from subscribed channels in parallel (shuffled selection if many channels)
        val selectedChannels = if (channels.size > 10) channels.shuffled().take(10) else channels
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
                    } catch (e: Exception) {
                        emptyList<StreamInfoItem>()
                    }
                }
            }
            subscribedVideos.addAll(deferredChannels.awaitAll().flatten())
        }

        // 2. Fetch related videos based on local interaction (varied seeds)
        val localSeedVideos = mutableListOf<String>()
        val liked = db.likedVideoDao().getAllOnce()
        val history = db.historyDao().getAllHistoryOnce()
        val watchLater = db.watchLaterDao().getAllOnce()
        val downloads = db.downloadedVideoDao().getAllDownloadsSync()
        
        localSeedVideos.addAll(liked.map { it.videoId })
        localSeedVideos.addAll(history.map { it.videoId })
        localSeedVideos.addAll(watchLater.map { it.videoId })
        localSeedVideos.addAll(downloads.map { it.videoId })
        
        val seedUrls = localSeedVideos.distinct().shuffled().take(4)
        val relatedToLocal = mutableListOf<StreamInfoItem>()
        if (seedUrls.isNotEmpty()) {
            coroutineScope {
                val deferredRelated = seedUrls.map { url ->
                    async {
                        try {
                            val info = ExtractorHelper.getStreamInfo(url, true)
                            info.relatedItems?.filterIsInstance<StreamInfoItem>()?.let {
                                if (isRefresh) it.shuffled() else it
                            }?.take(8) ?: emptyList()
                        } catch(e: Exception) {
                            emptyList<StreamInfoItem>()
                        }
                    }
                }
                relatedToLocal.addAll(deferredRelated.awaitAll().flatten())
            }
        }

        // 3. Fetch trending/topic videos with varied dynamic keywords
        val savedString = try {
            com.videhub.data.SettingsManager.getCustomTabs(context).first()
        } catch (e: Exception) {
            "Music,Gaming,News,Sports"
        }
        val defaultTopics = listOf("Music", "Gaming", "News", "Sports", "Podcasts", "Technology", "Entertainment")
        val userTopics = savedString.split(",").filter { it.isNotBlank() && it.trim() != "All" && it.trim() != "Home" }.map { it.trim() }
        val topics = (if (userTopics.isNotEmpty()) userTopics else defaultTopics).shuffled().take(4)
        
        val topicTrendingVideos = mutableListOf<StreamInfoItem>()
        if (topics.isNotEmpty()) {
            coroutineScope {
                val deferredTopics = topics.map { topic ->
                    async {
                        try {
                            val modifier = queryModifiers.shuffled().first()
                            val topicQuery = "$topic $modifier"
                            val searchResult = ExtractorHelper.searchYouTube(topicQuery, pages = 1)
                            searchResult.relatedItems.filterIsInstance<StreamInfoItem>()
                                .let { if (isRefresh) it.shuffled() else it }
                                .take(8)
                        } catch (e: Exception) {
                            emptyList<StreamInfoItem>()
                        }
                    }
                }
                topicTrendingVideos.addAll(deferredTopics.awaitAll().flatten())
            }
        }

        // 4. Fetch general trending videos
        val generalTrendingVideos = mutableListOf<StreamInfoItem>()
        try {
            val trending = ExtractorHelper.getTrending()
            val trendingItems = trending.filterIsInstance<StreamInfoItem>()
            if (isRefresh) {
                generalTrendingVideos.addAll(trendingItems.shuffled().take(15))
            } else {
                generalTrendingVideos.addAll(trendingItems.take(15))
            }
        } catch (e: Exception) {
            // Ignore failure
        }

        // Combine into candidate pool and remove duplicates
        val candidatePool = (subscribedVideos + relatedToLocal + topicTrendingVideos + generalTrendingVideos)
            .filter { it.url?.isNotBlank() == true }
            .distinctBy { it.url ?: "" }

        // Local data for scoring
        val topLikedChannels = liked.groupBy { it.channelName }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(5)
            .map { it.key }
            .toSet()
        val historyVideoIds = history.map { it.videoId }.toSet()
        val historyChannelNames = history.map { it.channelName }.toSet()
        val subscribedChannelNames = channels.map { it.name }.toSet()
        val subscribedChannelIds = channels.map { it.channelId }.toSet()

        // 5. Score each candidate with freshness randomness
        val scoredItems = candidatePool.map { video ->
            var score = 0
            
            // Subscribed channels boost
            val cId = video.uploaderUrl ?: ""
            val uploaderName = video.uploaderName ?: ""
            val isSubscribed = subscribedChannelNames.contains(uploaderName) || subscribedChannelIds.contains(cId)
            if (isSubscribed) {
                score += 45
            }
            
            // Liked channel affinity
            if (topLikedChannels.contains(uploaderName)) {
                score += 30
            }
            
            // Topic match
            val titleLower = (video.name ?: "").lowercase()
            val matchesTopic = topics.any { topic -> titleLower.contains(topic.lowercase()) }
            if (matchesTopic) {
                score += 20
            }
            
            // Channel from history
            if (historyChannelNames.contains(uploaderName)) {
                score += 15
            }
            
            // Freshness jitter on refresh / recommendation
            score += Random.nextInt(0, 30)
            
            // Demote already watched
            if (historyVideoIds.contains(video.url ?: "")) {
                score -= 1000
            }
            
            Pair(video, score)
        }

        // 6. Sort descending, filter out heavily penalized items, cap and return
        scoredItems.filter { it.second > -500 }
            .sortedByDescending { it.second }
            .take(50)
            .map { it.first }
    }
}