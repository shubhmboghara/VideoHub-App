package com.videhub.utils

import org.schabi.newpipe.extractor.stream.StreamInfo

object DashManifestGenerator {
    fun generateDashManifest(info: StreamInfo): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" type=\"static\" mediaPresentationDuration=\"PT${info.duration}S\">\n")
        sb.append("  <Period>\n")
        
        // Video Adaptation Set
        sb.append("    <AdaptationSet mimeType=\"video/mp4\" segmentAlignment=\"true\" startWithSAP=\"1\">\n")
        val videos = info.videoOnlyStreams?.filter { !it.content.isNullOrBlank() && it.format?.mimeType == "video/mp4" } ?: emptyList()
        videos.forEachIndexed { index, video ->
            val height = video.resolution?.replace("p", "")?.replace("fps", "")?.trim()?.toIntOrNull() ?: 0
            val bandwidth = if (height >= 1080) 4000000 else if (height >= 720) 2000000 else if (height >= 480) 1000000 else 500000
            val encodedUrl = video.content!!.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
            sb.append("      <Representation id=\"video_$index\" bandwidth=\"$bandwidth\" height=\"$height\">\n")
            sb.append("        <BaseURL>$encodedUrl</BaseURL>\n")
            sb.append("      </Representation>\n")
        }
        sb.append("    </AdaptationSet>\n")
        
        // Audio Adaptation Set
        sb.append("    <AdaptationSet mimeType=\"audio/mp4\" segmentAlignment=\"true\" startWithSAP=\"1\">\n")
        val audios = info.audioStreams?.filter { !it.content.isNullOrBlank() && it.format?.mimeType == "audio/mp4" } ?: emptyList()
        val bestAudio = audios.maxByOrNull { it.averageBitrate }
        if (bestAudio != null) {
            val encodedUrl = bestAudio.content!!.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
            sb.append("      <Representation id=\"audio_0\" bandwidth=\"${bestAudio.averageBitrate}\">\n")
            sb.append("        <BaseURL>$encodedUrl</BaseURL>\n")
            sb.append("      </Representation>\n")
        }
        sb.append("    </AdaptationSet>\n")
        
        sb.append("  </Period>\n")
        sb.append("</MPD>")
        return sb.toString()
    }
}
