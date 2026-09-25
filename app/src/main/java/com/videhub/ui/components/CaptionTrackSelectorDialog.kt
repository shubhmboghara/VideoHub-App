package com.videhub.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun CaptionTrackSelectorDialog(
    availableTracks: List<CaptionTrack>,
    selectedTrack: CaptionTrack?,
    onTrackSelected: (CaptionTrack?) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredTracks = remember(availableTracks, searchQuery) {
        if (searchQuery.isBlank()) availableTracks
        else availableTracks.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
            it.languageTag.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Select Language",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (availableTracks.size > 5) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search languages...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    item {
                        val isOffSelected = selectedTrack == null
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTrackSelected(null) }
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = isOffSelected,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Off",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isOffSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (filteredTracks.isEmpty() && searchQuery.isNotBlank()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching languages found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    itemsIndexed(filteredTracks, key = { index, track -> "${track.url}_${track.languageTag}_$index" }) { _, track ->
                        val isSelected = selectedTrack != null &&
                                ((selectedTrack.url.isNotBlank() && selectedTrack.url == track.url) ||
                                 (selectedTrack.languageTag.equals(track.languageTag, ignoreCase = true) && selectedTrack.isAutoGenerated == track.isAutoGenerated))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTrackSelected(track) }
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = track.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
