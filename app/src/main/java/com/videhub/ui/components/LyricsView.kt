package com.videhub.ui.components

import androidx.lifecycle.compose.collectAsStateWithLifecycle



import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LyricsView(
    lyrics: List<com.videhub.ui.components.CaptionLine3>,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentPosition by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while(true) {
            currentPosition = positionProvider()
            kotlinx.coroutines.delay(200)
        }
    }

    var seekedPosition by remember { mutableStateOf<Long?>(null) }
    
    DisposableEffect(context) {
        val player = com.videhub.service.MediaSessionManager.getOrCreatePlayer(context)
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int
            ) {
                if (reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK ||
                    reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                    seekedPosition = newPosition.positionMs
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(seekedPosition) {
        if (seekedPosition != null) {
            kotlinx.coroutines.delay(500)
            seekedPosition = null
        }
    }

    val effectivePosition = seekedPosition ?: currentPosition
    val currentLineIndex = remember(effectivePosition, lyrics) {
        lyrics.indexOfLast { effectivePosition >= it.startMillis }
    }
    val listState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scrollOffsetPx = remember { with(density) { (-120).dp.roundToPx() } }

    // Smooth auto-scroll behavior to center the active line
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0 && currentLineIndex < lyrics.size) {
            listState.animateScrollToItem(currentLineIndex, scrollOffset = scrollOffsetPx)
        }
    }

    val isUnsynced = remember(lyrics) {
        lyrics.isNotEmpty() && lyrics.all { it.startMillis == Long.MAX_VALUE }
    }

    val lyricsModeState by LyricsPreferenceManager.lyricsMode.collectAsStateWithLifecycle()
    
    val hasPhonetic = remember(lyrics) { lyrics.any { it.romanizedText != null } }
    val hasEnglish = remember(lyrics) { lyrics.any { it.englishText != null } }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        
        // Mode Selector
        if (hasPhonetic || hasEnglish) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        ModeChip(
                            text = "Native",
                            isSelected = lyricsModeState == LyricsMode.NATIVE,
                            onClick = { LyricsPreferenceManager.setMode(LyricsMode.NATIVE) }
                        )
                        if (hasPhonetic) {
                            ModeChip(
                                text = "Phonetic",
                                isSelected = lyricsModeState == LyricsMode.PHONETIC,
                                onClick = { LyricsPreferenceManager.setMode(LyricsMode.PHONETIC) }
                            )
                        }
                        if (hasEnglish) {
                            ModeChip(
                                text = "English",
                                isSelected = lyricsModeState == LyricsMode.TRANSLATION,
                                onClick = { LyricsPreferenceManager.setMode(LyricsMode.TRANSLATION) }
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            itemsIndexed(lyrics, key = { index, line -> "${line.startMillis}_${index}" }) { index, line ->
                val isCurrentLine = index == currentLineIndex
                
                val textToShow = when (lyricsModeState) {
                    LyricsMode.PHONETIC -> line.romanizedText ?: line.nativeText
                    LyricsMode.TRANSLATION -> line.englishText ?: line.nativeText
                    LyricsMode.NATIVE -> line.nativeText
                }

                Text(
                    text = textToShow,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrentLine || isUnsynced) {
                        MaterialTheme.colorScheme.onBackground 
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
