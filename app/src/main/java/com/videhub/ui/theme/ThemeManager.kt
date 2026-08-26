package com.videhub.ui.theme

import android.content.Context
import com.videhub.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromString(value: String): AppThemeMode {
            return try {
                valueOf(value.uppercase())
            } catch (_: Exception) {
                SYSTEM
            }
        }
    }
}

object ThemeManager {
    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isAmoledMode = MutableStateFlow(false)
    val isAmoledMode: StateFlow<Boolean> = _isAmoledMode.asStateFlow()

    fun init(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            SettingsManager.getThemeMode(context).collect { modeStr ->
                val mode = AppThemeMode.fromString(modeStr)
                _themeMode.value = mode
                _isDarkMode.value = (mode == AppThemeMode.DARK)
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            SettingsManager.getIsAmoledMode(context).collect { isAmoled ->
                _isAmoledMode.value = isAmoled
            }
        }
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        _themeMode.value = mode
        _isDarkMode.value = (mode == AppThemeMode.DARK)
        CoroutineScope(Dispatchers.IO).launch {
            SettingsManager.setThemeMode(context, mode.name)
        }
    }

    fun setDarkMode(context: Context, isDark: Boolean) {
        setThemeMode(context, if (isDark) AppThemeMode.DARK else AppThemeMode.LIGHT)
    }

    fun setAmoledMode(context: Context, isAmoled: Boolean) {
        _isAmoledMode.value = isAmoled
        CoroutineScope(Dispatchers.IO).launch {
            SettingsManager.setIsAmoledMode(context, isAmoled)
        }
    }
}
