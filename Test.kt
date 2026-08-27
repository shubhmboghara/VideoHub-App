fun main() {
    val description = "Some text before\nLyrics:\nToh phir aao mujhko sataao\nToh phir aao mujhko rulaao\n\nDil badal bane aankhein behne lagi"
    val lower = description.lowercase()
    val marker = "lyrics:"
    val idx = lower.indexOf(marker)
    val sub = description.substring(idx + marker.length)
    println("SUB:" + sub)
    
    val collectedLines = mutableListOf<String>()
    var consecutiveEmpty = 0

    for (rawLine in sub.lines()) {
        val line = rawLine.trim()
        if (line.isEmpty()) {
            consecutiveEmpty++
            if (consecutiveEmpty >= 3) break // allow up to 2 empty lines (e.g. verse spacing)
            collectedLines.add("")
        } else {
            consecutiveEmpty = 0
            val lowerLine = line.lowercase()
            // Stop if we hit promotional links, copyright, credits or social links
            if (lowerLine.startsWith("http://") || lowerLine.startsWith("https://") ||
                lowerLine.startsWith("www.") || lowerLine.startsWith("follow ") || lowerLine.startsWith("follow:") ||
                lowerLine.startsWith("subscribe") || lowerLine.startsWith("produced by") ||
                lowerLine.startsWith("directed by") || lowerLine.startsWith("music video by") ||
                lowerLine.startsWith("copyright") || lowerLine.startsWith("all rights reserved") ||
                lowerLine.startsWith("(c)") || lowerLine.startsWith("(p)") ||
                lowerLine.startsWith("stream / download") || lowerLine.startsWith("connect with") ||
                lowerLine.startsWith("listen on") || lowerLine.startsWith("buy on")) {
                break
            }
            collectedLines.add(line)
        }
    }

    while (collectedLines.isNotEmpty() && collectedLines.last().isBlank()) {
        collectedLines.removeAt(collectedLines.lastIndex)
    }
    
    // Remove leading empty lines
    while (collectedLines.isNotEmpty() && collectedLines.first().isBlank()) {
        collectedLines.removeAt(0)
    }

    println("RESULT:\n" + collectedLines.joinToString("\n").trim())
}
