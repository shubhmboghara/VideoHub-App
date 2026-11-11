package com.videhub.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

data class NavItem(val route: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector, val label: String)

fun isLibrarySubsection(route: String?): Boolean {
    if (route == null) return false
    val base = route.substringBefore("?").substringBefore("/")
    return when (base) {
        "history",
        "watch_later",
        "playlists",
        "playlist_detail",
        "custom_playlist",
        "downloads",
        "downloads_tab",
        "downloaded_playlist",
        "downloaded_playlist_detail",
        "liked_videos",
        "proxy_settings",
        "settings",
        "about",
        "channel_about",
        "channel_bio" -> true
        else -> false
    }
}

@Composable
fun BottomNavigationBar(navController: NavController, currentRoute: String?) {
    val haptic = LocalHapticFeedback.current

    val items = listOf(
        NavItem(Screen.Home.route, Icons.Filled.Home, Icons.Outlined.Home, "Home"),
        NavItem(Screen.Subscriptions.route, Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions, "Subscriptions"),
        NavItem(Screen.Library.route, Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary, "Library"),
    )

    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        items.forEach { item ->
            val isSelected = if (item.route == Screen.Library.route) {
                currentRoute == Screen.Library.route || isLibrarySubsection(currentRoute)
            } else {
                currentRoute == item.route
            }
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                selected = isSelected,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (item.route == Screen.Library.route) {
                        if (currentRoute != Screen.Library.route) {
                            val popped = try {
                                navController.popBackStack(Screen.Library.route, inclusive = false)
                            } catch (_: Exception) {
                                false
                            }
                            if (!popped) {
                                val startRoute = navController.graph.findStartDestination().route ?: Screen.Home.route
                                navController.navigate(Screen.Library.route) {
                                    popUpTo(startRoute) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    } else {
                        if (currentRoute != item.route) {
                            val startRoute = navController.graph.findStartDestination().route ?: Screen.Home.route
                            navController.navigate(item.route) {
                                popUpTo(startRoute) {
                                    saveState = (item.route != Screen.Home.route)
                                }
                                launchSingleTop = true
                                if (item.route != Screen.Home.route) {
                                    restoreState = true
                                }
                            }
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
