package com.videhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import com.videhub.utils.AvatarHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Circular shimmer skeleton shown while any channel avatar is loading or resolving.
 */
@Composable
fun AvatarSkeletonBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(shimmerBrush())
    )
}

/**
 * Neutral non-textual fallback shown only if an avatar permanently fails to load
 * or no avatar source exists. Never renders channel-name initial letters.
 */
@Composable
fun AvatarNeutralFallback(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxSize(0.55f)
        )
    }
}

/**
 * Unified circular channel avatar component used across the entire app.
 * - Shows a circular shimmer skeleton (`shimmerBrush()`) while resolving or loading an image.
 * - Resolves missing channel avatars asynchronously when [channelUrl] is provided.
 * - Never renders channel-name first-letter initials.
 */
@Composable
fun ChannelAvatarImage(
    avatarUrl: String?,
    channelName: String,
    channelUrl: String? = null,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    contentDescription: String? = if (channelName.isNotBlank()) "$channelName avatar" else "Channel avatar"
) {
    val sanitizedDirectUrl = remember(avatarUrl) {
        avatarUrl?.takeIf {
            it.isNotBlank() && it != "none" && !AvatarHelper.isVideoThumbnail(it)
        }
    }

    var resolvedUrl by remember(sanitizedDirectUrl, channelUrl) {
        mutableStateOf(
            sanitizedDirectUrl ?: AvatarHelper.peekCachedAvatar(channelUrl)
        )
    }

    val shouldResolve = resolvedUrl == null &&
        !channelUrl.isNullOrBlank() &&
        !AvatarHelper.isKnownUnavailable(channelUrl)

    var isResolving by remember(resolvedUrl, channelUrl) {
        mutableStateOf(shouldResolve)
    }

    LaunchedEffect(sanitizedDirectUrl, channelUrl) {
        if (sanitizedDirectUrl != null) {
            resolvedUrl = sanitizedDirectUrl
            isResolving = false
            return@LaunchedEffect
        }

        val cached = AvatarHelper.peekCachedAvatar(channelUrl)
        if (cached != null) {
            resolvedUrl = cached
            isResolving = false
            return@LaunchedEffect
        }

        if (!channelUrl.isNullOrBlank() && !AvatarHelper.isKnownUnavailable(channelUrl)) {
            isResolving = true
            try {
                val fetched = withContext(Dispatchers.IO) {
                    AvatarHelper.resolveRealChannelAvatarCached(channelUrl)
                }
                if (!fetched.isNullOrBlank()) {
                    resolvedUrl = fetched
                }
            } finally {
                isResolving = false
            }
        } else {
            isResolving = false
        }
    }

    val effectiveUrl = sanitizedDirectUrl ?: resolvedUrl

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading || isResolving -> {
                AvatarSkeletonBox()
            }
            !effectiveUrl.isNullOrBlank() -> {
                SubcomposeAsyncImage(
                    model = effectiveUrl,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        AvatarSkeletonBox()
                    },
                    error = {
                        AvatarNeutralFallback()
                    }
                )
            }
            else -> {
                AvatarNeutralFallback()
            }
        }
    }
}
