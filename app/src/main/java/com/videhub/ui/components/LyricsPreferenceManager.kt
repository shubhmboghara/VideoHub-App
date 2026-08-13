package com.videhub.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LyricsMode {
    NATIVE, PHONETIC, TRANSLATION
}

object LyricsPreferenceManager {
    private val _lyricsMode = MutableStateFlow(LyricsMode.NATIVE)
    val lyricsMode: StateFlow<LyricsMode> = _lyricsMode.asStateFlow()

    fun setMode(mode: LyricsMode) {
        _lyricsMode.value = mode
    }
}
