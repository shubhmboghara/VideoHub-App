package com.videhub.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle



import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.videhub.ui.theme.ThemeManager

@Composable
fun LibraryScreen(
    sharedViewModel: com.videhub.viewmodel.MainViewModel,
    onNavigate: (String) -> Unit,
    onWatchLaterClick: () -> Unit,
    onAboutClick: () -> Unit = { onNavigate("about") }
) {
    val context = LocalContext.current
    val themeMode by ThemeManager.themeMode.collectAsStateWithLifecycle()
    val isAmoledMode by ThemeManager.isAmoledMode.collectAsStateWithLifecycle()
    val systemInDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isEffectivelyDark = when (themeMode) {
        com.videhub.ui.theme.AppThemeMode.SYSTEM -> systemInDark
        com.videhub.ui.theme.AppThemeMode.LIGHT -> false
        com.videhub.ui.theme.AppThemeMode.DARK -> true
    }
    var showThemeDialog by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState(sharedViewModel.libraryScrollCache)

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val options: List<Triple<com.videhub.ui.theme.AppThemeMode, String, ImageVector>> = listOf(
                        Triple(com.videhub.ui.theme.AppThemeMode.SYSTEM, "System default", Icons.Default.SettingsBrightness),
                        Triple(com.videhub.ui.theme.AppThemeMode.LIGHT, "Light theme", Icons.Default.LightMode),
                        Triple(com.videhub.ui.theme.AppThemeMode.DARK, "Dark theme", Icons.Default.DarkMode)
                    )
                    options.forEach { (mode, title, icon) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ThemeManager.setThemeMode(context, mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (themeMode == mode),
                                onClick = {
                                    ThemeManager.setThemeMode(context, mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    androidx.compose.runtime.DisposableEffect(scrollState) {
        onDispose {
            sharedViewModel.libraryScrollCache = scrollState.value
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(vertical = 24.dp)
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp, start = 16.dp)
        )

        SectionTitle("My Content")
        LibraryItemRow(
            title = "History",
            icon = Icons.Default.History,
            onClick = { onNavigate("history") }
        )
        LibraryItemRow(
            title = "Watch Later",
            icon = Icons.Default.Bookmark,
            onClick = onWatchLaterClick
        )
        LibraryItemRow(
            title = "Playlists",
            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
            onClick = { onNavigate("playlists") }
        )
        LibraryItemRow(
            title = "Downloads",
            icon = Icons.Default.Download,
            onClick = { onNavigate("downloads") }
        )
        LibraryItemRow(
            title = "Liked Videos",
            icon = Icons.Default.ThumbUp,
            onClick = { onNavigate("liked_videos") }
        )
        
        SectionTitle("Preferences")
        ThemePreferenceRow(
            themeMode = themeMode,
            onClick = { showThemeDialog = true }
        )
        if (isEffectivelyDark) {
            SwitchRow(
                title = "AMOLED Dark Mode",
                icon = Icons.Default.DarkMode,
                isChecked = isAmoledMode,
                onToggle = { ThemeManager.setAmoledMode(context, it) }
            )
        }
        LibraryItemRow(
            title = "Settings",
            icon = Icons.Default.Settings,
            onClick = { onNavigate("proxy_settings") }
        )

        SectionTitle("More")
        LibraryItemRow(
            title = "About VideoHub",
            icon = Icons.Default.Info,
            onClick = onAboutClick
        )
        
        Spacer(modifier = Modifier.height(100.dp)) // Extra padding for bottom nav
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 24.dp)
    )
}

@Composable
fun LibraryItemRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SwitchRow(
    title: String,
    icon: ImageVector,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isChecked) }
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            colors = androidx.compose.material3.SwitchDefaults.colors(uncheckedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.outline, uncheckedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant),
            checked = isChecked,
            onCheckedChange = onToggle
        )
    }
}

@Composable
fun ThemePreferenceRow(
    themeMode: com.videhub.ui.theme.AppThemeMode,
    onClick: () -> Unit
) {
    val subtitle = when (themeMode) {
        com.videhub.ui.theme.AppThemeMode.SYSTEM -> "System default"
        com.videhub.ui.theme.AppThemeMode.LIGHT -> "Light theme"
        com.videhub.ui.theme.AppThemeMode.DARK -> "Dark theme"
    }
    val icon = when (themeMode) {
        com.videhub.ui.theme.AppThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
        com.videhub.ui.theme.AppThemeMode.LIGHT -> Icons.Default.LightMode
        com.videhub.ui.theme.AppThemeMode.DARK -> Icons.Default.DarkMode
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Theme",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
