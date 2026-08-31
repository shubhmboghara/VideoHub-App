package com.videhub.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle



import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videhub.ui.components.CaptionLine3

@Composable
fun BoxScope.CaptionsOverlay(
    isPipActive: Boolean,
    showCaptions: Boolean,
    controllerVisible: Boolean,
    offlineCaptions: List<CaptionLine3>,
    mediaPlayer: androidx.media3.common.Player?,
    activeCaptions: List<CharSequence>,
    isMusicMode: Boolean = false
) {
    if (!isPipActive && showCaptions) {
    val hasOfflineCaptions = offlineCaptions.isNotEmpty()
    var currentOfflineCaption = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.videhub.ui.components.CaptionLine3?>(null) }
    var seekedPosition = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }

    androidx.compose.runtime.DisposableEffect(mediaPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int
            ) {
                if (reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK ||
                    reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                    seekedPosition.value = newPosition.positionMs
                }
            }
        }
        mediaPlayer?.addListener(listener)
        onDispose { mediaPlayer?.removeListener(listener) }
    }

    androidx.compose.runtime.LaunchedEffect(seekedPosition.value) {
        if (seekedPosition.value != null) {
            kotlinx.coroutines.delay(500)
            seekedPosition.value = null
        }
    }

    androidx.compose.runtime.LaunchedEffect(mediaPlayer, offlineCaptions, seekedPosition.value) {
        while(true) {
            val pos = seekedPosition.value ?: mediaPlayer?.currentPosition ?: 0L
            currentOfflineCaption.value = offlineCaptions.lastOrNull { pos >= it.startMillis && pos <= it.endMillis }
            kotlinx.coroutines.delay(200)
        }
    }

        val bottomPadding by animateDpAsState(
            targetValue = if (controllerVisible) 80.dp else 16.dp,
            animationSpec = tween(200),
            label = "captionPadding"
        )

        when {
            // ✅ Case 1: If we have offline/manually fetched captions (lyrics or JSON3), show them
            hasOfflineCaptions && currentOfflineCaption.value != null -> {
                val lyricsModeState by com.videhub.ui.components.LyricsPreferenceManager.lyricsMode.collectAsStateWithLifecycle()
                val textToShow = when (lyricsModeState) {
                    com.videhub.ui.components.LyricsMode.PHONETIC -> currentOfflineCaption.value?.romanizedText ?: currentOfflineCaption.value?.nativeText ?: ""
                    com.videhub.ui.components.LyricsMode.TRANSLATION -> currentOfflineCaption.value?.englishText ?: currentOfflineCaption.value?.nativeText ?: ""
                    com.videhub.ui.components.LyricsMode.NATIVE -> currentOfflineCaption.value?.nativeText ?: ""
                }.let { com.videhub.audio.LyricsManager.cleanLyricsText(it) }
                
                if (textToShow.isNotBlank()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomPadding, start = 16.dp, end = 16.dp)
                    ) {
                        Text(
                            text = textToShow,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Medium,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    offset = Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            ),
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // ✅ Case 2: Video mode → ALWAYS use ExoPlayer activeCaptions
            // Never use offlineCaptions in video mode — they conflict
            activeCaptions.isNotEmpty() -> {
                val captionText = activeCaptions
                    .mapNotNull { it?.toString() }
                    .map { com.videhub.audio.LyricsManager.cleanLyricsText(it) }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString("\n")

                if (captionText.isNotBlank()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomPadding, start = 16.dp, end = 16.dp)
                    ) {
                        Text(
                            text = captionText,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Medium,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    offset = Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            ),
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
