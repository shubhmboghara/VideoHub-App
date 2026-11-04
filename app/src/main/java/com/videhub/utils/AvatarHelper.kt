package com.videhub.utils

import com.videhub.extractor.ExtractorHelper
import org.schabi.newpipe.extractor.Image

object AvatarHelper {

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

    /**
     * Resolves the actual channel avatar directly from YouTube via ChannelInfo,
     * guaranteeing that the DP is a true channel avatar and not a video thumbnail.
     */
    suspend fun resolveRealChannelAvatar(channelIdOrUrl: String): String? {
        if (channelIdOrUrl.isBlank()) return null
        val url = when {
            channelIdOrUrl.startsWith("http") -> channelIdOrUrl
            channelIdOrUrl.startsWith("@") -> "https://www.youtube.com/$channelIdOrUrl"
            else -> "https://www.youtube.com/channel/$channelIdOrUrl"
        }
        return try {
            val info = ExtractorHelper.getChannelInfo(url)
            getBestAvatarUrl(info.avatars)
        } catch (_: Exception) {
            null
        }
    }
}
