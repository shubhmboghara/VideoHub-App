package com.videhub.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.videhub.ui.components.safeBottomSheetPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import com.videhub.QueueManager
import com.videhub.PlayQueueItem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.videhub.data.AppDatabase
import com.videhub.data.dao.PlaylistWithThumbnail
import com.videhub.data.entity.HistoryEntity
import com.videhub.data.entity.LikedVideoEntity
import com.videhub.data.entity.PlaylistEntity
import com.videhub.data.entity.PlaylistVideoEntity
import com.videhub.data.entity.WatchLaterEntity
import com.videhub.extractor.ExtractorHelper
import com.videhub.recommendation.RecommendationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onPlaylistClick: (Int) -> Unit,
    onNavigate: (String) -> Unit = {},
    onVideoClick: (String, String, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    
    var playlists by remember { mutableStateOf<List<PlaylistWithThumbnail>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val likedVideos by db.likedVideoDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val watchLaterVideos by db.watchLaterDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val historyVideos by db.historyDao().getAllHistory().collectAsStateWithLifecycle(initialValue = emptyList())

    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var isGeneratingMix by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var importUrl by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }

    var activeMenuPlaylist by remember { mutableStateOf<PlaylistWithThumbnail?>(null) }
    var playlistToEdit by remember { mutableStateOf<PlaylistWithThumbnail?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaylistWithThumbnail?>(null) }
    var showWatchLaterMenu by remember { mutableStateOf(false) }

    val onBackPressedDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    LaunchedEffect(Unit) {
        db.playlistDao().getAllPlaylistsWithDetails().collectLatest {
            playlists = it
            isLoading = false
        }
    }

    fun generateSmartMix() {
        if (isGeneratingMix) return
        isGeneratingMix = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Fetch smart recommendations based on user history, likes, and watch later
                val feed = RecommendationEngine.getRecommendedFeed(db, context, isRefresh = true)
                val streamItems = feed.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>().take(20)
                
                if (streamItems.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isGeneratingMix = false
                        Toast.makeText(context, "Watch or like some videos first to generate a personalized mix!", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val dateStr = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date())
                val playlistTitle = "My Smart Mix ($dateStr)"
                val newId = db.playlistDao().insertPlaylist(PlaylistEntity(name = playlistTitle))
                
                streamItems.forEach { item ->
                    db.playlistDao().insertVideo(
                        PlaylistVideoEntity(
                            playlistId = newId.toInt(),
                            videoId = item.url ?: "",
                            title = item.name ?: "Unknown",
                            channelName = item.uploaderName ?: "",
                            thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: ""
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    isGeneratingMix = false
                    Toast.makeText(context, "Generated \"$playlistTitle\" with ${streamItems.size} tracks!", Toast.LENGTH_SHORT).show()
                    onPlaylistClick(newId.toInt())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isGeneratingMix = false
                    Toast.makeText(context, "Failed to generate mix: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playlists") },
                navigationIcon = {
                    IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { generateSmartMix() },
                        enabled = !isGeneratingMix
                    ) {
                        if (isGeneratingMix) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Generate Smart Mix",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Import from URL",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp)
            )
        },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { generateSmartMix() },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    text = { Text(if (isGeneratingMix) "Generating..." else "Smart Mix") },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Playlist")
                }
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── SMART / SYSTEM PLAYLISTS SECTION ───────────────────────
            item(span = { GridItemSpan(2) }) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
                    Text(
                        text = "Smart & System Playlists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Auto-generated from your likes, watch history, and watch later",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Liked Videos
            item {
                SmartPlaylistCard(
                    title = "Liked Videos",
                    count = likedVideos.size,
                    icon = Icons.Default.ThumbUp,
                    thumbnailUrl = likedVideos.firstOrNull()?.thumbnailUrl,
                    gradientColors = listOf(Color(0xFFE91E63), Color(0xFF880E4F)),
                    onClick = { onNavigate("liked_videos") }
                )
            }

            // Watch Later
            item {
                SmartPlaylistCard(
                    title = "Watch Later",
                    count = watchLaterVideos.size,
                    icon = Icons.Default.Bookmark,
                    thumbnailUrl = watchLaterVideos.firstOrNull()?.thumbnailUrl,
                    gradientColors = listOf(Color(0xFF3F51B5), Color(0xFF1A237E)),
                    onClick = { onNavigate("watch_later") },
                    onMoreClick = { showWatchLaterMenu = true }
                )
            }

            // History Mix
            item {
                SmartPlaylistCard(
                    title = "History Mix",
                    count = historyVideos.size,
                    icon = Icons.Default.History,
                    thumbnailUrl = historyVideos.firstOrNull()?.thumbnailUrl,
                    gradientColors = listOf(Color(0xFF009688), Color(0xFF004D40)),
                    onClick = { onNavigate("history") }
                )
            }

            // My Smart Mix (Auto-Recommended)
            item {
                SmartPlaylistCard(
                    title = "My Smart Mix",
                    subtitle = if (isGeneratingMix) "Creating mix..." else "Tap to generate",
                    count = null,
                    icon = Icons.Default.AutoAwesome,
                    thumbnailUrl = null,
                    gradientColors = listOf(Color(0xFF673AB7), Color(0xFF311B92)),
                    onClick = { generateSmartMix() }
                )
            }

            // ── CUSTOM & USER PLAYLISTS SECTION ─────────────────────────
            item(span = { GridItemSpan(2) }) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
                    Text(
                        text = "Your Playlists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (!isLoading && playlists.isEmpty()) {
                        Text(
                            text = "No custom playlists yet. Create one or import from link.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isLoading || isGeneratingMix || isImporting) {
                items(4, key = { "playlist_grid_shimmer_$it" }) {
                    val brush = com.videhub.ui.components.shimmerBrush()
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .background(brush)
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.75f)
                                        .height(15.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(brush)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.45f)
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(brush)
                                )
                            }
                        }
                    }
                }
            } else if (playlists.isNotEmpty()) {
                items(playlists, key = { it.playlist.id }) { playlist ->
                    PlaylistListItem(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist.playlist.id) },
                        onMoreClick = { activeMenuPlaylist = playlist },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        com.videhub.ui.components.NewPlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                coroutineScope.launch(Dispatchers.IO) {
                    val trimmed = name.trim()
                    if (trimmed.isNotBlank()) {
                        db.playlistDao().insertPlaylist(PlaylistEntity(name = trimmed))
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Created playlist \"$trimmed\"", Toast.LENGTH_SHORT).show()
                        }
                    }
                    withContext(Dispatchers.Main) {
                        showCreateDialog = false
                    }
                }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!isImporting) showImportDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Import Playlist from Link") },
            text = {
                Column {
                    Text(
                        text = "Paste a YouTube playlist, mix, or video URL to automatically clone it into your local library:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = importUrl,
                        onValueChange = { importUrl = it },
                        label = { Text("Playlist URL") },
                        placeholder = { Text("https://www.youtube.com/playlist?list=...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    if (isImporting) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Extracting & saving tracks...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = importUrl.isNotBlank() && !isImporting,
                    onClick = {
                        isImporting = true
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val cleanUrl = importUrl.trim()
                                val info = ExtractorHelper.getPlaylistInfo(cleanUrl)
                                val playlistName = info.name?.ifBlank { "Imported Playlist" } ?: "Imported Playlist"
                                
                                val playlistId = db.playlistDao().insertPlaylist(PlaylistEntity(name = playlistName))
                                
                                val items = info.relatedItems.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                                items.forEach { item ->
                                    db.playlistDao().insertVideo(
                                        PlaylistVideoEntity(
                                            playlistId = playlistId.toInt(),
                                            videoId = item.url ?: "",
                                            title = item.name ?: "Unknown",
                                            channelName = item.uploaderName ?: "",
                                            thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: ""
                                        )
                                    )
                                }
                                
                                withContext(Dispatchers.Main) {
                                    isImporting = false
                                    importUrl = ""
                                    showImportDialog = false
                                    Toast.makeText(context, "Imported ${items.size} tracks into \"$playlistName\"!", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isImporting = false
                                    Toast.makeText(context, "Error importing playlist: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = { showImportDialog = false }
                ) { Text("Cancel") }
            }
        )
    }

    // ── Playlist 3-Dot Options Bottom Sheet (Matching image_fe7e539f.png) ──────
    if (activeMenuPlaylist != null) {
        val targetPlaylist = activeMenuPlaylist!!
        ModalBottomSheet(
            onDismissRequest = { activeMenuPlaylist = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            containerColor = Color(0xFF222222),
            contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                    .safeBottomSheetPadding(extraBottom = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title header
                Text(
                    text = targetPlaylist.playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))

                // 1. Play next in queue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val target = targetPlaylist
                            activeMenuPlaylist = null
                            coroutineScope.launch(Dispatchers.IO) {
                                val videos = db.playlistDao().getVideosOnce(target.playlist.id)
                                if (videos.isNotEmpty()) {
                                    videos.reversed().forEach { v ->
                                        QueueManager.playNext(
                                            PlayQueueItem(
                                                url = v.videoId,
                                                title = v.title,
                                                uploaderName = v.channelName,
                                                thumbnailUrl = v.thumbnailUrl ?: ""
                                            )
                                        )
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Added ${videos.size} videos next in queue", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Playlist is empty", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.videhub.R.drawable.ic_play_next),
                        contentDescription = "Play next in queue",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Play next in queue",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }

                // 2. Delete
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val target = targetPlaylist
                            activeMenuPlaylist = null
                            playlistToDelete = target
                        }
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // 3. Edit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val target = targetPlaylist
                            activeMenuPlaylist = null
                            playlistToEdit = target
                        }
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }

    // ── Edit Playlist Sheet ───────────────────────────────────────────────────
    if (playlistToEdit != null) {
        val target = playlistToEdit!!
        com.videhub.ui.components.EditPlaylistSheet(
            initialTitle = target.playlist.name,
            initialCoverUrl = target.thumbnailUrl,
            onDismiss = { playlistToEdit = null },
            onSave = { newTitle, newCoverUrl ->
                coroutineScope.launch(Dispatchers.IO) {
                    db.playlistDao().updatePlaylistDetails(target.playlist.id, newTitle, newCoverUrl)
                    withContext(Dispatchers.Main) {
                        playlistToEdit = null
                        Toast.makeText(context, "Playlist updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // ── Delete Confirmation Dialog ─────────────────────────────────────────────
    if (playlistToDelete != null) {
        val target = playlistToDelete!!
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete playlist?") },
            text = { Text("Are you sure you want to delete '${target.playlist.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            db.playlistDao().deletePlaylist(target.playlist)
                            withContext(Dispatchers.Main) {
                                playlistToDelete = null
                                Toast.makeText(context, "Deleted playlist", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Watch Later 3-Dot Options Bottom Sheet (Only "Play next in queue" - image_53f74214.png) ──────
    if (showWatchLaterMenu) {
        ModalBottomSheet(
            onDismissRequest = { showWatchLaterMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            containerColor = Color(0xFF222222),
            contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                    .safeBottomSheetPadding(extraBottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showWatchLaterMenu = false
                            coroutineScope.launch(Dispatchers.IO) {
                                val items = db.watchLaterDao().getAllOnce()
                                if (items.isNotEmpty()) {
                                    items.reversed().forEach { item ->
                                        QueueManager.playNext(
                                            PlayQueueItem(
                                                url = item.videoId,
                                                title = item.title,
                                                uploaderName = item.channelName,
                                                thumbnailUrl = item.thumbnailUrl
                                            )
                                        )
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Added Watch Later videos next in queue", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Watch Later is empty", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.videhub.R.drawable.ic_play_next),
                        contentDescription = "Play next in queue",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Play next in queue",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun SmartPlaylistCard(
    title: String,
    count: Int? = null,
    subtitle: String? = null,
    icon: ImageVector,
    thumbnailUrl: String? = null,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(colors = gradientColors)
                        )
                )
            }

            if (onMoreClick != null) {
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle ?: "${com.videhub.utils.FormatHelper.formatCount((count ?: 0).toLong())} videos",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistListItem(
    playlist: PlaylistWithThumbnail,
    onClick: () -> Unit,
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    com.videhub.ui.components.PlaylistCard(
        title = playlist.playlist.name,
        subtitle = "${com.videhub.utils.FormatHelper.formatCount(playlist.videoCount.toLong())} videos",
        thumbnailUrl = playlist.thumbnailUrl,
        onClick = onClick,
        onMoreClick = onMoreClick,
        modifier = modifier
    )
}

