package com.videhub

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayQueueItem(
    val url: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String,
    val duration: Long = -1
)

object QueueManager {
    private val _queue = MutableStateFlow<List<PlayQueueItem>>(emptyList())
    val queue: StateFlow<List<PlayQueueItem>> = _queue.asStateFlow()

    val skipToNextEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val skipToPreviousEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled.asStateFlow()

    fun shuffle() {
        val current = _queue.value
        if (current.isNotEmpty()) {
            _queue.value = current.shuffled()
            _isShuffled.value = true
        }
    }

    fun toggleShuffle() {
        val current = _queue.value
        if (current.isNotEmpty()) {
            _queue.value = current.shuffled()
            _isShuffled.value = !_isShuffled.value
        }
    }

    fun enqueue(item: PlayQueueItem): Boolean {
        val current = _queue.value
        if (current.any { it.url == item.url }) {
            return false
        }
        _queue.value = current + item
        return true
    }

    fun playNext(item: PlayQueueItem) {
        val current = _queue.value.toMutableList()
        current.removeAll { it.url == item.url }
        current.add(0, item)
        _queue.value = current
    }

    // Returns true if there is a next item to play
    fun skipToNext(): PlayQueueItem? {
        return dequeue()
    }
    
    fun skipToPrevious(): PlayQueueItem? {
        return null // Removed for pure forward queue
    }
    
    fun getCurrentItem(): PlayQueueItem? {
        return _queue.value.firstOrNull()
    }

    fun dequeue(): PlayQueueItem? {
        val current = _queue.value
        if (current.isNotEmpty()) {
            val item = current.first()
            _queue.value = current.drop(1)
            return item
        }
        return null
    }

    @Synchronized
    fun getAndRemoveNext(): PlayQueueItem? {
        val current = _queue.value
        if (current.isNotEmpty()) {
            val item = current.first()
            _queue.value = current.drop(1)
            return item
        }
        return null
    }

    fun getNextVideo(): PlayQueueItem? {
        return getAndRemoveNext()
    }

    fun remove(item: PlayQueueItem) {
        _queue.value = _queue.value.filter { it.url != item.url }
    }

    fun clear() {
        _queue.value = emptyList()
    }
}

