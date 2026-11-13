package com.videhub.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.videhub.viewmodel.PlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlinePlaylistScreen(
    playlistUrl: String,
    onVideoClick: (String, String, String) -> Unit,
    viewModel: PlaylistViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playlistItems by viewModel.playlistItems.collectAsStateWithLifecycle()
    var showDownloadDialog by remember { mutableStateOf(false) }
    var selectedVideoForDownload by remember { mutableStateOf<org.schabi.newpipe.extractor.stream.StreamInfoItem?>(null) }

    LaunchedEffect(playlistUrl) {
        viewModel.loadOnlinePlaylist(context, playlistUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.playlistInfo?.name ?: "Playlist",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    val onBackPressedDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                    IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.playlistInfo != null) {
                        IconButton(onClick = {
                            viewModel.saveOnlinePlaylistToLocal(context)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Save to My Playlists", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showDownloadDialog = true }) {
                            Icon(Icons.Default.Download, contentDescription = "Download all")
                        }
                    }
                }
            )
        }
    ) { padding ->
        
        if (showDownloadDialog) {
            com.videhub.ui.components.PlaylistDownloadQualityDialog(
                playlistTitle = uiState.playlistInfo?.name?.ifBlank { "Playlist" } ?: "Playlist",
                onDismiss = { showDownloadDialog = false },
                onSelectOption = { isAudioOnly, quality ->
                    showDownloadDialog = false
                    viewModel.downloadEntirePlaylist(context, isAudioOnly = isAudioOnly)
                }
            )
        }

        if (selectedVideoForDownload != null) {
            val vid = selectedVideoForDownload!!
            val thumbUrl = vid.thumbnails.firstOrNull()?.url ?: ""
            com.videhub.ui.components.VideoDownloadDialog(
                videoUrl = vid.url,
                title = vid.name,
                thumbnailUrl = thumbUrl,
                streamInfo = null,
                onDismiss = { selectedVideoForDownload = null }
            )
        }

        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.loadOnlinePlaylist(context, playlistUrl, isRefresh = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading && playlistItems.isEmpty()) {
                com.videhub.ui.components.OnlinePlaylistShimmer()
            } else if (uiState.error != null && playlistItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.loadOnlinePlaylist(context, playlistUrl, isRefresh = true) }) {
                            Text("Retry")
                        }
                    }
                }
            } else if (playlistItems.isEmpty()) {
                com.videhub.ui.components.EmptyState(
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    title = "No videos in this playlist",
                    message = "This playlist has no available videos.",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    if (playlistItems.isNotEmpty()) {
                        val firstVideoThumb = playlistItems.first().item.thumbnails.firstOrNull()?.url ?: ""
                        item {
                            com.videhub.ui.components.PlaylistHeader(
                                title = uiState.playlistInfo?.name ?: "Playlist",
                                subtitle = "Online Playlist • ${com.videhub.utils.FormatHelper.formatCount(playlistItems.size.toLong())} videos",
                                thumbnailUrl = firstVideoThumb,
                                isPlayAllEnabled = playlistItems.isNotEmpty(),
                                isShuffleEnabled = playlistItems.isNotEmpty(),
                                onPlayAllClick = {
                                    if (playlistItems.isNotEmpty()) {
                                        com.videhub.QueueManager.clear()
                                        for (i in 1 until playlistItems.size) {
                                            val qItem = playlistItems[i].item
                                            com.videhub.QueueManager.enqueue(
                                                com.videhub.PlayQueueItem(
                                                    url = qItem.url,
                                                    title = qItem.name,
                                                    uploaderName = qItem.uploaderName ?: "",
                                                    thumbnailUrl = qItem.thumbnails.firstOrNull()?.url ?: ""
                                                )
                                            )
                                        }
                                        val first = playlistItems.first().item
                                        onVideoClick(first.url, first.name, first.thumbnails.firstOrNull()?.url ?: "")
                                    }
                                },
                                onShuffleClick = {
                                    if (playlistItems.isNotEmpty()) {
                                        com.videhub.QueueManager.clear()
                                        val shuffled = playlistItems.shuffled()
                                        for (i in 1 until shuffled.size) {
                                            val qItem = shuffled[i].item
                                            com.videhub.QueueManager.enqueue(
                                                com.videhub.PlayQueueItem(
                                                    url = qItem.url,
                                                    title = qItem.name,
                                                    uploaderName = qItem.uploaderName ?: "",
                                                    thumbnailUrl = qItem.thumbnails.firstOrNull()?.url ?: ""
                                                )
                                            )
                                        }
                                        val first = shuffled.first().item
                                        onVideoClick(first.url, first.name, first.thumbnails.firstOrNull()?.url ?: "")
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    itemsIndexed(playlistItems, key = { index, state -> "${state.item.url}_$index" }) { index, state ->
                        val video = state.item
                        val thumbUrl = video.thumbnails.firstOrNull()?.url ?: ""
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(32.dp).padding(start = 8.dp)
                            )
                            com.videhub.ui.components.VideoRowItem(
                                modifier = Modifier.weight(1f),
                                videoUrl = video.url,
                                title = video.name,
                                uploaderName = video.uploaderName ?: "",
                                thumbnailUrl = thumbUrl,
                                duration = video.duration,
                                viewCount = video.viewCount,
                                onClick = {
                                    com.videhub.QueueManager.clear()
                                    if (index in 0 until playlistItems.lastIndex) {
                                        for (i in (index + 1)..playlistItems.lastIndex) {
                                            val qItem = playlistItems[i].item
                                            com.videhub.QueueManager.enqueue(
                                                com.videhub.PlayQueueItem(
                                                    url = qItem.url,
                                                    title = qItem.name,
                                                    uploaderName = qItem.uploaderName ?: "",
                                                    thumbnailUrl = qItem.thumbnails.firstOrNull()?.url ?: ""
                                                )
                                            )
                                        }
                                    }
                                    onVideoClick(video.url, video.name, thumbUrl)
                                },
                                trailingIcon = {
                                    if (state.isDownloaded) {
                                        Icon(
                                            imageVector = Icons.Default.DownloadDone,
                                            contentDescription = "Downloaded",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (state.isDownloading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        IconButton(
                                            onClick = {
                                                selectedVideoForDownload = video
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Download",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
