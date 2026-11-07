package com.videhub.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videhub.PlayQueueItem
import com.videhub.QueueManager
import com.videhub.data.AppDatabase
import com.videhub.data.entity.PlaylistEntity
import com.videhub.data.entity.PlaylistVideoEntity
import com.videhub.extractor.ExtractorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistActionBottomSheet(
    playlist: PlaylistInfoItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    var isBusy by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 28.dp)
        ) {
            // 1. Play next in queue (Matching Image 1)
            ListItem(
                headlineContent = {
                    Text(
                        text = "Play next in queue",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                leadingContent = {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.videhub.R.drawable.ic_play_next),
                        contentDescription = "Play next in queue",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                },
                trailingContent = {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE91E63)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "P",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isBusy) {
                        isBusy = true
                        scope.launch {
                            try {
                                val url = playlist.url ?: ""
                                if (url.isBlank()) {
                                    Toast.makeText(context, "Playlist URL unavailable", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                    return@launch
                                }
                                val info = withContext(Dispatchers.IO) {
                                    ExtractorHelper.getPlaylistInfo(url)
                                }
                                val streamItems = info.relatedItems.filterIsInstance<StreamInfoItem>()
                                if (streamItems.isNotEmpty()) {
                                    // Add in reverse order so that first video remains the very next item
                                    streamItems.reversed().forEach { item ->
                                        val validThumb = item.thumbnails?.firstOrNull()?.url ?: ""
                                        QueueManager.playNext(
                                            PlayQueueItem(
                                                url = item.url ?: "",
                                                title = item.name ?: "Unknown",
                                                uploaderName = item.uploaderName ?: "",
                                                thumbnailUrl = validThumb,
                                                duration = item.duration
                                            )
                                        )
                                    }
                                    Toast.makeText(
                                        context,
                                        "Added ${streamItems.size} videos next in queue",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(context, "Playlist has no videos", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not load playlist videos", Toast.LENGTH_SHORT).show()
                            } finally {
                                isBusy = false
                                onDismiss()
                            }
                        }
                    }
            )

            // 2. Save to library (Matching Image 1)
            ListItem(
                headlineContent = {
                    Text(
                        text = "Save to library",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = "Save to library",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isBusy) {
                        isBusy = true
                        scope.launch {
                            try {
                                val url = playlist.url ?: ""
                                if (url.isBlank()) {
                                    Toast.makeText(context, "Playlist URL unavailable", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                    return@launch
                                }
                                val info = withContext(Dispatchers.IO) {
                                    ExtractorHelper.getPlaylistInfo(url)
                                }
                                val targetTitle = playlist.name?.ifBlank { info.name } ?: info.name ?: "Saved Playlist"
                                val coverUrl = playlist.thumbnails?.firstOrNull()?.url
                                    ?: info.thumbnails?.firstOrNull()?.url
                                    ?: ""

                                withContext(Dispatchers.IO) {
                                    val playlistId = db.playlistDao().insertPlaylist(
                                        PlaylistEntity(
                                            name = targetTitle,
                                            customCoverUrl = coverUrl
                                        )
                                    ).toInt()

                                    val streamItems = info.relatedItems.filterIsInstance<StreamInfoItem>()
                                    streamItems.forEach { item ->
                                        db.playlistDao().insertVideo(
                                            PlaylistVideoEntity(
                                                playlistId = playlistId,
                                                videoId = item.url ?: "",
                                                title = item.name ?: "Unknown",
                                                channelName = item.uploaderName ?: "",
                                                thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: ""
                                            )
                                        )
                                    }
                                }
                                Toast.makeText(context, "Saved to library!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to save playlist", Toast.LENGTH_SHORT).show()
                            } finally {
                                isBusy = false
                                onDismiss()
                            }
                        }
                    }
            )

            // 3. Share (Matching Image 1)
            ListItem(
                headlineContent = {
                    Text(
                        text = "Share",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                leadingContent = {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.videhub.R.drawable.ic_share_youtube),
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isBusy) {
                        val url = playlist.url ?: ""
                        if (url.isNotBlank()) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Playlist"))
                        }
                        onDismiss()
                    }
            )
        }
    }
}
