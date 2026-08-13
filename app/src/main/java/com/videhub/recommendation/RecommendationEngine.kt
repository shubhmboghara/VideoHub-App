package com.videhub.recommendation

import android.content.SharedPreferences
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

object RecommendationEngine {

    suspend fun getPartialRecommendedFeed(db: AppDatabase, context: android.content.Context, cachedSubscribedVideos: List<StreamInfoItem>): List<InfoItem> = withContext(Dispatchers.IO) {
        val channels = db.channelDao().getAllOnce()
        // Fetch related videos based on local interaction
        val localSeedVideos = mutableListOf<String>()
        val likedVideos = db.likedVideoDao().getAllOnce()
        val historyVideos = db.historyDao().getAllHistoryOnce()
        val watchLater = db.watchLaterDao().getAllOnce()
        val downloads = db.downloadedVideoDao().getAllDownloadsSync()
        
        localSeedVideos.addAll(likedVideos.map { it.videoId })
        localSeedVideos.addAll(historyVideos.map { it.videoId })
        localSeedVideos.addAll(watchLater.map { it.videoId })
        localSeedVideos.addAll(downloads.map { it.videoId })
        
        val seedUrls = localSeedVideos.shuffled().take(3)
        val relatedToLocal = mutableListOf<StreamInfoItem>()
        coroutineScope {
            val deferredRelated = seedUrls.map { url ->
                async {
                    try {
                        val info = ExtractorHelper.getStreamInfo(url, true)
                        info.relatedItems?.filterIsInstance<StreamInfoItem>()?.take(10) ?: emptyList()
                    } catch(e: Exception) {
                        emptyList<StreamInfoItem>()
                    }
                }
            }
            relatedToLocal.addAll(deferredRelated.awaitAll().flatten())
        }

        val savedString = com.videhub.data.SettingsManager.getCustomTabs(context).first()
        val topics = savedString.split(",").filter { it.isNotBlank() && it.trim() != "All" && it.trim() != "Home" }.map { it.trim() }

        val topicTrendingVideos = mutableListOf<StreamInfoItem>()
        if (topics.isNotEmpty()) {
            coroutineScope {
                val deferredTopics = topics.shuffled().take(3).map { topic ->
                    async {
                        try {
                            val topicQuery = "$topic trending"
                            val searchResult = ExtractorHelper.searchYouTube(topicQuery, pages = 1)
                            searchResult.relatedItems.filterIsInstance<StreamInfoItem>().take(10)
                        } catch (e: Exception) {
                            emptyList<StreamInfoItem>()
                        }
                    }
                }
                topicTrendingVideos.addAll(deferredTopics.awaitAll().flatten())
            }
        }

        val generalTrendingVideos = mutableListOf<StreamInfoItem>()
        if ((cachedSubscribedVideos.size + relatedToLocal.size + topicTrendingVideos.size) < 30) {
            try {
                val trending = ExtractorHelper.getTrending()
                generalTrendingVideos.addAll(trending.filterIsInstance<StreamInfoItem>())
            } catch (e: Exception) {
                // Ignore failure
            }
        }

        val candidatePool = (cachedSubscribedVideos + relatedToLocal + topicTrendingVideos + generalTrendingVideos).distinctBy { it.url ?: "" }

        val topLikedChannels = likedVideos.groupBy { it.channelName }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(5)
            .map { it.key }
            .toSet()

        val historyVideoIds = historyVideos.map { it.videoId }.toSet()
        val historyChannelNames = historyVideos.map { it.channelName }.toSet()

        val subscribedChannelNames = channels.map { it.name }.toSet()
        val subscribedChannelIds = channels.map { it.channelId }.toSet()

        val scoredItems = candidatePool.map { video ->
            var score = 0
            val cId = video.uploaderUrl ?: ""
            val uploaderName = video.uploaderName ?: ""
            val isSubscribed = subscribedChannelNames.contains(uploaderName) || subscribedChannelIds.contains(cId)
            if (isSubscribed) {
                score += 50
            }
            if (topLikedChannels.contains(uploaderName)) {
                score += 30
            }
            val titleLower = (video.name ?: "").lowercase()
            val matchesTopic = topics.any { topic -> titleLower.contains(topic.lowercase()) }
            if (matchesTopic) {
                score += 20
            }
            if (historyChannelNames.contains(uploaderName)) {
                score += 10
            }
            if (historyVideoIds.contains(video.url ?: "")) {
                score -= 1000
            }
            Pair(video, score)
        }

        scoredItems.filter { it.second > -500 }
            .sortedByDescending { it.second }
            .take(50)
            .map { it.first }
    }

