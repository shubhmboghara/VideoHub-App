package com.videhub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.videhub.data.AppDatabase
import com.videhub.data.entity.HistoryEntity
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onVideoClick: (String, String, String) -> Unit) {
    val context = LocalContext.current
    var history by remember { mutableStateOf<List<HistoryEntity>>(emptyList()) }
    val onBackPressedDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    LaunchedEffect(Unit) {
        val db = AppDatabase.getDatabase(context)
        db.historyDao().getAllHistory().collectLatest { list ->
            history = list
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0.dp)
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            com.videhub.ui.components.EmptyState(
                icon = androidx.compose.material.icons.Icons.Default.History,
                title = "No History",
                message = "Videos you watch will appear here.",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(history, key = { index, item -> "${item.videoId}_$index" }) { index, item ->
                    com.videhub.ui.components.VideoRowItem(
                            videoUrl = item.videoId,
                            title = item.title,
                            uploaderName = item.channelName,
                            thumbnailUrl = item.thumbnailUrl,
                            viewCount = item.viewCount,
                            uploadDate = com.videhub.utils.FormatHelper.formatDate(item.uploadDate),
                            modifier = Modifier.animateItem(),
                            onClick = {
                                com.videhub.QueueManager.clear()
                                val currentIndex = history.indexOf(item)
                                if (currentIndex in 0 until history.lastIndex) {
                                    for (i in (currentIndex + 1)..history.lastIndex) {
                                        val qItem = history[i]
                                        com.videhub.QueueManager.enqueue(com.videhub.PlayQueueItem(
                                            url = qItem.videoId,
                                            title = qItem.title,
                                            uploaderName = qItem.channelName,
                                            thumbnailUrl = qItem.thumbnailUrl ?: ""
                                        ))
                                    }
                                }
                                onVideoClick(item.videoId, item.title, item.thumbnailUrl ?: "")
                            }
                        )
                    }
                }
            }
        }
    }


