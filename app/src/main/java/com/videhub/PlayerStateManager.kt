package com.videhub

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Top-level window/layout states for the persistent video player.
 * Kept strictly separate from Android system Picture-in-Picture (PiP) mode vs in-app Miniplayer.
 */
enum class PlayerWindowState {
    WATCH_PAGE,
    FULLSCREEN,
    MINIPLAYER,
    PICTURE_IN_PICTURE,
    CLOSED
}

data class ActivePlayerMedia(
    val videoUrl: String,
    val title: String,
    val thumbnailUrl: String,
    val channelName: String = "",
    val isMusicMode: Boolean = false,
    val isRestoredFromSavedState: Boolean = false
)

/**
 * Pure, testable state machine managing valid player state transitions and preventing
 * double-transitions or invalid jumps (e.g. FULLSCREEN directly to MINIPLAYER).
 */
class PlayerStateMachine(
    private val minGestureTransitionIntervalMs: Long = 160L
) {
    private val _currentState = MutableStateFlow(PlayerWindowState.CLOSED)
    val currentState: StateFlow<PlayerWindowState> = _currentState.asStateFlow()

    private val _stateBeforePip = MutableStateFlow(PlayerWindowState.WATCH_PAGE)
    val stateBeforePip: StateFlow<PlayerWindowState> = _stateBeforePip.asStateFlow()

    private val _activeMedia = MutableStateFlow<ActivePlayerMedia?>(null)
    val activeMedia: StateFlow<ActivePlayerMedia?> = _activeMedia.asStateFlow()

    private var lastGestureTransitionMs: Long = 0L

    fun canTransition(from: PlayerWindowState, to: PlayerWindowState): Boolean {
        if (from == to) return true
        return when (from) {
            PlayerWindowState.CLOSED -> {
                to == PlayerWindowState.WATCH_PAGE ||
                    to == PlayerWindowState.FULLSCREEN ||
                    to == PlayerWindowState.MINIPLAYER
            }
            PlayerWindowState.WATCH_PAGE -> {
                to == PlayerWindowState.FULLSCREEN ||
                    to == PlayerWindowState.MINIPLAYER ||
                    to == PlayerWindowState.PICTURE_IN_PICTURE ||
                    to == PlayerWindowState.CLOSED
            }
            PlayerWindowState.FULLSCREEN -> {
                // Fullscreen must step down to WATCH_PAGE before collapsing to MINIPLAYER.
                to == PlayerWindowState.WATCH_PAGE ||
                    to == PlayerWindowState.PICTURE_IN_PICTURE ||
                    to == PlayerWindowState.CLOSED
            }
            PlayerWindowState.MINIPLAYER -> {
                // Miniplayer must expand to WATCH_PAGE before entering FULLSCREEN.
                to == PlayerWindowState.WATCH_PAGE ||
                    to == PlayerWindowState.PICTURE_IN_PICTURE ||
                    to == PlayerWindowState.CLOSED
            }
            PlayerWindowState.PICTURE_IN_PICTURE -> {
                to == PlayerWindowState.WATCH_PAGE ||
                    to == PlayerWindowState.FULLSCREEN ||
                    to == PlayerWindowState.MINIPLAYER ||
                    to == PlayerWindowState.CLOSED
            }
        }
    }

    private fun canTriggerGestureTransition(uptimeMs: Long): Boolean {
        if (uptimeMs <= 0L) return true
        if (uptimeMs - lastGestureTransitionMs < minGestureTransitionIntervalMs) {
            return false
        }
        return true
    }

    fun openVideo(
        url: String,
        title: String,
        thumbnailUrl: String,
        channelName: String = "",
        isMusicMode: Boolean = false,
        isFullscreen: Boolean = false
    ) {
        if (url.isBlank()) return
        val targetState = if (isFullscreen && !isMusicMode) {
            PlayerWindowState.FULLSCREEN
        } else {
            PlayerWindowState.WATCH_PAGE
        }
        _activeMedia.value = ActivePlayerMedia(
            videoUrl = url,
            title = title,
            thumbnailUrl = thumbnailUrl,
            channelName = channelName,
            isMusicMode = isMusicMode,
            isRestoredFromSavedState = false
        )
        _stateBeforePip.value = targetState
        _currentState.value = targetState
    }

    fun restoreToMiniplayer(
        url: String,
        title: String,
        thumbnailUrl: String,
        channelName: String,
        isMusicMode: Boolean = false
    ): Boolean {
        if (url.isBlank()) return false
        val from = _currentState.value
        if (!canTransition(from, PlayerWindowState.MINIPLAYER)) return false
        _activeMedia.value = ActivePlayerMedia(
            videoUrl = url,
            title = title,
            thumbnailUrl = thumbnailUrl,
            channelName = channelName,
            isMusicMode = isMusicMode,
            isRestoredFromSavedState = true
        )
        _stateBeforePip.value = PlayerWindowState.MINIPLAYER
        _currentState.value = PlayerWindowState.MINIPLAYER
        return true
    }

    fun enterFullscreen(uptimeMs: Long = 0L, isGesture: Boolean = false): Boolean {
        val from = _currentState.value
        if (_activeMedia.value == null || _activeMedia.value?.isMusicMode == true) return false
        if (from == PlayerWindowState.FULLSCREEN) return true
        if (!canTransition(from, PlayerWindowState.FULLSCREEN)) return false
        if (from == PlayerWindowState.CLOSED) return false
        if (isGesture && !canTriggerGestureTransition(uptimeMs)) return false
        if (isGesture && uptimeMs > 0L) lastGestureTransitionMs = uptimeMs
        _currentState.value = PlayerWindowState.FULLSCREEN
        _stateBeforePip.value = PlayerWindowState.FULLSCREEN
        return true
    }

    fun exitFullscreen(uptimeMs: Long = 0L, isGesture: Boolean = false): Boolean {
        val from = _currentState.value
        if (from == PlayerWindowState.WATCH_PAGE) return true
        if (from != PlayerWindowState.FULLSCREEN) return false
        if (!canTransition(from, PlayerWindowState.WATCH_PAGE)) return false
        if (isGesture && !canTriggerGestureTransition(uptimeMs)) return false
        if (isGesture && uptimeMs > 0L) lastGestureTransitionMs = uptimeMs
        _currentState.value = PlayerWindowState.WATCH_PAGE
        _stateBeforePip.value = PlayerWindowState.WATCH_PAGE
        return true
    }

    fun collapseToMiniplayer(uptimeMs: Long = 0L, isGesture: Boolean = false): Boolean {
        val from = _currentState.value
        if (from == PlayerWindowState.MINIPLAYER) return true
        if (from != PlayerWindowState.WATCH_PAGE) return false
        if (!canTransition(from, PlayerWindowState.MINIPLAYER)) return false
        if (isGesture && !canTriggerGestureTransition(uptimeMs)) return false
        if (isGesture && uptimeMs > 0L) lastGestureTransitionMs = uptimeMs
        _currentState.value = PlayerWindowState.MINIPLAYER
        _stateBeforePip.value = PlayerWindowState.MINIPLAYER
        return true
    }

    fun expandFromMiniplayer(uptimeMs: Long = 0L, isGesture: Boolean = false): Boolean {
        val from = _currentState.value
        if (from == PlayerWindowState.WATCH_PAGE) return true
        if (from != PlayerWindowState.MINIPLAYER) return false
        if (!canTransition(from, PlayerWindowState.WATCH_PAGE)) return false
        if (isGesture && !canTriggerGestureTransition(uptimeMs)) return false
        if (isGesture && uptimeMs > 0L) lastGestureTransitionMs = uptimeMs
        _activeMedia.value = _activeMedia.value?.copy(isRestoredFromSavedState = false)
        _currentState.value = PlayerWindowState.WATCH_PAGE
        _stateBeforePip.value = PlayerWindowState.WATCH_PAGE
        return true
    }

    fun enterPictureInPicture(): Boolean {
        val from = _currentState.value
        if (from == PlayerWindowState.PICTURE_IN_PICTURE) return true
        if (!canTransition(from, PlayerWindowState.PICTURE_IN_PICTURE)) return false
        _stateBeforePip.value = from
        _currentState.value = PlayerWindowState.PICTURE_IN_PICTURE
        return true
    }

    fun exitPictureInPicture(): Boolean {
        if (_currentState.value != PlayerWindowState.PICTURE_IN_PICTURE) return false
        val restoreTarget = _stateBeforePip.value.let {
            if (it == PlayerWindowState.CLOSED || it == PlayerWindowState.PICTURE_IN_PICTURE) {
                PlayerWindowState.WATCH_PAGE
            } else {
                it
            }
        }
        if (!canTransition(PlayerWindowState.PICTURE_IN_PICTURE, restoreTarget)) return false
        _currentState.value = restoreTarget
        return true
    }

    fun updateMusicMode(isMusicMode: Boolean) {
        _activeMedia.value = _activeMedia.value?.copy(isMusicMode = isMusicMode)
        if (isMusicMode && _currentState.value == PlayerWindowState.FULLSCREEN) {
            _currentState.value = PlayerWindowState.WATCH_PAGE
            _stateBeforePip.value = PlayerWindowState.WATCH_PAGE
        }
    }

    fun updateMetadata(title: String? = null, channelName: String? = null, thumbnailUrl: String? = null) {
        val current = _activeMedia.value ?: return
        _activeMedia.value = current.copy(
            title = title?.takeIf { it.isNotBlank() } ?: current.title,
            channelName = channelName?.takeIf { it.isNotBlank() } ?: current.channelName,
            thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() && it != "none" } ?: current.thumbnailUrl,
            isRestoredFromSavedState = false
        )
    }

    fun updateMediaInCurrentState(
        url: String,
        title: String,
        thumbnailUrl: String,
        channelName: String = "",
        isMusicMode: Boolean = _activeMedia.value?.isMusicMode ?: false
    ) {
        if (url.isBlank()) return
        if (_currentState.value == PlayerWindowState.CLOSED) {
            openVideo(url, title, thumbnailUrl, channelName, isMusicMode, isFullscreen = false)
            return
        }
        _activeMedia.value = ActivePlayerMedia(
            videoUrl = url,
            title = title,
            thumbnailUrl = thumbnailUrl,
            channelName = channelName,
            isMusicMode = isMusicMode,
            isRestoredFromSavedState = false
        )
    }

    fun close() {
        _currentState.value = PlayerWindowState.CLOSED
        _stateBeforePip.value = PlayerWindowState.WATCH_PAGE
        _activeMedia.value = null
        lastGestureTransitionMs = 0L
    }
}

