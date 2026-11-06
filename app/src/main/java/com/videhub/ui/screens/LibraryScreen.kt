package com.videhub.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videhub.ui.components.VideoHubTopBar
import com.videhub.ui.theme.AppThemeMode
import com.videhub.ui.theme.ThemeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    sharedViewModel: com.videhub.viewmodel.MainViewModel,
    onNavigate: (String) -> Unit,
    onWatchLaterClick: () -> Unit,
    onSearchClick: () -> Unit = { onNavigate("explore") },
    onAboutClick: () -> Unit = { onNavigate("about") }
) {
    val context = LocalContext.current
    val themeMode by ThemeManager.themeMode.collectAsStateWithLifecycle()
    val isAmoledMode by ThemeManager.isAmoledMode.collectAsStateWithLifecycle()
    val systemInDark = isSystemInDarkTheme()
    val isEffectivelyDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemInDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    var showThemeDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState(initial = sharedViewModel.libraryScrollCache)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val appLink = "https://crazy-store-rho.vercel.app/apps/videohub"
    val shareText = remember(appLink) {
        "Check out VideoHub, a clean, ad-free video and music player with offline downloads, background playback, and complete privacy: $appLink"
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val options: List<Triple<AppThemeMode, String, ImageVector>> = listOf(
                        Triple(AppThemeMode.SYSTEM, "System default", Icons.Default.SettingsBrightness),
                        Triple(AppThemeMode.LIGHT, "Light theme", Icons.Default.LightMode),
                        Triple(AppThemeMode.DARK, "Dark theme", Icons.Default.DarkMode)
                    )
                    options.forEach { (mode, title, icon) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable {
                                    ThemeManager.setThemeMode(context, mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (themeMode == mode),
                                onClick = {
                                    ThemeManager.setThemeMode(context, mode)
                                    showThemeDialog = false
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showThemeDialog = false },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Close")
                }
            }
        )
    }

    DisposableEffect(scrollState) {
        onDispose {
            sharedViewModel.libraryScrollCache = scrollState.value
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        VideoHubTopBar(
            showFilter = false,
            onSearch = onSearchClick,
            scrollBehavior = scrollBehavior
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(bottom = 120.dp)
        ) {
            SectionTitle("My content")
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

            SectionTitle("Settings")
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
            LibraryItemRow(
                title = "Share VideoHub App",
                icon = Icons.Default.Share,
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "VideoHub - Private Video & Music Player")
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share VideoHub"))
                }
            )
            LibraryItemRow(
                title = "About VideoHub",
                icon = Icons.Default.Info,
                onClick = onAboutClick
            )
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 20.dp)
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
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
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
            .minimumInteractiveComponentSize()
            .clickable { onToggle(!isChecked) }
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
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
            modifier = Modifier.minimumInteractiveComponentSize(),
            colors = SwitchDefaults.colors(
                uncheckedTrackColor = MaterialTheme.colorScheme.outline,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            checked = isChecked,
            onCheckedChange = onToggle
        )
    }
}

@Composable
fun ThemePreferenceRow(
    themeMode: AppThemeMode,
    onClick: () -> Unit
) {
    val subtitle = when (themeMode) {
        AppThemeMode.SYSTEM -> "System default"
        AppThemeMode.LIGHT -> "Light theme"
        AppThemeMode.DARK -> "Dark theme"
    }
    val icon = when (themeMode) {
        AppThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
        AppThemeMode.LIGHT -> Icons.Default.LightMode
        AppThemeMode.DARK -> Icons.Default.DarkMode
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Theme: $subtitle",
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun LibraryScreenPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            VideoHubTopBar(
                showFilter = false,
                onSearch = {}
            )
            SectionTitle("My content")
            LibraryItemRow(title = "History", icon = Icons.Default.History, onClick = {})
            LibraryItemRow(title = "Watch Later", icon = Icons.Default.Bookmark, onClick = {})
            LibraryItemRow(title = "Playlists", icon = Icons.AutoMirrored.Filled.PlaylistPlay, onClick = {})
            LibraryItemRow(title = "Downloads", icon = Icons.Default.Download, onClick = {})
            LibraryItemRow(title = "Liked Videos", icon = Icons.Default.ThumbUp, onClick = {})
            SectionTitle("Settings")
            ThemePreferenceRow(themeMode = AppThemeMode.DARK, onClick = {})
            LibraryItemRow(title = "Settings", icon = Icons.Default.Settings, onClick = {})
        }
    }
}
