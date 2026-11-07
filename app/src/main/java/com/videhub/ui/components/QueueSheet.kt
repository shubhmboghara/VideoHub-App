package com.videhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.videhub.PlayQueueItem
import com.videhub.viewmodel.QueueViewModel
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// ── Helpers ──

private fun formatDurationString(durationSeconds: Long): String? {
    if (durationSeconds <= 0) return null
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val seconds = durationSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun formatViewCount(views: Long?): String? {
    if (views == null || views <= 0) return null
    return "${com.videhub.utils.FormatHelper.formatCount(views)} views"
}

private fun buildMetadataString(channel: String?, durationSeconds: Long, viewCount: Long?): String {
    val parts = mutableListOf<String>()
    if (!channel.isNullOrBlank()) {
        parts.add(channel)
    }
    formatDurationString(durationSeconds)?.let { parts.add(it) }
    formatViewCount(viewCount)?.let { parts.add(it) }
    return parts.joinToString(" • ")
}

// ── Header ──

@Composable
fun QueueHeader(
    count: Int,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Up next · $count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (count > 0) {
            TextButton(
                onClick = onClearClick
            ) {
                Text(
                    text = "Clear",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

// ── Row ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueRow(
    item: PlayQueueItem,
    isFirstItem: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onRemove()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.EndToStart, SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.errorContainer
                else -> Color.Transparent
            }
            val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove from queue",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        modifier = modifier
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 16:9 Thumbnail, ~120dp wide, 12dp corners, duration badge bottom-right
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.thumbnailUrl)
                            .crossfade(true)
                            .size(320)
                            .build(),
                        contentDescription = "Video thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    val durationStr = formatDurationString(item.duration)
                    if (durationStr != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = durationStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title + Metadata
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isFirstItem) {
                        Text(
                            text = "Next",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val metadata = buildMetadataString(item.uploaderName, item.duration, item.viewCount)
                    if (metadata.isNotBlank()) {
                        Text(
                            text = metadata,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Actions: Drag handle (48dp target) and Close icon (48dp target, 8dp apart)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .then(dragHandleModifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Reorder in queue",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove from queue",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ── Now Playing Pinned Row ──

@Composable
fun NowPlayingRow(
    item: PlayQueueItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.thumbnailUrl)
                        .crossfade(true)
                        .size(320)
                        .build(),
                    contentDescription = "Now playing thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                val durationStr = formatDurationString(item.duration)
                if (durationStr != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = durationStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Now playing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val metadata = buildMetadataString(item.uploaderName, item.duration, item.viewCount)
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ── Empty State ──

@Composable
fun QueueEmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
            contentDescription = "Empty queue",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Nothing up next",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Add videos with 'Play next'",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── QueueSheet (Stateless) ──

@Composable
fun QueueSheet(
    queue: List<PlayQueueItem>,
    nowPlaying: PlayQueueItem? = null,
    onVideoClick: (PlayQueueItem) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveItem: (item: PlayQueueItem, originalIndex: Int) -> Unit,
    onUndoRemove: () -> Unit,
    onClearQueue: () -> Unit,
    onUndoClear: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var showClearDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onReorder(from.index, to.index)
    }

    if (showClearDialog) {
        val countText = if (queue.size == 1) "1 video" else "${queue.size} videos"
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "Clear queue?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Clear queue? This removes $countText.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearQueue()
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val result = snackbarHostState.showSnackbar(
                                message = "Queue cleared",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                onUndoClear()
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Clear",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
        ) {
            // Header
            QueueHeader(
                count = queue.size,
                onClearClick = { showClearDialog = true }
            )

            // Pinned Now Playing row
            if (nowPlaying != null) {
                NowPlayingRow(
                    item = nowPlaying,
                    onClick = { onVideoClick(nowPlaying) }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Queue Content
            if (queue.isEmpty()) {
                QueueEmptyState(modifier = Modifier.weight(1f, fill = false))
            } else {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = queue,
                        key = { _, item -> item.id }
                    ) { index, item ->
                        ReorderableItem(
                            state = reorderableLazyListState,
                            key = item.id
                        ) { isDragging ->
                            val elevation = if (isDragging) 8.dp else 0.dp
                            QueueRow(
                                item = item,
                                isFirstItem = index == 0,
                                onClick = { onVideoClick(item) },
                                onRemove = {
                                    onRemoveItem(item, index)
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Removed from queue",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onUndoRemove()
                                        }
                                    }
                                },
                                dragHandleModifier = Modifier.draggableHandle(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .longPressDraggableHandle()
                                    .shadow(elevation, RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── QueueSheet (Stateful ViewModel overload) ──

@Composable
fun QueueSheet(
    nowPlaying: PlayQueueItem? = null,
    onVideoClick: (PlayQueueItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QueueViewModel = viewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()

    QueueSheet(
        queue = queue,
        nowPlaying = nowPlaying,
        onVideoClick = onVideoClick,
        onReorder = { from, to -> viewModel.reorder(from, to) },
        onRemoveItem = { item, _ -> viewModel.removeItem(item) },
        onUndoRemove = { viewModel.undoRemove() },
        onClearQueue = { viewModel.clearQueue() },
        onUndoClear = { viewModel.undoClear() },
        modifier = modifier,
        snackbarHostState = snackbarHostState
    )
}

// ── Previews ──

private val sampleItems = listOf(
    PlayQueueItem(
        url = "https://example.com/1",
        title = "Top 10 Kotlin Coroutines Tricks for Android Devs",
        uploaderName = "Android Central",
        thumbnailUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=400",
        duration = 225, // 3:45
        viewCount = 1_200_000, // 1.2M
        id = "id_1"
    ),
    PlayQueueItem(
        url = "https://example.com/2",
        title = "Deep Focus Coding Beats & Ambient Chillout",
        uploaderName = "Lofi Vibes",
        thumbnailUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400",
        duration = 3680, // 1:01:20
        viewCount = 450_000, // 450K
        id = "id_2"
    ),
    PlayQueueItem(
        url = "https://example.com/3",
        title = "Jetpack Compose Animation Deep Dive",
        uploaderName = "Tech Lead",
        thumbnailUrl = "https://images.unsplash.com/photo-1461749280684-dccba630e2f6?w=400",
        duration = 540, // 9:00
        viewCount = 89_000, // 89K
        id = "id_3"
    ),
    PlayQueueItem(
        url = "https://example.com/4",
        title = "Building Clean Architecture Android Apps",
        uploaderName = "Kotlin Academy",
        thumbnailUrl = "https://images.unsplash.com/photo-1498050108023-c5249f4df085?w=400",
        duration = 1240, // 20:40
        viewCount = 2_100_000, // 2.1M
        id = "id_4"
    )
)

private val sampleNowPlaying = PlayQueueItem(
    url = "https://example.com/now",
    title = "Now Playing: Modern Jetpack Compose Masterclass",
    uploaderName = "Google Developers",
    thumbnailUrl = "https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=400",
    duration = 1800,
    viewCount = 5_400_000,
    id = "id_now"
)

@Preview(name = "Queue - 2 items", showBackground = true)
@Composable
fun QueueSheetPreviewTwoItems() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        QueueSheet(
            queue = sampleItems.take(2),
            nowPlaying = sampleNowPlaying,
            onVideoClick = {},
            onReorder = { _, _ -> },
            onRemoveItem = { _, _ -> },
            onUndoRemove = {},
            onClearQueue = {},
            onUndoClear = {}
        )
    }
}

@Preview(name = "Queue - Many items", showBackground = true)
@Composable
fun QueueSheetPreviewManyItems() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        QueueSheet(
            queue = sampleItems,
            nowPlaying = sampleNowPlaying,
            onVideoClick = {},
            onReorder = { _, _ -> },
            onRemoveItem = { _, _ -> },
            onUndoRemove = {},
            onClearQueue = {},
            onUndoClear = {}
        )
    }
}

@Preview(name = "Queue - Empty", showBackground = true)
@Composable
fun QueueSheetPreviewEmpty() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        QueueSheet(
            queue = emptyList(),
            nowPlaying = sampleNowPlaying,
            onVideoClick = {},
            onReorder = { _, _ -> },
            onRemoveItem = { _, _ -> },
            onUndoRemove = {},
            onClearQueue = {},
            onUndoClear = {}
        )
    }
}
