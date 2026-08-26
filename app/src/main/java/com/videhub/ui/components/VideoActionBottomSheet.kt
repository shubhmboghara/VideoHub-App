package com.videhub.ui.components
import androidx.compose.ui.graphics.Color

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.videhub.QueueManager
import com.videhub.PlayQueueItem
import com.videhub.data.AppDatabase
import com.videhub.data.entity.PlaylistEntity
import com.videhub.data.entity.PlaylistVideoEntity
import com.videhub.extractor.ExtractorHelper
import com.videhub.service.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionBottomSheet(
    videoUrl: String,
    title: String,
    thumbnailUrl: String,
    channelName: String,
    viewCount: Long = -1,
    uploadDate: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var streamInfo by remember { mutableStateOf<org.schabi.newpipe.extractor.stream.StreamInfo?>(null) }

    if (showDownloadDialog) {
        VideoDownloadDialog(
            videoUrl = videoUrl,
            title = title,
            thumbnailUrl = thumbnailUrl,
            streamInfo = streamInfo,
            onDismiss = { showDownloadDialog = false }
        )
    }

    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            videoUrl = videoUrl,
            title = title,
            thumbnailUrl = thumbnailUrl,
            channelName = channelName,
            onDismiss = {
                showAddToPlaylist = false
                onDismiss()
            }
        )
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Start Song Radio (Endless Mix)") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        com.videhub.audio.RadioManager.startRadio(videoUrl, title, channelName, thumbnailUrl)
                        Toast.makeText(context, "📻 Infinite Song Radio Started!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )

                ListItem(
                    headlineContent = { Text("Play next in queue") },
                    leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    modifier = Modifier.clickable {
                        scope.launch {
                            try {
                                val isLocal = videoUrl.startsWith("/") || videoUrl.startsWith("file://") || videoUrl.startsWith("content://")
                                val qi = if (isLocal) {
                                    PlayQueueItem(
                                        url = videoUrl,
                                        title = title,
                                        uploaderName = channelName,
                                        thumbnailUrl = thumbnailUrl,
                                        duration = -1
                                    )
                                } else {
                                    val streamInfo = withContext(Dispatchers.IO) { ExtractorHelper.getStreamInfo(videoUrl) }
                                    PlayQueueItem(
                                        url = streamInfo.url ?: videoUrl,
                                        title = streamInfo.name.ifBlank { title },
                                        uploaderName = streamInfo.uploaderName ?: channelName,
                                        thumbnailUrl = streamInfo.thumbnails?.firstOrNull()?.url ?: thumbnailUrl,
                                        duration = streamInfo.duration
                                    )
                                }
                                QueueManager.playNext(qi)
                                Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not queue video.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                
                ListItem(
                    headlineContent = { Text("Save to Watch Later") },
                    leadingContent = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                    modifier = Modifier.clickable {
                        scope.launch {
                            db.watchLaterDao().insert(
                                com.videhub.data.entity.WatchLaterEntity(
                                    videoId = videoUrl,
                                    title = title,
                                    thumbnailUrl = thumbnailUrl ?: "",
                                    channelName = channelName
                                )
                            )
                            Toast.makeText(context, "Saved to Watch Later", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                )
                
                ListItem(
                    headlineContent = { Text("Save to playlist") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showAddToPlaylist = true
                    }
                )

                ListItem(
                    headlineContent = { Text("Share") },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = "Share") },
                    modifier = Modifier.clickable {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, if (title.isNotBlank()) "$title\n$videoUrl" else videoUrl)
                            putExtra(Intent.EXTRA_SUBJECT, title)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                        onDismiss()
                    }
                )
                
                ListItem(
                    headlineContent = { Text("Download") },
                    leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showDownloadDialog = true
                        scope.launch(Dispatchers.Main) {
                            try {
                                val fresh = withContext(Dispatchers.IO) { ExtractorHelper.getStreamInfo(videoUrl) }
                                streamInfo = fresh
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not fetch video info.", Toast.LENGTH_SHORT).show()
                                showDownloadDialog = false
                            }
                        }
                    }
                )

            }
        }
    }
}
