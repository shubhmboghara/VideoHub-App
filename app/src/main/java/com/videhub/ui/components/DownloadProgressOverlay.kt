package com.videhub.ui.components

import androidx.lifecycle.compose.collectAsStateWithLifecycle



import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videhub.utils.DownloadProgressTracker

@Composable
fun DownloadProgressOverlay(modifier: Modifier = Modifier) {
    val activeDownloads by DownloadProgressTracker.activeDownloads.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = activeDownloads.isNotEmpty(),
        enter = expandVertically(animationSpec = tween(300)),
        exit = shrinkVertically(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                activeDownloads.values.forEach { progress ->
                    androidx.compose.runtime.key(progress.notificationId) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        val icon = when {
                            progress.isComplete -> Icons.Default.CheckCircle
                            progress.isError -> Icons.Default.Error
                            else -> Icons.Default.Download
                        }
                        val tint = when {
                            progress.isComplete -> MaterialTheme.colorScheme.primary
                            progress.isError -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                        
                        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = progress.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!progress.isComplete && !progress.isError) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(2.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}