/**
 * Application-wide coordinator syncing [PlayerStateMachine] and legacy [MiniPlayerState] observers.
 */
object PlayerStateManager {
    private val machine = PlayerStateMachine()

    val currentState: StateFlow<PlayerWindowState> = machine.currentState
    val stateBeforePip: StateFlow<PlayerWindowState> = machine.stateBeforePip
    val activeMedia: StateFlow<ActivePlayerMedia?> = machine.activeMedia

    fun canTransition(from: PlayerWindowState, to: PlayerWindowState): Boolean =
        machine.canTransition(from, to)

    fun openVideo(
        url: String,
        title: String,
        thumbnailUrl: String,
        channelName: String = "",
        isMusicMode: Boolean = false,
        isFullscreen: Boolean = false
    ) {
        machine.openVideo(url, title, thumbnailUrl, channelName, isMusicMode, isFullscreen)
        MiniPlayerState.markPlaybackStarted()
        MiniPlayerState.update(
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            isMusicMode = isMusicMode,
            url = url
        )
        MiniPlayerState.hide()
    }

    fun updateMediaInCurrentState(
        url: String,
        title: String,
        thumbnailUrl: String,
        channelName: String = "",
        isMusicMode: Boolean = machine.activeMedia.value?.isMusicMode ?: false
    ) {
        machine.updateMediaInCurrentState(url, title, thumbnailUrl, channelName, isMusicMode)
        MiniPlayerState.markPlaybackStarted()
        MiniPlayerState.update(
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            isMusicMode = isMusicMode,
            url = url
        )
        syncLegacyMiniPlayerVisibility()
    }

