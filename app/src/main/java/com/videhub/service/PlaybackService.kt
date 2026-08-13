package com.videhub.service

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var playerListener: Player.Listener? = null

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VideoHub:PlaybackWakeLock")

        val session = MediaSessionManager.getOrCreateSession(this)
        val player = session.player

        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    if (wakeLock?.isHeld == false) {
                        try {
                            wakeLock?.acquire(24 * 60 * 60 * 1000L)
                        } catch (e: Exception) {
                            android.util.Log.e("PlaybackService", "Error acquiring WakeLock", e)
                        }
                    }
                } else {
                    if (wakeLock?.isHeld == true) {
                        try {
                            wakeLock?.release()
                        } catch (e: Exception) {
                            android.util.Log.e("PlaybackService", "Error releasing WakeLock", e)
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    val ctx = applicationContext
                    val tempLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VideoHub:ServiceEndedWakeLock")
                    try {
                        tempLock.acquire(30000)
                    } catch (e: Exception) {}

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val isAutoplayEnabled = com.videhub.data.SettingsManager.getAutoplay(ctx).firstOrNull() ?: true
                            val isLoopEnabled = com.videhub.data.SettingsManager.getLoopVideo(ctx).firstOrNull() ?: false
                            if (isLoopEnabled) {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    player.seekTo(0)
                                    player.play()
                                }
                            } else {
                                BackgroundAutoplayHandler.handleAutoplay(ctx, player, isAutoplayEnabled) {
                                    com.videhub.QueueManager.getNextVideo()
                                }
                            }
                        } finally {
                            if (tempLock.isHeld) tempLock.release()
                        }
                    }
                }
            }
        }

        player.addListener(playerListener!!)
        if (player.isPlaying && wakeLock?.isHeld == false) {
            try {
                wakeLock?.acquire(24 * 60 * 60 * 1000L)
            } catch (e: Exception) {}
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return MediaSessionManager.getOrCreateSession(this)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = MediaSessionManager.getOrCreateSession(this)
        val player = session.player

        if (!player.playWhenReady || player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
            val exoPlayer = player as? androidx.media3.exoplayer.ExoPlayer
            player.stop()
            player.clearMediaItems()
            exoPlayer?.setWakeMode(androidx.media3.common.C.WAKE_MODE_NONE)
            if (wakeLock?.isHeld == true) {
                try {
                    wakeLock?.release()
                } catch (e: Exception) {}
            }
            MediaSessionManager.release()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        val session = MediaSessionManager.getOrCreateSession(this)
        val player = session.player
        playerListener?.let { player.removeListener(it) }
        playerListener = null

        if (wakeLock?.isHeld == true) {
            try {
                wakeLock?.release()
            } catch (e: Exception) {}
        }
        wakeLock = null

        val exoPlayer = player as? androidx.media3.exoplayer.ExoPlayer
        player.stop()
        exoPlayer?.setWakeMode(androidx.media3.common.C.WAKE_MODE_NONE)
        MediaSessionManager.release()
        super.onDestroy()
    }
}

