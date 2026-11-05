package com.videhub.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videhub.viewmodel.DownloadViewModel
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

    PlaylistDownloadQualityDialog(
        playlistTitle = title.ifBlank { "Video" },
        onDismiss = onDismiss,
        onSelectOption = { isAudioOnly, quality ->
            if (isAudioOnly) {
                val bestAudio = streamInfo?.audioStreams
                    ?.filter { !it.content.isNullOrBlank() && it.format?.name?.contains("M4A", ignoreCase = true) == true }
                    ?.maxByOrNull { it.averageBitrate }
                    ?: streamInfo?.audioStreams
                        ?.filter { !it.content.isNullOrBlank() }
                        ?.maxByOrNull { it.averageBitrate }
                downloadViewModel.prepareAndStartDownload(
                    context = context,
                    videoUrl = videoUrl,
                    title = title,
                    thumbnailUrl = thumbnailUrl,
                    streamInfo = streamInfo,
                    resolution = null,
                    bitrate = bestAudio?.averageBitrate ?: 128,
                    formatName = bestAudio?.format?.name ?: "m4a",
                    isAudioOnly = true
                )
            } else {
                val chosenRes = when (quality) {
                    "144p" -> {
                        val p144 = streamInfo?.videoStreams
                            ?.filter { !it.isVideoOnly && !it.content.isNullOrBlank() }
                            ?.find { it.resolution?.contains("144") == true || (it.height ?: 0) <= 144 }
                            ?: streamInfo?.videoStreams
                                ?.filter { !it.isVideoOnly && !it.content.isNullOrBlank() }
                                ?.minByOrNull { it.height ?: 0 }
                        p144?.resolution ?: "144p"
                    }
                    "360p" -> {
                        val p360 = streamInfo?.videoStreams
                            ?.filter { !it.isVideoOnly && !it.content.isNullOrBlank() }
                            ?.find { it.resolution?.contains("360") == true || (it.height ?: 0) == 360 }
                            ?: streamInfo?.videoStreams
                                ?.filter { !it.isVideoOnly && !it.content.isNullOrBlank() }
                                ?.minByOrNull { it.height ?: 0 }
                        p360?.resolution ?: "360p"
                    }
                    "720p", "High Quality" -> {
                        val p720 = streamInfo?.videoStreams
                            ?.filter { !it.isVideoOnly && !it.content.isNullOrBlank() }
                            ?.find { it.resolution?.contains("720") == true || (it.height ?: 0) == 720 }
                            ?: streamInfo?.videoStreams
                                ?.filter { !it.isVideoOnly && !it.content.isNullOrBlank() }
                                ?.maxByOrNull { it.height ?: 0 }
                        p720?.resolution ?: "720p"
                    }
                    else -> {
                        val high = streamInfo?.videoStreams
                            ?.filter { !it.isVideoOnly && !it.content.isNullOrBlank() }
                            ?.maxByOrNull { it.height ?: 0 }
                        high?.resolution ?: "720p"
                    }
                }
                downloadViewModel.prepareAndStartDownload(
                    context = context,
                    videoUrl = videoUrl,
                    title = title,
                    thumbnailUrl = thumbnailUrl,
                    streamInfo = streamInfo,
                    resolution = chosenRes,
                    bitrate = null,
                    formatName = null,
                    isAudioOnly = false
                )
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

@Composable
fun PlaylistDownloadQualityDialog(
    playlistTitle: String = "Playlist",
    onDismiss: () -> Unit,
    onSelectOption: (isAudioOnly: Boolean, quality: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download Options", style = MaterialTheme.typography.titleLarge) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Select download quality",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                DownloadItem(
                    title = "720p",
                    subtitle = "HD video with audio",
                    icon = Icons.Default.Movie,
                    onClick = {
                        onSelectOption(false, "720p")
                        onDismiss()
                    }
                )

                DownloadItem(
                    title = "360p",
                    subtitle = "Standard video quality (saves space)",
                    icon = Icons.Default.Movie,
                    onClick = {
                        onSelectOption(false, "360p")
                        onDismiss()
                    }
                )

                DownloadItem(
                    title = "144p",
                    subtitle = "Lowest data usage (fastest video download)",
                    icon = Icons.Default.Movie,
                    onClick = {
                        onSelectOption(false, "144p")
                        onDismiss()
                    }
                )

                DownloadItem(
                    title = "Audio Only",
                    subtitle = "High quality M4A / MP3 music audio",
                    icon = Icons.Default.Audiotrack,
                    onClick = {
                        onSelectOption(true, "Audio")
                        onDismiss()
                    }
                )
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