    fun restoreToMiniplayer(
        url: String,
        title: String,
        thumbnailUrl: String,
        channelName: String,
        position: Long,
        duration: Long,
        musicMode: Boolean = false
    ): Boolean {
        val transitioned = machine.restoreToMiniplayer(url, title, thumbnailUrl, channelName, musicMode)
        if (transitioned) {
            MiniPlayerState.restore(
                url = url,
                title = title,
                thumb = thumbnailUrl,
                channel = channelName,
                position = position,
                duration = duration,
                musicMode = musicMode
            )
        }
        return transitioned
    }

    fun enterFullscreen(uptimeMs: Long = android.os.SystemClock.uptimeMillis(), isGesture: Boolean = false): Boolean =
        machine.enterFullscreen(uptimeMs, isGesture)

    fun exitFullscreen(uptimeMs: Long = android.os.SystemClock.uptimeMillis(), isGesture: Boolean = false): Boolean =
        machine.exitFullscreen(uptimeMs, isGesture)

    fun collapseToMiniplayer(uptimeMs: Long = android.os.SystemClock.uptimeMillis(), isGesture: Boolean = false): Boolean {
        val ok = machine.collapseToMiniplayer(uptimeMs, isGesture)
        if (ok) {
            syncLegacyMiniPlayerVisibility()
        }
        return ok
    }

