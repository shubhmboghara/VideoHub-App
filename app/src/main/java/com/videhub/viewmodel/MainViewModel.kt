package com.videhub.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.media3.session.MediaController
import androidx.media3.common.Player
import com.google.common.util.concurrent.ListenableFuture
import com.videhub.data.entity.ChannelEntity
import org.schabi.newpipe.extractor.InfoItem

class MainViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    // ── MediaController State ──
    var mediaController = androidx.compose.runtime.mutableStateOf<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val playerListeners = mutableListOf<Player.Listener>()

    fun getOrCreateMediaController(context: android.content.Context, onReady: ((MediaController) -> Unit)? = null) {
        if (controllerFuture != null && mediaController.value != null) {
            onReady?.invoke(mediaController.value!!)
            return
        }
        if (controllerFuture == null) {
            val sessionToken = androidx.media3.session.SessionToken(
                context, 
                android.content.ComponentName(context, com.videhub.service.PlaybackService::class.java)
            )
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future
            
            future.addListener({
                val controller = future.get()
                mediaController.value = controller
                onReady?.invoke(controller)
            }, androidx.core.content.ContextCompat.getMainExecutor(context))
        }
    }

    fun addPlayerListener(listener: Player.Listener) {
        playerListeners.add(listener)
        mediaController.value?.addListener(listener)
    }

    fun removePlayerListener(listener: Player.Listener) {
        playerListeners.remove(listener)
        mediaController.value?.removeListener(listener)
    }

    override fun onCleared() {
        super.onCleared()
        // Explicitly remove all MediaController.Listener references to prevent ghost callbacks
        val currentController = mediaController.value
        if (currentController != null) {
            playerListeners.forEach { listener ->
                currentController.removeListener(listener)
            }
            playerListeners.clear()
            currentController.release()
        }
        
        controllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }
        controllerFuture = null
        mediaController.value = null
    }
    
    // Home screen cache per tab
    val homeVideosCacheMap = androidx.compose.runtime.mutableStateMapOf<Int, List<Any>>()
    val homePagingSourceMap = mutableMapOf<Int, com.videhub.extractor.ListExtractorPagingSource?>()
    val homeScrollStateMap = mutableMapOf<Int, Pair<Int, Int>>()
    
    var homeSelectedTabCache: Int
        get() = savedStateHandle.get<Int>("home_tab") ?: 0
        set(value) { savedStateHandle.set("home_tab", value) }

    // Explore screen cache
    var exploreQueryCache: String = ""
    var exploreVideosCache: List<Any>? = null
    var explorePagingSourceCache: com.videhub.extractor.ListExtractorPagingSource? = null
    var exploreScrollIndexCache: Int = 0
    var exploreScrollOffsetCache: Int = 0

    // Subscriptions screen cache
    var subscriptionsSelectedChannelCache: String?
        get() = savedStateHandle.get<String>("sub_channel")
        set(value) { savedStateHandle.set("sub_channel", value) }
        
    val subscriptionsVideosCacheMap = androidx.compose.runtime.mutableStateMapOf<String, List<Any>>()
    val subscriptionsPagingSourceMap = mutableMapOf<String, com.videhub.extractor.ListExtractorPagingSource?>()
    val subscriptionsScrollStateMap = mutableMapOf<String, Pair<Int, Int>>()
    
    // Subscriptions Channels cache
    var subscribedChannelsCache: List<com.videhub.data.entity.ChannelEntity> = emptyList()
    var isInitialSubLoadingCache: Boolean = true

    // Global state to check if we've already initialized
    var isAppInitialized = false

    // Channel screen cache
    var currentChannelId: String? = null
    var channelInfoCache: org.schabi.newpipe.extractor.channel.ChannelInfo? = null
    var channelAboutInfoCache: com.videhub.extractor.ChannelAboutInfo? = null
    var channelVideosCache: List<org.schabi.newpipe.extractor.stream.StreamInfoItem>? = null
    var channelPagingSourceCache: com.videhub.extractor.ListExtractorPagingSource? = null
    var channelScrollIndexCache: Int = 0
    var channelScrollOffsetCache: Int = 0

    // Player screen cache
    var currentPlayerUrl: String? = null
    var playerStreamInfoCache: org.schabi.newpipe.extractor.stream.StreamInfo? = null
    var playerRelatedItemsCache: List<org.schabi.newpipe.extractor.InfoItem>? = null
    var playerScrollIndexCache: Int = 0
    var playerScrollOffsetCache: Int = 0
    // Library screen cache
    var libraryScrollCache: Int = 0
    
    // History of played videos this session to prevent autoplay loops
    val sessionPlayHistory = mutableSetOf<String>()
}
