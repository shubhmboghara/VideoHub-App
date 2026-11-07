package com.videhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.videhub.viewmodel.MainViewModel
import com.videhub.ui.components.ClickableDescriptionText
import com.videhub.ui.components.openUrlSafely
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelAboutScreen(
    sharedViewModel: MainViewModel,
    channelId: String? = null,
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit
) {
    var info by remember { mutableStateOf(sharedViewModel.channelInfoCache) }
    var aboutInfo by remember { mutableStateOf(sharedViewModel.channelAboutInfoCache) }
    val effectiveChannelId = channelId?.ifBlank { null } ?: sharedViewModel.currentChannelId
    var isLoading by remember { mutableStateOf(info == null && !effectiveChannelId.isNullOrBlank()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(effectiveChannelId, retryTrigger) {
        if (info == null && !effectiveChannelId.isNullOrBlank()) {
            isLoading = true
            errorMessage = null
            try {
                val actualUrl = when {
                    effectiveChannelId.startsWith("http") -> effectiveChannelId
                    effectiveChannelId.startsWith("@") -> "https://www.youtube.com/$effectiveChannelId"
                    else -> "https://www.youtube.com/channel/$effectiveChannelId"
                }
                val (fetchedInfo, fetchedAbout) = kotlinx.coroutines.coroutineScope {
                    val infoDeferred = async(Dispatchers.IO) { com.videhub.extractor.ExtractorHelper.getChannelInfo(actualUrl) }
                    val aboutDeferred = async(Dispatchers.IO) { com.videhub.extractor.ExtractorHelper.getChannelAboutInfo(actualUrl) }
                    Pair(infoDeferred.await(), aboutDeferred.await())
                }
                info = fetchedInfo
                aboutInfo = fetchedAbout
                sharedViewModel.channelInfoCache = fetchedInfo
                sharedViewModel.channelAboutInfoCache = fetchedAbout
                sharedViewModel.currentChannelId = effectiveChannelId
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load channel info"
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = info?.name ?: "About",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        if (info == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = errorMessage ?: "Channel info not available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (!effectiveChannelId.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { retryTrigger++ }) {
                            Text("Retry")
                        }
                    }
                }
            }
            return@Scaffold
        }

        val nonNullInfo = info!!
        val currentAbout = aboutInfo

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // ── Description ──────────────────────────────────────────
            val rawDescription = nonNullInfo.description?.trim() ?: ""
            if (rawDescription.isNotBlank()) {
                val context = LocalContext.current
                Column {
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Strip hashtag-only lines
                    val cleanDescription = androidx.compose.runtime.remember(rawDescription) {
                        rawDescription
                            .lines()
                            .filter { line ->
                                val words = line.trim()
                                    .split("\\s+".toRegex())
                                    .filter { it.isNotBlank() }
                                words.isEmpty() || !words.all { it.startsWith("#") }
                            }
                            .joinToString("\n")
                            .trim()
                    }

                    if (cleanDescription.isNotBlank()) {
                        ClickableDescriptionText(
                            text = cleanDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            linkColor = MaterialTheme.colorScheme.primary,
                            onUrlClick = { url ->
                                openUrlSafely(context, uriHandler, url)
                            }
                        )
                    } else {
                        Text(
                            text = "No description available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            // ── More info (Image 7) ──────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "More info",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Channel URL link
                val channelUrl = nonNullInfo.url ?: ""
                if (channelUrl.isNotBlank()) {
                    val displayUrl = channelUrl.removePrefix("https://").removePrefix("http://")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try { uriHandler.openUri(channelUrl) } catch (_: Exception) {}
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Channel link",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = displayUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Country
                val country = currentAbout?.country
                if (!country.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Location",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = country,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Join date
                val joinDate = currentAbout?.joinDate
                if (!joinDate.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Joined date",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        val joinText = if (joinDate.startsWith("Joined", ignoreCase = true)) {
                            joinDate
                        } else {
                            "Joined $joinDate"
                        }
                        Text(
                            text = joinText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Total views
                val totalViews = currentAbout?.totalViews ?: -1L
                if (totalViews > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Views",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        val formattedViews = String.format(java.util.Locale.US, "%,d views", totalViews)
                        Text(
                            text = formattedViews,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

private fun formatSubscriberCount(count: Long): String {
    return when {
        count >= 1_000_000_000 -> "%.1fB".format(count / 1_000_000_000.0)
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}

private fun getFlagEmoji(countryName: String): String {
    val locale = java.util.Locale.getAvailableLocales().firstOrNull { 
        it.displayCountry.equals(countryName, ignoreCase = true) 
    }
    if (locale != null && locale.country.length == 2) {
        val firstLetter = Character.codePointAt(locale.country, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(locale.country, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }
    return ""
}