package com.videhub.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Computes a guaranteed safe bottom padding for ModalBottomSheet content.
 * On many Android devices (especially 3-button navigation bars with enableEdgeToEdge),
 * ModalBottomSheet's internal DialogWindow reports 0 for WindowInsets.navigationBars,
 * causing sheet content to render behind the < O ||| navigation bar buttons.
 * This helper reads both Compose WindowInsets and the Android system navigation_bar_height
 * dimension resource to guarantee the sheet content always clears the system navigation bar.
 */
@Composable
fun rememberSafeBottomSheetPadding(extraBottom: Dp = 16.dp): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current

    val navBarPx = WindowInsets.navigationBars.getBottom(density)
    val gesturesPx = WindowInsets.systemGestures.getBottom(density)
    val tappablePx = WindowInsets.tappableElement.getBottom(density)
    val composeInsetDp = with(density) {
        maxOf(navBarPx, gesturesPx, tappablePx).toDp()
    }

    val systemNavBarDp = remember(context, density) {
        val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resId > 0) {
            with(density) { context.resources.getDimensionPixelSize(resId).toDp() }
        } else {
            48.dp
        }
    }

    return maxOf(composeInsetDp, systemNavBarDp, 48.dp) + extraBottom
}

@Composable
fun Modifier.safeBottomSheetPadding(extraBottom: Dp = 16.dp): Modifier {
    val bottomPadding = rememberSafeBottomSheetPadding(extraBottom = extraBottom)
    return this
        .imePadding()
        .padding(bottom = bottomPadding)
}
