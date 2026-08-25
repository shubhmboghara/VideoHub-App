
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        try {
            startService(android.content.Intent(this, com.videhub.service.PlaybackService::class.java))
        } catch (e: Exception) {}
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
                val db = com.videhub.data.AppDatabase.getDatabase(applicationContext)
                db.downloadedPlaylistDao().deleteAllPendingPlaylistVideoCrossRefs()
                db.downloadedVideoDao().deleteAllPendingVideos()
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val prefs = getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
        val lastCrash = prefs.getString("last_crash", null)
        prefs.edit().remove("last_crash").apply()
        
        coil.ImageLoader.Builder(this)
            .components {
                add(coil.decode.VideoFrameDecoder.Factory())
            }
            .build()
            .let { coil.Coil.setImageLoader(it) }

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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    
    val sharedViewModel: com.videhub.viewmodel.MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    
    // ── Shared Player State for MiniPlayer ──
    val context = androidx.compose.ui.platform.LocalContext.current
    var mediaPlayer by remember { mutableStateOf<androidx.media3.common.Player?>(null) }
    var currentMediaItem by remember { mutableStateOf<androidx.media3.common.MediaItem?>(null) }
    var hasActiveVideo by remember { mutableStateOf(false) }
    val isMiniPlayerVisible by MiniPlayerState.isVisible.collectAsState()
    val miniPlayerMusicMode by MiniPlayerState.isMusicMode.collectAsState()
    val miniPlayerArtworkUrl by MiniPlayerState.currentThumbnailUrl.collectAsState()
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
        val isSameVideo = mediaPlayer?.currentMediaItem?.mediaId == safeUrl
        if (!isSameVideo) {
            mediaPlayer?.stop()
            mediaPlayer?.clearMediaItems()
            com.videhub.ui.components.LiveCaptionsManager.clear()
        } else {
            if (mediaPlayer?.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.play()
            }
        }
        if (safeUrl.isNotBlank()) {
            val isCurrentlyOnPlayer = navController.currentDestination?.route?.startsWith("player") == true
            navController.navigate(
                Screen.Player.createRoute(
                    android.net.Uri.encode(safeUrl),
                    android.net.Uri.encode(t?.ifBlank { "Video" } ?: "Video"),
                    android.net.Uri.encode(thumb?.ifBlank { "none" } ?: "none"),
                    isMusicMode,
                    isFullscreen
                )
            ) {
                if (isCurrentlyOnPlayer) {
                    popUpTo(navController.currentDestination!!.id) {
                        inclusive = true
                    }
                }
                launchSingleTop = true
            }
        }
    }


    LaunchedEffect(Unit) {
        sharedViewModel.getOrCreateMediaController(context)
        val player = com.videhub.service.MediaSessionManager.getOrCreatePlayer(context)
        mediaPlayer = player
        
        val listener = object : androidx.media3.common.Player.Listener {
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
                if (currentRoute?.startsWith("player") == true && com.videhub.MainActivity.isAppInForeground) {
                    val nextItem = com.videhub.QueueManager.getNextVideo()
                    if (nextItem != null) {
                        navigateToPlayer(nextItem.url, nextItem.title, nextItem.thumbnailUrl, MiniPlayerState.isMusicMode.value)
                    } else {
                        val currentUrl = player.currentMediaItem?.mediaId
                        val isCurrentLocal = currentUrl?.let { it.startsWith("/") || it.startsWith("file://") || it.startsWith("content://") } ?: false
                        if (isCurrentLocal) {
                            val isMusic = MiniPlayerState.isMusicMode.value
                            val nextD = getNextDownloadedItem(context, currentUrl!!, isMusic)
                            if (nextD != null) {
                                val nextPath = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), nextD.fileName).absolutePath
                                navigateToPlayer(nextPath, nextD.title, nextD.thumbnailUrl, isMusic)
                            }
                            return@collect
                        }
                        val nextRecommended = withContext(Dispatchers.IO) {
                            currentUrl?.let {
                                com.videhub.service.BackgroundAutoplayHandler.findNextRecommendedCandidate(context, it, MiniPlayerState.isMusicMode.value)
                            }
                        }
                        if (nextRecommended != null && !nextRecommended.url.isNullOrBlank()) {
                            navigateToPlayer(nextRecommended.url, nextRecommended.name ?: "", nextRecommended.thumbnails?.firstOrNull()?.url ?: "", MiniPlayerState.isMusicMode.value)
                        }
                    }
                } else {
                    com.videhub.service.BackgroundAutoplayHandler.handleAutoplay(context, player) { com.videhub.QueueManager.getNextVideo() }
                }
            }
        }
        launch {
            com.videhub.QueueManager.skipToPreviousEvent.collect {
                if (currentRoute?.startsWith("player") == true && com.videhub.MainActivity.isAppInForeground) {
                    val prevItem = com.videhub.QueueManager.skipToPrevious()
                    if (prevItem != null) {
                        navigateToPlayer(prevItem.url, prevItem.title, prevItem.thumbnailUrl, MiniPlayerState.isMusicMode.value)
                    }
                } else {
                    com.videhub.service.BackgroundAutoplayHandler.handleAutoplay(context, player) { com.videhub.QueueManager.skipToPrevious() }
                }
            }
        }
        
        while(true) {
            currentMediaItem = player.currentMediaItem
            hasActiveVideo = currentMediaItem != null && player.playbackState != androidx.media3.common.Player.STATE_IDLE && player.playbackState != androidx.media3.common.Player.STATE_ENDED
            kotlinx.coroutines.delay(1000)
        }
    }

    LaunchedEffect(currentRoute, hasActiveVideo, com.videhub.PipState.isActive.value, currentMediaItem?.mediaId) {
        val isPlayerScreen = currentRoute?.startsWith("player") == true
        if (isPlayerScreen || !hasActiveVideo) {
            MiniPlayerState.hide()
        } else {
            val title = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Unknown"
            val artist = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown"
            var artwork = currentMediaItem?.mediaMetadata?.artworkUri?.toString() ?: ""
            if (artwork == "none") artwork = ""
            val url = currentMediaItem?.mediaId ?: ""; MiniPlayerState.show(url, title, artwork, artist, MiniPlayerState.isMusicMode.value)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.Scaffold(
            bottomBar = {
                if (currentRoute != null && !currentRoute.startsWith("player") && !com.videhub.PipState.isActive.value) {
                    com.videhub.navigation.BottomNavigationBar(navController = navController, currentRoute = currentRoute)
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (com.videhub.PipState.isActive.value) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding)
                    .consumeWindowInsets(if (com.videhub.PipState.isActive.value) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding)
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
        
        androidx.compose.animation.AnimatedVisibility(
            visible = isMiniPlayerVisible && !com.videhub.PipState.isActive.value,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            com.videhub.ui.components.MiniPlayer(
                title = MiniPlayerState.currentTitle.value ?: "",
                channelName = MiniPlayerState.currentChannelName.value ?: "",
                isLoadingNext = com.videhub.MiniPlayerState.isLoadingNext.collectAsState().value,
                artworkUrl = miniPlayerArtworkUrl ?: "",
                player = mediaPlayer,
                isMusicMode = miniPlayerMusicMode,
                onExpand = {
                    if (currentMediaItem != null) {
                        val id = currentMediaItem!!.mediaId
                        navigateToPlayer(id, MiniPlayerState.currentTitle.value ?: "", miniPlayerArtworkUrl, miniPlayerMusicMode)
                    }
                },
                onCloseClick = {
                    mediaPlayer?.stop()
                    mediaPlayer?.clearMediaItems()
                    MiniPlayerState.hide()
                }
            )
        }
    }
}
