package com.videhub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.videhub.MiniPlayerGlobalState
import com.videhub.MiniPlayerStateEnum
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
  onVideoPlay: (String, String, String) -> Unit = { _,_,_ -> },
  modifier: Modifier = Modifier
) {
  // ─── SAFETY CHECKS ───────────────────────────────────────
  if (player == null) return
  val state = MiniPlayerGlobalState.state.value
  if (state == MiniPlayerStateEnum.Hidden) return

  // ─── SCREEN MEASUREMENTS ─────────────────────────────────
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val configuration = LocalConfiguration.current

  // Total screen size in pixels
  val screenWidthPx = with(density) { 
    configuration.screenWidthDp.dp.toPx() 
  }
  val screenHeightPx = with(density) { 
    configuration.screenHeightDp.dp.toPx() 
  }

  // Safe padding from edges (8dp)
  val paddingPx = with(density) { 8.dp.toPx() }

  // ─── CARD SIZE CALCULATION ───────────────────────────────
  // Base width = screen width minus 24dp padding
  val basePipWidthDp = configuration.screenWidthDp - 24f
  val basePipWidthPx = with(density) { basePipWidthDp.dp.toPx() }

  // Height = 16:9 ratio of width
  val basePipHeightPx = basePipWidthPx * (9f / 16f)

  // Apply user's pinch scale
  val pipScale = remember { 
    Animatable(MiniPlayerGlobalState.pipScale.floatValue) 
  }

  // Actual rendered size after scale
  val pipWidthPx = basePipWidthPx * pipScale.value
  val pipHeightPx = basePipHeightPx * pipScale.value
  val pipWidthDp = with(density) { pipWidthPx.toDp() }
  val pipHeightDp = with(density) { pipHeightPx.toDp() }

  // Maximum X and Y before card goes off screen
  val maxX = screenWidthPx - pipWidthPx - paddingPx
  val maxY = screenHeightPx - pipHeightPx - paddingPx

  // ─── CARD POSITION ───────────────────────────────────────
  // Default position = top right corner
  val pipOffsetX = remember {
    Animatable(
      if (MiniPlayerGlobalState.pipOffsetX.floatValue < 0f)
        maxX  // first time = top right
      else
        MiniPlayerGlobalState.pipOffsetX.floatValue // remembered
    )
  }
  val pipOffsetY = remember {
    Animatable(
      if (MiniPlayerGlobalState.pipOffsetY.floatValue < 0f)
        paddingPx  // first time = near top
      else
        MiniPlayerGlobalState.pipOffsetY.floatValue
    )
  }

  // ─── PROGRESS BAR ────────────────────────────────────────
  var progress by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(player) {
    while (true) {
      val duration = player.duration.takeIf { it > 0 } ?: 1L
      progress = (player.currentPosition.toFloat() / duration.toFloat())
        .coerceIn(0f, 1f)
      delay(1000) // update every 1 second
    }
  }

  // ─── CONTROLS VISIBILITY ─────────────────────────────────
  // Controls show on tap, hide after 3 seconds
  var controlsVisible by remember { mutableStateOf(true) }
  LaunchedEffect(controlsVisible) {
    if (controlsVisible) {
      delay(3000)
      controlsVisible = false
    }
  }

  // ─── MAIN LAYOUT ─────────────────────────────────────────
  // This Box takes up the FULL SCREEN as an overlay
  // The card is positioned using offset()
  Box(modifier = modifier.fillMaxSize()) {

    // ── EDGE DOCKED STATE — Show Pill Tab ─────────────────
    if (state == MiniPlayerStateEnum.EdgeDockedLeft) {
      // Show pill on LEFT edge
      EdgePeekTab(
        direction = EdgeDirection.LEFT,
        modifier = Modifier.align(Alignment.CenterStart).offset(y = with(density) { (pipOffsetY.value - screenHeightPx / 2).toDp() + pipHeightDp/2 }),
        onClick = {
          // Undock — slide card back from left edge
          MiniPlayerGlobalState.state.value =
            MiniPlayerStateEnum.FloatingPip
          val targetX = paddingPx
          MiniPlayerGlobalState.pipOffsetX.floatValue = targetX
          scope.launch {
            pipOffsetX.animateTo(
              targetX,
              spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
              )
            )
          }
        }
      )
    }

    if (state == MiniPlayerStateEnum.EdgeDockedRight) {
      // Show pill on RIGHT edge
      EdgePeekTab(
        direction = EdgeDirection.RIGHT,
        modifier = Modifier.align(Alignment.CenterEnd).offset(y = with(density) { (pipOffsetY.value - screenHeightPx / 2).toDp() + pipHeightDp/2 }),
        onClick = {
          // Undock — slide card back from right edge
          MiniPlayerGlobalState.state.value =
            MiniPlayerStateEnum.FloatingPip
          val targetX = maxX
          MiniPlayerGlobalState.pipOffsetX.floatValue = targetX
          scope.launch {
            pipOffsetX.animateTo(
              targetX,
              spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
              )
            )
          }
        }
      )
    }

    // ── FLOATING STATE — Show Full Card ───────────────────
    Box(
      modifier = Modifier
        .offset {
          IntOffset(
            pipOffsetX.value.roundToInt(),
            pipOffsetY.value.roundToInt()
          )
        }
        .size(width = pipWidthDp, height = pipHeightDp)
        .shadow(elevation = 16.dp, shape = RoundedCornerShape(12.dp))
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surface)
        .pointerInput(Unit) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            
            var hasMoved = false
            var totalPan = androidx.compose.ui.geometry.Offset.Zero
            val touchSlop = viewConfiguration.touchSlop
            
            do {
              val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
              val zoom = event.calculateZoom()
              val pan = event.calculatePan()
              
              if (!hasMoved) {
                totalPan += pan
                if (totalPan.getDistance() > touchSlop || zoom != 1f) {
                  hasMoved = true
                }
              }
              
              if (hasMoved) {
                event.changes.forEach { it.consume() }
                scope.launch {
                  if (zoom != 1f) {
                    val newScale = (pipScale.value * zoom).coerceIn(0.5f, 1.5f)
                    val oldW = basePipWidthPx * pipScale.value
                    val newW = basePipWidthPx * newScale
                    val oldH = basePipHeightPx * pipScale.value
                    val newH = basePipHeightPx * newScale
                    
                    pipOffsetX.snapTo(pipOffsetX.value - (newW - oldW) / 2)
                    pipOffsetY.snapTo(pipOffsetY.value - (newH - oldH) / 2)
                    pipScale.snapTo(newScale)
                    com.videhub.MiniPlayerGlobalState.pipScale.floatValue = newScale
                  }
                  if (pan != androidx.compose.ui.geometry.Offset.Zero) {
                    pipOffsetX.snapTo(pipOffsetX.value + pan.x)
                    pipOffsetY.snapTo(pipOffsetY.value + pan.y)
                  }
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
              val x = pipOffsetX.value
              val y = pipOffsetY.value
              scope.launch {
                if (velocity.y > 2500f || y > maxY + paddingPx * 2) {
                  pipOffsetY.animateTo(screenHeightPx, androidx.compose.animation.core.spring())
                  onCloseClick()
                } else if (velocity.x < -2500f) {
                  pipOffsetX.animateTo(-pipWidthPx, androidx.compose.animation.core.spring())
                  onCloseClick()
                } else if (velocity.x > 2500f) {
                  pipOffsetX.animateTo(screenWidthPx, androidx.compose.animation.core.spring())
                  onCloseClick()
                } else {
                  val targetX = if (x < screenWidthPx / 2) paddingPx else maxX
                  val targetY = y.coerceIn(paddingPx, maxOf(paddingPx, maxY))
                  
                  com.videhub.MiniPlayerGlobalState.pipOffsetX.floatValue = targetX
                  com.videhub.MiniPlayerGlobalState.pipOffsetY.floatValue = targetY
                  
                  launch {
                    pipOffsetX.animateTo(targetX, androidx.compose.animation.core.spring(
                      dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                      stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    ))
                  }
                  launch {
                    pipOffsetY.animateTo(targetY, androidx.compose.animation.core.spring(
                      dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                      stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    ))
                  }
                }
              }

              }
          }
        }
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.view.LayoutInflater.from(ctx).inflate(com.videhub.R.layout.mini_player_view, null, false) as androidx.media3.ui.PlayerView
            },
            update = { view ->
                if (view.player != player) {
                    view.player = player
                }
            },
            onRelease = { view ->
                view.player = null
            },
            modifier = Modifier.fillMaxSize().background(Color.Black)
        )
        
        val artworkData = player?.currentMediaItem?.mediaMetadata?.artworkData
        val validArtUrl = artworkUrl.isNotBlank() && artworkUrl != "none"
        val hasArtwork = validArtUrl || artworkData != null
        var isPlaying by androidx.compose.runtime.remember(player) { androidx.compose.runtime.mutableStateOf(player?.isPlaying ?: false) }
        androidx.compose.runtime.DisposableEffect(player) {
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            }
            player?.addListener(listener)
            onDispose {
                player?.removeListener(listener)
            }
        }

        if (isMusicMode) {
            // Dark background for music mode
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))

            var artworkScale by remember { mutableFloatStateOf(1f) }
            val scaleAnimatable = remember { androidx.compose.animation.core.Animatable(artworkScale) }

            LaunchedEffect(isPlaying) {
                if (isPlaying) {
                    scaleAnimatable.animateTo(
                        targetValue = 1.05f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(2000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        )
                    )
                } else {
                    scaleAnimatable.animateTo(
                        targetValue = 1f,
                        animationSpec = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    )
                }
            }

            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "glow_pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.15f,
                targetValue = 0.45f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(1500),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "pulse_alpha"
            )

            val targetGlowAlpha = if (isPlaying) pulseAlpha else 0f
            val glowAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = targetGlowAlpha,
                animationSpec = androidx.compose.animation.core.tween(800),
                label = "glow_fade"
            )

            Column(
                modifier = Modifier.fillMaxSize(),
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
                            .aspectRatio(1f)
                            .padding(8.dp)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = glowAlpha),
                                        Color.Transparent
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                    
                    if (hasArtwork) {
                        val modelData: Any? = if (artworkData != null) artworkData else if (artworkUrl.startsWith("/")) java.io.File(artworkUrl) else if (validArtUrl) artworkUrl else ""
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(16.dp)
                                .shadow(8.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .graphicsLayer {
                                    scaleX = scaleAnimatable.value
                                    scaleY = scaleAnimatable.value
                                }
                        ) {
                            // Blurred Background Layer
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data(modelData)
                                    .error(android.R.drawable.ic_media_play)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop, // Crop to fill
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(radius = 20.dp)
                            )
                            // Darkening overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                            )
                            // Main Foreground Image
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data(modelData)
                                    .error(android.R.drawable.ic_media_play)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(16.dp)
                                .shadow(8.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .graphicsLayer {
                                    scaleX = scaleAnimatable.value
                                    scaleY = scaleAnimatable.value
                                }
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Audiotrack,
                                contentDescription = "No Artwork",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxSize(0.6f)
                            )
                        }
                    }
                }
                
                // Text at the bottom
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (channelName.isNotBlank()) {
                        Text(
                            text = channelName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Equalizer Bars (Bottom Start)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(16.dp)
                ) {
                    val eqAnim1 = remember { androidx.compose.animation.core.Animatable(4f) }
                    val eqAnim2 = remember { androidx.compose.animation.core.Animatable(4f) }
                    val eqAnim3 = remember { androidx.compose.animation.core.Animatable(4f) }
                    
                    LaunchedEffect(isPlaying) {
                        if (isPlaying) {
                            launch {
                                eqAnim1.animateTo(16f, androidx.compose.animation.core.infiniteRepeatable(
                                    animation = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.LinearEasing),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                ))
                            }
                            launch {
                                eqAnim2.animateTo(16f, androidx.compose.animation.core.infiniteRepeatable(
                                    animation = androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.LinearEasing),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                ))
                            }
                            launch {
                                eqAnim3.animateTo(16f, androidx.compose.animation.core.infiniteRepeatable(
                                    animation = androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.LinearEasing),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                ))
                            }
                        } else {
                            launch { eqAnim1.animateTo(4f, androidx.compose.animation.core.tween(300)) }
                            launch { eqAnim2.animateTo(4f, androidx.compose.animation.core.tween(300)) }
                            launch { eqAnim3.animateTo(4f, androidx.compose.animation.core.tween(300)) }
                        }
                    }
                    
                    Box(modifier = Modifier.width(3.dp).height(eqAnim1.value.dp).clip(RoundedCornerShape(1.5.dp)).background(MaterialTheme.colorScheme.onSurface))
                    Box(modifier = Modifier.width(3.dp).height(eqAnim2.value.dp).clip(RoundedCornerShape(1.5.dp)).background(MaterialTheme.colorScheme.onSurface))
                    Box(modifier = Modifier.width(3.dp).height(eqAnim3.value.dp).clip(RoundedCornerShape(1.5.dp)).background(MaterialTheme.colorScheme.onSurface))
                }
            }

        } else {
            // Video Mode Gradient and Title
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                            startY = 50f
                        )
                    )
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
        
        // Expand Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onExpand() },
            contentAlignment = Alignment.Center
        ) {
            if (isLoadingNext) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        

        // Previous button (Top Start - offset)
        // Play/Pause button (Top Start)
        if (!isLoadingNext) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(64.dp)
                    .clickable { if (isPlaying) player.pause() else player.play() },
                contentAlignment = Alignment.TopStart
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .size(32.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(64.dp)
                .clickable { onCloseClick() },
            contentAlignment = Alignment.TopEnd
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .size(32.dp)
            )
        }
        }
    }
}


enum class EdgeDirection { LEFT, RIGHT }

@androidx.compose.runtime.Composable
fun EdgePeekTab(
    direction: EdgeDirection,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    onClick: () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .width(24.dp)
            .height(80.dp)
            .clip(
                if (direction == EdgeDirection.LEFT) 
                    androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                else 
                    androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
            )
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .clickable { onClick() },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = if (direction == EdgeDirection.LEFT) androidx.compose.material.icons.Icons.Default.ChevronRight else androidx.compose.material.icons.Icons.Default.ChevronLeft,
            contentDescription = "Expand",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}
