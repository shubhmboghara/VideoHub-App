package com.videhub.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.videhub.data.AppDatabase
import com.videhub.data.entity.PlaylistEntity
import com.videhub.data.entity.PlaylistVideoEntity
import com.videhub.data.entity.WatchLaterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistDialog(
    videoUrl: String,
    title: String,
    thumbnailUrl: String,
    channelName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    var playlists by remember { mutableStateOf<List<PlaylistEntity>>(emptyList()) }
    var playlistCovers by remember { mutableStateOf<Map<Int, String?>>(emptyMap()) }
    var isSavedInWatchLater by remember { mutableStateOf(false) }
    var watchLaterCover by remember { mutableStateOf<String?>(null) }
    var savedPlaylistIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // Load Watch Later and Custom Playlists data
    LaunchedEffect(videoUrl) {
        withContext(Dispatchers.IO) {
            // Check Watch Later state
            isSavedInWatchLater = db.watchLaterDao().isInWatchLater(videoUrl)

            // Watch Later cover thumbnail (ONLY use thumbnail of videos actually saved in Watch Later)
            val wlAll = db.watchLaterDao().getAllOnce()
            watchLaterCover = wlAll.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl

            // Custom Playlists
            val allPlaylists = db.playlistDao().getAllPlaylistsOnce()
            playlists = allPlaylists

            // Load cover thumbnails and saved state for each playlist
            val coversMap = mutableMapOf<Int, String?>()
            val savedIds = mutableSetOf<Int>()

            for (pl in allPlaylists) {
                val videos = db.playlistDao().getVideosOnce(pl.id)
                val firstThumb = videos.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
                coversMap[pl.id] = firstThumb
                if (videos.any { it.videoId == videoUrl }) {
                    savedIds.add(pl.id)
                }
            }

            playlistCovers = coversMap
            savedPlaylistIds = savedIds
        }
    }

    // Compact Modal Bottom Sheet matching YouTube's playlist sheet height
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .safeBottomSheetPadding(extraBottom = 16.dp)
        ) {
            // Header Title: "Save to..." + Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Save to...",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Playlist Item Rows (Compact max height ~240dp so sheet stays compact & list scrolls internally)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                // Item 1: "Watch later"
                item(key = "watch_later_row") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    if (isSavedInWatchLater) {
                                        db.watchLaterDao().deleteById(videoUrl)
                                        isSavedInWatchLater = false
                                        val remainingWl = db.watchLaterDao().getAllOnce()
                                        watchLaterCover = remainingWl.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Removed from Watch later", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        db.watchLaterDao().insert(
                                            WatchLaterEntity(
                                                videoId = videoUrl,
                                                title = title,
                                                thumbnailUrl = thumbnailUrl,
                                                channelName = channelName
                                            )
                                        )
                                        isSavedInWatchLater = true
                                        if (watchLaterCover == null) {
                                            watchLaterCover = thumbnailUrl
                                        }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Saved to Watch later", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Thumbnail (~72dp x ~44dp, 10dp rounded corners)
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            val thumbToUse = watchLaterCover
                            if (!thumbToUse.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(thumbToUse)
                                        .crossfade(true)
                                        .size(200)
                                        .build(),
                                    contentDescription = "Watch later cover",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Middle: Title "Watch later" (No public/private subtitle)
                        Text(
                            text = "Watch later",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Right: Bookmark Icon
                        Icon(
                            imageVector = if (isSavedInWatchLater) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isSavedInWatchLater) "Saved in Watch later" else "Save to Watch later",
                            tint = if (isSavedInWatchLater) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Subsequent Items: Custom Playlists (if any)
                items(playlists, key = { it.id }) { playlist ->
                    val isSavedInThis = playlist.id in savedPlaylistIds
                    val cover = playlistCovers[playlist.id]

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    if (isSavedInThis) {
                                        db.playlistDao().deleteVideoByPair(playlist.id, videoUrl)
                                        savedPlaylistIds = savedPlaylistIds - playlist.id
                                        val remainingVideos = db.playlistDao().getVideosOnce(playlist.id)
                                        val newCover = remainingVideos.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
                                        playlistCovers = playlistCovers + (playlist.id to newCover)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Removed from '${playlist.name}'", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        db.playlistDao().insertVideo(
                                            PlaylistVideoEntity(
                                                playlistId = playlist.id,
                                                videoId = videoUrl,
                                                title = title,
                                                thumbnailUrl = thumbnailUrl,
                                                channelName = channelName
                                            )
                                        )
                                        savedPlaylistIds = savedPlaylistIds + playlist.id
                                        if (playlistCovers[playlist.id] == null) {
                                            playlistCovers = playlistCovers + (playlist.id to thumbnailUrl)
                                        }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Added to '${playlist.name}'", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Cover Thumbnail (~72dp x ~44dp, 10dp rounded corners)
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!cover.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(cover)
                                        .crossfade(true)
                                        .size(200)
                                        .build(),
                                    contentDescription = "Playlist cover",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Middle: Playlist Title (No public/private subtitle)
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Right: Bookmark Icon
                        Icon(
                            imageVector = if (isSavedInThis) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isSavedInThis) "Saved in playlist" else "Save to playlist",
                            tint = if (isSavedInThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bottom Full-Width Pill Button: "+ New playlist" matching screenshots
            Surface(
                onClick = { showCreatePlaylistDialog = true },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "New playlist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // New Playlist Creation Dialog matching YouTube screenshot image_5d2042aa.png
    if (showCreatePlaylistDialog) {
        NewPlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { name ->
                scope.launch(Dispatchers.IO) {
                    val trimmedName = name.trim()
                    if (trimmedName.isBlank()) return@launch

                    // Create a new playlist with the requested name (even if another playlist has the same name)
                    val playlistId = db.playlistDao().insertPlaylist(
                        PlaylistEntity(name = trimmedName)
                    ).toInt()

                    db.playlistDao().insertVideo(
                        PlaylistVideoEntity(
                            playlistId = playlistId,
                            videoId = videoUrl,
                            title = title,
                            thumbnailUrl = thumbnailUrl,
                            channelName = channelName
                        )
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Created & saved to '$trimmedName'", Toast.LENGTH_SHORT).show()
                        showCreatePlaylistDialog = false
                        onDismiss()
                    }
                }
            }
        )
    }
}

// ── New Playlist Dialog Component matching YouTube screenshot image_5d2042aa.png ──
@Composable
fun NewPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = {
        if (!isSubmitting) onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF212121),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title
                Text(
                    text = "New playlist",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Outlined Text Field for "Title" matching image_5d2042aa.png
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Title") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons (Cancel & Create) matching image_5d2042aa.png
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel Button (Outlined Pill)
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Create Button (Filled Pill)
                    Button(
                        onClick = {
                            val trimmed = playlistName.trim()
                            if (trimmed.isNotBlank() && !isSubmitting) {
                                isSubmitting = true
                                onCreate(trimmed)
                            }
                        },
                        enabled = playlistName.isNotBlank() && !isSubmitting,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE0E0E0),
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF424242),
                            disabledContentColor = Color.LightGray.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.height(40.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Create", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
