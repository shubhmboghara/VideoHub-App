package com.videhub.data.model

data class PlaylistDownloadSummary(
    val playlistId: String,
    val playlistName: String?,
    val videoCount: Int,
    val totalVideos: Int = 0,
    val coverThumbnailUrl: String?
)
