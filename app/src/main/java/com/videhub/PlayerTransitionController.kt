package com.videhub

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Geometry endpoints describing the persistent video surface in Watch Page,
 * Miniplayer, and Fullscreen states.
 */
data class PlayerGeometryBounds(
    val watchX: Float,
    val watchY: Float,
    val watchWidth: Float,
    val watchHeight: Float,
    val miniX: Float,
    val miniY: Float,
    val miniWidth: Float,
    val miniHeight: Float,
    val containerWidth: Float,
    val containerHeight: Float,
    val miniCornerDp: Float
)

/**
 * Interpolated visual properties for a single frame of the persistent player surface
 * and its surrounding Watch Page / Miniplayer chrome.
 */
data class PlayerSurfaceFrame(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val cornerRadiusDp: Float,
    val elevationDp: Float,
    val borderAlpha: Float,
    val detailsAlpha: Float,
    val detailsTranslationYPx: Float,
    val backdropAlpha: Float,
    val watchControlsAlpha: Float,
    val miniChromeAlpha: Float
)

/**
 * Pure, testable transition math & gesture completion curves for the persistent player surface.
 */
object PlayerTransitionMath {
    fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
        if (edge1 <= edge0) return if (value >= edge1) 1f else 0f
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun computeSurfaceFrame(
        collapseProgress: Float,
        fullscreenProgress: Float,
        bounds: PlayerGeometryBounds,
        detailsSlideOffsetPx: Float,
        isSettledMiniplayer: Boolean
    ): PlayerSurfaceFrame {
        val cp = collapseProgress.coerceIn(0f, 1f)
        val fp = fullscreenProgress.coerceIn(0f, 1f)

        // Watch-to-Miniplayer linear-within-spring morph (interpolates directly to Miniplayer X/Y, never center-shrink)
        val watchToMiniX = lerp(bounds.watchX, bounds.miniX, cp)
        val watchToMiniY = lerp(bounds.watchY, bounds.miniY, cp)
        val watchToMiniW = lerp(bounds.watchWidth, bounds.miniWidth, cp)
        val watchToMiniH = lerp(bounds.watchHeight, bounds.miniHeight, cp)

        // Watch/Miniplayer-to-Fullscreen morph
        val x = lerp(watchToMiniX, 0f, fp)
        val y = lerp(watchToMiniY, 0f, fp)
        val width = lerp(watchToMiniW, bounds.containerWidth, fp).coerceAtLeast(1f)
        val height = lerp(watchToMiniH, bounds.containerHeight, fp).coerceAtLeast(1f)

        // Progressive corner radius & elevation
        val cornerRadiusDp = lerp(bounds.miniCornerDp * cp, 0f, fp).coerceAtLeast(0f)
        val elevationDp = lerp(18f * cp, 0f, fp).coerceAtLeast(0f)
        val borderAlpha = (0.14f * cp * (1f - fp)).coerceIn(0f, 0.14f)

        // Coordinated opacity & translation curves
        val collapseDetailsFade = 1f - smoothStep(0.0f, 0.48f, cp)
        val fullscreenDetailsFade = 1f - smoothStep(0.0f, 0.50f, fp)
        val detailsAlpha = (collapseDetailsFade * fullscreenDetailsFade).coerceIn(0f, 1f)

        val detailsProgress = maxOf(cp, fp)
        val detailsTranslationYPx = detailsSlideOffsetPx * smoothStep(0.0f, 0.55f, detailsProgress)

        val backdropAlpha = ((1f - smoothStep(0.05f, 0.90f, cp)) * (1f - fp)).coerceIn(0f, 1f)
        val watchControlsAlpha = (1f - smoothStep(0.0f, 0.36f, cp)).coerceIn(0f, 1f)

        val miniChromeAlpha = if (isSettledMiniplayer) {
            1f
        } else {
            (smoothStep(0.55f, 0.98f, cp) * (1f - fp)).coerceIn(0f, 1f)
        }

        return PlayerSurfaceFrame(
            x = x,
            y = y,
            width = width,
            height = height,
            cornerRadiusDp = cornerRadiusDp,
            elevationDp = elevationDp,
            borderAlpha = borderAlpha,
            detailsAlpha = detailsAlpha,
            detailsTranslationYPx = detailsTranslationYPx,
            backdropAlpha = backdropAlpha,
            watchControlsAlpha = watchControlsAlpha,
            miniChromeAlpha = miniChromeAlpha
        )
    }

