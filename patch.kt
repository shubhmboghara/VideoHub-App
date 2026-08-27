    private fun extractLyricsTextFromDescription(description: String): String? {
        val lower = description.lowercase()
        val lyricMarkers = listOf(
            // English
            "lyrics:", "lyrics :", "[lyrics]", "(lyrics)", "--- lyrics ---",
            "song lyrics:", "full lyrics:", "lyrics below:", "lyrics in description",
            "read lyrics:", "lyrics -", "lyrics ♫",
            // Spanish / Portuguese
            "letra:", "letras:", "letra da música:", "letra de la canción:",
            // French
            "paroles:", "paroles de la chanson:",
            // Italian
            "testo:", "testo della canzone:",
            // German
            "songtext:", "songtexte:",
            // Indonesian / Malay
            "lirik:", "lirik lagu:",
            // Russian / Slavic
            "текст песни:", "слова песни:", "текст:",
            // Hindi / Bengali
            "बोल:", "गीत:", "গান:",
            // Japanese / Chinese / Korean
            "歌詞:", "가사:", "歌词:"
        )

        val blocks = mutableListOf<String>()

        // 1. Extract based on explicit markers
        for (marker in lyricMarkers) {
            var idx = lower.indexOf(marker)
            while (idx != -1) {
                val sub = description.substring(idx + marker.length)
                blocks.add(extractLyricsBlock(sub))
                idx = lower.indexOf(marker, idx + 1)
            }
        }

        // 2. Fallback: Check for verse/chorus structure markers
        if (blocks.isEmpty() || blocks.maxOf { it.length } < 40) {
            val structureMarkers = listOf("[verse 1]", "verse 1:", "[intro]", "intro:", "[chorus]", "chorus:")
            for (marker in structureMarkers) {
                var idx = lower.indexOf(marker)
                while (idx != -1) {
                    val sub = description.substring(idx) // keep the marker
                    blocks.add(extractLyricsBlock(sub))
                    idx = lower.indexOf(marker, idx + 1)
                }
            }
        }

        val bestBlock = blocks.maxByOrNull { it.length } ?: ""
        return if (bestBlock.length > 40) bestBlock else null
    }

    private fun extractLyricsBlock(sub: String): String {
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

        return collectedLines.joinToString("\n").trim()
    }
