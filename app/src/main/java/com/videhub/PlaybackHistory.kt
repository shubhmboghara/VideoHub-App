package com.videhub

object PlaybackHistory {
    private val _history = mutableListOf<PlayQueueItem>()

    // Call this every time a new song starts
    fun addToHistory(item: PlayQueueItem) {
        _history.add(item)
        // Keep max 50 items to avoid memory bloat
        if (_history.size > 50) {
            _history.removeAt(0)
        }
    }

    // Get previous song and remove from history
    fun getPrevious(): PlayQueueItem? {
        if (_history.size < 2) return null
        // Remove current item
        _history.removeAt(_history.lastIndex)
        // Return the previous item which is now at the end
        return _history.lastOrNull()
    }

    fun hasPrevious(): Boolean = _history.size >= 2

    fun clear() {
        _history.clear()
    }
}
