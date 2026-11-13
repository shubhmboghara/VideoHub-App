package com.videhub.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.videhub.ui.components.DoubleTapSeekOverlay
import com.videhub.ui.components.SeekDirection
import com.videhub.ui.components.VideoSeekBar
import com.videhub.ui.components.formatVideoDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun ModernPlayerControls(
    modifier: Modifier = Modifier,
    mediaPlayer: Player?,
    isVisible: Boolean,
    isFullscreen: Boolean,
    isPortrait: Boolean = true,
    showCaptions: Boolean,
    currentSpeed: Float = 1f,
    videoScale: Float = 1f,
    relatedVideos: List<StreamInfoItem> = emptyList(),
    onVideoSelect: (String, String, String) -> Unit = { _, _, _ -> },
    onScaleChange: (Float, Offset) -> Unit = { _, _ -> },
    onResetZoom: () -> Unit = {},
    onToggleFullscreen: () -> Unit,
    onEnterFullscreen: () -> Unit = onToggleFullscreen,
    onExitFullscreen: () -> Unit = onToggleFullscreen,
    onMinimize: () -> Unit = {},
    onCollapseDragStart: () -> Unit = {},
    onCollapseDragDelta: (Float) -> Unit = {},
    onCollapseDragEnd: (Float) -> Unit = { onMinimize() },
    onCollapseDragCancel: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onCaptionsClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSingleTap: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val currentIsFullscreen by rememberUpdatedState(isFullscreen)
    val currentIsPortrait by rememberUpdatedState(isPortrait)
    val currentIsVisible by rememberUpdatedState(isVisible)
    val currentPlaybackSpeed by rememberUpdatedState(currentSpeed)
    val currentOnEnterFullscreen by rememberUpdatedState(onEnterFullscreen)
    val currentOnExitFullscreen by rememberUpdatedState(onExitFullscreen)
    val currentOnMinimize by rememberUpdatedState(onMinimize)
    val currentOnCollapseDragStart by rememberUpdatedState(onCollapseDragStart)
    val currentOnCollapseDragDelta by rememberUpdatedState(onCollapseDragDelta)
    val currentOnCollapseDragEnd by rememberUpdatedState(onCollapseDragEnd)
    val currentOnCollapseDragCancel by rememberUpdatedState(onCollapseDragCancel)
    val currentOnScaleChange by rememberUpdatedState(onScaleChange)
    val currentOnResetZoom by rememberUpdatedState(onResetZoom)
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnPreviousClick by rememberUpdatedState(onPreviousClick)
    val currentOnNextClick by rememberUpdatedState(onNextClick)
    val currentRelatedVideos by rememberUpdatedState(relatedVideos)

    var isPlaying by remember { mutableStateOf(mediaPlayer?.isPlaying ?: false) }
    var isBuffering by remember { mutableStateOf(mediaPlayer?.playbackState == Player.STATE_BUFFERING) }
    var currentTime by remember { mutableLongStateOf(mediaPlayer?.currentPosition ?: 0L) }
    var totalTime by remember { mutableLongStateOf(mediaPlayer?.duration?.coerceAtLeast(0L) ?: 0L) }
    var bufferedTime by remember { mutableLongStateOf(mediaPlayer?.bufferedPosition?.coerceAtLeast(0L) ?: 0L) }

    // 1. Double-Tap Quick Seek State
    var seekDirection by remember { mutableStateOf<SeekDirection?>(null) }
    var seekSeconds by remember { mutableIntStateOf(0) }
    var isSeekOverlayVisible by remember { mutableStateOf(false) }
    var hideSeekJob by remember { mutableStateOf<Job?>(null) }

    // 2. Hold-for-2x Speed State
    var isHold2xActive by remember { mutableStateOf(false) }

    // 3. Zoom Pill Toast State
    var showZoomToast by remember { mutableStateOf(false) }
    var hideZoomToastJob by remember { mutableStateOf<Job?>(null) }

    // 4. Horizontal Drag-to-Seek State
    var isHorizontalScrubbing by remember { mutableStateOf(false) }
    var scrubStartPosMs by remember { mutableLongStateOf(0L) }
    var scrubTargetPosMs by remember { mutableLongStateOf(0L) }

    // 6. Fullscreen Swipe-Up "Up Next / Related Videos" Drawer State
    var showFullscreenRelatedDrawer by remember { mutableStateOf(false) }
    LaunchedEffect(isFullscreen) {
        if (!isFullscreen) {
            showFullscreenRelatedDrawer = false
        }
    }

    // 7. 2-Finger Double-Tap Skip Toast State
    var twoFingerSkipMessage by remember { mutableStateOf<String?>(null) }
    var hideTwoFingerSkipJob by remember { mutableStateOf<Job?>(null) }
    var lastTwoFingerTapTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(mediaPlayer) {
        while (true) {
            mediaPlayer?.let {
                isPlaying = it.isPlaying
                isBuffering = it.playbackState == Player.STATE_BUFFERING || (it.playbackState == Player.STATE_IDLE && it.playWhenReady)
                if (!isHorizontalScrubbing) {
                    currentTime = it.currentPosition
                }
                totalTime = it.duration.coerceAtLeast(0L)
                bufferedTime = it.bufferedPosition.coerceAtLeast(0L)
            }
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isHold2xActive) {
                mediaPlayer?.playbackParameters = PlaybackParameters(currentPlaybackSpeed)
            }
        }
    }

    val swipeThresholdPx = with(density) { 34.dp.toPx() }
    val flickMinTravelPx = with(density) { 14.dp.toPx() }
    val topEdgeFullscreenIgnorePx = with(density) { 24.dp.toPx() }
    val topEdgeWatchIgnorePx = with(density) { 10.dp.toPx() }
    val bottomEdgeVisibleIgnorePx = with(density) { 32.dp.toPx() }
    val bottomEdgeHiddenIgnorePx = with(density) { 12.dp.toPx() }
    val edgeBackIgnorePx = with(density) { 24.dp.toPx() }

    var boxCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun triggerVerticalSwipeAction(isSwipeUp: Boolean) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (isSwipeUp) {
            if (!currentIsFullscreen) {
                currentOnEnterFullscreen()
            } else if (currentIsPortrait) {
                currentOnExitFullscreen()
            } else if (currentRelatedVideos.isNotEmpty()) {
                showFullscreenRelatedDrawer = true
            }
        } else {
            if (showFullscreenRelatedDrawer) {
                showFullscreenRelatedDrawer = false
            } else if (currentIsFullscreen) {
                currentOnExitFullscreen()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { boxCoordinates = it }
            // Outer tap/double-tap/long-press handler: runs AFTER inner awaitEachGesture on Main pass
            // so consumed drags/pinches cancel taps automatically without firing stray clicks.
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (showFullscreenRelatedDrawer) {
                            showFullscreenRelatedDrawer = false
                        } else {
                            currentOnSingleTap()
                        }
                    },
                    onDoubleTap = { offset ->
                        val width = size.width
                        val isFwd = offset.x >= width / 2
                        val dir = if (isFwd) SeekDirection.FORWARD else SeekDirection.BACKWARD

                        if (seekDirection == dir && isSeekOverlayVisible) {
                            seekSeconds += 10
                        } else {
                            seekDirection = dir
                            seekSeconds = 10
                        }
                        isSeekOverlayVisible = true

                        val deltaMs = if (isFwd) 10000L else -10000L
                        mediaPlayer?.let {
                            val newPos = (it.currentPosition + deltaMs).coerceIn(0L, it.duration.coerceAtLeast(0L))
                            it.seekTo(newPos)
                        }

                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                        hideSeekJob?.cancel()
                        hideSeekJob = coroutineScope.launch {
                            delay(650)
                            isSeekOverlayVisible = false
                            seekDirection = null
                            seekSeconds = 0
                        }
                    },
                    onLongPress = {
                        mediaPlayer?.let { player ->
                            if (player.isPlaying) {
                                isHold2xActive = true
                                player.playbackParameters = PlaybackParameters(2.0f)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    },
                    onPress = {
                        try {
                            awaitRelease()
                        } finally {
                            if (isHold2xActive) {
                                isHold2xActive = false
                                mediaPlayer?.playbackParameters = PlaybackParameters(currentPlaybackSpeed)
                            }
                        }
                    }
                )
            }
            // Inner unified gesture layer (fixed window-space coordinates prevent self-translation feedback):
            //  - 2-Finger Pinch-to-Zoom & 2-Finger Double-Tap Skip
            //  - 1-Finger Horizontal Slide to Seek (with strict horizontal ratio & Back edge safety)
            //  - 1-Finger Vertical Swipe UP (Fullscreen / Exit Portrait Fullscreen / More Videos Drawer)
            //  - 1-Finger Vertical Drag DOWN (1:1 Interactive Collapse -> Miniplayer / Exit Fullscreen)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(
                        requireUnconsumed = true,
                        pass = PointerEventPass.Main
                    )
                    fun toWindowPos(localPos: Offset): Offset =
                        boxCoordinates?.takeIf { it.isAttached }?.localToWindow(localPos) ?: localPos

                    val velocityTracker = VelocityTracker()
                    var trackedPointerId = firstDown.id
                    var lastWindowPos = toWindowPos(firstDown.position)
                    velocityTracker.addPosition(firstDown.uptimeMillis, lastWindowPos)

                    val gestureStartMs = System.currentTimeMillis()
                    val startX = firstDown.position.x
                    val startY = firstDown.position.y
                    val viewWidth = size.width.toFloat().coerceAtLeast(1f)
                    val viewHeight = size.height.toFloat().coerceAtLeast(1f)
                    val touchSlop = viewConfiguration.touchSlop

                    val activeTopIgnore = if (currentIsFullscreen) topEdgeFullscreenIgnorePx else topEdgeWatchIgnorePx
                    val activeBottomIgnore = if (currentIsVisible) bottomEdgeVisibleIgnorePx else bottomEdgeHiddenIgnorePx
                    val inSafeVerticalRegion = startY > activeTopIgnore && startY < (viewHeight - activeBottomIgnore)
                    val inSafeHorizontalRegion = startX > edgeBackIgnorePx && startX < (viewWidth - edgeBackIgnorePx)

                    var isMultiTouch = false
                    var maxPressedCount = 1
                    var twoFingerCenterX = startX
                    var multiTouchTotalMove = 0f

                    var gestureMode = "NONE" // "NONE", "HORIZ_SEEK", "VERT_ACTION", "VERT_COLLAPSE"
                    var totalWindowPanX = 0f
                    var totalWindowPanY = 0f
                    var verticalActionTriggered = false
                    var collapseDragStarted = false
                    var finishedNormally = false

                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val pressedChanges = event.changes.filter { it.pressed }
                            val pressedCount = pressedChanges.size
                            if (pressedCount > maxPressedCount) {
                                maxPressedCount = pressedCount
                            }

                            if (pressedCount >= 2) {
                                if (!isMultiTouch) {
                                    isMultiTouch = true
                                    if (collapseDragStarted) {
                                        collapseDragStarted = false
                                        currentOnCollapseDragCancel()
                                    }
                                    if (isHorizontalScrubbing) {
                                        isHorizontalScrubbing = false
                                    }
                                    gestureMode = "NONE"
                                }
                                twoFingerCenterX = pressedChanges.map { it.position.x }.average().toFloat()
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                multiTouchTotalMove += pan.getDistance() + abs(zoom - 1f) * 200f

                                if (abs(zoom - 1f) > 0.004f || pan.getDistance() > 1.5f) {
                                    currentOnScaleChange(zoom, pan)
                                    showZoomToast = true
                                    hideZoomToastJob?.cancel()
                                    hideZoomToastJob = coroutineScope.launch {
                                        delay(1200)
                                        showZoomToast = false
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            } else if (!isMultiTouch && pressedCount == 1 && !isHold2xActive) {
                                val change = pressedChanges.first()
                                if (change.isConsumed && gestureMode == "NONE") {
                                    lastWindowPos = toWindowPos(change.position)
                                    trackedPointerId = change.id
                                    continue
                                }

                                val curWindowPos = toWindowPos(change.position)
                                val deltaWindow = if (change.id == trackedPointerId) {
                                    curWindowPos - lastWindowPos
                                } else {
                                    trackedPointerId = change.id
                                    Offset.Zero
                                }
                                lastWindowPos = curWindowPos
                                velocityTracker.addPosition(change.uptimeMillis, curWindowPos)

                                totalWindowPanX += deltaWindow.x
                                totalWindowPanY += deltaWindow.y

                                if (gestureMode == "NONE" && inSafeVerticalRegion) {
                                    val dist = hypot(totalWindowPanX, totalWindowPanY)
                                    if (dist > touchSlop * 1.1f) {
                                        if (abs(totalWindowPanX) > abs(totalWindowPanY) * 2.2f &&
                                            abs(totalWindowPanX) > touchSlop * 1.8f &&
                                            totalTime > 0L &&
                                            inSafeHorizontalRegion
                                        ) {
                                            gestureMode = "HORIZ_SEEK"
                                            isHorizontalScrubbing = true
                                            scrubStartPosMs = mediaPlayer?.currentPosition ?: 0L
                                            scrubTargetPosMs = scrubStartPosMs
                                            event.changes.forEach { it.consume() }
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        } else if (abs(totalWindowPanY) > abs(totalWindowPanX) * 1.15f) {
                                            event.changes.forEach { it.consume() }
                                            if (!currentIsFullscreen && totalWindowPanY > 0f) {
                                                gestureMode = "VERT_COLLAPSE"
                                                collapseDragStarted = true
                                                currentOnCollapseDragStart()
                                                currentOnCollapseDragDelta(totalWindowPanY)
                                            } else {
                                                gestureMode = "VERT_ACTION"
                                            }
                                        }
                                    }
                                } else if (gestureMode == "VERT_COLLAPSE") {
                                    event.changes.forEach { it.consume() }
                                    currentOnCollapseDragDelta(deltaWindow.y)
                                    continue
                                }

                                when (gestureMode) {
                                    "HORIZ_SEEK" -> {
                                        event.changes.forEach { it.consume() }
                                        val maxSwipeSeekMs = minOf(90_000L, totalTime.coerceAtLeast(15_000L))
                                        val deltaMs = ((totalWindowPanX / viewWidth) * maxSwipeSeekMs).toLong()
                                        scrubTargetPosMs = (scrubStartPosMs + deltaMs).coerceIn(0L, totalTime.coerceAtLeast(0L))
                                        currentTime = scrubTargetPosMs
                                    }
                                    "VERT_ACTION" -> {
                                        event.changes.forEach { it.consume() }
                                        if (!verticalActionTriggered && abs(totalWindowPanY) >= swipeThresholdPx) {
                                            verticalActionTriggered = true
                                            triggerVerticalSwipeAction(isSwipeUp = totalWindowPanY < 0f)
                                        }
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        finishedNormally = true
                        val velocity = velocityTracker.calculateVelocity()

                        if (collapseDragStarted) {
                            collapseDragStarted = false
                            currentOnCollapseDragEnd(velocity.y)
                        } else if (gestureMode == "VERT_ACTION" && !verticalActionTriggered) {
                            val isUpFlick = totalWindowPanY <= -flickMinTravelPx &&
                                velocity.y <= -600f &&
                                abs(velocity.y) > abs(velocity.x) * 1.25f
                            val isDownFlick = totalWindowPanY >= flickMinTravelPx &&
                                velocity.y >= 600f &&
                                abs(velocity.y) > abs(velocity.x) * 1.25f
                            if (isUpFlick) {
                                verticalActionTriggered = true
                                triggerVerticalSwipeAction(isSwipeUp = true)
                            } else if (isDownFlick) {
                                verticalActionTriggered = true
                                triggerVerticalSwipeAction(isSwipeUp = false)
                            }
                        }

                        if (isHorizontalScrubbing) {
                            mediaPlayer?.seekTo(scrubTargetPosMs)
                            isHorizontalScrubbing = false
                        }

                        val gestureDurationMs = System.currentTimeMillis() - gestureStartMs
                        if (maxPressedCount == 2 && gestureDurationMs < 260L && multiTouchTotalMove < touchSlop * 1.5f) {
                            val nowMs = System.currentTimeMillis()
                            if (nowMs - lastTwoFingerTapTimeMs <= 380L) {
                                lastTwoFingerTapTimeMs = 0L
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (twoFingerCenterX >= viewWidth / 2f) {
                                    currentOnNextClick()
                                    twoFingerSkipMessage = "Skipped to Next"
                                } else {
                                    currentOnPreviousClick()
                                    twoFingerSkipMessage = "Skipped to Previous"
                                }
                                hideTwoFingerSkipJob?.cancel()
                                hideTwoFingerSkipJob = coroutineScope.launch {
                                    delay(1200)
                                    twoFingerSkipMessage = null
                                }
                            } else {
                                lastTwoFingerTapTimeMs = nowMs
                            }
                        }
                    } finally {
                        if (!finishedNormally) {
                            if (collapseDragStarted) {
                                collapseDragStarted = false
                                currentOnCollapseDragCancel()
                            }
                            if (isHorizontalScrubbing) {
                                isHorizontalScrubbing = false
                            }
                            if (isHold2xActive) {
                                isHold2xActive = false
                                mediaPlayer?.playbackParameters = PlaybackParameters(currentPlaybackSpeed)
                            }
                        }
                    }
                }
            }
    ) {
        // Double-Tap Quick Seek Animated Ripple & Chevrons Overlay
        DoubleTapSeekOverlay(
            direction = seekDirection,
            seconds = seekSeconds,
            isVisible = isSeekOverlayVisible
        )

        // Standalone buffering indicator when controls overlay is hidden
        if (!isVisible && isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // ── 1. Hold-for-2x Speed Pill Indicator ──
        AnimatedVisibility(
            visible = isHold2xActive,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.78f),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "2x speed",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // ── 2. Horizontal Slide-to-Seek Center Preview Badge ──
        AnimatedVisibility(
            visible = isHorizontalScrubbing,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val deltaSec = ((scrubTargetPosMs - scrubStartPosMs) / 1000L).toInt()
            val deltaSign = if (deltaSec >= 0) "+${deltaSec}s" else "${deltaSec}s"
            val scrubFrac = if (totalTime > 0L) (scrubTargetPosMs.toFloat() / totalTime.toFloat()).coerceIn(0f, 1f) else 0f

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.82f),
                modifier = Modifier.border(0.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .width(164.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = deltaSign,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${formatVideoDuration(scrubTargetPosMs)} / ${formatVideoDuration(totalTime)}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(scrubFrac)
                                .background(Color.White)
                        )
                    }
                }
            }
        }

        // ── 3. 2-Finger Double-Tap Skip Toast Pill ──
        AnimatedVisibility(
            visible = twoFingerSkipMessage != null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.82f)
            ) {
                Text(
                    text = twoFingerSkipMessage ?: "",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        // ── 4. Floating Zoom Level Indicator ──
        AnimatedVisibility(
            visible = !isHold2xActive && (videoScale > 1.05f || showZoomToast),
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.75f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .clickable { currentOnResetZoom() }
                    .testTag("zoom_indicator_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val zoomLabel = when {
                        videoScale <= 1.05f -> "Original"
                        videoScale in 1.22f..1.42f -> "Zoomed to fill (${String.format(Locale.US, "%.1f", videoScale)}x)"
                        else -> "Zoom ${String.format(Locale.US, "%.1f", videoScale)}x"
                    }
                    Text(
                        text = zoomLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        )
                    )
                    if (videoScale > 1.05f) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset zoom",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ── 6. Standard Player Controls UI Overlay ──
        AnimatedVisibility(
            visible = isVisible && !isHold2xActive && !isHorizontalScrubbing && !showFullscreenRelatedDrawer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
            ) {
                // Center Playback Controls (Previous, Play/Pause, Next) matching YouTube player
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .testTag("player_center_controls"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousClick,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .testTag("player_prev_button")
                            .semantics { contentDescription = "Previous video" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(40.dp))

                    IconButton(
                        onClick = {
                            mediaPlayer?.let { player ->
                                if (player.playbackState == Player.STATE_IDLE) {
                                    player.prepare()
                                    player.play()
                                } else if (player.playbackState == Player.STATE_ENDED) {
                                    player.seekTo(0)
                                    player.play()
                                } else if (player.isPlaying || player.playWhenReady) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(60.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .testTag("player_play_pause_button")
                            .semantics { contentDescription = if (isPlaying) "Pause video" else "Play video" }
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(40.dp))

                    IconButton(
                        onClick = onNextClick,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .testTag("player_next_button")
                            .semantics { contentDescription = "Next video" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // YouTube-style Bottom Bar (Timestamp on left, More videos & Fullscreen on right, SeekBar along bottom)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isFullscreen) 20.dp else 0.dp,
                            vertical = if (isFullscreen) 14.dp else 0.dp
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = if (isFullscreen) 0.dp else 12.dp,
                                end = if (isFullscreen) 0.dp else 4.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("player_time_label")
                        ) {
                            Text(
                                text = formatVideoDuration(currentTime),
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFeatureSettings = "tnum",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = " / ${formatVideoDuration(totalTime)}",
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFeatureSettings = "tnum",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isFullscreen && relatedVideos.isNotEmpty()) {
                                Surface(
                                    onClick = { showFullscreenRelatedDrawer = true },
                                    shape = RoundedCornerShape(50),
                                    color = Color.Black.copy(alpha = 0.55f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "More videos",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "More videos",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = onToggleFullscreen,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("player_fullscreen_button")
                                    .semantics { contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen" }
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    VideoSeekBar(
                        positionMs = currentTime,
                        durationMs = totalTime,
                        bufferedPositionMs = bufferedTime,
                        onSeek = { seekPosition ->
                            currentTime = seekPosition
                            mediaPlayer?.seekTo(seekPosition)
                        },
                        activeColor = Color.White,
                        bufferedColor = Color.White.copy(alpha = 0.45f),
                        inactiveColor = Color.White.copy(alpha = 0.25f),
                        thumbColor = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isFullscreen) 0.dp else 12.dp)
                    )
                }
            }
        }

        // ── 7. Fullscreen Swipe-Up "More Videos / Up Next" Drawer ──
        AnimatedVisibility(
            visible = isFullscreen && showFullscreenRelatedDrawer && relatedVideos.isNotEmpty(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.86f))
                    .padding(top = 12.dp, bottom = 18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "More videos",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { showFullscreenRelatedDrawer = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close more videos",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(
                        items = relatedVideos.take(15),
                        key = { index, item -> "${item.url}_$index" }
                    ) { _, video ->
                        val thumb = video.thumbnails.firstOrNull()?.url ?: ""
                        val vTitle = video.name ?: "Video"
                        val vUploader = video.uploaderName ?: ""
                        val vDuration = video.duration

                        Column(
                            modifier = Modifier
                                .width(196.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val url = video.url ?: ""
                                    if (url.isNotBlank()) {
                                        showFullscreenRelatedDrawer = false
                                        onVideoSelect(url, vTitle, thumb)
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF222222))
                            ) {
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = vTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (vDuration > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black.copy(alpha = 0.8f))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = com.videhub.utils.FormatHelper.formatDuration(vDuration),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = vTitle,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (vUploader.isNotBlank()) {
                                Text(
                                    text = vUploader,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
