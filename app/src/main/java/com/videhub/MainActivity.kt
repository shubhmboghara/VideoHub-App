
package com.videhub

import androidx.compose.ui.Modifier

import android.content.Context
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.videhub.navigation.BottomNavigationBar
import com.videhub.navigation.Screen
import com.videhub.ui.screens.*
import com.videhub.ui.theme.AppTheme
import com.videhub.ui.theme.ThemeManager
import com.videhub.extractor.ExtractorHelper
import java.net.URLDecoder
import java.net.URLEncoder

import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.background
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.draw.clip

import android.app.PictureInPictureParams
import android.util.Rational
import android.os.Build
import com.videhub.data.AppDatabase
import com.videhub.data.entity.PlaylistEntity

object PipState {
    var isActive = mutableStateOf(false)
    var canEnterPip = false
    var videoAspectRatio: Rational? = null
}

class MainActivity : ComponentActivity() {
    companion object {
        var isAppInForeground = false
        var syncPlayerOnResume: (() -> Unit)? = null
        val pendingDeepLink = kotlinx.coroutines.flow.MutableSharedFlow<String>(
            extraBufferCapacity = 5,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
        )
    }

    override fun onResume() {
        super.onResume()
        isAppInForeground = true
        syncPlayerOnResume?.invoke()
    }

    override fun onPause() {
        super.onPause()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInPictureInPictureMode) {
            // Still visible in PiP
        } else {
            isAppInForeground = false
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.videhub.data.DataBackupManager.createBackup(this@MainActivity, silent = true)
            } catch (_: Exception) {}
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val action = intent.action
        val data = intent.dataString
        if (action == android.content.Intent.ACTION_VIEW) {
            if (!data.isNullOrBlank()) {
                pendingDeepLink.tryEmit(data)
            } else {
                val schemePart = intent.data?.schemeSpecificPart
                if (!schemePart.isNullOrBlank()) {
                    pendingDeepLink.tryEmit("https://www.youtube.com/watch?v=$schemePart")
                }
            }
        } else if (action == android.content.Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                pendingDeepLink.tryEmit(text)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PipState.canEnterPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspect = PipState.videoAspectRatio ?: Rational(16, 9)
            // Ensure aspect ratio is within Android's valid limits (1:2.39 to 2.39:1)
            val safeAspect = if (aspect.toFloat() < 0.4184f) Rational(4184, 10000) else if (aspect.toFloat() > 2.39f) Rational(239, 100) else aspect
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(safeAspect)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setSeamlessResizeEnabled(true)
            }
            enterPictureInPictureMode(builder.build())
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipState.isActive.value = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            PlayerStateManager.enterPictureInPicture()
        } else {
            PlayerStateManager.exitPictureInPicture()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.videhub.data.DataBackupManager.restoreBackup(this@MainActivity, silent = true)
            } catch (_: Exception) {}
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        com.videhub.service.ActiveDownloadTracker.clearAll()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Recover any interrupted or orphaned downloads on app start
                com.videhub.service.DownloadRecoveryManager.recoverDownloads(applicationContext)
                val db = com.videhub.data.AppDatabase.getDatabase(applicationContext)
                val channels = db.channelDao().getAllOnce()
                
                // Deduplicate: If there are channels with the same name, keep the one whose ID is a URL
                val grouped = channels.groupBy { it.name }
                for ((name, list) in grouped) {
                    if (list.size > 1) {
                        val goodOne = list.find { it.channelId.startsWith("http") } ?: list.first()
                        for (ch in list) {
                            if (ch.channelId != goodOne.channelId) {
                                db.channelDao().deleteById(ch.channelId)
                            }
                        }
                    }
                }

                // Sanitize channel avatars: heal any stored channels with video thumbnails or missing avatars
                val activeChannels = db.channelDao().getAllOnce()
                for (ch in activeChannels) {
                    if (ch.thumbnailUrl.isNullOrBlank() || ch.thumbnailUrl == "none" || com.videhub.utils.AvatarHelper.isVideoThumbnail(ch.thumbnailUrl)) {
                        val realAvatar = com.videhub.utils.AvatarHelper.resolveRealChannelAvatar(ch.channelId)
                        if (!realAvatar.isNullOrBlank() && !com.videhub.utils.AvatarHelper.isVideoThumbnail(realAvatar)) {
                            db.channelDao().insert(ch.copy(thumbnailUrl = realAvatar))
                        }
                    }
                }

                // Clean up catastrophic runaway bloated playlists (e.g. 15,000 duplicate "song" records from infinite loops)
                // Users can still intentionally keep 2, 3, 5 playlists with the same name
                val allPlaylists = db.playlistDao().getAllPlaylistsOnce()
                if (allPlaylists.size > 20) {
                    val playlistsByName = allPlaylists.groupBy { it.name.trim().lowercase() }
                    val duplicateIdsToDelete = mutableListOf<Int>()
                    for ((_, sameNameList) in playlistsByName) {
                        // Only intervene if there is an extreme anomaly (more than 15 identical playlists)
                        if (sameNameList.size > 15) {
                            val duplicates = sameNameList.drop(1)
                            for (dup in duplicates) {
                                duplicateIdsToDelete.add(dup.id)
                            }
                        }
                    }
                    if (duplicateIdsToDelete.isNotEmpty()) {
                        duplicateIdsToDelete.chunked(500).forEach { batch ->
                            db.playlistDao().deletePlaylistsByIds(batch)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val prefs = getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
        val lastCrash = prefs.getString("last_crash", null)
        prefs.edit().remove("last_crash").apply()

        setContent {
            AppTheme {
                var showCrashDialog by remember { mutableStateOf(lastCrash != null) }
                if (showCrashDialog && lastCrash != null) {
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    val context = androidx.compose.ui.platform.LocalContext.current
                    AlertDialog(
                        onDismissRequest = { showCrashDialog = false },
                        title = { Text("App Crashed Previously") },
                        text = { 
                            Text(
                                "Error details:\n$lastCrash", 
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                fontSize = 12.sp
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showCrashDialog = false }) { Text("OK") }
                        },
                        dismissButton = {
                            IconButton(onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(lastCrash))
                                android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Error")
                            }
                        }
                    )
                }
                MainScreen()
            }
        }
    }
}


