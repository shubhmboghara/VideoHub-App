package com.videhub.utils

import com.videhub.extractor.ExtractorHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.schabi.newpipe.extractor.Image
import java.util.concurrent.ConcurrentHashMap

object AvatarHelper {

    private val avatarCache = ConcurrentHashMap<String, String>()
    private val failedAvatarKeys = ConcurrentHashMap<String, Boolean>()
    private val inFlightRequests = ConcurrentHashMap<String, CompletableDeferred<String?>>()
    private val resolveSemaphore = Semaphore(4)

    /**
     * Checks if the given image URL is a video thumbnail rather than a channel avatar (DP).
     * YouTube video thumbnails typically match ytimg.com/vi/ or standard default thumbnail filenames.
     */
    fun isVideoThumbnail(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.contains("/vi/") ||
               lower.contains("hqdefault") ||
               lower.contains("mqdefault") ||
               lower.contains("sddefault") ||
               lower.contains("maxresdefault") ||
               lower.contains("hq720") ||
               lower.contains("default.jpg") ||
               lower.contains("vi_webp")
    }

    /**
     * Extracts the best valid channel avatar URL from a list of Image objects,
     * ignoring any items that point to video thumbnails.
     */
    fun getBestAvatarUrl(images: List<Image>?): String? {
        if (images.isNullOrEmpty()) return null
        val valid = images.filter { it.url != null && !isVideoThumbnail(it.url) }
        return valid.maxByOrNull { it.height }?.url ?: valid.firstOrNull()?.url
    }

    private fun normalizeChannelKey(channelIdOrUrl: String): String {
        val trimmed = channelIdOrUrl.trim()
        return when {
            trimmed.startsWith("http") -> trimmed
            trimmed.startsWith("@") -> "https://www.youtube.com/$trimmed"
            else -> "https://www.youtube.com/channel/$trimmed"
        }
    }

    /**
     * Synchronously returns a previously resolved channel avatar URL from in-memory cache, if present.
     */
    fun peekCachedAvatar(channelIdOrUrl: String?): String? {
        if (channelIdOrUrl.isNullOrBlank()) return null
        val key = normalizeChannelKey(channelIdOrUrl)
        return avatarCache[key]?.takeIf { it.isNotBlank() && !isVideoThumbnail(it) }
    }

    /**
     * Returns true if resolution for this channel previously completed with no avatar available.
     */
    fun isKnownUnavailable(channelIdOrUrl: String?): Boolean {
        if (channelIdOrUrl.isNullOrBlank()) return false
        val key = normalizeChannelKey(channelIdOrUrl)
        return failedAvatarKeys.containsKey(key) && !avatarCache.containsKey(key)
    }

    /**
     * Resolves and caches the channel avatar with in-flight deduplication and concurrency limiting.
     */
    suspend fun resolveRealChannelAvatarCached(channelIdOrUrl: String): String? {
        if (channelIdOrUrl.isBlank()) return null
        val key = normalizeChannelKey(channelIdOrUrl)

        avatarCache[key]?.let { cached ->
            if (cached.isNotBlank() && !isVideoThumbnail(cached)) return cached
        }
        if (failedAvatarKeys.containsKey(key)) return null

        val existing = inFlightRequests[key]
        if (existing != null) {
            return existing.await()
        }

        val deferred = CompletableDeferred<String?>()
        val raced = inFlightRequests.putIfAbsent(key, deferred)
        if (raced != null) {
            return raced.await()
        }

        return try {
            val resolved = resolveSemaphore.withPermit {
                val info = ExtractorHelper.getChannelInfo(key)
                getBestAvatarUrl(info.avatars)?.takeIf { it.isNotBlank() && !isVideoThumbnail(it) }
            }
            if (resolved != null) {
                avatarCache[key] = resolved
                failedAvatarKeys.remove(key)
            } else {
                failedAvatarKeys[key] = true
            }
            deferred.complete(resolved)
            resolved
        } catch (e: kotlinx.coroutines.CancellationException) {
            deferred.cancel(e)
            throw e
        } catch (_: Exception) {
            failedAvatarKeys[key] = true
            deferred.complete(null)
            null
        } finally {
            inFlightRequests.remove(key, deferred)
        }
    }

    /**
     * Resolves the actual channel avatar directly from YouTube via ChannelInfo,
     * guaranteeing that the DP is a true channel avatar and not a video thumbnail.
     */
    suspend fun resolveRealChannelAvatar(channelIdOrUrl: String): String? {
        return resolveRealChannelAvatarCached(channelIdOrUrl)
    }
}

