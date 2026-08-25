package com.videhub.audio

import android.content.Context
import android.util.Log
import com.videhub.PlayQueueItem
import com.videhub.QueueManager
import com.videhub.extractor.ExtractorHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object RadioManager {
    private const val TAG = "RadioManager"

    private val _isRadioActive = MutableStateFlow(false)
    val isRadioActive = _isRadioActive.asStateFlow()

    private val _radioSeedTitle = MutableStateFlow<String?>(null)
    val radioSeedTitle = _radioSeedTitle.asStateFlow()

    private var radioJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val playedUrls = mutableSetOf<String>()

    /**
     * Starts an infinite radio mix based on a seed video / track.
     */
    fun startRadio(
        videoUrl: String,
        title: String,
        artist: String,
        thumbnailUrl: String,
        isAudioOnly: Boolean = true
    ) {
        radioJob?.cancel()
        _isRadioActive.value = true
        _radioSeedTitle.value = title
        playedUrls.clear()
        playedUrls.add(videoUrl)

        radioJob = scope.launch {
            try {
                // Fetch related videos from the stream
                val streamInfo = try {
                    ExtractorHelper.getStreamInfo(videoUrl, true)
                } catch (e: Exception) {
                    null
                }

                val related = streamInfo?.relatedItems
                    ?.filterIsInstance<StreamInfoItem>()
                    ?: emptyList()

                val searchMix = try {
                    val query = if (artist.isNotBlank() && artist != "Unknown Artist") {
                        "$artist mix"
                    } else {
                        "$title audio mix"
                    }
                    ExtractorHelper.getMoreSearchItems(query)
                        .filterIsInstance<StreamInfoItem>()
                } catch (e: Exception) {
                    emptyList()
                }

                val combined = (related + searchMix)
                    .distinctBy { it.url }
                    .filter { it.url !in playedUrls }
                    .shuffled()

                withContext(Dispatchers.Main) {
                    combined.take(15).forEach { item ->
                        val thumb = item.thumbnails?.firstOrNull()?.url ?: ""
                        val qItem = PlayQueueItem(
                            url = item.url,
                            title = item.name ?: "Unknown",
                            uploaderName = item.uploaderName ?: "",
                            thumbnailUrl = thumb,
                            duration = item.duration
                        )
                        QueueManager.enqueue(qItem)
                        playedUrls.add(item.url)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting radio", e)
            }
        }
    }

    /**
     * Checks if queue is getting low and auto-fetches more matching tracks if radio is active.
     */
    fun checkAndRefillRadio(currentTrackUrl: String, title: String, artist: String) {
        if (!_isRadioActive.value) return
        val currentQueue = QueueManager.queue.value
        if (currentQueue.size > 3) return // Still plenty of tracks

        radioJob?.cancel()
        radioJob = scope.launch {
            try {
                val query = if (artist.isNotBlank() && artist != "Unknown Artist") {
                    "$artist tracks songs"
                } else {
                    "$title music"
                }

                val items = ExtractorHelper.getMoreSearchItems(query)
                    .filterIsInstance<StreamInfoItem>()
                    .filter { it.url !in playedUrls }
                    .shuffled()

                withContext(Dispatchers.Main) {
                    items.take(10).forEach { item ->
                        val thumb = item.thumbnails?.firstOrNull()?.url ?: ""
                        val qItem = PlayQueueItem(
                            url = item.url,
                            title = item.name ?: "Unknown",
                            uploaderName = item.uploaderName ?: "",
                            thumbnailUrl = thumb,
                            duration = item.duration
                        )
                        QueueManager.enqueue(qItem)
                        playedUrls.add(item.url)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refilling radio", e)
            }
        }
    }

    fun stopRadio() {
        radioJob?.cancel()
        _isRadioActive.value = false
        _radioSeedTitle.value = null
        playedUrls.clear()
    }
}
