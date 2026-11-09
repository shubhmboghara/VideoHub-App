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

        val db = remember { com.videhub.data.AppDatabase.getDatabase(context) }
        val historyCount by db.historyDao().getCount().collectAsStateWithLifecycle(initialValue = null)
        val watchLaterCount by db.watchLaterDao().getCount().collectAsStateWithLifecycle(initialValue = null)
        val playlistsCount by db.playlistDao().getCount().collectAsStateWithLifecycle(initialValue = null)
        val downloadsCount by db.downloadedVideoDao().getCompletedCount().collectAsStateWithLifecycle(initialValue = null)
        val totalDownloadedBytes by db.downloadedVideoDao().getTotalDownloadedBytes().collectAsStateWithLifecycle(initialValue = null)
        val likedCount by db.likedVideoDao().getCount().collectAsStateWithLifecycle(initialValue = null)

        val watchLaterSubtitle = when {
            watchLaterCount == null -> null
            watchLaterCount == 1 -> "1 video"
            watchLaterCount!! > 1 -> "$watchLaterCount videos"
            else -> null
        }

        val downloadsSubtitle = when {
            downloadsCount == null -> null
            downloadsCount!! > 0 -> {
                val countLabel = if (downloadsCount == 1) "1 video" else "$downloadsCount videos"
                val totalBytes = totalDownloadedBytes ?: 0L
                if (totalBytes > 0L) {
                    val mb = totalBytes.toDouble() / (1024.0 * 1024.0)
                    val sizeLabel = if (mb >= 1024.0) {
                        String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0)
                    } else {
                        String.format(java.util.Locale.US, "%.0f MB", mb)
                    }
                    "$countLabel · $sizeLabel"
                } else {
                    countLabel
                }
            }
            else -> null
        }

        val playlistsSubtitle = when {
            playlistsCount == null -> null
            playlistsCount!! > 0 -> "$playlistsCount"
            else -> null
        }

        val likedSubtitle = when {
            likedCount == null -> null
            likedCount!! > 0 -> "$likedCount"
            else -> null
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(bottom = 120.dp)
        ) {
            SectionTitle("My content")

            // Prominent Downloads and Watch Later cards side by side
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LibraryProminentCard(
                    title = "Downloads",
                    subtitle = downloadsSubtitle,
                    icon = Icons.Default.Download,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate("downloads") }
                )
                LibraryProminentCard(
                    title = "Watch Later",
                    subtitle = watchLaterSubtitle,
                    icon = Icons.Default.Bookmark,
                    modifier = Modifier.weight(1f),
                    onClick = onWatchLaterClick
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LibraryItemRow(
                title = "History",
                subtitle = null,
                icon = Icons.Default.History,
                onClick = { onNavigate("history") }
            )
            LibraryItemRow(
                title = "Playlists",
                subtitle = null,
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                onClick = { onNavigate("playlists") }
            )
            LibraryItemRow(
                title = "Liked Videos",
                subtitle = likedSubtitle,
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
                subtitle = null,
                icon = Icons.Default.Settings,
                onClick = { onNavigate("proxy_settings") }
            )
            LibraryItemRow(
                title = "Share VideoHub App",
                subtitle = null,
                painter = androidx.compose.ui.res.painterResource(id = com.videhub.R.drawable.ic_share_youtube),
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
                subtitle = null,
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
fun LibraryProminentCard(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = 88.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun LibraryItemRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (painter != null) {
            Icon(
                painter = painter,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
    com.videhub.ui.theme.AppTheme {
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LibraryProminentCard(
                    title = "Downloads",
                    subtitle = "5 videos · 120 MB",
                    icon = Icons.Default.Download,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
                LibraryProminentCard(
                    title = "Watch Later",
                    subtitle = "3 videos",
                    icon = Icons.Default.Bookmark,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }
            LibraryItemRow(title = "History", subtitle = null, icon = Icons.Default.History, onClick = {})
            LibraryItemRow(title = "Playlists", subtitle = "2", icon = Icons.AutoMirrored.Filled.PlaylistPlay, onClick = {})
            LibraryItemRow(title = "Liked Videos", subtitle = "12", icon = Icons.Default.ThumbUp, onClick = {})
            SectionTitle("Preferences")
            ThemePreferenceRow(themeMode = AppThemeMode.DARK, onClick = {})
            LibraryItemRow(title = "Settings", subtitle = null, icon = Icons.Default.Settings, onClick = {})
        }
    }
}
