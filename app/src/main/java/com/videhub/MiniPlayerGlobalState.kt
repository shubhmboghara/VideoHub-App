package com.videhub

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf

// All possible states of the mini player
sealed class MiniPlayerStateEnum {
  object Hidden : MiniPlayerStateEnum()        // not visible
  object FloatingPip : MiniPlayerStateEnum()   // floating freely
  object EdgeDockedLeft : MiniPlayerStateEnum() // hidden left edge
  object EdgeDockedRight : MiniPlayerStateEnum() // hidden right edge
}

object MiniPlayerGlobalState {
  // Current state
  var state = mutableStateOf<MiniPlayerStateEnum>(
    MiniPlayerStateEnum.Hidden
  )

  // Card position on screen (pixels)
  // -1f means "not set yet, use default position"
  var pipOffsetX = mutableFloatStateOf(-1f)
  var pipOffsetY = mutableFloatStateOf(-1f)

  // Card scale (1f = normal, 0.5f = half size, 1.5f = big)
  var pipScale = mutableFloatStateOf(1f)
}