    fun shouldCompleteFullscreenEnter(
        dragTravelPx: Float,
        minTravelPx: Float,
        velocityY: Float,
        flickTravelPx: Float = minTravelPx * 0.45f
    ): Boolean {
        val upwardTravel = -dragTravelPx
        if (upwardTravel >= minTravelPx && velocityY <= 350f) return true
        return upwardTravel >= flickTravelPx && velocityY <= -550f
    }

    fun shouldCompleteFullscreenExit(
        dragTravelPx: Float,
        minTravelPx: Float,
        velocityY: Float,
        isPortraitFullscreen: Boolean,
        flickTravelPx: Float = minTravelPx * 0.45f
    ): Boolean {
        val downwardTrigger = (dragTravelPx >= minTravelPx && velocityY >= -350f) ||
            (dragTravelPx >= flickTravelPx && velocityY >= 550f)
        if (downwardTrigger) return true
        if (isPortraitFullscreen) {
            val upwardTravel = -dragTravelPx
            return (upwardTravel >= minTravelPx && velocityY <= 350f) ||
                (upwardTravel >= flickTravelPx && velocityY <= -550f)
        }
        return false
    }
}

/**
 * Separates player animation physics, interactive drag tracking, and velocity-seeded
 * spring settling from playback/ExoPlayer lifecycle.
 */
