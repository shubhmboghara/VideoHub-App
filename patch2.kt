        // 5. PRIORITY 4: Video Description (Timestamped or Lyrics block) as the absolute last resort
        if ((lyrics == null || !lyrics.isSynced) && !description.isNullOrBlank()) {
            val descLyrics = fromDescription(description, durationSeconds)
            if (descLyrics != null && descLyrics.lines.isNotEmpty()) {
                lyrics = descLyrics
            }
        }
