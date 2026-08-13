package com.videhub.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.videhub.PlayQueueItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import com.videhub.service.QueueDownloadService

@Composable
fun PlayerScreenDialogs(
    showAddToPlaylistDialog: Boolean,
    onDismissAddToPlaylistDialog: () -> Unit,
    showDownloadDialog: Boolean,
    onDismissDownloadDialog: () -> Unit,
    showVideoActionBottomSheet: Boolean,
    onDismissVideoActionBottomSheet: () -> Unit,
    showQueueDownloadDialog: Boolean,
    onDismissQueueDownloadDialog: () -> Unit,
    
    originalVideoId: String,
    videoUrl: String,
    title: String,
    thumbnailUrl: String,
    channelName: String,
    streamInfo: StreamInfo?,
    queue: List<PlayQueueItem>,
    context: Context
) {
    if (showAddToPlaylistDialog) {
        com.videhub.ui.components.AddToPlaylistDialog(
            videoUrl = originalVideoId,
            title = title,
            thumbnailUrl = thumbnailUrl,
            channelName = channelName,
            onDismiss = onDismissAddToPlaylistDialog
        )
    }

    if (showDownloadDialog) {
        com.videhub.ui.components.VideoDownloadDialog(
            videoUrl = videoUrl,
            title = title,
            thumbnailUrl = thumbnailUrl,
            streamInfo = streamInfo,
            onDismiss = onDismissDownloadDialog
        )
    }

    if (showVideoActionBottomSheet) {
        com.videhub.ui.components.VideoActionBottomSheet(
            videoUrl = videoUrl,
            title = title,
            thumbnailUrl = thumbnailUrl,
            channelName = channelName,
            onDismiss = onDismissVideoActionBottomSheet
        )
    }

    if (showQueueDownloadDialog) {
        AlertDialog(
            onDismissRequest = onDismissQueueDownloadDialog,
            title = { Text("Download all videos in your queue?") },
            text = { Text("The highest available quality will be downloaded for each video.") },
            confirmButton = {
                TextButton(onClick = {
                    onDismissQueueDownloadDialog()
                    val urls = ArrayList(queue.map { it.url })
                    val titles = ArrayList(queue.map { it.title })
                    val intent = Intent(context, QueueDownloadService::class.java).apply {
                        putStringArrayListExtra("urls", urls)
                        putStringArrayListExtra("titles", titles)
                    }
                    androidx.core.content.ContextCompat.startForegroundService(context, intent)
                }) {
                    Text("Download All")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissQueueDownloadDialog) {
                    Text("Cancel")
                }
            }
        )
    }
}
