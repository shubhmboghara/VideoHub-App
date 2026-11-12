package com.videhub.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Resolves a playlist cover URL/path (local file path, file:// URI, content:// URI, or http/https URL)
 * into a model object that Coil and Palette can reliably load.
 */
fun resolveCoverImageModel(rawUrl: String?): Any? {
    if (rawUrl.isNullOrBlank()) return null
    val clean = rawUrl.trim()
    return when {
        clean.startsWith("file://") -> {
            val path = clean.removePrefix("file://")
            val file = File(path)
            if (file.exists() && file.length() > 0L) file else Uri.parse(clean)
        }
        clean.startsWith("/") -> {
            val file = File(clean)
            if (file.exists() && file.length() > 0L) file else null
        }
        clean.startsWith("content://") -> Uri.parse(clean)
        else -> clean
    }
}

/**
 * Extracts a two-tone ambient color pair (left color & right color) from a cover image
 * so playlist headers display YouTube's multi-color ambient backdrop.
 */
@Composable
fun rememberPlaylistAmbientColors(
    coverUrl: String?,
    defaultLeft: Color = Color(0xFF282238),
    defaultRight: Color = Color(0xFF381E24)
): Pair<Color, Color> {
    val context = LocalContext.current
    var targetLeft by remember { mutableStateOf(defaultLeft) }
    var targetRight by remember { mutableStateOf(defaultRight) }

    LaunchedEffect(coverUrl) {
        val model = resolveCoverImageModel(coverUrl)
        if (model == null) {
            targetLeft = Color(0xFF222222)
            targetRight = Color(0xFF1A1A1A)
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val loader = coil.Coil.imageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(model)
                    .allowHardware(false)
                    .size(320)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null && bitmap.width > 4 && bitmap.height > 4) {
                        val fullPalette = Palette.from(bitmap).generate()
                        val leftPalette = Palette.from(bitmap)
                            .setRegion(0, 0, bitmap.width / 2, bitmap.height)
                            .generate()
                        val rightPalette = Palette.from(bitmap)
                            .setRegion(bitmap.width / 2, 0, bitmap.width, bitmap.height)
                            .generate()

                        val leftSwatch = leftPalette.vibrantSwatch
                            ?: leftPalette.dominantSwatch
                            ?: leftPalette.darkVibrantSwatch
                            ?: fullPalette.dominantSwatch
                        val rightSwatch = rightPalette.vibrantSwatch
                            ?: rightPalette.dominantSwatch
                            ?: rightPalette.darkVibrantSwatch
                            ?: fullPalette.vibrantSwatch
                            ?: fullPalette.mutedSwatch

                        fun normalizeAmbientColor(rgb: Int): Color {
                            val raw = Color(rgb)
                            // Darken overly bright colors so white text and icons always stay crisp
                            val factor = if (raw.luminance() > 0.35f) 0.55f else 0.75f
                            return Color(
                                red = (raw.red * factor).coerceIn(0.06f, 0.55f),
                                green = (raw.green * factor).coerceIn(0.06f, 0.55f),
                                blue = (raw.blue * factor).coerceIn(0.06f, 0.55f),
                                alpha = 1f
                            )
                        }

                        if (leftSwatch != null) {
                            targetLeft = normalizeAmbientColor(leftSwatch.rgb)
                        }
                        if (rightSwatch != null) {
                            targetRight = normalizeAmbientColor(rightSwatch.rgb)
                        } else if (leftSwatch != null) {
                            targetRight = normalizeAmbientColor(leftSwatch.rgb)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    val animatedLeft by animateColorAsState(
        targetValue = targetLeft,
        animationSpec = tween(450),
        label = "ambientLeft"
    )
    val animatedRight by animateColorAsState(
        targetValue = targetRight,
        animationSpec = tween(450),
        label = "ambientRight"
    )
    return animatedLeft to animatedRight
}

private suspend fun copyUriToInternalPlaylistCover(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "playlist_covers").apply {
                if (!exists()) mkdirs()
            }
            val destFile = File(dir, "playlist_cover_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0L) {
                destFile.absolutePath
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

private suspend fun saveBitmapToInternalPlaylistCover(context: Context, bitmap: Bitmap): String? =
    withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "playlist_covers").apply {
                if (!exists()) mkdirs()
            }
            val destFile = File(dir, "playlist_cover_${System.currentTimeMillis()}.jpg")
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (destFile.exists() && destFile.length() > 0L) {
                destFile.absolutePath
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaylistSheet(
    initialTitle: String,
    initialCoverUrl: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, coverUrl: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var coverUrl by rememberSaveable { mutableStateOf(initialCoverUrl) }
    var showImageSourceDialog by rememberSaveable { mutableStateOf(false) }

    // Launcher for selecting photo from photo library / gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val savedPath = copyUriToInternalPlaylistCover(context, uri)
                if (!savedPath.isNullOrBlank()) {
                    coverUrl = savedPath
                } else {
                    coverUrl = uri.toString()
                }
            }
        }
    }

    // Launcher for taking photo with camera
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            scope.launch {
                val savedPath = saveBitmapToInternalPlaylistCover(context, bitmap)
                if (!savedPath.isNullOrBlank()) {
                    coverUrl = savedPath
                } else {
                    Toast.makeText(context, "Could not save photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color(0xFF1E1E1E),
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.Gray.copy(alpha = 0.5f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .safeBottomSheetPadding(extraBottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: "Edit" Title + Close Button (X)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Edit playlist",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playlist Cover Image (16:9) with Edit Pencil Button Overlay
            val resolvedModel = remember(coverUrl) { resolveCoverImageModel(coverUrl) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2C2C2C))
                    .clickable { showImageSourceDialog = true },
                contentAlignment = Alignment.Center
            ) {
                if (resolvedModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(resolvedModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Playlist Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                }

                // Pencil button for photo edit
                Surface(
                    onClick = { showImageSourceDialog = true },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.72f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Cover Image",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title Input Field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                    focusedContainerColor = Color(0xFF2B2B2B),
                    unfocusedContainerColor = Color(0xFF252525)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save Button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title.trim(), coverUrl)
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.White.copy(alpha = 0.4f),
                    disabledContentColor = Color.Black.copy(alpha = 0.4f)
                )
            ) {
                Text(
                    text = "Save",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }

    // Photo Source Choice Dialog (Choose from library / Take photo)
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            containerColor = Color(0xFF222222),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Text(
                    text = "Change playlist cover",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showImageSourceDialog = false
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Choose from library",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showImageSourceDialog = false
                                cameraLauncher.launch(null)
                            }
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Take photo",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImageSourceDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.75f))
                }
            }
        )
    }
}