    suspend fun getRecommendedFeed(db: AppDatabase, context: android.content.Context): List<InfoItem> = withContext(Dispatchers.IO) {
        val channels = db.channelDao().getAllOnce()
        val subscribedVideos = mutableListOf<StreamInfoItem>()
        
        // 1. Fetch latest videos from subscribed channels in parallel
        coroutineScope {
            val deferredChannels = channels.map { channel ->
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
                            .take(5)
                            
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

        // 2. Fetch related videos based on local interaction
        val localSeedVideos = mutableListOf<String>()
        val liked = db.likedVideoDao().getAllOnce()
        val history = db.historyDao().getAllHistoryOnce()
        val watchLater = db.watchLaterDao().getAllOnce()
        val downloads = db.downloadedVideoDao().getAllDownloadsSync()
        
        localSeedVideos.addAll(liked.map { it.videoId })
        localSeedVideos.addAll(history.map { it.videoId })
        localSeedVideos.addAll(watchLater.map { it.videoId })
        localSeedVideos.addAll(downloads.map { it.videoId })
        
        val seedUrls = localSeedVideos.shuffled().take(3)
        val relatedToLocal = mutableListOf<StreamInfoItem>()
        coroutineScope {
            val deferredRelated = seedUrls.map { url ->
                async {
                    try {
                        val info = ExtractorHelper.getStreamInfo(url, true)
                        info.relatedItems?.filterIsInstance<StreamInfoItem>()?.take(10) ?: emptyList()
                    } catch(e: Exception) {
                        emptyList<StreamInfoItem>()
                    }
                }
            }
            relatedToLocal.addAll(deferredRelated.awaitAll().flatten())
        }

        // 3. Fetch trending videos filtered to user's onboarding topics
        val savedString = com.videhub.data.SettingsManager.getCustomTabs(context).first()
        val topics = savedString.split(",").filter { it.isNotBlank() && it.trim() != "All" && it.trim() != "Home" }.map { it.trim() }
        
        val topicTrendingVideos = mutableListOf<StreamInfoItem>()
        if (topics.isNotEmpty()) {
            coroutineScope {
                val deferredTopics = topics.shuffled().take(3).map { topic ->
                    async {
                        try {
                            val topicQuery = "$topic trending"
                            val searchResult = ExtractorHelper.searchYouTube(topicQuery, pages = 1)
                            searchResult.relatedItems.filterIsInstance<StreamInfoItem>().take(10)
                        } catch (e: Exception) {
                            emptyList<StreamInfoItem>()
                        }
                    }
                }
                topicTrendingVideos.addAll(deferredTopics.awaitAll().flatten())
            }
        }

        // 4. Fallback to general trending if combined pool is small
        val generalTrendingVideos = mutableListOf<StreamInfoItem>()
        if ((subscribedVideos.size + relatedToLocal.size + topicTrendingVideos.size) < 30) {
            try {
                val trending = ExtractorHelper.getTrending()
                generalTrendingVideos.addAll(trending.filterIsInstance<StreamInfoItem>())
            } catch (e: Exception) {
                // Ignore failure
            }
        }

        // Combine into candidate pool and remove duplicates
        val candidatePool = (subscribedVideos + relatedToLocal + topicTrendingVideos + generalTrendingVideos).distinctBy { it.url ?: "" }

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

        // 5. Score each candidate
        val scoredItems = candidatePool.map { video ->
            var score = 0
            
            // +50 if channel matches subscribed
            val cId = video.uploaderUrl ?: ""
            val uploaderName = video.uploaderName ?: ""
            val isSubscribed = subscribedChannelNames.contains(uploaderName) || subscribedChannelIds.contains(cId)
            if (isSubscribed) {
                score += 50
            }
            
            // +30 if from most-frequently-liked channels
            if (topLikedChannels.contains(uploaderName)) {
                score += 30
            }
            
            // +20 if topic string appears in title
            val titleLower = (video.name ?: "").lowercase()
            val matchesTopic = topics.any { topic -> titleLower.contains(topic.lowercase()) }
            if (matchesTopic) {
                score += 20
            }
            
            // +10 if channel appears in watch history
            if (historyChannelNames.contains(uploaderName)) {
                score += 10
            }
            
            // -1000 if in history
            if (historyVideoIds.contains(video.url ?: "")) {
                score -= 1000
            }
            
            Pair(video, score)
        }

        // 6. Sort descending, filter, cap at 50, and return
        scoredItems.filter { it.second > -500 }
            .sortedByDescending { it.second }
            .take(50)
            .map { it.first }
    }
}