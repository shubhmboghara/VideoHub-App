package com.videhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.videhub.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelAboutScreen(
    sharedViewModel: MainViewModel,
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit
) {
    val info = sharedViewModel.channelInfoCache
    val aboutInfo = sharedViewModel.channelAboutInfoCache
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
        if (info == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Channel info not available.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // ── Description ──────────────────────────────────────────
            val rawDescription = info.description?.trim() ?: ""
            if (rawDescription.isNotBlank()) {
                Column {
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleMedium,
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

                    Text(
                        text = cleanDescription.ifBlank { "No description available." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // ── Links (from getTags() — where YouTube puts social links) ──
            // getTags() returns things like:
            // ["https://instagram.com/xyz", "https://twitter.com/xyz"]
            val allTags = try { info.tags ?: emptyList() } catch (e: Throwable) { emptyList() }

            // Filter tags that look like URLs (social links)
            val linkTags = androidx.compose.runtime.remember(allTags) { allTags.filter { tag ->
                tag.startsWith("http://") || tag.startsWith("https://")
            } }

            // Also check donationLinks as secondary source
            val donationList = try {
                info.donationLinks?.toList() ?: emptyList()
            } catch (e: Throwable) {
                emptyList()
            }

            // Merge both, deduplicate and filter out the current channel URL (already shown in More info)
            val aboutLinks = aboutInfo?.socialLinks ?: emptyList()
            val allLinks = (linkTags + donationList + aboutLinks)
                .distinct()
                .filter { link ->
                    val lower = link.lowercase()
                    // Filter out any YouTube links that look like a channel
                    val isYouTubeChannel = lower.contains("youtube.com/channel/") || 
                                           lower.contains("youtube.com/@") ||
                                           lower.contains("youtube.com/c/") ||
                                           lower.contains("youtube.com/user/")
                    !isYouTubeChannel
                }

            if (allLinks.isNotEmpty()) {
                Column {
                    Text(
                        text = "Links",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    allLinks.forEach { link ->
                        val lower = link.lowercase()
                        val isYouTubeChannel = lower.contains("youtube.com/channel/") || 
                                               lower.contains("youtube.com/@")

                        // Map URL to branded icon + label
                        val (iconUrl, platformName) = when {
                            lower.contains("instagram.com") ->
                                "https://cdn-icons-png.flaticon.com/512/1384/1384063.png" to "Instagram"
                            lower.contains("twitter.com") || lower.contains("x.com") ->
                                "https://cdn-icons-png.flaticon.com/512/5969/5969020.png" to "Twitter / X"
                            lower.contains("facebook.com") ->
                                "https://cdn-icons-png.flaticon.com/512/1384/1384053.png" to "Facebook"
                            lower.contains("tiktok.com") ->
                                "https://cdn-icons-png.flaticon.com/512/3046/3046122.png" to "TikTok"
                            lower.contains("reddit.com") ->
                                "https://cdn-icons-png.flaticon.com/512/1384/1384066.png" to "Reddit"
                            lower.contains("discord.gg") || lower.contains("discord.com") ->
                                "https://cdn-icons-png.flaticon.com/512/5968/5968756.png" to "Discord"
                            lower.contains("twitch.tv") ->
                                "https://cdn-icons-png.flaticon.com/512/5968/5968819.png" to "Twitch"
                            lower.contains("spotify.com") ->
                                "https://cdn-icons-png.flaticon.com/512/1384/1384012.png" to "Spotify"
                            lower.contains("patreon.com") ->
                                "https://cdn-icons-png.flaticon.com/512/3670/3670157.png" to "Patreon"
                            lower.contains("youtube.com") ->
                                "https://cdn-icons-png.flaticon.com/512/1384/1384060.png" to "YouTube"
                            lower.contains("linkedin.com") ->
                                "https://cdn-icons-png.flaticon.com/512/1384/1384014.png" to "LinkedIn"
                            lower.contains("snapchat.com") ->
                                "https://cdn-icons-png.flaticon.com/512/1384/1384023.png" to "Snapchat"
                            else ->
                                null to link // generic link — show raw URL
                        }

                        // Clean display text — strip https:// and trailing slash
                        val displayText = if (iconUrl != null) {
                            val urlPath = link.substringAfter("://", "").substringAfter("/", "").substringBefore("?")
                            val segments = urlPath.split("/").filter { it.isNotBlank() }
                            val ignorePaths = setOf("add", "c", "user", "channel", "in", "p", "reel", "shorts")
                            val handleRaw = segments.firstOrNull { it.lowercase() !in ignorePaths } ?: ""
                            val handle = try { java.net.URLDecoder.decode(handleRaw, "UTF-8") } catch(e: Exception) { handleRaw }
                            
                            if (handle.isNotEmpty()) {
                                if (handle.startsWith("@")) handle else "@$handle"
                            } else {
                                platformName
                            }
                        } else {
                            link.removePrefix("https://")
                                .removePrefix("http://")
                                .trimEnd('/')
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isYouTubeChannel) {
                                        onChannelClick(link)
                                    } else {
                                        try { uriHandler.openUri(link) } catch (e: Exception) { }
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon circle
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (iconUrl != null) {
                                    AsyncImage(
                                        model = iconUrl,
                                        contentDescription = platformName,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ── More Info ─────────────────────────────────────────────
            Column {
                Text(
                    text = "More info",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Subscriber count
                val subCount = info.subscriberCount
                if (subCount >= 0) {
                    InfoRow(
                        label = formatSubscriberCount(subCount) + " subscribers"
                    )
                }

                // Total views
                if (aboutInfo != null && aboutInfo.totalViews > 0) {
                    InfoRow(
                        label = formatSubscriberCount(aboutInfo.totalViews) + " views"
                    )
                }

                // Country
                if (aboutInfo?.country != null && aboutInfo.country.isNotBlank()) {
                    val country = aboutInfo.country
                    val flag = getFlagEmoji(country)
                    InfoRow(
                        label = if (flag.isNotEmpty()) "$flag $country" else country
                    )
                }

                // Join date
                if (aboutInfo?.joinDate != null && aboutInfo.joinDate.isNotBlank()) {
                    InfoRow(
                        label = aboutInfo.joinDate // e.g. "Joined Feb 5, 2010" or we prefix it if not present
                    )
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