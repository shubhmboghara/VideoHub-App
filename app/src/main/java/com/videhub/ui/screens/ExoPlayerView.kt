package com.videhub.ui.screens

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.PlayerView

import androidx.compose.runtime.rememberUpdatedState

@Composable
fun ExoPlayerView(
    modifier: Modifier = Modifier,
    mediaPlayer: Player?,
    isFullscreen: Boolean,
    isPipActive: Boolean = false,
    isMusicMode: Boolean,
    isScreenLocked: Boolean = false,
    showCaptions: Boolean,
    onToggleFullscreen: () -> Unit,
    onControllerVisibilityChanged: (Boolean) -> Unit,
    onCaptionsRequested: () -> Unit,
    onActiveCaptionsChanged: (List<String>) -> Unit
) {
    val currentShowCaptions = rememberUpdatedState(showCaptions)
    val currentOnActiveCaptionsChanged = rememberUpdatedState(onActiveCaptionsChanged)
    val currentOnCaptionsRequested = rememberUpdatedState(onCaptionsRequested)
    val currentOnControllerVisibilityChanged = rememberUpdatedState(onControllerVisibilityChanged)
    val currentOnToggleFullscreen = rememberUpdatedState(onToggleFullscreen)


    androidx.compose.runtime.LaunchedEffect(mediaPlayer) {
        mediaPlayer?.let { player ->
            val params = player.trackSelectionParameters
            if (params.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_TEXT)) {
                player.trackSelectionParameters = params.buildUpon()
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                    .build()
            }
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val darkCtx = android.view.ContextThemeWrapper(ctx, android.R.style.Theme_Material_NoActionBar)
            PlayerView(darkCtx).apply {
                useController = !com.videhub.PipState.isActive.value
                player = mediaPlayer
                setShowSubtitleButton(false)
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                
                
                
                

                findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.let { settingsBtn ->
                    settingsBtn.visibility = View.GONE
                    val lp = settingsBtn.layoutParams
                    lp.width = 0
                    lp.height = 0
                    settingsBtn.layoutParams = lp
                }

                findViewById<View>(androidx.media3.ui.R.id.exo_prev)?.let { prevBtn ->
                    prevBtn.visibility = View.VISIBLE
                    val lp = prevBtn.layoutParams
                    lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    prevBtn.layoutParams = lp
                }
                findViewById<View>(androidx.media3.ui.R.id.exo_next)?.let { nextBtn ->
                    nextBtn.visibility = View.VISIBLE
                    val lp = nextBtn.layoutParams
                    lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    nextBtn.layoutParams = lp
                }

                findViewById<View>(androidx.media3.ui.R.id.exo_ffwd)?.let { ffwdBtn ->
                    ffwdBtn.visibility = View.VISIBLE
                    val lp = ffwdBtn.layoutParams
                    lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    ffwdBtn.layoutParams = lp
                }
                findViewById<View>(androidx.media3.ui.R.id.exo_rew)?.let { rewBtn ->
                    rewBtn.visibility = View.VISIBLE
                    val lp = rewBtn.layoutParams
                    lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    rewBtn.layoutParams = lp
                }

                setFullscreenButtonClickListener { isFullscreenNow ->
                    currentOnToggleFullscreen.value()
                }

                subtitleView?.visibility = View.INVISIBLE
                setShowSubtitleButton(false)
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                
                
                
                


                val origCc = findViewById<ImageView>(androidx.media3.ui.R.id.exo_subtitle)
                origCc?.let { cc ->
                    val parent = cc.parent as? ViewGroup
                    if (parent != null) {
                        cc.visibility = View.GONE
                        val customCc = ImageView(ctx).apply {
                            val oldLp = cc.layoutParams
                            if (oldLp is LinearLayout.LayoutParams) {
                                layoutParams = LinearLayout.LayoutParams(oldLp).apply {
                                    marginEnd = (8 * ctx.resources.displayMetrics.density).toInt()
                                }
                            } else if (oldLp is FrameLayout.LayoutParams) {
                                layoutParams = FrameLayout.LayoutParams(oldLp).apply {
                                    marginEnd = (8 * ctx.resources.displayMetrics.density).toInt()
                                }
                            } else if (oldLp is ViewGroup.MarginLayoutParams) {
                                layoutParams = ViewGroup.MarginLayoutParams(oldLp).apply {
                                    marginEnd = (8 * ctx.resources.displayMetrics.density).toInt()
                                }
                            } else {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                            }

                            setPadding(cc.paddingLeft, cc.paddingTop, cc.paddingRight, cc.paddingBottom)
                            background = cc.background
                            contentDescription = "Toggle Captions"
                            tag = "custom_cc_btn"

                            minimumWidth = (48 * ctx.resources.displayMetrics.density).toInt()
                            minimumHeight = (48 * ctx.resources.displayMetrics.density).toInt()
                        }
                        val index = parent.indexOfChild(cc)
                        parent.addView(customCc, index)

                        val ccLp = cc.layoutParams
                        if (ccLp != null) {
                            ccLp.width = 0
                            ccLp.height = 0
                            cc.layoutParams = ccLp
                        }
                    }
                }

                val settingsButton = findViewById<View>(androidx.media3.ui.R.id.exo_settings)
                settingsButton?.let {
                    val params = it.layoutParams as? ViewGroup.MarginLayoutParams
                    if (params != null) {
                        params.marginEnd = (8 * ctx.resources.displayMetrics.density).toInt()
                        it.layoutParams = params
                    }
                    it.minimumWidth = (48 * ctx.resources.displayMetrics.density).toInt()
                    it.minimumHeight = (48 * ctx.resources.displayMetrics.density).toInt()
                }

                val fullscreenButton = findViewById<View>(androidx.media3.ui.R.id.exo_fullscreen)
                fullscreenButton?.let {
                    it.minimumWidth = (48 * ctx.resources.displayMetrics.density).toInt()
                    it.minimumHeight = (48 * ctx.resources.displayMetrics.density).toInt()
                }

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                    currentOnControllerVisibilityChanged.value(visibility == View.VISIBLE)
                })
            }
        },
        update = { view ->
            val oldPlayer = view.player
            if (oldPlayer != mediaPlayer) {
                val listener = view.getTag(com.videhub.R.id.custom_cue_listener) as? Player.Listener
                if (listener != null) {
                    oldPlayer?.removeListener(listener)
                    mediaPlayer?.addListener(listener)
                }
                view.player = mediaPlayer
            }
            view.useController = !isPipActive && !isMusicMode && !isScreenLocked
            if (isPipActive) {
                view.useController = false
                view.controllerAutoShow = false
                view.hideController()
            }
            view.subtitleView?.visibility = View.INVISIBLE
            view.setShowSubtitleButton(false)
                view.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            
            val lastIsFullscreen = view.getTag(com.videhub.R.id.is_fullscreen_tag) as? Boolean
            if (lastIsFullscreen != isFullscreen) {
                val fsIconRes = if (isFullscreen) {
                    androidx.media3.ui.R.drawable.exo_ic_fullscreen_exit
                } else {
                    androidx.media3.ui.R.drawable.exo_ic_fullscreen_enter
                }
                val btn = view.findViewById<ImageView>(androidx.media3.ui.R.id.exo_fullscreen)
                btn?.setImageDrawable(ContextCompat.getDrawable(view.context, fsIconRes))
                view.setTag(com.videhub.R.id.is_fullscreen_tag, isFullscreen)
            }
            
            val lastShowCaptions = view.getTag(com.videhub.R.id.show_captions_tag) as? Boolean
            if (lastShowCaptions != showCaptions) {
                val parent = view.findViewById<ImageView>(androidx.media3.ui.R.id.exo_subtitle)?.parent as? ViewGroup
                val customCc = parent?.findViewWithTag<ImageView>("custom_cc_btn")
                if (customCc != null) {
                    val iconRes = if (showCaptions) androidx.media3.ui.R.drawable.exo_styled_controls_subtitle_on else androidx.media3.ui.R.drawable.exo_styled_controls_subtitle_off
                    customCc.setImageDrawable(ContextCompat.getDrawable(view.context, iconRes))
                }
                view.setTag(com.videhub.R.id.show_captions_tag, showCaptions)
            }
            
            // Still need to make sure the click listener uses the latest state, but we updated currentOnCaptionsRequested so it's fine.
            val parentClick = view.findViewById<ImageView>(androidx.media3.ui.R.id.exo_subtitle)?.parent as? ViewGroup
            val customCcClick = parentClick?.findViewWithTag<ImageView>("custom_cc_btn")
            customCcClick?.setOnClickListener {
                currentOnCaptionsRequested.value()
            }
        },
        onRelease = { view ->
            val oldPlayer = view.player
            val listener = view.getTag(com.videhub.R.id.custom_cue_listener) as? Player.Listener
            if (listener != null) {
                oldPlayer?.removeListener(listener)
            }
            view.player = null
        }
    )
}
