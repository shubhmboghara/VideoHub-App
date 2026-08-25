package com.videhub.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.FlashOn
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

@Composable
fun BottomNavigationBar(navController: NavController, currentRoute: String?) {
    val haptic = LocalHapticFeedback.current

    val items = listOf(
        NavItem(Screen.Home.route, Icons.Filled.Home, Icons.Outlined.Home, "Home"),
        NavItem(Screen.Shorts.route, Icons.Filled.FlashOn, Icons.Outlined.FlashOn, "Shorts"),
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
            val isSelected = currentRoute == item.route
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
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            val startRoute = navController.graph.findStartDestination().route
                            if (startRoute != null) {
                                popUpTo(startRoute) {
                                    saveState = (item.route != Screen.Home.route) // Don't save state when going to home, just clear
                                }
                            } else {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = (item.route != Screen.Home.route)
                                }
                            }
                            launchSingleTop = true
                            if (item.route != Screen.Home.route) {
                                restoreState = true
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
