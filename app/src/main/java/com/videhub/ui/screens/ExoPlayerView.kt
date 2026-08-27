package com.videhub.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView

@Composable
fun ExoPlayerView(
    modifier: Modifier = Modifier,
    mediaPlayer: Player?,
    isFullscreen: Boolean,
    isPipActive: Boolean = false,
    isMusicMode: Boolean,
    isScreenLocked: Boolean = false,
    showCaptions: Boolean,
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    onToggleFullscreen: () -> Unit,
    onControllerVisibilityChanged: (Boolean) -> Unit,
    onCaptionsRequested: () -> Unit,
    onActiveCaptionsChanged: (List<String>) -> Unit
) {
    val currentShowCaptions = rememberUpdatedState(showCaptions)
    val currentOnActiveCaptionsChanged = rememberUpdatedState(onActiveCaptionsChanged)
    val currentOnCaptionsRequested = rememberUpdatedState(onCaptionsRequested)
    val currentOnControllerVisibilityChanged = rememberUpdatedState(onControllerVisibilityChanged)
    val currentOnToggleFullscreen = rememberUpdatedState(onToggleFullscreen)

    LaunchedEffect(mediaPlayer) {
        mediaPlayer?.let { player ->
            val params = player.trackSelectionParameters
            if (params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
                player.trackSelectionParameters = params.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
            }
        }
    }

    Box(
        modifier = modifier.clipToBounds()
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = mediaPlayer
                    useArtwork = false
                    setShowSubtitleButton(false)
                    subtitleView?.visibility = android.view.View.INVISIBLE
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player != mediaPlayer) {
                    view.player = mediaPlayer
                }
            },
            onRelease = { view ->
                view.player = null
            }
        )
    }
}
