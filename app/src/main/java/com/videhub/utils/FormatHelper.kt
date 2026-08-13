package com.videhub.utils

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object FormatHelper {
    private val titleCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val dateCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val countCache = java.util.concurrent.ConcurrentHashMap<Long, String>()
    
    private val spacesRegex = Regex("\\s+")

    fun cleanDisplayTitle(rawTitle: String): String {
        return titleCache.getOrPut(rawTitle) {
            rawTitle
                .replace("_", " ")  // underscores → spaces
                .replace(spacesRegex, " ") // collapse multiple spaces
                .trim()
        }
    }

    fun formatCount(count: Long): String {
        val formatFn = { value: Double, suffix: String ->
            val formatted = String.format(java.util.Locale.US, "%.1f", value)
            if (formatted.endsWith(".0")) {
                formatted.substringBefore(".") + suffix
            } else {
                formatted + suffix
            }
        }
        return countCache.getOrPut(count) { 
        when {
            count >= 1_000_000_000 -> formatFn(count / 1_000_000_000.0, "b")
            count >= 1_000_000 -> formatFn(count / 1_000_000.0, "m")
            count >= 1_000 -> formatFn(count / 1_000.0, "k")
            else -> count.toString()
        }
        }
    }
    
    
    private val subCountCache = java.util.concurrent.ConcurrentHashMap<Long, String>()
    
    fun formatSubscriberCount(count: Long): String {
        return subCountCache.getOrPut(count) {
            val formatFn = { value: Double, suffix: String ->
                val formatted = String.format(java.util.Locale.US, "%.1f", value)
                if (formatted.endsWith(".0")) {
                    formatted.substringBefore(".") + suffix
                } else {
                    formatted + suffix
                }
            }
            val countText = when {
                count >= 10_000_000 -> formatFn(count / 10_000_000.0, "Cr")
                count >= 100_000 -> formatFn(count / 100_000.0, "L")
                count >= 1_000 -> formatFn(count / 1_000.0, "K")
                else -> count.toString()
            }
            "$countText subscribers"
        }
    }

    fun formatDate(dateString: String?): String {
        if (dateString.isNullOrBlank()) return ""
        return dateCache.getOrPut(dateString) {
            if (!dateString.contains("T") && !dateString.startsWith("DateWrapper")) {
                return@getOrPut dateString
            }
        try {
            var isoString = dateString
            if (dateString.startsWith("DateWrapper{instant=")) {
                isoString = dateString.substringAfter("instant=").substringBefore(",").trimEnd('}')
            }
            val instant = java.time.Instant.parse(isoString)
            val now = java.time.Instant.now()
            val diff = java.time.Duration.between(instant, now)
            val seconds = diff.seconds
            
            return when {
                seconds < 60 -> "just now"
                seconds < 3600 -> {
                    val mins = seconds / 60
                    if (mins == 1L) "1 minute ago" else "$mins minutes ago"
                }
                seconds < 86400 -> {
                    val hours = seconds / 3600
                    if (hours == 1L) "1 hour ago" else "$hours hours ago"
                }
                seconds < 2592000 -> {
                    val days = seconds / 86400
                    if (days == 1L) "1 day ago" else "$days days ago"
                }
                seconds < 31536000 -> {
                    val months = seconds / 2592000
                    if (months == 1L) "1 month ago" else "$months months ago"
                }
                else -> {
                    val years = seconds / 31536000
                    if (years == 1L) "1 year ago" else "$years years ago"
                }
            }
        } catch (e: Exception) {
            dateString
        }
        }
    }
}
