package com.videhub.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.videhub.data.BackupSummary
import com.videhub.data.DataBackupManager
import com.videhub.data.ImportMode
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PendingImportPayload(
    val jsonText: String,
    val summary: BackupSummary
)

@Stable
class DataBackupController internal constructor(
    val currentSummary: BackupSummary?,
    val isBusy: Boolean,
    val lastStatusBanner: String?,
    val openExportOptions: () -> Unit,
    val startImportPicker: () -> Unit,
    val dismissStatusBanner: () -> Unit
)

@Composable
fun rememberDataBackupController(
    onDataImported: (() -> Unit)? = null
): DataBackupController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentSummary by remember { mutableStateOf<BackupSummary?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var includePreferencesOnExport by remember { mutableStateOf(true) }
    var pendingImport by remember { mutableStateOf<PendingImportPayload?>(null) }
    var selectedImportMode by remember { mutableStateOf(ImportMode.MERGE) }
    var restorePreferencesOnImport by remember { mutableStateOf(true) }
    var lastStatusBanner by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        currentSummary = try {
            DataBackupManager.getCurrentDataSummary(context)
        } catch (_: Exception) {
            null
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            val result = DataBackupManager.exportToUri(
                context = context,
                uri = uri,
                includePreferences = includePreferencesOnExport
            )
            isBusy = false
            if (result.success) {
                val msg = "Backup saved (${result.summary.subscriptionsCount} subs, ${result.summary.playlistsCount} playlists, ${result.summary.historyCount} history, ${result.summary.watchLaterCount} watch later)"
                lastStatusBanner = msg
                Toast.makeText(context, "Backup exported successfully", Toast.LENGTH_SHORT).show()
            } else {
                val err = result.errorMessage ?: "Export failed"
                lastStatusBanner = err
                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            val inspected = DataBackupManager.inspectUri(context, uri)
            isBusy = false
            if (inspected == null) {
                Toast.makeText(
                    context,
                    "Could not read a valid VideoHub backup from that file",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                selectedImportMode = ImportMode.MERGE
                restorePreferencesOnImport = inspected.second.hasPreferences
                pendingImport = PendingImportPayload(
                    jsonText = inspected.first,
                    summary = inspected.second
                )
            }
        }
    }

    if (showExportDialog) {
        val summary = currentSummary
        AlertDialog(
            onDismissRequest = { if (!isBusy) showExportDialog = false },
            title = {
                Text("Export Library Data", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Save a portable backup file to transfer your data to a new device or restore later:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (summary != null) {
                        BackupCountsGrid(summary = summary)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includePreferencesOnExport = !includePreferencesOnExport }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includePreferencesOnExport,
                            onCheckedChange = { includePreferencesOnExport = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Include theme & playback preferences",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isBusy = true
                                val created = DataBackupManager.createShareableBackupFile(
                                    context = context,
                                    includePreferences = includePreferencesOnExport
                                )
                                isBusy = false
                                showExportDialog = false
                                if (created != null) {
                                    val (file, _) = created
                                    val fileUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, fileUri)
                                        putExtra(Intent.EXTRA_SUBJECT, "VideoHub Library Backup")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Send Backup to New Device")
                                    )
                                    lastStatusBanner = "Backup file ready to send (${file.name})"
                                } else {
                                    Toast.makeText(context, "Could not prepare backup file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("export_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share to Another Device")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportDialog = false
                        createDocumentLauncher.launch(DataBackupManager.defaultExportFileName())
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("export_save_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save File")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExportDialog = false },
                    enabled = !isBusy,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingImport?.let { payload ->
        val dateText = remember(payload.summary.exportedAtMs) {
            if (payload.summary.exportedAtMs > 0L) {
                SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
                    .format(Date(payload.summary.exportedAtMs))
            } else {
                "Previous backup"
            }
        }
        AlertDialog(
            onDismissRequest = { if (!isBusy) pendingImport = null },
            title = { Text("Import Backup Data", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Backup from $dateText",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    BackupCountsGrid(summary = payload.summary)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = "How should this backup be applied?",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isBusy) { selectedImportMode = ImportMode.MERGE }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedImportMode == ImportMode.MERGE,
                            onClick = { if (!isBusy) selectedImportMode = ImportMode.MERGE }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Merge with current data", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "Adds missing subscriptions, playlists, history & watch later without duplicates",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isBusy) { selectedImportMode = ImportMode.REPLACE }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedImportMode == ImportMode.REPLACE,
                            onClick = { if (!isBusy) selectedImportMode = ImportMode.REPLACE }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Replace current data", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "Clears current subscriptions, playlists, history & watch later first",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (payload.summary.hasPreferences) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isBusy) {
                                    restorePreferencesOnImport = !restorePreferencesOnImport
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = restorePreferencesOnImport,
                                onCheckedChange = { if (!isBusy) restorePreferencesOnImport = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Restore theme & playback settings",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isBusy = true
                            val result = DataBackupManager.importFromJsonString(
                                context = context,
                                jsonText = payload.jsonText,
                                mode = selectedImportMode,
                                restorePreferences = restorePreferencesOnImport,
                                restoreDownloadsMetadata = false
                            )
                            isBusy = false
                            pendingImport = null
                            refreshTrigger++
                            if (result.success) {
                                val msg = result.buildSummaryMessage()
                                lastStatusBanner = msg
                                onDataImported?.invoke()
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            } else {
                                val err = result.errorMessage ?: "Import failed"
                                lastStatusBanner = err
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("confirm_import_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isBusy) "Importing..." else "Import")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingImport = null },
                    enabled = !isBusy,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    return remember(currentSummary, isBusy, lastStatusBanner) {
        DataBackupController(
            currentSummary = currentSummary,
            isBusy = isBusy,
            lastStatusBanner = lastStatusBanner,
            openExportOptions = { showExportDialog = true },
            startImportPicker = {
                openDocumentLauncher.launch(
                    arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
                )
            },
            dismissStatusBanner = { lastStatusBanner = null }
        )
    }
}

@Composable
private fun BackupCountsGrid(summary: BackupSummary) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BackupCountLine(Icons.Default.Subscriptions, "Subscriptions", summary.subscriptionsCount)
            BackupCountLine(
                Icons.AutoMirrored.Filled.PlaylistPlay,
                "Playlists",
                summary.playlistsCount,
                extra = if (summary.playlistVideosCount > 0) " (${summary.playlistVideosCount} videos)" else ""
            )
            BackupCountLine(Icons.Default.History, "Watch History", summary.historyCount)
            BackupCountLine(Icons.Default.Bookmark, "Watch Later", summary.watchLaterCount)
            BackupCountLine(Icons.Default.ThumbUp, "Liked Videos", summary.likedVideosCount)
        }
    }
}

@Composable
private fun BackupCountLine(
    icon: ImageVector,
    label: String,
    count: Int,
    extra: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = "$count$extra",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DataTransferCard(
    controller: DataBackupController,
    modifier: Modifier = Modifier
) {
    val summary = controller.currentSummary
    val summaryLine = remember(summary) {
        if (summary == null) {
            "Subscriptions, playlists, history, watch later & likes"
        } else {
            "${summary.subscriptionsCount} subs · ${summary.playlistsCount} playlists · ${summary.historyCount} history · ${summary.watchLaterCount} watch later"
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Backup & Transfer Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = summaryLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (controller.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = controller.openExportOptions,
                    enabled = !controller.isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("export_data_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = "Export data",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Data")
                }

                Button(
                    onClick = controller.startImportPicker,
                    enabled = !controller.isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("import_data_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Import data",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Data")
                }
            }

            AnimatedVisibility(visible = controller.lastStatusBanner != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = controller.lastStatusBanner ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = controller.dismissStatusBanner) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}
