package com.videhub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.videhub.audio.LyricLine
import com.videhub.audio.LyricsData
import com.videhub.audio.LyricsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SyncedLyricsView(
    title: String,
    channelName: String,
    durationSeconds: Long = 0,
    mediaPlayer: Player?,
    offlineCaptions: List<CaptionLine3> = emptyList(),
    description: String? = null,
    modifier: Modifier = Modifier
) {
    var lyricsData by remember { mutableStateOf<LyricsData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    val chapters = remember(description, offlineCaptions) { 
        if (offlineCaptions.isNotEmpty()) emptyList() 
        else LyricsManager.extractChapters(description ?: "") 
    }
    var currentChapter by remember { mutableStateOf<com.videhub.audio.VideoChapter?>(null) }

    // Fetch lyrics prioritizing CC -> Description -> Cleaned LRC search
    LaunchedEffect(title, channelName, offlineCaptions, description, currentChapter) {
        isLoading = true
        lyricsData = null
        
        val searchTitle = currentChapter?.title?.takeIf { it.isNotBlank() } ?: title
        val searchArtist = currentChapter?.artist?.takeIf { it.isNotBlank() } ?: channelName
        val searchDuration = if (currentChapter != null) {
             val idx = chapters.indexOf(currentChapter)
             if (idx >= 0 && idx < chapters.size - 1) {
                 (chapters[idx+1].timeMs - chapters[idx].timeMs) / 1000L
             } else 0L
        } else durationSeconds

        val fetched = LyricsManager.getLyrics(
            title = searchTitle,
            channel = searchArtist,
            durationSeconds = searchDuration,
            captions = offlineCaptions,
            description = description
        )
        
        if (fetched != null && fetched.isSynced && currentChapter != null) {
             val shiftMs = currentChapter!!.timeMs
             lyricsData = fetched.copy(lines = fetched.lines.map { it.copy(timeMs = it.timeMs + shiftMs) }, source = fetched.source + " (Chapter)")
        } else {
             lyricsData = fetched
        }
        isLoading = false
    }

    // Continuously update current position for synchronization
    LaunchedEffect(mediaPlayer) {
        while (true) {
            mediaPlayer?.let { p ->
                if (p.isPlaying) {
                    currentPositionMs = p.currentPosition
                    
                    if (chapters.isNotEmpty()) {
                        val activeChapter = chapters.lastOrNull { it.timeMs <= currentPositionMs } ?: chapters.first()
                        if (activeChapter != currentChapter) {
                            currentChapter = activeChapter
                        }
                    }
                }
            }
            delay(150L)
        }
    }

    // Determine currently active lyric index
    val activeIndex = remember(currentPositionMs, lyricsData) {
        val lines = lyricsData?.lines ?: emptyList()
        if (lines.isEmpty()) -1
        else {
            var found = -1
            for (i in lines.indices) {
                if (currentPositionMs >= lines[i].timeMs) {
                    found = i
                } else {
                    break
                }
            }
            found
        }
    }

    // Auto-scroll to active lyric
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lyricsData?.isSynced == true) {
            try {
                val target = (activeIndex - 2).coerceAtLeast(0)
                listState.animateScrollToItem(target)
            } catch (e: Exception) {
                // Ignore scroll glitches
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        if (isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Searching synchronized lyrics...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (lyricsData == null || lyricsData?.lines.isNullOrEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lyrics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No lyrics found for this track",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Try playing the official song version",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            
                            val searchTitle = currentChapter?.title?.takeIf { it.isNotBlank() } ?: title
                            val searchArtist = currentChapter?.artist?.takeIf { it.isNotBlank() } ?: channelName
                            val searchDuration = if (currentChapter != null) {
                                 val idx = chapters.indexOf(currentChapter)
                                 if (idx >= 0 && idx < chapters.size - 1) {
                                     (chapters[idx+1].timeMs - chapters[idx].timeMs) / 1000L
                                 } else 0L
                            } else durationSeconds

                            val fetched = LyricsManager.getLyrics(
                                title = searchTitle,
                                channel = searchArtist,
                                durationSeconds = searchDuration,
                                captions = offlineCaptions,
                                description = if (currentChapter != null) null else description
                            )
                            
                            if (fetched != null && fetched.isSynced && currentChapter != null) {
                                 val shiftMs = currentChapter!!.timeMs
                                 lyricsData = fetched.copy(lines = fetched.lines.map { it.copy(timeMs = it.timeMs + shiftMs) }, source = fetched.source + " (Chapter)")
                            } else {
                                 lyricsData = fetched
                            }
                            
                            isLoading = false
                        }
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry Search")
                }
            }
        } else {
            val lines = lyricsData!!.lines
            val isSynced = lyricsData!!.isSynced

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 48.dp,
                    bottom = 120.dp,
                    start = 24.dp,
                    end = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                itemsIndexed(lines) { index, line ->
                    val isActive = index == activeIndex && isSynced
                    val isPast = index < activeIndex && isSynced

                    val alpha by animateFloatAsState(
                        targetValue = when {
                            isActive -> 1f
                            isPast -> 0.45f
                            else -> 0.35f
                        },
                        animationSpec = tween(300),
                        label = "LyricAlpha"
                    )

                    val textColor by animateColorAsState(
                        targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color.White,
                        animationSpec = tween(300),
                        label = "LyricColor"
                    )

                    Text(
                        text = line.text,
                        fontSize = if (isActive) 24.sp else 19.sp,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                        lineHeight = if (isActive) 32.sp else 26.sp,
                        color = textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(alpha)
                            .clickable {
                                if (isSynced) {
                                    mediaPlayer?.seekTo(line.timeMs)
                                    currentPositionMs = line.timeMs
                                }
                            }
                            .padding(vertical = 4.dp)
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Lyrics provided by ${lyricsData?.source ?: "LRCLIB"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
