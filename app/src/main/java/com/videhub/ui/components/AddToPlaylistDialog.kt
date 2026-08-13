package com.videhub.ui.components

import android.widget.Toast
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import com.videhub.data.AppDatabase
import com.videhub.data.entity.PlaylistEntity
import com.videhub.data.entity.PlaylistVideoEntity
import kotlinx.coroutines.launch

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
    val db = AppDatabase.getDatabase(context)
    var playlists by remember { mutableStateOf<List<PlaylistEntity>>(emptyList()) }
    var showCreatePlaylistNameDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        db.playlistDao().getAllPlaylists().collect { playlists = it }
    }

    if (!showCreatePlaylistNameDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Save to playlist") },
            text = {
                LazyColumn {
                    itemsIndexed(playlists, key = { index, playlist -> "${playlist.id}_$index" }) { index, playlist ->
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val isDuplicate = com.videhub.utils.DuplicateChecker.isVideoInPlaylist(context, playlist.id, videoUrl)
                                    if (isDuplicate) {
                                        Toast.makeText(context, "Already in '${playlist.name}' playlist", Toast.LENGTH_SHORT).show()
                                    } else {
                                        db.playlistDao().insertVideo(
                                            PlaylistVideoEntity(
                                                playlistId = playlist.id, videoId = videoUrl,
                                                title = title, thumbnailUrl = thumbnailUrl,
                                                channelName = channelName
                                            )
                                        )
                                        Toast.makeText(context, "Added to '${playlist.name}' playlist", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) { Text(playlist.name) }
                    }
                    item {
                        TextButton(
                            onClick = {
                                showCreatePlaylistNameDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) { Text("+ Create new") }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) { Text("Cancel") }
            }
        )
    }

    if (showCreatePlaylistNameDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistNameDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmedName = newPlaylistName.trim()
                        if (trimmedName.isNotBlank()) {
                            scope.launch {
                                val existingPlaylist = db.playlistDao().getPlaylistByName(trimmedName)
                                if (existingPlaylist != null) {
                                    val isDuplicate = com.videhub.utils.DuplicateChecker.isVideoInPlaylist(context, existingPlaylist.id, videoUrl)
                                    if (isDuplicate) {
                                        Toast.makeText(context, "Already in '${existingPlaylist.name}' playlist", Toast.LENGTH_SHORT).show()
                                    } else {
                                        db.playlistDao().insertVideo(
                                            PlaylistVideoEntity(
                                                playlistId = existingPlaylist.id, videoId = videoUrl,
                                                title = title, thumbnailUrl = thumbnailUrl,
                                                channelName = channelName
                                            )
                                        )
                                        Toast.makeText(context, "Added to existing '${existingPlaylist.name}' playlist", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val newId = db.playlistDao()
                                        .insertPlaylist(PlaylistEntity(name = trimmedName)).toInt()
                                    db.playlistDao().insertVideo(
                                        PlaylistVideoEntity(
                                            playlistId = newId, videoId = videoUrl,
                                            title = title, thumbnailUrl = thumbnailUrl,
                                            channelName = channelName
                                        )
                                    )
                                    Toast.makeText(context, "Created & added", Toast.LENGTH_SHORT).show()
                                }
                                newPlaylistName = ""
                                showCreatePlaylistNameDialog = false
                                onDismiss()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreatePlaylistNameDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) { Text("Cancel") }
            }
        )
    }
}
