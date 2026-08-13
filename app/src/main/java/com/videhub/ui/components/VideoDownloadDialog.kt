package com.videhub.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videhub.extractor.ExtractorHelper
import com.videhub.service.DownloadService
import com.videhub.viewmodel.DownloadViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo

@Composable
fun VideoDownloadDialog(
    videoUrl: String,
    title: String,
    thumbnailUrl: String,
    streamInfo: StreamInfo?,
    onDismiss: () -> Unit,
    downloadViewModel: DownloadViewModel = viewModel()
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download", style = MaterialTheme.typography.titleLarge) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            if (streamInfo != null) {
                val progressiveVideos = streamInfo.videoStreams
                    .filter { !it.isVideoOnly && !it.content.isNullOrBlank() }
                    .sortedByDescending { it.height ?: 0 }
                    .distinctBy { it.getResolution() }

                val bestAudio = streamInfo.audioStreams
                    .filter { !it.content.isNullOrBlank() && it.format?.name?.contains("M4A", ignoreCase = true) == true }
                    ?.maxByOrNull { it.averageBitrate }
                    ?: streamInfo.audioStreams
                        .filter { !it.content.isNullOrBlank() }
                        ?.maxByOrNull { it.averageBitrate }

                val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.6).dp
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = maxDialogHeight)
                ) {
                    if (progressiveVideos.isNotEmpty()) {
                        item { 
                            Text(
                                text = "Video (with audio)", 
                                style = MaterialTheme.typography.labelLarge, 
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
                            ) 
                        }
                        itemsIndexed(progressiveVideos, key = { index, stream -> "${stream.url}_$index" }) { index, stream ->
                            DownloadItem(
                                title = "${stream.resolution}",
                                subtitle = "MP4 Format",
                                icon = Icons.Default.Movie,
                                onClick = {
                                    downloadViewModel.prepareAndStartDownload(
                                        context = context,
                                        videoUrl = videoUrl,
                                        title = title,
                                        thumbnailUrl = thumbnailUrl,
                                        streamInfo = streamInfo,
                                        resolution = stream.resolution,
                                        bitrate = null,
                                        formatName = null,
                                        isAudioOnly = false
                                    )
                                    onDismiss()
                                }
                            )
                        }
                    }

                    if (bestAudio != null) {
                        item {
                            if (progressiveVideos.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                            Text(
                                text = "Audio Only", 
                                style = MaterialTheme.typography.labelLarge, 
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
                            )
                        }
                        item {
                            DownloadItem(
                                title = "High Quality",
                                subtitle = "${bestAudio.averageBitrate} kbps",
                                icon = Icons.Default.Audiotrack,
                                onClick = {
                                    downloadViewModel.prepareAndStartDownload(
                                        context = context,
                                        videoUrl = videoUrl,
                                        title = title,
                                        thumbnailUrl = thumbnailUrl,
                                        streamInfo = streamInfo,
                                        resolution = null,
                                        bitrate = bestAudio.averageBitrate,
                                        formatName = bestAudio.format?.name,
                                        isAudioOnly = true
                                    )
                                    onDismiss()
                                }
                            )
                        }
                    }

                    if (progressiveVideos.isEmpty() && bestAudio == null) {
                        item { 
                            Text(
                                text = "No downloadable streams available.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ) 
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { 
                Text("Cancel", style = MaterialTheme.typography.labelLarge) 
            }
        }
    )
}

@Composable
private fun DownloadItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
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
}
