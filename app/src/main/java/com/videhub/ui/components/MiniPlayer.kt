package com.videhub.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.videhub.MiniPlayerGlobalState
import com.videhub.MiniPlayerStateEnum
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
    player: Player?,           // shared ExoPlayer instance
    isMusicMode: Boolean,      // audio or video?
    artworkUrl: String,        // thumbnail/album art
    title: String,
    channelName: String,
    isLoadingNext: Boolean,    // true if pre-fetching next global track
    onExpand: () -> Unit,      // called when user taps to expand
    onCloseClick: () -> Unit,  // called when user dismisses
    onVideoPlay: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    // ─── SAFETY CHECKS ───────────────────────────────────────
    if (player == null) return
    val state = MiniPlayerGlobalState.state.value
    if (state == MiniPlayerStateEnum.Hidden) return

    // ─── SCREEN & INSET MEASUREMENTS ─────────────────────────
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current

    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val screenHeightDp = configuration.screenHeightDp.toFloat()
    val screenWidthPx = with(density) { screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { screenHeightDp.dp.toPx() }

    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Safe margins so the miniplayer never overlaps status bar or bottom navigation bar
    val horizontalMarginPx = with(density) { 10.dp.toPx() }
    val topMarginPx = with(density) { (statusBarTopDp + 10.dp).toPx() }
    val bottomMarginPx = with(density) { (navBarBottomDp + 68.dp).toPx() }

    // ─── YOUTUBE-CALIBRATED MIN / DEFAULT / MAX SIZES ────────
    // Default width ~56% of screen (~216dp), Min width ~43% (~166dp), Max width ~88% (~340dp)
    val basePipWidthDp = (screenWidthDp * 0.56f).coerceIn(196f, 236f)
    val minPipWidthDp = (screenWidthDp * 0.43f).coerceIn(156f, 176f)
    val maxPipWidthDp = (screenWidthDp * 0.88f).coerceIn(288f, screenWidthDp - 20f)

    val minScale = (minPipWidthDp / basePipWidthDp).coerceIn(0.68f, 0.88f)
    val maxScale = (maxPipWidthDp / basePipWidthDp).coerceIn(1.30f, 1.75f)

    val basePipWidthPx = with(density) { basePipWidthDp.dp.toPx() }
    val basePipHeightPx = basePipWidthPx * (9f / 16f)

    val initialClampedScale = MiniPlayerGlobalState.pipScale.floatValue.coerceIn(minScale, maxScale)
    val pipScale = remember { Animatable(initialClampedScale) }

    val pipWidthPx = basePipWidthPx * pipScale.value
    val pipHeightPx = basePipHeightPx * pipScale.value
    val pipWidthDp = with(density) { pipWidthPx.toDp() }
    val pipHeightDp = with(density) { pipHeightPx.toDp() }

    // Proportional UI scale factor (1.0f at default size, ~0.76f at min size, ~1.55f at max size)
    val sizeFactor = (pipWidthDp.value / basePipWidthDp).coerceIn(minScale, maxScale)

    val minX = horizontalMarginPx
    val minY = topMarginPx
    val maxX = maxOf(minX, screenWidthPx - pipWidthPx - horizontalMarginPx)
    val maxY = maxOf(minY, screenHeightPx - pipHeightPx - bottomMarginPx)

    // ─── CARD POSITION (Default = Bottom-Right above Bottom Nav like real YouTube) ───
    val pipOffsetX = remember {
        Animatable(
            if (MiniPlayerGlobalState.pipOffsetX.floatValue < 0f)
                maxX
            else
                MiniPlayerGlobalState.pipOffsetX.floatValue.coerceIn(minX, maxX)
        )
    }
    val pipOffsetY = remember {
        Animatable(
            if (MiniPlayerGlobalState.pipOffsetY.floatValue < 0f)
                maxY
            else
                MiniPlayerGlobalState.pipOffsetY.floatValue.coerceIn(minY, maxY)
        )
    }

    // Keep position clamped if orientation or bounds change
    LaunchedEffect(maxX, maxY) {
        val targetX = if (MiniPlayerGlobalState.pipOffsetX.floatValue < 0f) maxX else pipOffsetX.value.coerceIn(minX, maxX)
        val targetY = if (MiniPlayerGlobalState.pipOffsetY.floatValue < 0f) maxY else pipOffsetY.value.coerceIn(minY, maxY)
        pipOffsetX.snapTo(targetX)
        pipOffsetY.snapTo(targetY)
        MiniPlayerGlobalState.pipOffsetX.floatValue = targetX
        MiniPlayerGlobalState.pipOffsetY.floatValue = targetY
    }

    // ─── PROGRESS BAR ────────────────────────────────────────
    var progress by remember { mutableFloatStateOf(0f) }
    val isRestored by com.videhub.MiniPlayerState.isRestoredFromSavedState.collectAsState()
    val savedPos by com.videhub.MiniPlayerState.lastSavedPosition.collectAsState()
    val savedDur by com.videhub.MiniPlayerState.lastSavedDuration.collectAsState()

    LaunchedEffect(player, isRestored, savedPos, savedDur) {
        while (true) {
            if (isRestored && (player.mediaItemCount == 0 || player.playbackState == Player.STATE_IDLE)) {
                val duration = savedDur.takeIf { it > 0 } ?: 1L
                progress = (savedPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else {
                val duration = player.duration.takeIf { it > 0 } ?: 1L
                progress = (player.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            }
            delay(500)
        }
    }

    // ─── MAIN LAYOUT ─────────────────────────────────────────
    Box(modifier = modifier.fillMaxSize()) {

        // ── EDGE DOCKED STATE — Show Pill Tab ─────────────────
        if (state == MiniPlayerStateEnum.EdgeDockedLeft) {
            EdgePeekTab(
                direction = EdgeDirection.LEFT,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(y = with(density) { (pipOffsetY.value - screenHeightPx / 2).toDp() + pipHeightDp / 2 }),
                onClick = {
                    MiniPlayerGlobalState.state.value = MiniPlayerStateEnum.FloatingPip
                    val targetX = minX
                    MiniPlayerGlobalState.pipOffsetX.floatValue = targetX
                    scope.launch {
                        pipOffsetX.animateTo(
                            targetX,
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                }
            )
        }

        if (state == MiniPlayerStateEnum.EdgeDockedRight) {
            EdgePeekTab(
                direction = EdgeDirection.RIGHT,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(y = with(density) { (pipOffsetY.value - screenHeightPx / 2).toDp() + pipHeightDp / 2 }),
                onClick = {
                    MiniPlayerGlobalState.state.value = MiniPlayerStateEnum.FloatingPip
                    val targetX = maxX
                    MiniPlayerGlobalState.pipOffsetX.floatValue = targetX
                    scope.launch {
                        pipOffsetX.animateTo(
                            targetX,
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                }
            )
        }

        // ── FLOATING STATE — YouTube Style Floating MiniPlayer Card ───
        val cornerRadius = (12.dp * sizeFactor).coerceIn(10.dp, 16.dp)
        val cardShape = RoundedCornerShape(cornerRadius)

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        pipOffsetX.value.roundToInt(),
                        pipOffsetY.value.roundToInt()
                    )
                }
                .size(width = pipWidthDp, height = pipHeightDp)
                .shadow(elevation = 18.dp, shape = cardShape, spotColor = Color.Black, ambientColor = Color.Black)
                .clip(cardShape)
                .background(Color(0xFF0F0F0F))
                .border(width = 0.75.dp, color = Color.White.copy(alpha = 0.14f), shape = cardShape)
                .pointerInput(screenWidthPx, screenHeightPx, minScale, maxScale) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        var hasMoved = false
                        var initialSlopPan = Offset.Zero
                        var totalDragX = 0f
                        var totalDragY = 0f
                        var isHorizontalLocked = false
                        val touchSlop = viewConfiguration.touchSlop

                        var curScale = pipScale.value.coerceIn(minScale, maxScale)
                        var curX = pipOffsetX.value
                        var curY = pipOffsetY.value

                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val pointerCount = event.changes.count { it.pressed }

                            if (!hasMoved) {
                                initialSlopPan += pan
                                if (initialSlopPan.getDistance() > touchSlop || (pointerCount > 1 && abs(zoom - 1f) > 0.01f)) {
                                    hasMoved = true
                                    totalDragX += initialSlopPan.x
                                    totalDragY += initialSlopPan.y
                                    if (abs(totalDragX) > abs(totalDragY)) {
                                        isHorizontalLocked = true
                                    }
                                }
                            } else {
                                totalDragX += pan.x
                                totalDragY += pan.y
                                if (abs(totalDragX) > abs(totalDragY)) {
                                    isHorizontalLocked = true
                                }
                            }

                            if (hasMoved) {
                                event.changes.forEach { it.consume() }

                                val oldW = basePipWidthPx * curScale
                                val oldH = basePipHeightPx * curScale
                                val newScale = if (zoom != 1f) {
                                    (curScale * zoom).coerceIn(minScale, maxScale)
                                } else {
                                    curScale
                                }
                                val newW = basePipWidthPx * newScale
                                val newH = basePipHeightPx * newScale

                                val dynamicMaxX = maxOf(minX, screenWidthPx - newW - horizontalMarginPx)
                                val dynamicMaxY = maxOf(minY, screenHeightPx - newH - bottomMarginPx)

                                val scaleDeltaX = (newW - oldW) / 2f
                                val scaleDeltaY = (newH - oldH) / 2f

                                curScale = newScale
                                curX = (curX - scaleDeltaX + pan.x).coerceIn(minX, dynamicMaxX)
                                val maxAllowedDragY = if (isHorizontalLocked || pointerCount > 1) dynamicMaxY else screenHeightPx
                                curY = (curY - scaleDeltaY + pan.y).coerceIn(minY, maxAllowedDragY)

                                MiniPlayerGlobalState.pipScale.floatValue = curScale
                                MiniPlayerGlobalState.pipOffsetX.floatValue = curX
                                MiniPlayerGlobalState.pipOffsetY.floatValue = curY

                                scope.launch {
                                    pipScale.snapTo(curScale)
                                    pipOffsetX.snapTo(curX)
                                    pipOffsetY.snapTo(curY)
                                }
                            }

                            event.changes.firstOrNull()?.let { change ->
                                if (change.pressed) {
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        val velocity = velocityTracker.calculateVelocity()
                        if (hasMoved) {
                            val currentW = basePipWidthPx * curScale
                            val currentH = basePipHeightPx * curScale
                            val dynamicMaxX = maxOf(minX, screenWidthPx - currentW - horizontalMarginPx)
                            val dynamicMaxY = maxOf(minY, screenHeightPx - currentH - bottomMarginPx)
                            val x = curX
                            val y = curY

                            val isPredominantlyVerticalDown =
                                !isHorizontalLocked &&
                                    totalDragY > 0f &&
                                    abs(totalDragY) > abs(totalDragX) * 1.5f

                            val bottomDismissZoneThreshold = screenHeightPx * 0.88f
                            val crossedBottomZone = (y + currentH) >= bottomDismissZoneThreshold && y > dynamicMaxY + horizontalMarginPx
                            val hasDownwardFlingVelocity =
                                velocity.y > 1600f &&
                                    abs(velocity.y) > abs(velocity.x) * 1.5f

                            val shouldDismissDownward =
                                isPredominantlyVerticalDown && (crossedBottomZone || hasDownwardFlingVelocity)

                            scope.launch {
                                if (shouldDismissDownward) {
                                    pipOffsetY.animateTo(
                                        targetValue = screenHeightPx,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                    onCloseClick()
                                } else {
                                    val currentCenterX = x + currentW / 2f
                                    val targetX = if (currentCenterX < screenWidthPx / 2f) minX else dynamicMaxX
                                    val targetY = y.coerceIn(minY, dynamicMaxY)

                                    MiniPlayerGlobalState.pipOffsetX.floatValue = targetX
                                    MiniPlayerGlobalState.pipOffsetY.floatValue = targetY

                                    launch {
                                        pipOffsetX.animateTo(
                                            targetValue = targetX,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                    launch {
                                        pipOffsetY.animateTo(
                                            targetValue = targetY,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            // ── 1. Video Surface ───────────────────────────────────
            AndroidView(
                factory = { ctx ->
                    android.view.LayoutInflater.from(ctx)
                        .inflate(com.videhub.R.layout.mini_player_view, null, false) as PlayerView
                },
                update = { view ->
                    if (view.player != player) {
                        view.player = player
                    }
                },
                onRelease = { view ->
                    view.player = null
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )

            MiniPlayerCardChrome(
                player = player,
                isMusicMode = isMusicMode,
                artworkUrl = artworkUrl,
                title = title,
                channelName = channelName,
                isLoadingNext = isLoadingNext,
                isRestored = isRestored,
                progress = progress,
                pipHeightDp = pipHeightDp,
                sizeFactor = sizeFactor,
                onExpand = onExpand,
                onCloseClick = onCloseClick,
                onVideoPlay = onVideoPlay
            )
        }
    }
}

@Composable
fun MiniPlayerCardChrome(
    player: Player?,
    isMusicMode: Boolean,
    artworkUrl: String,
    title: String,
    channelName: String,
    isLoadingNext: Boolean,
    isRestored: Boolean,
    progress: Float,
    pipHeightDp: Dp,
    sizeFactor: Float,
    onExpand: () -> Unit,
    onCloseClick: () -> Unit,
    onVideoPlay: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val artworkData = player?.currentMediaItem?.mediaMetadata?.artworkData
    val validArtUrl = artworkUrl.isNotBlank() && artworkUrl != "none"
    val hasArtwork = validArtUrl || artworkData != null
    val modelData: Any? = remember(artworkData, artworkUrl, validArtUrl) {
        if (artworkData != null) artworkData
        else if (artworkUrl.startsWith("/")) java.io.File(artworkUrl)
        else if (validArtUrl) artworkUrl
        else null
    }

    var isPlaying by remember(player) { mutableStateOf(player?.isPlaying ?: false) }
    var playbackState by remember(player) { mutableIntStateOf(player?.playbackState ?: Player.STATE_IDLE) }
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        isPlaying = player.isPlaying
        playbackState = player.playbackState
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ── Mode-Specific Visuals (Music Mode vs Video Mode) ──
        if (isMusicMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF141414))
            )

            // Ambient blurred artwork background
            if (hasArtwork && modelData != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(modelData)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 22.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.52f))
                )
            }

            val scaleAnimatable = remember { Animatable(1f) }
            LaunchedEffect(isPlaying) {
                if (isPlaying) {
                    scaleAnimatable.animateTo(
                        targetValue = 1.04f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                } else {
                    scaleAnimatable.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(600, easing = FastOutSlowInEasing)
                    )
                }
            }

            val infiniteTransition = rememberInfiniteTransition(label = "glow_pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.15f,
                targetValue = 0.42f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_alpha"
            )
            val glowAlpha by animateFloatAsState(
                targetValue = if (isPlaying) pulseAlpha else 0f,
                animationSpec = tween(600),
                label = "glow_fade"
            )

            val artOuterPad = (6.dp * sizeFactor).coerceIn(4.dp, 12.dp)
            val artCorner = (10.dp * sizeFactor).coerceIn(8.dp, 14.dp)
            val titleFontSp = (11.5f * sizeFactor).coerceIn(10f, 14f).sp
            val subFontSp = (9.5f * sizeFactor).coerceIn(8.5f, 11.5f).sp
            val showChannelSubtitle = channelName.isNotBlank() && pipHeightDp >= 106.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = (4.dp * sizeFactor).coerceIn(2.dp, 8.dp), bottom = (6.dp * sizeFactor).coerceIn(4.dp, 10.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Glow Ring
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .padding(artOuterPad * 0.5f)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = glowAlpha),
                                        Color.Transparent
                                    )
                                ),
                                shape = RoundedCornerShape(artCorner)
                            )
                    )

                    if (hasArtwork && modelData != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f)
                                .padding(artOuterPad)
                                .shadow(6.dp, RoundedCornerShape(artCorner))
                                .clip(RoundedCornerShape(artCorner))
                                .graphicsLayer {
                                    scaleX = scaleAnimatable.value
                                    scaleY = scaleAnimatable.value
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(modelData)
                                    .error(android.R.drawable.ic_media_play)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f)
                                .padding(artOuterPad)
                                .shadow(6.dp, RoundedCornerShape(artCorner))
                                .clip(RoundedCornerShape(artCorner))
                                .background(Color(0xFF272727))
                                .graphicsLayer {
                                    scaleX = scaleAnimatable.value
                                    scaleY = scaleAnimatable.value
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Audiotrack,
                                contentDescription = "No Artwork",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.fillMaxSize(0.5f)
                            )
                        }
                    }
                }

                // Bottom Metadata
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = (24.dp * sizeFactor).coerceIn(18.dp, 36.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        fontSize = titleFontSp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showChannelSubtitle) {
                        Text(
                            text = channelName,
                            fontSize = subFontSp,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Equalizer Bars (Bottom Start)
            val maxEqHeight = (13f * sizeFactor).coerceIn(10f, 18f)
            val minEqHeight = (3.5f * sizeFactor).coerceIn(3f, 5f)
            val barWidth = (2.5.dp * sizeFactor).coerceIn(2.dp, 3.5.dp)

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = (8.dp * sizeFactor).coerceIn(6.dp, 12.dp),
                        bottom = (8.dp * sizeFactor).coerceIn(6.dp, 12.dp)
                    )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(maxEqHeight.dp)
                ) {
                    val eqAnim1 = remember { Animatable(minEqHeight) }
                    val eqAnim2 = remember { Animatable(minEqHeight) }
                    val eqAnim3 = remember { Animatable(minEqHeight) }

                    LaunchedEffect(isPlaying, maxEqHeight, minEqHeight) {
                        if (isPlaying) {
                            launch {
                                eqAnim1.animateTo(
                                    maxEqHeight,
                                    infiniteRepeatable(
                                        animation = tween(300, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )
                            }
                            launch {
                                eqAnim2.animateTo(
                                    maxEqHeight,
                                    infiniteRepeatable(
                                        animation = tween(440, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )
                            }
                            launch {
                                eqAnim3.animateTo(
                                    maxEqHeight,
                                    infiniteRepeatable(
                                        animation = tween(360, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )
                            }
                        } else {
                            launch { eqAnim1.animateTo(minEqHeight, tween(250)) }
                            launch { eqAnim2.animateTo(minEqHeight, tween(250)) }
                            launch { eqAnim3.animateTo(minEqHeight, tween(250)) }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(barWidth)
                            .height(eqAnim1.value.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color.White.copy(alpha = 0.9f))
                    )
                    Box(
                        modifier = Modifier
                            .width(barWidth)
                            .height(eqAnim2.value.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color.White.copy(alpha = 0.9f))
                    )
                    Box(
                        modifier = Modifier
                            .width(barWidth)
                            .height(eqAnim3.value.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color.White.copy(alpha = 0.9f))
                    )
                }
            }
        } else {
            // Video Mode: show thumbnail when idle/restored before video surface renders
            if ((isRestored || (player?.mediaItemCount ?: 0) == 0 || playbackState == Player.STATE_IDLE) && hasArtwork && modelData != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(modelData)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom legibility gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.22f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
            )

            Text(
                text = title,
                fontSize = (11.5f * sizeFactor).coerceIn(10f, 14f).sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = (10.dp * sizeFactor).coerceIn(8.dp, 14.dp),
                        end = (10.dp * sizeFactor).coerceIn(8.dp, 14.dp),
                        bottom = (7.dp * sizeFactor).coerceIn(5.dp, 11.dp)
                    )
            )
        }

        // ── Tap-to-Expand Center Overlay & Loading Spinner ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onExpand() }
                .testTag("miniplayer_expand_area"),
            contentAlignment = Alignment.Center
        ) {
            if (isLoadingNext) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size((32.dp * sizeFactor).coerceIn(24.dp, 42.dp))
                )
            }
        }

        // ── Proportional YouTube-Style Corner Controls (Play/Pause & Close) ──
        val badgeSize = (30.dp * sizeFactor).coerceIn(24.dp, 38.dp)
        val iconSize = (18.dp * sizeFactor).coerceIn(14.dp, 22.dp)
        val cornerPad = (6.dp * sizeFactor).coerceIn(4.dp, 10.dp)
        val touchTargetSize = (42.dp * sizeFactor).coerceIn(34.dp, 52.dp)

        if (!isLoadingNext) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(touchTargetSize)
                    .clickable {
                        if (player != null && isPlaying) {
                            player.pause()
                        } else {
                            if (player == null || player.mediaItemCount == 0 || isRestored) {
                                com.videhub.PlayerStateManager.markPlaybackStarted()
                                val url = com.videhub.MiniPlayerState.currentVideoUrl.value ?: ""
                                val t = com.videhub.MiniPlayerState.currentTitle.value ?: ""
                                val thumb = com.videhub.MiniPlayerState.currentThumbnailUrl.value ?: artworkUrl
                                onVideoPlay(url, t, thumb)
                            } else {
                                player.play()
                            }
                        }
                    }
                    .testTag("miniplayer_play_pause_button"),
                contentAlignment = Alignment.TopStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = cornerPad, top = cornerPad)
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.62f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(touchTargetSize)
                .clickable { onCloseClick() }
                .testTag("miniplayer_close_button"),
            contentAlignment = Alignment.TopEnd
        ) {
            Box(
                modifier = Modifier
                    .padding(end = cornerPad, top = cornerPad)
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.62f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        // ── YouTube Bottom Playback Progress Bar ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.5.dp)
                .background(Color.White.copy(alpha = 0.24f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(Color(0xFFFF0033))
            )
        }
    }
}

enum class EdgeDirection { LEFT, RIGHT }

@Composable
fun EdgePeekTab(
    direction: EdgeDirection,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .width(24.dp)
            .height(80.dp)
            .clip(
                if (direction == EdgeDirection.LEFT)
                    RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                else
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
            )
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (direction == EdgeDirection.LEFT) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
            contentDescription = "Expand",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}
