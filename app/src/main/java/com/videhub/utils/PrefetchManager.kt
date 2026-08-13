package com.videhub.utils

import android.content.Context
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import com.videhub.service.MediaSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PrefetchManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefetchChannel = Channel<String>(Channel.UNLIMITED)
    private val prefetchedUrls = mutableSetOf<String>()
    private var isProcessing = false

    fun prefetch(context: Context, videoUrl: String) {
        if (prefetchedUrls.contains(videoUrl)) return
        prefetchedUrls.add(videoUrl)
        prefetchChannel.trySend(videoUrl)
        
        if (!isProcessing) {
            isProcessing = true
            scope.launch {
                for (url in prefetchChannel) {
                    processPrefetch(context, url)
                }
                isProcessing = false
            }
        }
    }

    private suspend fun processPrefetch(context: Context, videoUrl: String) {
        try {
            val streamInfo = com.videhub.extractor.ExtractorHelper.getStreamInfo(videoUrl, true)
            val audioUrl = streamInfo.audioStreams?.maxByOrNull { it.averageBitrate }?.content ?: return
            
            prefetchUrl(context, audioUrl)
            
            val videoStreamUrl = streamInfo.videoOnlyStreams?.maxByOrNull { 
                it.getResolution()?.replace("p","")?.toIntOrNull() ?: 0 
            }?.content
            
            if (videoStreamUrl != null) {
                prefetchUrl(context, videoStreamUrl)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            prefetchedUrls.remove(videoUrl)
        }
    }

    private suspend fun prefetchUrl(context: Context, url: String) {
        withContext(Dispatchers.IO) {
            try {
                MediaSessionManager.getOrCreatePlayer(context) // Ensure simpleCache is initialized
                val cache = MediaSessionManager.simpleCache ?: return@withContext
                val dsf = MediaSessionManager.dataSourceFactory ?: return@withContext
                val dataSpec = DataSpec.Builder().setUri(url).setLength(512 * 1024).build() // 512KB is enough for instant start
                
                val cacheWriter = CacheWriter(
                    CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(dsf).createDataSource(),
                    dataSpec,
                    null,
                    null
                )
                cacheWriter.cache()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
