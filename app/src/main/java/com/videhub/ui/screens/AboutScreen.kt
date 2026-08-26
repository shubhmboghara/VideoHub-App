package com.videhub.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videhub.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    var hasAnimated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40)
        hasAnimated = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "About VideoHub",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Navigate back" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ── Logo with bounce-in scale + fade, glow ring behind it ──────────
            val logoScale by animateFloatAsState(
                targetValue = if (hasAnimated) 1f else 0.7f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "logoScale"
            )
            val logoAlpha by animateFloatAsState(
                targetValue = if (hasAnimated) 1f else 0f,
                animationSpec = tween(durationMillis = 400),
                label = "logoAlpha"
            )

            Box(
                modifier = Modifier
                    .size(136.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                                )
                            )
                        )
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = "VideoHub logo",
                    modifier = Modifier.size(96.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            StaggeredEntrance(visible = hasAnimated, delayMillis = 60) {
                Text(
                    text = "VideoHub",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            StaggeredEntrance(visible = hasAnimated, delayMillis = 100) {
                Text(
                    text = "The ultimate private streaming & music player",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            StaggeredEntrance(visible = hasAnimated, delayMillis = 140) {
                Text(
                    text = "VideoHub is a clean, lightweight, ad-free streaming engine designed for private audio and video consumption. No ads, no trackers, and no Google sign-in required.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Core Philosophy Card ──
            StaggeredEntrance(visible = hasAnimated, delayMillis = 180) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Privacy & Freedom First",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enjoy your media without interruptions, forced telemetry, or paywalls. Everything is stored locally on your device with complete data ownership.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Section 1: Music & Synced Lyrics ──
            FeatureCategoryHeader(
                title = "Music & Synced Lyrics",
                visible = hasAnimated,
                delayMillis = 220
            )

            val musicFeatures = listOf(
                FeatureData(Icons.Default.MusicNote, "Music mode", "Dedicated Music Player", "Seamless audio-only mode with full background & notification controls."),
                FeatureData(Icons.Default.Mic, "Lyrics icon", "Synchronized Karaoke Lyrics", "Multi-tiered lyrics engine with live auto-scroll, YouTube CC & description parser."),
                FeatureData(Icons.Default.GraphicEq, "Equalizer icon", "Audio Pitch & Equalizer", "Fine-tune frequencies, bass boost, and pitch correction for optimal listening.")
            )

            FeatureList(features = musicFeatures, visible = hasAnimated, startDelay = 240)

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section 2: Video Playback & Controls ──
            FeatureCategoryHeader(
                title = "Video Playback & Smart Controls",
                visible = hasAnimated,
                delayMillis = 340
            )

            val videoFeatures = listOf(
                FeatureData(Icons.Default.PlayArrow, "Ad-free play", "100% Ad-Free Streaming", "Watch videos with zero pre-roll, mid-roll, or sponsored banners."),
                FeatureData(Icons.Default.FlashOn, "Shorts icon", "Shorts Feed & Search Carousel", "Scroll seamless short-form vertical reels and explore Shorts directly inside search results."),
                FeatureData(Icons.Default.PictureInPictureAlt, "PiP icon", "Picture-in-Picture & Background", "Multitask freely while keeping your video or audio playing seamlessly."),
                FeatureData(Icons.Default.Speed, "Speed icon", "Speed & Pitch Controls", "Variable playback speed from 0.25x up to 3.0x with pitch-preservation."),
                FeatureData(Icons.Default.Timer, "Sleep timer", "Custom Sleep Timer", "Schedule automatic playback shutoff when listening to sleep tracks or podcasts.")
            )

            FeatureList(features = videoFeatures, visible = hasAnimated, startDelay = 360)

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section 3: Downloads & Offline Media ──
            FeatureCategoryHeader(
                title = "Offline Downloads & Media",
                visible = hasAnimated,
                delayMillis = 480
            )

            val downloadFeatures = listOf(
                FeatureData(Icons.Default.Download, "Download icon", "Clean Video & Audio Downloads", "Save your favorite streams for offline listening and watching wherever you are."),
                FeatureData(Icons.AutoMirrored.Filled.PlaylistPlay, "Playlist download", "Batch Playlist Downloads", "Queue and download entire collections with reliable background management."),
                FeatureData(Icons.Default.Subtitles, "Offline subtitles", "Offline Captions & Lyrics", "Keeps lyrics and subtitles bundled right alongside your saved media.")
            )

            FeatureList(features = downloadFeatures, visible = hasAnimated, startDelay = 500)

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section 4: Subtitles & Translation ──
            FeatureCategoryHeader(
                title = "Closed Captions & Translation",
                visible = hasAnimated,
                delayMillis = 620
            )

            val captionFeatures = listOf(
                FeatureData(Icons.Default.Subtitles, "Captions icon", "Multilingual Closed Captions", "Official YouTube CC, auto-generated streams, and community subtitles."),
                FeatureData(Icons.Default.Translate, "Translate icon", "Live Subtitle Translation", "On-device real-time neural translation into your preferred native language.")
            )

            FeatureList(features = captionFeatures, visible = hasAnimated, startDelay = 640)

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section 5: Privacy & Local Data ──
            FeatureCategoryHeader(
                title = "Privacy & Local Organization",
                visible = hasAnimated,
                delayMillis = 720
            )

            val privacyFeatures = listOf(
                FeatureData(Icons.Default.Subscriptions, "Subscriptions", "Local Channel Subscriptions", "Subscribe and organize favorite creators without signing into a Google account."),
                FeatureData(Icons.AutoMirrored.Filled.PlaylistPlay, "Custom playlists", "Custom Playlists & Queue", "Create playlists, reorder items, shuffle, and manage ongoing queues locally."),
                FeatureData(Icons.Default.History, "History icon", "Watch & Search History", "Instant offline history and search suggestions with one-tap clear privacy controls."),
                FeatureData(Icons.Default.Security, "Privacy icon", "No Tracking & No Telemetry", "100% private. Zero telemetry, zero analytics, zero data sharing.")
            )

            FeatureList(features = privacyFeatures, visible = hasAnimated, startDelay = 740)

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Version 1.0.0 (Release Build)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Crafted with care by Shubham Boghara.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            val uriHandler = LocalUriHandler.current
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PressableSocialButton(
                    iconRes = R.drawable.ic_github_logo,
                    text = "GitHub",
                    iconTint = MaterialTheme.colorScheme.onSurface
                ) {
                    uriHandler.openUri("https://github.com/shubhmboghara/")
                }
                PressableSocialButton(
                    iconRes = R.drawable.ic_linkedin_logo,
                    text = "LinkedIn",
                    iconTint = Color(0xFF0077B5)
                ) {
                    uriHandler.openUri("https://in.linkedin.com/in/shubham-boghara-60b4a1343")
                }
                PressableSocialButton(
                    iconRes = R.drawable.ic_instagram_logo,
                    text = "Instagram",
                    iconTint = Color(0xFFE4405F)
                ) {
                    uriHandler.openUri("https://www.instagram.com/shubhambogharadotcom?igsh=NDFpNnY2MXJ5czVi&igsi=NDFpNnY2MXJ5czVi")
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

private data class FeatureData(
    val icon: ImageVector,
    val contentDescription: String,
    val title: String,
    val subtitle: String
)

@Composable
private fun FeatureCategoryHeader(
    title: String,
    visible: Boolean,
    delayMillis: Int
) {
    StaggeredEntrance(visible = visible, delayMillis = delayMillis) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun FeatureList(
    features: List<FeatureData>,
    visible: Boolean,
    startDelay: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        features.forEachIndexed { index, item ->
            StaggeredEntrance(
                visible = visible,
                delayMillis = startDelay + (index * 40),
                fromLeft = true
            ) {
                FeatureItem(
                    icon = item.icon,
                    contentDescription = item.contentDescription,
                    title = item.title,
                    subtitle = item.subtitle
                )
            }
        }
    }
}

// ── Staggered fade + slide entrance wrapper ──────────────────────────────────
@Composable
private fun StaggeredEntrance(
    visible: Boolean,
    delayMillis: Int,
    fromLeft: Boolean = false,
    content: @Composable () -> Unit
) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(delayMillis.toLong())
            show = true
        }
    }
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(animationSpec = tween(350)) + slideInVertically(
            animationSpec = tween(350),
            initialOffsetY = { if (fromLeft) 0 else it / 4 }
        )
    ) {
        content()
    }
}

// ── Feature row item ─────────────────────────────────────────────────────────
@Composable
fun FeatureItem(
    icon: ImageVector,
    contentDescription: String,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Press-animated social logo button ─────────────────────────────────────────
@Composable
private fun PressableSocialButton(
    iconRes: Int,
    text: String,
    iconTint: Color? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "buttonScale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .scale(scale)
            .semantics { role = Role.Button }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = text,
                tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
