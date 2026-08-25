package com.videhub.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import java.util.regex.Pattern

/**
 * A reusable Composable that parses plain text with URLs, timestamps, and hashtags,
 * styling links distinctively and allowing users to click to open them or perform actions.
 */
@Composable
fun ClickableDescriptionText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    linkColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onSeekToSeconds: ((Long) -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null,
    onUrlClick: ((String) -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    val annotatedString = remember(text, color, linkColor) {
        buildAnnotatedString {
            // Combined regex for URLs, timestamps (e.g. 1:23, 01:23:45), and hashtags (#music)
            val urlPattern = "https?://[a-zA-Z0-9.-]+(?:\\.[a-zA-Z]{2,})+(?:/[^\\s]*)?"
            val timestampPattern = "\\b(?:(\\d{1,2}):)?([0-5]?\\d):([0-5]\\d)\\b"
            val hashtagPattern = "#[\\p{L}0-9_]+"

            val combinedRegex = Pattern.compile("($urlPattern)|($timestampPattern)|($hashtagPattern)")
            val matcher = combinedRegex.matcher(text)

            var lastIndex = 0
            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                val match = matcher.group()

                // Append unlinked text before this match
                if (start > lastIndex) {
                    append(text.substring(lastIndex, start))
                }

                when {
                    match.startsWith("http://") || match.startsWith("https://") -> {
                        val tag = "URL"
                        pushStringAnnotation(tag = tag, annotation = match)
                        pushStyle(
                            SpanStyle(
                                color = linkColor,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                        append(match)
                        pop()
                        pop()
                    }
                    match.startsWith("#") -> {
                        val tag = "HASHTAG"
                        pushStringAnnotation(tag = tag, annotation = match)
                        pushStyle(
                            SpanStyle(
                                color = linkColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        append(match)
                        pop()
                        pop()
                    }
                    else -> {
                        // Timestamp like 01:23 or 1:02:30
                        val tag = "TIMESTAMP"
                        pushStringAnnotation(tag = tag, annotation = match)
                        pushStyle(
                            SpanStyle(
                                color = linkColor,
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                        append(match)
                        pop()
                        pop()
                    }
                }
                lastIndex = end
            }

            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = overflow,
        onClick = { offset ->
            annotatedString.getStringAnnotations(offset, offset).firstOrNull()?.let { annotation ->
                when (annotation.tag) {
                    "URL" -> {
                        val url = annotation.item
                        if (onUrlClick != null) {
                            onUrlClick(url)
                        } else {
                            openUrlSafely(context, uriHandler, url)
                        }
                    }
                    "TIMESTAMP" -> {
                        val seconds = parseTimestampToSeconds(annotation.item)
                        if (seconds >= 0 && onSeekToSeconds != null) {
                            onSeekToSeconds(seconds)
                        }
                    }
                    "HASHTAG" -> {
                        onHashtagClick?.invoke(annotation.item)
                    }
                }
            }
        }
    )
}

/**
 * Safely opens a URL in a browser or external app
 */
fun openUrlSafely(
    context: Context,
    uriHandler: androidx.compose.ui.platform.UriHandler? = null,
    url: String
) {
    try {
        var cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        
        if (uriHandler != null) {
            try {
                uriHandler.openUri(cleanUrl)
                return
            } catch (_: Exception) {}
        }
        
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open link: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Parses timestamps like "1:23" or "01:23:45" to seconds
 */
private fun parseTimestampToSeconds(timestamp: String): Long {
    val parts = timestamp.split(":").mapNotNull { it.toLongOrNull() }
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> -1
    }
}
