package com.videhub

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerTransitionControllerTest {

    private class DeterministicFrameClock : MonotonicFrameClock {
        private var frameTimeNanos = 0L
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            yield()
            frameTimeNanos += 16_666_667L
            return onFrame(frameTimeNanos)
        }
    }

    private fun sampleBounds(): PlayerGeometryBounds = PlayerGeometryBounds(
        watchX = 0f,
        watchY = 96f,
        watchWidth = 1080f,
        watchHeight = 607.5f,
        miniX = 510f,
        miniY = 1780f,
        miniWidth = 540f,
        miniHeight = 303.75f,
        containerWidth = 1080f,
        containerHeight = 2400f,
        miniCornerDp = 12f
    )

    @Test
    fun `watch page to fullscreen morph expands bounds fades details and clears corner radius`() {
        val bounds = sampleBounds()
        val atWatch = PlayerTransitionMath.computeSurfaceFrame(
            collapseProgress = 0f,
            fullscreenProgress = 0f,
            bounds = bounds,
            detailsSlideOffsetPx = 56f,
            isSettledMiniplayer = false
        )
        assertEquals(bounds.watchX, atWatch.x, 0.01f)
        assertEquals(bounds.watchY, atWatch.y, 0.01f)
        assertEquals(bounds.watchWidth, atWatch.width, 0.01f)
        assertEquals(bounds.watchHeight, atWatch.height, 0.01f)
        assertEquals(0f, atWatch.cornerRadiusDp, 0.01f)
        assertEquals(1f, atWatch.detailsAlpha, 0.01f)
        assertEquals(1f, atWatch.backdropAlpha, 0.01f)

        val atMid = PlayerTransitionMath.computeSurfaceFrame(
            collapseProgress = 0f,
            fullscreenProgress = 0.35f,
            bounds = bounds,
            detailsSlideOffsetPx = 56f,
            isSettledMiniplayer = false
        )
        assertTrue(atMid.y in 0f..bounds.watchY)
        assertTrue(atMid.width in bounds.watchWidth..bounds.containerWidth)
        assertTrue(atMid.height in bounds.watchHeight..bounds.containerHeight)
        assertTrue(atMid.detailsAlpha > 0f && atMid.detailsAlpha < 1f)
        assertTrue(atMid.detailsTranslationYPx > 0f)

        val atFullscreen = PlayerTransitionMath.computeSurfaceFrame(
            collapseProgress = 0f,
            fullscreenProgress = 1f,
            bounds = bounds,
            detailsSlideOffsetPx = 56f,
            isSettledMiniplayer = false
        )
        assertEquals(0f, atFullscreen.x, 0.01f)
        assertEquals(0f, atFullscreen.y, 0.01f)
        assertEquals(bounds.containerWidth, atFullscreen.width, 0.01f)
        assertEquals(bounds.containerHeight, atFullscreen.height, 0.01f)
        assertEquals(0f, atFullscreen.cornerRadiusDp, 0.01f)
        assertEquals(0f, atFullscreen.elevationDp, 0.01f)
        assertEquals(0f, atFullscreen.detailsAlpha, 0.01f)
        assertEquals(0f, atFullscreen.backdropAlpha, 0.01f)
    }

    @Test
    fun `watch page to miniplayer morph tracks direct destination bounds rather than center scale`() {
        val bounds = sampleBounds()
        val atMid = PlayerTransitionMath.computeSurfaceFrame(
            collapseProgress = 0.5f,
            fullscreenProgress = 0f,
            bounds = bounds,
            detailsSlideOffsetPx = 56f,
            isSettledMiniplayer = false
        )
        // Must interpolate linearly toward miniX / miniY (never center-shrink)
        assertEquals((bounds.watchX + bounds.miniX) / 2f, atMid.x, 0.01f)
        assertEquals((bounds.watchY + bounds.miniY) / 2f, atMid.y, 0.01f)
        assertEquals((bounds.watchWidth + bounds.miniWidth) / 2f, atMid.width, 0.01f)
        assertEquals((bounds.watchHeight + bounds.miniHeight) / 2f, atMid.height, 0.01f)
        assertEquals(6f, atMid.cornerRadiusDp, 0.01f)
        assertEquals(9f, atMid.elevationDp, 0.01f)
        assertEquals(0f, atMid.detailsAlpha, 0.01f)
        assertEquals(0f, atMid.watchControlsAlpha, 0.01f)

        val atMini = PlayerTransitionMath.computeSurfaceFrame(
            collapseProgress = 1f,
            fullscreenProgress = 0f,
            bounds = bounds,
            detailsSlideOffsetPx = 56f,
            isSettledMiniplayer = true
        )
        assertEquals(bounds.miniX, atMini.x, 0.01f)
        assertEquals(bounds.miniY, atMini.y, 0.01f)
        assertEquals(bounds.miniWidth, atMini.width, 0.01f)
        assertEquals(bounds.miniHeight, atMini.height, 0.01f)
        assertEquals(bounds.miniCornerDp, atMini.cornerRadiusDp, 0.01f)
        assertEquals(18f, atMini.elevationDp, 0.01f)
        assertEquals(1f, atMini.miniChromeAlpha, 0.01f)
        assertEquals(0f, atMini.backdropAlpha, 0.01f)
    }

    @Test
    fun `settled miniplayer position and size updates track drag bounds 1 to 1`() {
        val initialBounds = sampleBounds()
        val draggedBounds = initialBounds.copy(
            miniX = 32f,
            miniY = 420f,
            miniWidth = 620f,
            miniHeight = 348.75f
        )

        val frameBefore = PlayerTransitionMath.computeSurfaceFrame(
            collapseProgress = 1f,
            fullscreenProgress = 0f,
            bounds = initialBounds,
            detailsSlideOffsetPx = 56f,
            isSettledMiniplayer = true
        )
        val frameAfter = PlayerTransitionMath.computeSurfaceFrame(
            collapseProgress = 1f,
            fullscreenProgress = 0f,
            bounds = draggedBounds,
            detailsSlideOffsetPx = 56f,
            isSettledMiniplayer = true
        )

        assertEquals(initialBounds.miniX, frameBefore.x, 0.01f)
        assertEquals(initialBounds.miniY, frameBefore.y, 0.01f)
        assertEquals(draggedBounds.miniX, frameAfter.x, 0.01f)
        assertEquals(draggedBounds.miniY, frameAfter.y, 0.01f)
        assertEquals(draggedBounds.miniWidth, frameAfter.width, 0.01f)
        assertEquals(draggedBounds.miniHeight, frameAfter.height, 0.01f)
    }

    @Test
    fun `drag below threshold and cancel settle cleanly back to watch page`() = runTest {
        val clock = DeterministicFrameClock()
        val scope = CoroutineScope(coroutineContext + clock + Job())
        val controller = PlayerTransitionController(scope = scope)

        var collapsedCommitted = false
        controller.startCollapseDrag()
        assertTrue(controller.isDraggingCollapse)

        // Small downward drag (200 / 1684 = ~0.118 < 0.30 threshold)
        controller.dragCollapseBy(deltaPx = 200f, collapseDistancePx = 1684f)
        assertEquals(200f / 1684f, controller.currentCollapseProgress, 0.001f)

        controller.finishCollapseDrag(
            velocityY = 100f,
            collapseDistancePx = 1684f,
            onCommitMiniplayer = { collapsedCommitted = true }
        )
        advanceUntilIdle()

        assertFalse(collapsedCommitted)
        assertFalse(controller.isDraggingCollapse)
        assertEquals(0f, controller.currentCollapseProgress, 0.005f)
    }

    @Test
    fun `fast downward fling completes collapse to miniplayer with velocity`() = runTest {
        val clock = DeterministicFrameClock()
        val scope = CoroutineScope(coroutineContext + clock + Job())
        val controller = PlayerTransitionController(scope = scope)

        var collapsedCommitted = false
        controller.startCollapseDrag()
        controller.dragCollapseBy(deltaPx = 240f, collapseDistancePx = 1684f)
        controller.finishCollapseDrag(
            velocityY = 900f,
            collapseDistancePx = 1684f,
            onCommitMiniplayer = { collapsedCommitted = true }
        )
        advanceUntilIdle()

        assertTrue(collapsedCommitted)
        assertEquals(1f, controller.currentCollapseProgress, 0.005f)
    }

    @Test
    fun `cancel collapse drag restores current state smoothly`() = runTest {
        val clock = DeterministicFrameClock()
        val scope = CoroutineScope(coroutineContext + clock + Job())
        val controller = PlayerTransitionController(scope = scope, initialCollapse = 0f)

        controller.startCollapseDrag()
        controller.dragCollapseBy(deltaPx = 700f, collapseDistancePx = 1684f)
        assertTrue(controller.currentCollapseProgress > 0.35f)

        controller.cancelCollapseDrag(isCurrentlyMiniplayer = false)
        advanceUntilIdle()
        assertEquals(0f, controller.currentCollapseProgress, 0.005f)
    }

    @Test
    fun `portrait fullscreen supports swipe up and swipe down interactive exit`() = runTest {
        val clock = DeterministicFrameClock()
        val scope = CoroutineScope(coroutineContext + clock + Job())
        val controller = PlayerTransitionController(scope = scope, initialFullscreen = 1f)

        var exitedFromSwipeUp = false
        controller.startFullscreenDrag(currentlyFullscreen = true)
        controller.dragFullscreenBy(deltaY = -140f, dragRangePx = 1500f, isPortraitFullscreen = true)
        assertTrue(controller.currentFullscreenProgress < 1f)

        controller.finishFullscreenDrag(
            velocityY = -750f,
            dragRangePx = 1500f,
            minTravelPx = 90f,
            isPortraitFullscreen = true,
            onCommitEnterFullscreen = {},
            onCommitExitFullscreen = { exitedFromSwipeUp = true }
        )
        advanceUntilIdle()

        assertTrue(exitedFromSwipeUp)
        assertEquals(0f, controller.currentFullscreenProgress, 0.005f)

        // Reset to fullscreen and test downward exit too
        scope.launch { controller.syncFullscreenTarget(true) }
        advanceUntilIdle()
        assertEquals(1f, controller.currentFullscreenProgress, 0.005f)

        var exitedFromSwipeDown = false
        controller.startFullscreenDrag(currentlyFullscreen = true)
        controller.dragFullscreenBy(deltaY = 180f, dragRangePx = 1500f, isPortraitFullscreen = false)
        assertTrue(controller.currentFullscreenProgress < 1f)

        controller.finishFullscreenDrag(
            velocityY = 400f,
            dragRangePx = 1500f,
            minTravelPx = 90f,
            isPortraitFullscreen = false,
            onCommitEnterFullscreen = {},
            onCommitExitFullscreen = { exitedFromSwipeDown = true }
        )
        advanceUntilIdle()

        assertTrue(exitedFromSwipeDown)
        assertEquals(0f, controller.currentFullscreenProgress, 0.005f)
    }

    @Test
    fun `mid flight interruption reverses collapse animation without jump`() = runTest {
        val clock = DeterministicFrameClock()
        val scope = CoroutineScope(coroutineContext + clock + Job())
        val controller = PlayerTransitionController(scope = scope, initialCollapse = 1f)

        // Start expanding Miniplayer -> Watch Page (target = 0f)
        val expandJob = scope.launch { controller.syncCollapseTarget(isMiniplayer = false) }
        // Advance just a couple of frames so animation is mid-flight
        repeat(4) { yield() }
        val midProgress = controller.currentCollapseProgress
        assertTrue("Expected mid-flight progress, was $midProgress", midProgress > 0.05f && midProgress < 0.98f)

        // Interrupt mid-flight with a fresh drag
        controller.startCollapseDrag()
        assertEquals(midProgress, controller.currentCollapseProgress, 0.05f)
        controller.cancelCollapseDrag(isCurrentlyMiniplayer = true)
        advanceUntilIdle()
        expandJob.cancel()

        assertEquals(1f, controller.currentCollapseProgress, 0.005f)
    }

    @Test
    fun `rapid state sync settles accurately on final target`() = runTest {
        val clock = DeterministicFrameClock()
        val scope = CoroutineScope(coroutineContext + clock + Job())
        val controller = PlayerTransitionController(scope = scope)

        val job1 = scope.launch { controller.syncCollapseTarget(isMiniplayer = true) }
        repeat(2) { yield() }
        job1.cancel()

        val job2 = scope.launch { controller.syncCollapseTarget(isMiniplayer = false) }
        advanceUntilIdle()
        job2.join()

        assertEquals(0f, controller.currentCollapseProgress, 0.005f)
    }

    @Test
    fun `landscape fullscreen downward exit drag shrinks player cleanly inside container`() {
        val landscapeBounds = PlayerGeometryBounds(
            watchX = 0f,
            watchY = 0f,
            watchWidth = 2400f,
            watchHeight = 1350f, // > containerHeight if not handled
            miniX = 1800f,
            miniY = 720f,
            miniWidth = 540f,
            miniHeight = 303.75f,
            containerWidth = 2400f,
            containerHeight = 1080f,
            miniCornerDp = 12f,
            isLandscapeContainer = true
        )

        val fullFrame = PlayerTransitionMath.computeSurfaceFrame(
            collapseProgress = 0f,
            fullscreenProgress = 1f,
            bounds = landscapeBounds,
            detailsSlideOffsetPx = 56f,
            isSettledMiniplayer = false
        )
        assertEquals(0f, fullFrame.x, 0.01f)
        assertEquals(0f, fullFrame.y, 0.01f)
        assertEquals(2400f, fullFrame.width, 0.01f)
        assertEquals(1080f, fullFrame.height, 0.01f)
        assertEquals(0f, fullFrame.cornerRadiusDp, 0.01f)

        val midExitFrame = PlayerTransitionMath.computeSurfaceFrame(
            collapseProgress = 0f,
            fullscreenProgress = 0.5f,
            bounds = landscapeBounds,
            detailsSlideOffsetPx = 56f,
            isSettledMiniplayer = false
        )
        assertTrue("Expected shrunk width < 2400, was ${midExitFrame.width}", midExitFrame.width < 2400f)
        assertTrue("Expected shrunk height < 1080, was ${midExitFrame.height}", midExitFrame.height < 1080f)
        assertTrue("Expected non-zero corner radius during shrink, was ${midExitFrame.cornerRadiusDp}", midExitFrame.cornerRadiusDp > 0f)
        assertTrue("Expected card inset horizontally, x=${midExitFrame.x}", midExitFrame.x > 0f)
        assertTrue("Expected card shifted downward cleanly, y=${midExitFrame.y}", midExitFrame.y > 0f)
        assertTrue(
            "Expected card bottom inside screen, bottom=${midExitFrame.y + midExitFrame.height}",
            midExitFrame.y + midExitFrame.height <= 1080f + 0.01f
        )
    }
}
