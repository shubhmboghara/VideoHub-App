package com.videhub.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
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
        delay(50)
        hasAnimated = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
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
            Spacer(modifier = Modifier.height(32.dp))

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
                    .size(140.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
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

            Spacer(modifier = Modifier.height(24.dp))

            StaggeredEntrance(visible = hasAnimated, delayMillis = 80) {
                Text(
                    text = "VideoHub",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            StaggeredEntrance(visible = hasAnimated, delayMillis = 140) {
                Text(
                    text = "Ad-free YouTube, your way.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            StaggeredEntrance(visible = hasAnimated, delayMillis = 200) {
                Text(
                    text = "VideoHub is a clean, private YouTube player. No ads, no trackers, no account required. Just search, watch, and enjoy your favorite videos.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            StaggeredEntrance(visible = hasAnimated, delayMillis = 260) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Philosophy",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "We believe you should be in control. No annoying ads, no hidden data collection, and no forced recommendations. Just the videos you love, whenever and however you want.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            StaggeredEntrance(visible = hasAnimated, delayMillis = 320) {
                Text(
                    text = "Key Features",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }

            val features = listOf(
                Triple(Icons.Default.PlayArrow, "Play icon", "Watch videos ad-free, even in the background"),
                Triple(Icons.Default.Search, "Search icon", "Find videos, channels, and playlists"),
                Triple(Icons.Default.Download, "Download icon", "Save videos to watch offline, in any quality"),
                Triple(Icons.AutoMirrored.Filled.PlaylistPlay, "Playlist icon", "Create your own playlists"),
                Triple(Icons.Default.Subscriptions, "Subscriptions icon", "Follow channels you love, privately"),
                Triple(Icons.Default.History, "History icon", "Easily find videos you watched before"),
                Triple(Icons.Default.VisibilityOff, "Private icon", "100% private — no tracking, no sign-in")
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                features.forEachIndexed { index, (icon, desc, title) ->
                    StaggeredEntrance(
                        visible = hasAnimated,
                        delayMillis = 360 + (index * 50),
                        fromLeft = true
                    ) {
                        FeatureItem(icon = icon, contentDescription = desc, title = title)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Version 1.0.0 (Beta)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Made with care by Shubham Boghara.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            val uriHandler = LocalUriHandler.current
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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

// ── Feature row ───────────────────────────────────────────────────────────────
@Composable
fun FeatureItem(
    icon: ImageVector,
    contentDescription: String,
    title: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
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
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
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
        modifier = Modifier.scale(scale)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = text,
                tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
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