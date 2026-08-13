package com.videhub.ui.theme

import androidx.lifecycle.compose.collectAsStateWithLifecycle



import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

val BogharaDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF272727),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF272727),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF272727),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFF272727),
    surfaceTint = Color.Transparent,
    onPrimary = Color(0xFF000000),
    onSecondary = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF3F3F3F),
    inverseSurface = Color(0xFFF1F1F1),
    inverseOnSurface = Color(0xFF0F0F0F),
    error = Color(0xFFFF5252),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val BogharaLightColorScheme = lightColorScheme(
    primary = Color(0xFF000000),
    primaryContainer = Color(0xFFF2F2F2),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFF2F2F2),
    onSecondaryContainer = Color(0xFF000000),
    tertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFFF2F2F2),
    onTertiaryContainer = Color(0xFF000000),
    background = Color(0xFFF9F9F9),
    surface = Color(0xFFF9F9F9),
    surfaceVariant = Color(0xFFF2F2F2),
    surfaceTint = Color.Transparent,
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    onSurfaceVariant = Color(0xFF606060),
    outline = Color(0xFFCCCCCC),
    inverseSurface = Color(0xFF0F0F0F),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsStateWithLifecycle()
    val isAmoledMode by ThemeManager.isAmoledMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    val colorScheme = when {
        isDarkMode -> {
            if (isAmoledMode) {
                BogharaDarkColorScheme.copy(background = Color(0xFF000000), surface = Color(0xFF000000), surfaceVariant = Color(0xFF000000))
            } else {
                BogharaDarkColorScheme
            }
        }
        else -> BogharaLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDarkMode
            insetsController.isAppearanceLightNavigationBars = !isDarkMode
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = {
            androidx.compose.material3.Surface(
                color = colorScheme.background,
                contentColor = colorScheme.onBackground,
                content = content
            )
        }
    )
}