    fun expandFromMiniplayer(uptimeMs: Long = android.os.SystemClock.uptimeMillis(), isGesture: Boolean = false): Boolean {
        val ok = machine.expandFromMiniplayer(uptimeMs, isGesture)
        if (ok) {
            MiniPlayerState.markPlaybackStarted()
            MiniPlayerState.hide()
        }
        return ok
    }

    fun enterPictureInPicture(): Boolean {
        val ok = machine.enterPictureInPicture()
        if (ok) {
            MiniPlayerState.hide()
        }
        return ok
    }

    fun exitPictureInPicture(): Boolean {
        val ok = machine.exitPictureInPicture()
        if (ok) {
            syncLegacyMiniPlayerVisibility()
        }
        return ok
    }

    fun updateMusicMode(isMusicMode: Boolean) {
        machine.updateMusicMode(isMusicMode)
        val media = machine.activeMedia.value ?: return
        MiniPlayerState.update(
            title = media.title,
            channelName = media.channelName,
            thumbnailUrl = media.thumbnailUrl,
            isMusicMode = isMusicMode,
            url = media.videoUrl
        )
    }

    fun updateMetadata(title: String? = null, channelName: String? = null, thumbnailUrl: String? = null) {
        machine.updateMetadata(title, channelName, thumbnailUrl)
        val media = machine.activeMedia.value ?: return
        MiniPlayerState.update(
            title = media.title,
            channelName = media.channelName,
            thumbnailUrl = media.thumbnailUrl,
            isMusicMode = media.isMusicMode,
            url = media.videoUrl
        )
    }

    fun syncLegacyMiniPlayerVisibility() {
        val state = machine.currentState.value
        val media = machine.activeMedia.value
        if (state == PlayerWindowState.MINIPLAYER && media != null) {
            if (!media.isRestoredFromSavedState) {
                MiniPlayerState.show(
                    url = media.videoUrl,
                    title = media.title,
                    thumb = media.thumbnailUrl,
                    channel = media.channelName,
                    musicMode = media.isMusicMode
                )
            }
        } else {
            MiniPlayerState.hide()
        }
    }

    fun markPlaybackStarted() {
        val current = machine.activeMedia.value
        if (current != null && current.isRestoredFromSavedState) {
            machine.updateMetadata()
        }
        MiniPlayerState.markPlaybackStarted()
    }

    data class MiniPlayerWidthBounds(
        val baseWidthDp: Float,
        val minWidthDp: Float,
        val maxWidthDp: Float,
        val minScale: Float,
        val maxScale: Float
    )

    fun computeMiniPlayerWidthBounds(screenWidthDp: Float): MiniPlayerWidthBounds {
        val safeScreenWidthDp = screenWidthDp.coerceIn(120f, 2000f)
        val availableWidthDp = (safeScreenWidthDp - 20f).coerceAtLeast(96f)

        val minBaseCap = minOf(196f, availableWidthDp)
        val maxBaseCap = maxOf(minBaseCap, minOf(236f, availableWidthDp))
        val baseWidthDp = (safeScreenWidthDp * 0.56f)
            .coerceIn(minBaseCap, maxBaseCap)
            .coerceAtLeast(1f)

        val minLower = minOf(156f, baseWidthDp)
        val minUpper = maxOf(minLower, minOf(176f, baseWidthDp))
        val minWidthDp = (safeScreenWidthDp * 0.43f).coerceIn(minLower, minUpper)

        val maxUpper = maxOf(baseWidthDp, availableWidthDp)
        val maxLower = minOf(288f, maxUpper)
        val maxWidthDp = (safeScreenWidthDp * 0.88f).coerceIn(maxLower, maxUpper)

        val minScale = (minWidthDp / baseWidthDp).coerceIn(0.68f, 1.0f)
        val maxScaleUpper = maxOf(minScale, 1.75f)
        val maxScaleLower = minOf(maxScaleUpper, maxOf(minScale, 1.0f))
        val maxScale = (maxWidthDp / basePipSafeDivisor(baseWidthDp))
            .coerceIn(maxScaleLower, maxScaleUpper)

        return MiniPlayerWidthBounds(
            baseWidthDp = baseWidthDp,
            minWidthDp = minWidthDp,
            maxWidthDp = maxWidthDp,
            minScale = minScale,
            maxScale = maxScale
        )
    }

    private fun basePipSafeDivisor(value: Float): Float = value.coerceAtLeast(1f)

    fun shouldCollapseOnDragEnd(collapseFraction: Float, velocityY: Float): Boolean {
        return velocityY >= 650f || (collapseFraction >= 0.30f && velocityY > -350f)
    }

    fun close() {
        machine.close()
        MiniPlayerState.hide()
    }
}