@Stable
class PlayerTransitionController(
    private val scope: CoroutineScope,
    initialCollapse: Float = 0f,
    initialFullscreen: Float = 0f
) {
    val collapseAnimatable = Animatable(initialCollapse.coerceIn(0f, 1f))
    val fullscreenAnimatable = Animatable(initialFullscreen.coerceIn(0f, 1f))

    var isDraggingCollapse by mutableStateOf(false)
        private set
    private var activeDragCollapse by mutableFloatStateOf(initialCollapse.coerceIn(0f, 1f))

    var isDraggingFullscreen by mutableStateOf(false)
        private set
    private var activeDragFullscreen by mutableFloatStateOf(initialFullscreen.coerceIn(0f, 1f))
    private var fullscreenDragTravelPx by mutableFloatStateOf(0f)
    private var fullscreenDragStartedInFullscreen by mutableStateOf(false)

    val currentCollapseProgress: Float
        get() = (if (isDraggingCollapse) activeDragCollapse else collapseAnimatable.value).coerceIn(0f, 1f)

    val currentFullscreenProgress: Float
        get() = (if (isDraggingFullscreen) activeDragFullscreen else fullscreenAnimatable.value).coerceIn(0f, 1f)

    // Critically damped physical springs (~270-320ms visual settle)
    private val settleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 380f,
        visibilityThreshold = 0.001f
    )

    private val returnSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 420f,
        visibilityThreshold = 0.001f
    )

    fun computeCurrentFrame(
        bounds: PlayerGeometryBounds,
        detailsSlideOffsetPx: Float,
        isSettledMiniplayer: Boolean
    ): PlayerSurfaceFrame {
        return PlayerTransitionMath.computeSurfaceFrame(
            collapseProgress = currentCollapseProgress,
            fullscreenProgress = currentFullscreenProgress,
            bounds = bounds,
            detailsSlideOffsetPx = detailsSlideOffsetPx,
            isSettledMiniplayer = isSettledMiniplayer
        )
    }

    suspend fun syncCollapseTarget(isMiniplayer: Boolean) {
        if (isDraggingCollapse) return
        val target = if (isMiniplayer) 1f else 0f
        if (collapseAnimatable.isRunning && abs(collapseAnimatable.targetValue - target) < 0.01f) {
            return
        }
        if (abs(collapseAnimatable.value - target) > 0.001f) {
            collapseAnimatable.animateTo(
                targetValue = target,
                animationSpec = if (target == 0f) settleSpring else returnSpring
            )
        }
    }

    suspend fun syncFullscreenTarget(effectiveFullscreen: Boolean) {
        if (isDraggingFullscreen) return
        val target = if (effectiveFullscreen) 1f else 0f
        if (fullscreenAnimatable.isRunning && abs(fullscreenAnimatable.targetValue - target) < 0.01f) {
            return
        }
        if (abs(fullscreenAnimatable.value - target) > 0.001f) {
            fullscreenAnimatable.animateTo(
                targetValue = target,
                animationSpec = settleSpring
            )
        }
    }

    fun startCollapseDrag() {
        activeDragCollapse = collapseAnimatable.value.coerceIn(0f, 1f)
        isDraggingCollapse = true
        scope.launch { collapseAnimatable.stop() }
    }

    fun dragCollapseBy(deltaPx: Float, collapseDistancePx: Float) {
        if (!isDraggingCollapse) return
        val safeDist = collapseDistancePx.coerceAtLeast(180f)
        activeDragCollapse = (activeDragCollapse + deltaPx / safeDist).coerceIn(0f, 1f)
    }

    fun finishCollapseDrag(
        velocityY: Float,
        collapseDistancePx: Float,
        onCommitMiniplayer: () -> Unit
    ) {
        if (!isDraggingCollapse) return
        val endFraction = activeDragCollapse.coerceIn(0f, 1f)
        isDraggingCollapse = false
        val safeDist = collapseDistancePx.coerceAtLeast(180f)
        val normalizedVelocity = (velocityY / safeDist).coerceIn(-12f, 12f)
        val shouldCollapse = PlayerStateManager.shouldCollapseOnDragEnd(endFraction, velocityY)

        scope.launch {
            collapseAnimatable.snapTo(endFraction)
            if (shouldCollapse) {
                onCommitMiniplayer()
                collapseAnimatable.animateTo(
                    targetValue = 1f,
                    animationSpec = settleSpring,
                    initialVelocity = normalizedVelocity
                )
            } else {
                collapseAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = returnSpring,
                    initialVelocity = normalizedVelocity
                )
            }
        }
    }

    fun cancelCollapseDrag(isCurrentlyMiniplayer: Boolean) {
        if (!isDraggingCollapse) return
        val endFraction = activeDragCollapse.coerceIn(0f, 1f)
        isDraggingCollapse = false
        val target = if (isCurrentlyMiniplayer) 1f else 0f
        scope.launch {
            collapseAnimatable.snapTo(endFraction)
            collapseAnimatable.animateTo(
                targetValue = target,
                animationSpec = returnSpring
            )
        }
    }

    fun startFullscreenDrag(currentlyFullscreen: Boolean) {
        fullscreenDragStartedInFullscreen = currentlyFullscreen
        fullscreenDragTravelPx = 0f
        activeDragFullscreen = fullscreenAnimatable.value.coerceIn(0f, 1f)
        isDraggingFullscreen = true
        scope.launch { fullscreenAnimatable.stop() }
    }

    fun dragFullscreenBy(
        deltaY: Float,
        dragRangePx: Float,
        isPortraitFullscreen: Boolean
    ) {
        if (!isDraggingFullscreen) return
        fullscreenDragTravelPx += deltaY
        val safeRange = dragRangePx.coerceAtLeast(180f)
        val next = if (!fullscreenDragStartedInFullscreen) {
            // Upward swipe on Watch Page lifts preview up to 0.45f
            (-fullscreenDragTravelPx / safeRange).coerceIn(0f, 0.45f)
        } else {
            val signedExitTravel = if (isPortraitFullscreen && fullscreenDragTravelPx < 0f) {
                -fullscreenDragTravelPx
            } else {
                fullscreenDragTravelPx
            }
            (1f - signedExitTravel / safeRange).coerceIn(0.55f, 1f)
        }
        activeDragFullscreen = next
    }

    fun finishFullscreenDrag(
        velocityY: Float,
        dragRangePx: Float,
        minTravelPx: Float,
        isPortraitFullscreen: Boolean,
        onCommitEnterFullscreen: () -> Unit,
        onCommitExitFullscreen: () -> Unit
    ) {
        if (!isDraggingFullscreen) return
        val endFraction = activeDragFullscreen.coerceIn(0f, 1f)
        val travel = fullscreenDragTravelPx
        val startedFullscreen = fullscreenDragStartedInFullscreen
        isDraggingFullscreen = false

        val safeRange = dragRangePx.coerceAtLeast(180f)
        val rawNormVel = if (!startedFullscreen) {
            (-velocityY / safeRange).coerceIn(-10f, 10f)
        } else if (isPortraitFullscreen && travel < 0f) {
            (velocityY / safeRange).coerceIn(-10f, 10f)
        } else {
            (-velocityY / safeRange).coerceIn(-10f, 10f)
        }

        val shouldToggle = if (!startedFullscreen) {
            PlayerTransitionMath.shouldCompleteFullscreenEnter(
                dragTravelPx = travel,
                minTravelPx = minTravelPx,
                velocityY = velocityY
            )
        } else {
            PlayerTransitionMath.shouldCompleteFullscreenExit(
                dragTravelPx = travel,
                minTravelPx = minTravelPx,
                velocityY = velocityY,
                isPortraitFullscreen = isPortraitFullscreen
            )
        }

        scope.launch {
            fullscreenAnimatable.snapTo(endFraction)
            if (!startedFullscreen) {
                if (shouldToggle) {
                    onCommitEnterFullscreen()
                    fullscreenAnimatable.animateTo(
                        targetValue = 1f,
                        animationSpec = settleSpring,
                        initialVelocity = rawNormVel
                    )
                } else {
                    fullscreenAnimatable.animateTo(
                        targetValue = 0f,
                        animationSpec = returnSpring,
                        initialVelocity = rawNormVel
                    )
                }
            } else {
                if (shouldToggle) {
                    onCommitExitFullscreen()
                    fullscreenAnimatable.animateTo(
                        targetValue = 0f,
                        animationSpec = settleSpring,
                        initialVelocity = rawNormVel
                    )
                } else {
                    fullscreenAnimatable.animateTo(
                        targetValue = 1f,
                        animationSpec = returnSpring,
                        initialVelocity = rawNormVel
                    )
                }
            }
        }
    }

    fun cancelFullscreenDrag(currentlyFullscreen: Boolean) {
        if (!isDraggingFullscreen) return
        val endFraction = activeDragFullscreen.coerceIn(0f, 1f)
        isDraggingFullscreen = false
        val target = if (currentlyFullscreen) 1f else 0f
        scope.launch {
            fullscreenAnimatable.snapTo(endFraction)
            fullscreenAnimatable.animateTo(
                targetValue = target,
                animationSpec = returnSpring
            )
        }
    }
}

@Composable
fun rememberPlayerTransitionController(
    initialWindowState: PlayerWindowState
): PlayerTransitionController {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        PlayerTransitionController(
            scope = scope,
            initialCollapse = if (initialWindowState == PlayerWindowState.MINIPLAYER) 1f else 0f,
            initialFullscreen = if (initialWindowState == PlayerWindowState.FULLSCREEN) 1f else 0f
        )
    }
}
