package com.videhub.viewmodel

import androidx.lifecycle.ViewModel
import com.videhub.PlayQueueItem
import com.videhub.QueueManager
import kotlinx.coroutines.flow.StateFlow

class QueueViewModel : ViewModel() {
    val queue: StateFlow<List<PlayQueueItem>> = QueueManager.queue

    // Stores (originalIndex, item) for restoring item at its original position
    private var lastRemoved: Pair<Int, PlayQueueItem>? = null
    // Snapshot of the entire queue prior to clearing
    private var lastCleared: List<PlayQueueItem> = emptyList()

    /**
     * Reorders items in the queue from fromIndex to toIndex.
     */
    fun reorder(fromIndex: Int, toIndex: Int) {
        val current = queue.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            QueueManager.setQueue(current)
        }
    }

    /**
     * Removes an item at a specific index from the queue.
     * Records the item and its original index for undo restoration.
     */
    fun removeAt(index: Int): PlayQueueItem? {
        val current = queue.value.toMutableList()
        if (index in current.indices) {
            val item = current.removeAt(index)
            lastRemoved = index to item
            QueueManager.setQueue(current)
            return item
        }
        return null
    }

    /**
     * Removes an item matching the given PlayQueueItem.
     * Returns the original index of the item, or -1 if not found.
     */
    fun removeItem(item: PlayQueueItem): Int {
        val current = queue.value
        val index = current.indexOfFirst { it.id == item.id || it.url == item.url }
        if (index != -1) {
            removeAt(index)
        }
        return index
    }

    /**
     * Restores the most recently removed item at its exact original index.
     */
    fun undoRemove() {
        lastRemoved?.let { (index, item) ->
            val current = queue.value.toMutableList()
            val targetIndex = index.coerceIn(0, current.size)
            current.add(targetIndex, item)
            QueueManager.setQueue(current)
            lastRemoved = null
        }
    }

    /**
     * Clears all videos from the queue and saves a snapshot for undo.
     */
    fun clearQueue() {
        lastCleared = queue.value
        QueueManager.clear()
    }

    /**
     * Restores the full queue in its original order after being cleared.
     */
    fun undoClear() {
        if (lastCleared.isNotEmpty()) {
            QueueManager.setQueue(lastCleared)
            lastCleared = emptyList()
        }
    }
}
