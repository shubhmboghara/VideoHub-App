package com.videhub.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object MediaSessionManager {
    var simpleCache: SimpleCache? = null
    var player: ExoPlayer? = null
    private var wrappedPlayer: androidx.media3.common.Player? = null
    private var mediaSession: MediaSession? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private var prefetchJob: kotlinx.coroutines.Job? = null
    private var lastPrefetchedMediaId: String? = null
    
    private fun updateIsPlayingState() {
        val p = player ?: return
        val isActuallyPlaying = !androidx.media3.common.util.Util.shouldShowPlayButton(p)
        _isPlaying.value = isActuallyPlaying
    }
    var trackSelector: androidx.media3.exoplayer.trackselection.DefaultTrackSelector? = null
        private set
    var dataSourceFactory: androidx.media3.datasource.DataSource.Factory? = null
        private set

    fun getOrCreatePlayer(context: Context): androidx.media3.common.Player {
        val ctx = context.applicationContext
        if (wrappedPlayer != null) return wrappedPlayer!!
        if (player == null) {
            val okHttpClient = com.videhub.extractor.ExtractorHelper.buildClient()
            val httpDsf = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent("com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
                .setDefaultRequestProperties(
                    mapOf(
                        "Accept-Language" to "en-US,en;q=0.9",
                        "Referer"         to "https://www.youtube.com/",
                        "Origin"          to "https://www.youtube.com"
                    )
                )
            val dsf = androidx.media3.datasource.DefaultDataSource.Factory(ctx, httpDsf)
            
            if (simpleCache == null) {
                val cacheDir = File(ctx.cacheDir, "media_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024) // 200MB max cache
                val databaseProvider = StandaloneDatabaseProvider(ctx)
                simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
            }
            
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(simpleCache!!)
                .setUpstreamDataSourceFactory(dsf)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

            dataSourceFactory = cacheDataSourceFactory

            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(ctx)
                .setDataSourceFactory(cacheDataSourceFactory)

            // Enhance buffer configuration for smoother playback, preventing "cut cut" issues
            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    64000, // min buffer
                    120000, // max buffer
                    5000, // buffer for playback
                    10000 // buffer for playback after rebuffer
                ).build()

            trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(ctx)
            player = ExoPlayer.Builder(ctx)
                .setMediaSourceFactory(mediaSourceFactory)
                .setTrackSelector(trackSelector!!)
                .setLoadControl(loadControl)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .build()

            com.videhub.audio.EqualizerManager.init(ctx, player!!.audioSessionId)
            com.videhub.audio.CrossfadeManager.init(ctx)
                
            prefetchJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                while(true) {
                    val p = player
                    if (p != null && p.isPlaying) {
                        val pos = p.currentPosition
                        val dur = p.duration
                        val currentIndex = p.currentMediaItemIndex
                        val currentMediaId = p.currentMediaItem?.mediaId
                        
                        // Crossfade check
                        com.videhub.audio.CrossfadeManager.checkAndApplyFadeOut(p)

                        if (dur > 0 && dur - pos < 25000 && currentMediaId != null && lastPrefetchedMediaId != currentMediaId) {
                            if (p.currentTimeline.windowCount == currentIndex + 1) {
                                lastPrefetchedMediaId = currentMediaId
                                val isAutoplayEnabled = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    com.videhub.data.SettingsManager.getAutoplay(ctx).firstOrNull() ?: true
                                }
                                val isLoopEnabled = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    com.videhub.data.SettingsManager.getLoopVideo(ctx).firstOrNull() ?: false
                                }
                                if (!isLoopEnabled) {
                                    android.util.Log.d("MediaSessionManager", "Prefetching next track...")
                                    BackgroundAutoplayHandler.handleAutoplay(ctx, p, isAutoplayEnabled, isPrefetch = true) { com.videhub.QueueManager.getNextVideo() }
                                }
                            }
                        }
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }

            player!!.addListener(object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateIsPlayingState()
                }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    updateIsPlayingState()
                }
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    player?.let { com.videhub.audio.CrossfadeManager.applyFadeIn(it) }
                    if (mediaItem != null) {
                        val title = mediaItem.mediaMetadata.title?.toString() ?: ""
                        val artist = mediaItem.mediaMetadata.artist?.toString() ?: ""
                        val artwork = mediaItem.mediaMetadata.artworkUri?.toString() ?: ""
                        val cleanArtwork = if (artwork == "none") "" else artwork
                        val url = mediaItem.mediaId ?: ""
                        if (title.isNotBlank()) {
                            com.videhub.MiniPlayerState.update(title, artist, cleanArtwork, com.videhub.MiniPlayerState.isMusicMode.value, url)
                        }
                        if (url.isNotBlank()) {
                            com.videhub.audio.RadioManager.checkAndRefillRadio(url, title, artist)
                            com.videhub.ui.components.LiveCaptionsManager.clear()
                            if (url.startsWith("/") || url.startsWith("file://") || url.startsWith("content://")) {
                                com.videhub.ui.components.LiveCaptionsManager.loadCaptionsFromDb(ctx, url)
                            }
                        }
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("MediaSessionManager", "Player error during playback: ${error.message}", error)
                    // Do NOT auto-skip to next video on player error. Let UI handle retry or stream fallback.
                }
                override fun onPlaybackStateChanged(state: Int) {
                    updateIsPlayingState()
                    if (state == androidx.media3.common.Player.STATE_ENDED) {
                        val p = player ?: return
                        val dur = p.duration
                        val pos = p.currentPosition
                        // Only trigger autoplay when video actually reached the end
                        if (dur > 0 && pos >= (dur - 3000L).coerceAtLeast(0L)) {
                            val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                            val wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "VideHub:AutoplayCheckWakeLock")
                            wakeLock.acquire(30000)
                            
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    val isAutoplayEnabled = com.videhub.data.SettingsManager.getAutoplay(ctx).firstOrNull() ?: true
                                    val isLoopEnabled = com.videhub.data.SettingsManager.getLoopVideo(ctx).firstOrNull() ?: false
                                    
                                    if (isLoopEnabled) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            player?.seekTo(0)
                                            player?.play()
                                        }
                                        kotlinx.coroutines.delay(1500)
                                    } else {
                                        android.util.Log.d("MediaSessionManager", "Autoplay/Queue triggered on normal video completion!")
                                        BackgroundAutoplayHandler.handleAutoplay(ctx, player!!, isAutoplayEnabled) { com.videhub.QueueManager.getNextVideo() }
                                    }
                                } finally {
                                    if (wakeLock.isHeld) wakeLock.release()
                                }
                            }
                        }
                    }
                }
            })
        }
        
        wrappedPlayer = object : androidx.media3.common.ForwardingPlayer(player!!) {
            override fun getAvailableCommands(): androidx.media3.common.Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
                    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }
            
            override fun seekToNext() {
                if (player!!.hasNextMediaItem()) { player!!.seekToNextMediaItem() } else { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { android.util.Log.d("MediaSessionManager", "Autoplay triggered!")
                                val isAutoplayEnabled = com.videhub.data.SettingsManager.getAutoplay(ctx).firstOrNull() ?: true
                                BackgroundAutoplayHandler.handleAutoplay(ctx, player!!, isAutoplayEnabled) { com.videhub.QueueManager.getNextVideo() } } }
            }
            
            override fun seekToNextMediaItem() {
                if (player!!.hasNextMediaItem()) { player!!.seekToNextMediaItem() } else { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { android.util.Log.d("MediaSessionManager", "Autoplay triggered!")
                                val isAutoplayEnabled = com.videhub.data.SettingsManager.getAutoplay(ctx).firstOrNull() ?: true
                                BackgroundAutoplayHandler.handleAutoplay(ctx, player!!, isAutoplayEnabled) { com.videhub.QueueManager.getNextVideo() } } }
            }
            
            override fun seekToPrevious() {
                player?.seekTo(0)
            }
            
            override fun seekToPreviousMediaItem() {
                player?.seekTo(0)
            }
        }
        return wrappedPlayer!!
    }

    fun getOrCreateSession(context: Context): MediaSession {
        val ctx = context.applicationContext
        val activePlayer = getOrCreatePlayer(ctx)
        if (mediaSession == null) {
            val customBitmapLoader = object : androidx.media3.common.util.BitmapLoader {
                private val defaultLoader = androidx.media3.session.CacheBitmapLoader(androidx.media3.datasource.DataSourceBitmapLoader(ctx))
                override fun decodeBitmap(data: ByteArray): com.google.common.util.concurrent.ListenableFuture<android.graphics.Bitmap> = defaultLoader.decodeBitmap(data)
                override fun loadBitmapFromMetadata(metadata: androidx.media3.common.MediaMetadata): com.google.common.util.concurrent.ListenableFuture<android.graphics.Bitmap>? = defaultLoader.loadBitmapFromMetadata(metadata)
                override fun loadBitmap(uri: android.net.Uri): com.google.common.util.concurrent.ListenableFuture<android.graphics.Bitmap> = loadBitmap(uri, null)
                override fun loadBitmap(uri: android.net.Uri, options: android.graphics.BitmapFactory.Options?): com.google.common.util.concurrent.ListenableFuture<android.graphics.Bitmap> {
                    val future = com.google.common.util.concurrent.SettableFuture.create<android.graphics.Bitmap>()
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            if (uri.scheme == "file") {
                                val path = uri.path
                                if (path != null) {
                                    val file = java.io.File(path)
                                    if (file.exists()) {
                                        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                        if (bitmap != null) future.set(bitmap) else future.setException(Exception("Bitmap null"))
                                    } else {
                                        future.setException(java.io.FileNotFoundException())
                                    }
                                } else {
                                    future.setException(Exception("URI path null"))
                                }
                            } else {
                                val imageLoader = coil.ImageLoader(ctx)
                                val request = coil.request.ImageRequest.Builder(ctx)
                                    .data(uri.toString())
                                    .allowHardware(false)
                                    .build()
                                val result = imageLoader.execute(request)
                                val drawable = result.drawable
                                if (drawable is android.graphics.drawable.BitmapDrawable) {
                                    future.set(drawable.bitmap)
                                } else if (drawable != null) {
                                    val bitmap = android.graphics.Bitmap.createBitmap(
                                        drawable.intrinsicWidth.coerceAtLeast(1),
                                        drawable.intrinsicHeight.coerceAtLeast(1),
                                        android.graphics.Bitmap.Config.ARGB_8888
                                    )
                                    val canvas = android.graphics.Canvas(bitmap)
                                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                                    drawable.draw(canvas)
                                    future.set(bitmap)
                                } else {
                                    future.setException(Exception("Failed to load image via Coil"))
                                }
                            }
                        } catch (e: Exception) {
                            future.setException(e)
                        }
                    }
                    return future
                }
            }
            mediaSession = MediaSession.Builder(ctx, activePlayer)
                .setBitmapLoader(customBitmapLoader)
                .setCallback(object : MediaSession.Callback {})
                .build()
        }
        return mediaSession!!
    }

    fun release() {
        prefetchJob?.cancel()
        prefetchJob = null
        com.videhub.ui.components.LiveCaptionsManager.release()
        // Can't directly call audioFocusManager here easily, but stop() does it
        wrappedPlayer?.stop()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        wrappedPlayer = null
    }
}