private fun resolveYouTubeUrl(rawText: String): String? {
    val trimmed = rawText.trim()
    if (trimmed.startsWith("vnd.youtube:", ignoreCase = true)) {
        val id = trimmed.substringAfter("vnd.youtube:").substringBefore("?").substringBefore("&")
        if (id.isNotBlank()) return "https://www.youtube.com/watch?v=$id"
    }
    val ytRegex = "(https?://(?:[a-zA-Z0-9.-]+\\.)?(?:youtube\\.com/(?:watch\\?[^\\s]+|shorts/[^\\s]+|live/[^\\s]+|playlist\\?[^\\s]+|channel/[^\\s]+|c/[^\\s]+|user/[^\\s]+|@[^\\s]+|embed/[^\\s]+|v/[^\\s]+)|youtu\\.be/[^\\s]+))".toRegex()
    val match = ytRegex.find(trimmed)
    if (match != null) return match.value

    val generalUrlRegex = "(https?://[^\\s]+)".toRegex()
    val genMatch = generalUrlRegex.find(trimmed)
    if (genMatch != null) return genMatch.value

    if (trimmed.length == 11 && !trimmed.contains(" ") && !trimmed.contains("/")) {
        return "https://www.youtube.com/watch?v=$trimmed"
    }
    if (trimmed.startsWith("@") || trimmed.startsWith("UC")) {
        return "https://www.youtube.com/$trimmed"
    }
    return if (trimmed.startsWith("http")) trimmed else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    
    val sharedViewModel: com.videhub.viewmodel.MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    
    // ── Shared Player State for Persistent Morphing Player & MiniPlayer ──
    val context = androidx.compose.ui.platform.LocalContext.current
    var mediaPlayer by remember { mutableStateOf<androidx.media3.common.Player?>(null) }
    var currentMediaItem by remember { mutableStateOf<androidx.media3.common.MediaItem?>(null) }
    var hasActiveVideo by remember { mutableStateOf(false) }
    val playerWindowState by PlayerStateManager.currentState.collectAsStateWithLifecycle()
    val activePlayerMedia by PlayerStateManager.activeMedia.collectAsStateWithLifecycle()
    var playerCollapseFraction by remember {
        androidx.compose.runtime.mutableFloatStateOf(
            if (playerWindowState == PlayerWindowState.MINIPLAYER) 1f else 0f
        )
    }
    LaunchedEffect(playerWindowState) {
        playerCollapseFraction = if (playerWindowState == PlayerWindowState.MINIPLAYER) 1f else 0f
    }
    
    LaunchedEffect(context) {
        val lang = com.videhub.data.SettingsManager.getContentLanguage(context).first()
        val country = com.videhub.data.SettingsManager.getContentCountry(context).first()
        com.videhub.extractor.ExtractorHelper.updateLocalization(lang, country)
    }
    
    val scope = rememberCoroutineScope()
    var globalAutoplayJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    
    val autoplayEnabled by com.videhub.data.SettingsManager.getAutoplay(context).collectAsStateWithLifecycle(initialValue = true)
    val autoplayRef = remember { mutableStateOf(true) }
    LaunchedEffect(autoplayEnabled) {
        autoplayRef.value = autoplayEnabled
    }
    val isHandlingAutoplay = remember { com.videhub.utils.MutableRef(false) }
    
    suspend fun getNextDownloadedItem(context: android.content.Context, currentUrl: String, isMusic: Boolean): com.videhub.data.entity.DownloadedVideoEntity? {
        val db = com.videhub.data.AppDatabase.getDatabase(context)
        val allDownloads = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.downloadedVideoDao().getAllDownloadsSync()
        }
        val currentFileName = java.io.File(currentUrl).name
        val currentItem = allDownloads.find { it.fileName == currentFileName }
        val validItems = if (currentItem?.playlistId != null) {
            allDownloads.filter { it.playlistId == currentItem.playlistId }.sortedBy { it.playlistIndex }
        } else {
            allDownloads.filter { d ->
                val path = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), d.fileName).absolutePath
                val isAudio = com.videhub.utils.FileUtils.isAudio(d, path)
                isAudio == isMusic && d.playlistId == null
            }
        }
        val currentIndex = validItems.indexOfFirst { it.fileName == currentFileName }
        if (currentIndex != -1 && currentIndex < validItems.lastIndex) return validItems[currentIndex + 1]
        return null
    }
    
    fun navigateToPlayer(url: String?, t: String?, thumb: String?, isMusicMode: Boolean = false, isFullscreen: Boolean = false) {
        globalAutoplayJob?.cancel()
        globalAutoplayJob = null

        val safeUrl = url ?: ""
        val isLocal = com.videhub.QueueManager.isLocal(safeUrl)
        if (!isLocal && com.videhub.QueueManager.hasDownloadedItems()) {
            com.videhub.QueueManager.clearDownloadedItems()
        }

        val isSameVideo = mediaPlayer?.currentMediaItem?.mediaId == safeUrl
        if (!isSameVideo) {
            mediaPlayer?.stop()
            mediaPlayer?.clearMediaItems()
            com.videhub.ui.components.LiveCaptionsManager.clear()
            if (sharedViewModel.currentPlayerUrl != safeUrl) {
                sharedViewModel.playerStreamInfoCache = null
                sharedViewModel.playerRelatedItemsCache = null
            }
        } else {
            if (mediaPlayer?.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.play()
            }
        }
        if (safeUrl.isNotBlank()) {
            sharedViewModel.currentPlayerUrl = safeUrl
            sharedViewModel.currentPlayerTitle = t
            sharedViewModel.currentPlayerThumbnailUrl = thumb

            val resolvedTitle = t?.ifBlank { "Video" } ?: "Video"
            val resolvedThumb = thumb?.ifBlank { "none" } ?: "none"
            val resolvedChannel = com.videhub.QueueManager.queue.value.find { it.url == safeUrl }?.uploaderName.orEmpty()

            PlayerStateManager.openVideo(
                url = safeUrl,
                title = resolvedTitle,
                thumbnailUrl = resolvedThumb,
                channelName = resolvedChannel,
                isMusicMode = isMusicMode,
                isFullscreen = isFullscreen
            )
            playerCollapseFraction = 0f
        }
    }


    LaunchedEffect(Unit) {
        sharedViewModel.getOrCreateMediaController(context)
        val player = com.videhub.service.MediaSessionManager.getOrCreatePlayer(context)
        mediaPlayer = player

        launch {
            if (PlayerStateManager.currentState.value == PlayerWindowState.CLOSED && player.currentMediaItem == null) {
                val lastState = com.videhub.data.SettingsManager.getLastPlaybackState(context).firstOrNull()
                if (lastState != null && lastState.videoUrl.isNotBlank()) {
                    PlayerStateManager.restoreToMiniplayer(
                        url = lastState.videoUrl,
                        title = lastState.title,
                        thumbnailUrl = lastState.thumbnailUrl,
                        channelName = lastState.channel,
                        position = lastState.position,
                        duration = lastState.duration,
                        musicMode = lastState.isMusicMode
                    )
                }
            }
        }
        
        launch {
            MainActivity.pendingDeepLink.collect { rawText ->
                val cleanUrl = resolveYouTubeUrl(rawText)
                if (!cleanUrl.isNullOrBlank()) {
                    if (cleanUrl.contains("/channel/") || cleanUrl.contains("/c/") || cleanUrl.contains("/user/") || cleanUrl.contains("/@")) {
                        PlayerStateManager.collapseToMiniplayer()
                        navController.navigate(Screen.Channel.createRoute(cleanUrl))
                    } else if (cleanUrl.contains("/playlist?list=") || (cleanUrl.contains("list=") && !cleanUrl.contains("v="))) {
                        PlayerStateManager.collapseToMiniplayer()
                        navController.navigate(Screen.OnlinePlaylist.createRoute(cleanUrl))
                    } else {
                        navigateToPlayer(cleanUrl, "Loading...", "none", isMusicMode = false, isFullscreen = false)
                    }
                }
            }
        }
        
        fun syncActiveVideoState() {
            currentMediaItem = player.currentMediaItem
            hasActiveVideo = currentMediaItem != null &&
                    player.playbackState != androidx.media3.common.Player.STATE_IDLE &&
                    player.playbackState != androidx.media3.common.Player.STATE_ENDED
        }
        syncActiveVideoState()

        val listener = object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                syncActiveVideoState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                syncActiveVideoState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncActiveVideoState()
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    com.videhub.PipState.videoAspectRatio = android.util.Rational(videoSize.width, videoSize.height)
                    if (com.videhub.PipState.isActive.value && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        try {
                            val aspect = com.videhub.PipState.videoAspectRatio!!
                            val safeAspect = if (aspect.toFloat() < 0.4184f) android.util.Rational(4184, 10000) else if (aspect.toFloat() > 2.39f) android.util.Rational(239, 100) else aspect
                            val builder = android.app.PictureInPictureParams.Builder().setAspectRatio(safeAspect)
                            (context as? android.app.Activity)?.setPictureInPictureParams(builder.build())
                        } catch (e: Exception) {}
                    }
                }
            }
        }
        player.addListener(listener)

        launch {
            com.videhub.QueueManager.skipToNextEvent.collect {
                val currentWinState = PlayerStateManager.currentState.value
                val inForegroundWatch = (currentWinState == PlayerWindowState.WATCH_PAGE ||
                    currentWinState == PlayerWindowState.FULLSCREEN) && com.videhub.MainActivity.isAppInForeground
                if (inForegroundWatch) {
                    val currentUrl = player.currentMediaItem?.mediaId
                    val isCurrentLocal = currentUrl?.let { com.videhub.QueueManager.isLocal(it) } ?: false

                    if (!isCurrentLocal && com.videhub.QueueManager.hasDownloadedItems()) {
                        com.videhub.QueueManager.clearDownloadedItems()
                    }

                    val nextItem = com.videhub.QueueManager.getNextVideo()
                    val preserveFs = currentWinState == PlayerWindowState.FULLSCREEN
                    if (nextItem != null && (isCurrentLocal || !com.videhub.QueueManager.isLocal(nextItem.url))) {
                        navigateToPlayer(nextItem.url, nextItem.title, nextItem.thumbnailUrl, MiniPlayerState.isMusicMode.value, preserveFs)
                    } else {
                        if (isCurrentLocal) {
                            val isMusic = MiniPlayerState.isMusicMode.value
                            val nextD = getNextDownloadedItem(context, currentUrl!!, isMusic)
                            if (nextD != null) {
                                val nextPath = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), nextD.fileName).absolutePath
                                navigateToPlayer(nextPath, nextD.title, nextD.thumbnailUrl, isMusic, preserveFs)
                            }
                            return@collect
                        }
                        val nextRecommended = withContext(Dispatchers.IO) {
                            currentUrl?.let {
                                com.videhub.service.BackgroundAutoplayHandler.findNextRecommendedCandidate(context, it, MiniPlayerState.isMusicMode.value)
                            }
                        }
                        if (nextRecommended != null && !nextRecommended.url.isNullOrBlank()) {
                            navigateToPlayer(nextRecommended.url, nextRecommended.name ?: "", nextRecommended.thumbnails?.firstOrNull()?.url ?: "", MiniPlayerState.isMusicMode.value, preserveFs)
                        }
                    }
                } else {
                    com.videhub.service.BackgroundAutoplayHandler.handleAutoplay(context, player) { com.videhub.QueueManager.getNextVideo() }
                }
            }
        }
        launch {
            com.videhub.QueueManager.skipToPreviousEvent.collect {
                val currentWinState = PlayerStateManager.currentState.value
                val inForegroundWatch = (currentWinState == PlayerWindowState.WATCH_PAGE ||
                    currentWinState == PlayerWindowState.FULLSCREEN) && com.videhub.MainActivity.isAppInForeground
                if (inForegroundWatch) {
                    val prevItem = com.videhub.QueueManager.skipToPrevious()
                    if (prevItem != null) {
                        val preserveFs = currentWinState == PlayerWindowState.FULLSCREEN
                        navigateToPlayer(prevItem.url, prevItem.title, prevItem.thumbnailUrl, MiniPlayerState.isMusicMode.value, preserveFs)
                    }
                } else {
                    com.videhub.service.BackgroundAutoplayHandler.handleAutoplay(context, player) { com.videhub.QueueManager.skipToPrevious() }
                }
            }
        }
    }

    LaunchedEffect(hasActiveVideo, currentMediaItem?.mediaId) {
        if (hasActiveVideo && PlayerStateManager.currentState.value == PlayerWindowState.CLOSED) {
            val item = currentMediaItem
            val url = item?.mediaId
            if (!url.isNullOrBlank()) {
                val title = item.mediaMetadata.title?.toString() ?: "Unknown"
                val artist = item.mediaMetadata.artist?.toString() ?: ""
                var artwork = item.mediaMetadata.artworkUri?.toString() ?: ""
                if (artwork == "none") artwork = ""
                PlayerStateManager.restoreToMiniplayer(
                    url = url,
                    title = title,
                    thumbnailUrl = artwork,
                    channelName = artist,
                    position = mediaPlayer?.currentPosition ?: 0L,
                    duration = mediaPlayer?.duration ?: 0L,
                    musicMode = MiniPlayerState.isMusicMode.value
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val isPlayerBlockingScreen = playerWindowState == PlayerWindowState.FULLSCREEN ||
            playerWindowState == PlayerWindowState.PICTURE_IN_PICTURE ||
            (playerWindowState == PlayerWindowState.WATCH_PAGE && playerCollapseFraction < 0.35f)
        val showBottomBar = !isPlayerBlockingScreen && !com.videhub.PipState.isActive.value
        val useZeroPadding = com.videhub.PipState.isActive.value ||
            playerWindowState == PlayerWindowState.FULLSCREEN ||
            playerWindowState == PlayerWindowState.PICTURE_IN_PICTURE
        androidx.compose.material3.Scaffold(
            contentWindowInsets = if (useZeroPadding) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
            bottomBar = {
                if (showBottomBar) {
                    com.videhub.navigation.BottomNavigationBar(
                        navController = navController,
                        currentRoute = currentRoute ?: Screen.Home.route
                    )
                }
            }
        ) { innerPadding ->
            val appliedPadding = if (useZeroPadding) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(appliedPadding)
                    .consumeWindowInsets(appliedPadding)
            ) {
                com.videhub.navigation.NavGraph(
                    navController = navController,
                    sharedViewModel = sharedViewModel,
                    isDarkMode = isDarkMode,
                    onThemeChange = { isDark -> com.videhub.ui.theme.ThemeManager.setDarkMode(context, isDark) },
                    onNavigateToPlayer = { url, t, thumb, isMusic, isFs -> navigateToPlayer(url, t, thumb, isMusic, isFs) },
                    mediaPlayer = mediaPlayer
                )
            }
        }

        val media = activePlayerMedia
        if (playerWindowState != PlayerWindowState.CLOSED && media != null) {
            PlayerScreen(
                sharedViewModel = sharedViewModel,
                videoUrl = media.videoUrl,
                title = media.title,
                thumbnailUrl = media.thumbnailUrl,
                onBack = { PlayerStateManager.collapseToMiniplayer(isGesture = false) },
                onVideoPlay = { url, t, thumb, isMusic, isFs ->
                    navigateToPlayer(url, t, thumb, isMusic, isFs)
                },
                initialFullscreen = playerWindowState == PlayerWindowState.FULLSCREEN,
                onChannelClick = { ch ->
                    PlayerStateManager.collapseToMiniplayer(isGesture = false)
                    navController.navigate(Screen.Channel.createRoute(ch))
                },
                forceMusicMode = media.isMusicMode,
                mediaPlayer = mediaPlayer,
                playerWindowState = playerWindowState,
                isRestoredFromSavedState = media.isRestoredFromSavedState,
                onCollapseToMiniplayer = {
                    PlayerStateManager.collapseToMiniplayer(isGesture = false)
                },
                onExpandFromMiniplayer = {
                    PlayerStateManager.expandFromMiniplayer(isGesture = false)
                },
                onEnterFullscreen = {
                    PlayerStateManager.enterFullscreen(isGesture = false)
                },
                onExitFullscreen = {
                    PlayerStateManager.exitFullscreen(isGesture = false)
                },
                onClosePlayer = {
                    val currentUrl = mediaPlayer?.currentMediaItem?.mediaId ?: media.videoUrl
                    val isLocal = com.videhub.QueueManager.isLocal(currentUrl)
                    mediaPlayer?.stop()
                    mediaPlayer?.clearMediaItems()
                    PlayerStateManager.close()
                    if (isLocal || com.videhub.QueueManager.hasDownloadedItems()) {
                        com.videhub.QueueManager.clearDownloadedItems()
                    }
                    scope.launch(Dispatchers.IO) {
                        com.videhub.data.SettingsManager.clearLastPlaybackState(context)
                    }
                    sharedViewModel.currentPlayerUrl = null
                    sharedViewModel.playerStreamInfoCache = null
                    sharedViewModel.playerRelatedItemsCache = null
                },
                onCollapseProgressChanged = { fraction ->
                    playerCollapseFraction = fraction
                }
            )
        }
    }
}
