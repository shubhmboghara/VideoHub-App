package com.videhub

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MiniPlayerState {
    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    private val _currentVideoUrl = MutableStateFlow<String?>(null)
    val currentVideoUrl: StateFlow<String?> = _currentVideoUrl.asStateFlow()

    private val _currentTitle = MutableStateFlow<String?>(null)
    val currentTitle: StateFlow<String?> = _currentTitle.asStateFlow()

    private val _currentThumbnailUrl = MutableStateFlow<String?>(null)
    val currentThumbnailUrl: StateFlow<String?> = _currentThumbnailUrl.asStateFlow()

    private val _currentChannelName = MutableStateFlow<String?>(null)
    val currentChannelName: StateFlow<String?> = _currentChannelName.asStateFlow()

    private val _isMusicMode = MutableStateFlow(false)
    val isMusicMode: StateFlow<Boolean> = _isMusicMode.asStateFlow()

    private val _isLoadingNext = MutableStateFlow(false)
    val isLoadingNext: StateFlow<Boolean> = _isLoadingNext.asStateFlow()

    fun setLoadingNext(loading: Boolean) {
        _isLoadingNext.value = loading
    }

    fun show(url: String, title: String, thumb: String, channel: String, musicMode: Boolean = false) {
        _currentVideoUrl.value = url
        _currentTitle.value = title
        _currentThumbnailUrl.value = thumb
        _currentChannelName.value = channel
        _isMusicMode.value = musicMode
        
        _isVisible.value = true
        MiniPlayerGlobalState.state.value = MiniPlayerStateEnum.FloatingPip
    }

    fun update(title: String, channelName: String, thumbnailUrl: String, isMusicMode: Boolean = true, url: String? = null) {
        _currentTitle.value = title
        _currentChannelName.value = channelName
        _currentThumbnailUrl.value = thumbnailUrl
        _isMusicMode.value = isMusicMode
        if (!url.isNullOrBlank()) {
            _currentVideoUrl.value = url
        }
    }

    fun hide() {
        _isVisible.value = false
        MiniPlayerGlobalState.state.value = MiniPlayerStateEnum.Hidden
        MiniPlayerGlobalState.pipOffsetX.floatValue = -1f
        MiniPlayerGlobalState.pipOffsetY.floatValue = -1f
    }
}
