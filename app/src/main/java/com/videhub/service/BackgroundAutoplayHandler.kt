package com.videhub.service

import android.content.Context
import android.os.PowerManager
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object BackgroundAutoplayHandler {
    var globalAutoplayJob: Job? = null
    var relatedItemsCache: List<org.schabi.newpipe.extractor.InfoItem> = emptyList()
    var currentStreamInfo: org.schabi.newpipe.extractor.stream.StreamInfo? = null

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
                            relatedItemsCache = fetchedStreamInfo.relatedItems
                            currentStreamInfo = fetchedStreamInfo
                        }
                        // Wait for ExoPlayer to process prepare/play commands and acquire WAKE_MODE_NETWORK wakelock
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
                // Handle fallback to related videos if needed
                val currentUrl = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { player.currentMediaItem?.mediaId }
                android.util.Log.d("BackgroundAutoplayHandler", "currentUrl: $currentUrl")
                if (currentUrl == null) return@launch
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
                if (relatedItemsCache.isEmpty() && currentUrl.isNotBlank()) {
                    try {
                        val currentInfo = withContext(Dispatchers.IO) {
                            com.videhub.extractor.ExtractorHelper.getStreamInfo(currentUrl)
                        }
                        relatedItemsCache = currentInfo.relatedItems ?: emptyList()
                    } catch (e: Exception) {
                        android.util.Log.e("BackgroundAutoplayHandler", "Failed to fetch related items fallback", e)
                    }
                }
                val alreadyQueued = com.videhub.QueueManager.queue.value.map { it.url }.toSet()
                val relatedCandidates = relatedItemsCache.filter { it.url != currentUrl && it.url !in alreadyQueued }
                
                for (nextRelated in relatedCandidates) {
                    try {
                        val streamInfo = withContext(Dispatchers.IO) {
                            com.videhub.extractor.ExtractorHelper.getStreamInfo(nextRelated.url ?: "")
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
                            val title = nextRelated.name ?: ""
                            val artist = streamInfo.uploaderName ?: ""
                            val thumb = nextRelated.thumbnails?.firstOrNull()?.url ?: ""
                            val metadata = androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(title)
                                .setArtist(artist)
                                .setArtworkUri(android.net.Uri.parse(if (thumb.isNotBlank()) thumb else "none"))
                                .build()
                                
                            val mediaItem = androidx.media3.common.MediaItem.Builder()
                                .setMediaId(nextRelated.url ?: "")
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
                                com.videhub.MiniPlayerState.update(title, artist, thumb, com.videhub.MiniPlayerState.isMusicMode.value, nextRelated.url)
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
                            
                            if (streamInfo != null) {
                                relatedItemsCache = streamInfo.relatedItems
                                currentStreamInfo = streamInfo
                            }
                            // Wait for ExoPlayer to process the prepare/play commands on its internal thread
                            // so it can acquire its own WAKE_MODE_NETWORK wakelock before we release ours.
                            kotlinx.coroutines.delay(8000)
                            break // Successfully played, break the loop
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Continue to the next related item
                    }
                }
            }
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                // Do not aggressively release wifiLock here, let ExoPlayer's WAKE_MODE_NETWORK handle it when it begins buffering/playing
                // If it fails altogether, we just release it.
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
