package com.videhub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.videhub.service.MediaSessionManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerGestureAndStateRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PlayerStateManager.close()
    }

    @After
    fun tearDown() {
        PlayerStateManager.close()
    }

    @Test
    fun `watch page to fullscreen and back preserves active media`() {
        val machine = PlayerStateMachine()
        machine.openVideo(
            url = "https://www.youtube.com/watch?v=test123",
            title = "Sample Video",
            thumbnailUrl = "https://img.youtube.com/vi/test123/hqdefault.jpg",
            channelName = "Test Channel"
        )
        assertEquals(PlayerWindowState.WATCH_PAGE, machine.currentState.value)

        assertTrue(machine.enterFullscreen(uptimeMs = 1000L, isGesture = true))
        assertEquals(PlayerWindowState.FULLSCREEN, machine.currentState.value)

        assertTrue(machine.exitFullscreen(uptimeMs = 1300L, isGesture = true))
        assertEquals(PlayerWindowState.WATCH_PAGE, machine.currentState.value)
        assertEquals("https://www.youtube.com/watch?v=test123", machine.activeMedia.value?.videoUrl)
    }

    @Test
    fun `invalid direct jumps between fullscreen and miniplayer are rejected`() {
        val machine = PlayerStateMachine()

        // Cannot enter fullscreen or collapse when CLOSED
        assertFalse(machine.enterFullscreen(uptimeMs = 100L, isGesture = true))
        assertFalse(machine.collapseToMiniplayer(uptimeMs = 200L, isGesture = true))

        machine.openVideo(
            url = "https://www.youtube.com/watch?v=abc",
            title = "Video",
            thumbnailUrl = "thumb"
        )
        assertTrue(machine.enterFullscreen(uptimeMs = 500L, isGesture = true))

        // FULLSCREEN -> MINIPLAYER directly is invalid (must step down to WATCH_PAGE first)
        assertFalse(machine.canTransition(PlayerWindowState.FULLSCREEN, PlayerWindowState.MINIPLAYER))
        assertFalse(machine.collapseToMiniplayer(uptimeMs = 800L, isGesture = true))
        assertEquals(PlayerWindowState.FULLSCREEN, machine.currentState.value)

        // Step down to WATCH_PAGE, then collapse to MINIPLAYER
        assertTrue(machine.exitFullscreen(uptimeMs = 800L, isGesture = true))
        assertTrue(machine.collapseToMiniplayer(uptimeMs = 1100L, isGesture = true))
        assertEquals(PlayerWindowState.MINIPLAYER, machine.currentState.value)

        // MINIPLAYER -> FULLSCREEN directly is invalid (must expand to WATCH_PAGE first)
        assertFalse(machine.canTransition(PlayerWindowState.MINIPLAYER, PlayerWindowState.FULLSCREEN))
        assertFalse(machine.enterFullscreen(uptimeMs = 1400L, isGesture = true))
        assertEquals(PlayerWindowState.MINIPLAYER, machine.currentState.value)
    }

    @Test
    fun `gesture cooldown prevents single swipe from triggering multiple transitions`() {
        val machine = PlayerStateMachine(minGestureTransitionIntervalMs = 160L)
        machine.openVideo("https://www.youtube.com/watch?v=cd1", "Cooldown Video", "thumb")

        // First swipe enters fullscreen at t = 1000ms
        assertTrue(machine.enterFullscreen(uptimeMs = 1000L, isGesture = true))
        assertEquals(PlayerWindowState.FULLSCREEN, machine.currentState.value)

        // Second transition inside 160ms cooldown window (t = 1050ms) is ignored
        assertFalse(machine.exitFullscreen(uptimeMs = 1050L, isGesture = true))
        assertEquals(PlayerWindowState.FULLSCREEN, machine.currentState.value)

        // Transition after cooldown window (t = 1200ms) succeeds
        assertTrue(machine.exitFullscreen(uptimeMs = 1200L, isGesture = true))
        assertEquals(PlayerWindowState.WATCH_PAGE, machine.currentState.value)
    }

    @Test
    fun `music mode disables fullscreen transition and exits fullscreen cleanly`() {
        val machine = PlayerStateMachine()
        machine.openVideo("https://www.youtube.com/watch?v=song1", "Song", "thumb", isMusicMode = true)

        assertFalse(machine.enterFullscreen(uptimeMs = 1000L, isGesture = false))
        assertEquals(PlayerWindowState.WATCH_PAGE, machine.currentState.value)

        // Switching to music mode while in fullscreen exits to watch page
        machine.openVideo("https://www.youtube.com/watch?v=vid2", "Vid", "thumb", isMusicMode = false, isFullscreen = true)
        assertEquals(PlayerWindowState.FULLSCREEN, machine.currentState.value)
        machine.updateMusicMode(true)
        assertEquals(PlayerWindowState.WATCH_PAGE, machine.currentState.value)
    }

    @Test
    fun `system picture in picture stays separate from miniplayer and restores prior state`() {
        val machine = PlayerStateMachine()
        machine.openVideo("https://www.youtube.com/watch?v=pip1", "PiP Video", "thumb")
        assertTrue(machine.enterFullscreen())

        // Enter PiP from FULLSCREEN
        assertTrue(machine.enterPictureInPicture())
        assertEquals(PlayerWindowState.PICTURE_IN_PICTURE, machine.currentState.value)

        // Exiting PiP restores FULLSCREEN (not MINIPLAYER)
        assertTrue(machine.exitPictureInPicture())
        assertEquals(PlayerWindowState.FULLSCREEN, machine.currentState.value)

        // Step back to WATCH_PAGE, then MINIPLAYER, then PiP -> exiting PiP restores MINIPLAYER
        assertTrue(machine.exitFullscreen())
        assertTrue(machine.collapseToMiniplayer())
        assertTrue(machine.enterPictureInPicture())
        assertTrue(machine.exitPictureInPicture())
        assertEquals(PlayerWindowState.MINIPLAYER, machine.currentState.value)
    }

    @Test
    fun `auto advance preserves current player window state`() {
        val machine = PlayerStateMachine()
        machine.openVideo("https://www.youtube.com/watch?v=first", "First", "thumb1")
        assertTrue(machine.collapseToMiniplayer())
        assertEquals(PlayerWindowState.MINIPLAYER, machine.currentState.value)

        machine.updateMediaInCurrentState(
            url = "https://www.youtube.com/watch?v=second",
            title = "Second",
            thumbnailUrl = "thumb2",
            channelName = "Channel 2"
        )
        assertEquals(PlayerWindowState.MINIPLAYER, machine.currentState.value)
        assertEquals("https://www.youtube.com/watch?v=second", machine.activeMedia.value?.videoUrl)
    }

    @Test
    fun `collapse threshold and fling velocity determine watch page vs miniplayer settlement`() {
        // Accidental small drag -> stay on Watch Page
        assertFalse(PlayerStateManager.shouldCollapseOnDragEnd(collapseFraction = 0.12f, velocityY = 120f))

        // Drag past 35% without upward fling -> collapse to Miniplayer
        assertTrue(PlayerStateManager.shouldCollapseOnDragEnd(collapseFraction = 0.40f, velocityY = 50f))

        // Crisp downward fling -> collapse to Miniplayer even under 35%
        assertTrue(PlayerStateManager.shouldCollapseOnDragEnd(collapseFraction = 0.18f, velocityY = 950f))

        // Strong upward pull-back before release -> cancel collapse back to Watch Page
        assertFalse(PlayerStateManager.shouldCollapseOnDragEnd(collapseFraction = 0.55f, velocityY = -650f))
    }

    @Test
    fun `close clears active media and sets CLOSED state`() {
        PlayerStateManager.openVideo("https://www.youtube.com/watch?v=cls", "Close Test", "thumb")
        PlayerStateManager.collapseToMiniplayer()
        assertEquals(PlayerWindowState.MINIPLAYER, PlayerStateManager.currentState.value)

        PlayerStateManager.close()
        assertEquals(PlayerWindowState.CLOSED, PlayerStateManager.currentState.value)
        assertNull(PlayerStateManager.activeMedia.value)
    }

    @Test
    fun `single persistent ExoPlayer instance is reused across window state transitions`() {
        val playerInitial = MediaSessionManager.getOrCreatePlayer(context)

        PlayerStateManager.openVideo("https://www.youtube.com/watch?v=cont1", "Continuity", "thumb")
        val playerWatch = MediaSessionManager.getOrCreatePlayer(context)

        PlayerStateManager.enterFullscreen()
        val playerFullscreen = MediaSessionManager.getOrCreatePlayer(context)

        PlayerStateManager.exitFullscreen()
        val playerBackToWatch = MediaSessionManager.getOrCreatePlayer(context)

        PlayerStateManager.collapseToMiniplayer()
        val playerMini = MediaSessionManager.getOrCreatePlayer(context)

        PlayerStateManager.expandFromMiniplayer()
        val playerReExpanded = MediaSessionManager.getOrCreatePlayer(context)

        assertSame(playerInitial, playerWatch)
        assertSame(playerWatch, playerFullscreen)
        assertSame(playerFullscreen, playerBackToWatch)
        assertSame(playerBackToWatch, playerMini)
        assertSame(playerMini, playerReExpanded)
    }
}
