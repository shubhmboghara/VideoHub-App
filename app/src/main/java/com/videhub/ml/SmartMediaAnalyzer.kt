package com.videhub.ml

/**
 * Smart Search Query Normalization & Typo Correction.
 * Pre-compiles regex patterns so search normalization runs with zero allocation overhead.
 */
object SmartMediaAnalyzer {
    private val whitespaceRegex = Regex("\\s+")

    private val replacements = listOf(
        Regex("(?i)\\bft\\.?\\b") to "feat",
        Regex("(?i)\\bvs\\.?\\b") to "vs",
        Regex("(?i)\\bost\\b") to "soundtrack",
        Regex("(?i)\\blirik\\b") to "lyrics",
        Regex("(?i)\\bletra\\b") to "lyrics",
        Regex("(?i)\\bparoles\\b") to "lyrics"
    )

    fun normalizeSearchQuery(query: String): String {
        var clean = query.trim()
        if (clean.isBlank()) return ""

        for ((regex, replacement) in replacements) {
            clean = clean.replace(regex, replacement)
        }

        return clean.replace(whitespaceRegex, " ").trim()
    }
}
