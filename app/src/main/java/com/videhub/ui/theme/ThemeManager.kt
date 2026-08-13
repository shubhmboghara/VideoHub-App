package com.videhub.ui.theme

import android.content.Context
import com.videhub.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ThemeManager {
    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isAmoledMode = MutableStateFlow(false)
    val isAmoledMode: StateFlow<Boolean> = _isAmoledMode.asStateFlow()

    fun init(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            SettingsManager.getIsDarkMode(context).collect { isDark ->
                _isDarkMode.value = isDark
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            SettingsManager.getIsAmoledMode(context).collect { isAmoled ->
                _isAmoledMode.value = isAmoled
            }
        }
    }

    fun setDarkMode(context: Context, isDark: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            SettingsManager.setIsDarkMode(context, isDark)
        }
    }

    fun setAmoledMode(context: Context, isAmoled: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            SettingsManager.setIsAmoledMode(context, isAmoled)
        }
    }
}
