package com.videhub.ui.components
import androidx.compose.ui.graphics.Color

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Autorenew
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
    val autoplayFlow = remember { com.videhub.data.SettingsManager.getAutoplay(context) }
    val autoplayEnabled by autoplayFlow.collectAsState(initial = true)

    if (showDownloadDialog) {
        VideoDownloadDialog(
            videoUrl = videoUrl,
            title = title,
            thumbnailUrl = thumbnailUrl,
            streamInfo = streamInfo,
            onDismiss = {
                showDownloadDialog = false
                onDismiss()
            }
        )
    } else if (showAddToPlaylist) {
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
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeBottomSheetPadding(extraBottom = 12.dp)
                    .verticalScroll(rememberScrollState())
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
                    leadingContent = {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.videhub.R.drawable.ic_play_next),
                            contentDescription = "Play next in queue",
                            modifier = Modifier.size(24.dp)
                        )
                    },
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

                ListItem(
                    headlineContent = { Text("Autoplay next") },
                    supportingContent = { Text(if (autoplayEnabled) "Automatically play next track" else "Autoplay disabled") },
                    leadingContent = { Icon(Icons.Default.Autorenew, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = autoplayEnabled,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    com.videhub.data.SettingsManager.setAutoplay(context, checked)
                                }
                            }
                        )
                    },
                    modifier = Modifier.clickable {
                        scope.launch {
                            com.videhub.data.SettingsManager.setAutoplay(context, !autoplayEnabled)
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text("Share") },
                    leadingContent = {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.videhub.R.drawable.ic_share_youtube),
                            contentDescription = "Share video",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    modifier = Modifier.clickable {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, videoUrl)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Video"))
                        onDismiss()
                    }
                )

            }
        }
    }
}
