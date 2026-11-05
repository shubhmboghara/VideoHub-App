package com.videhub.data.model

data class PlaylistDownloadSummary(
    val playlistId: String,
    val playlistName: String?,
    val videoCount: Int,
    val totalVideos: Int = 0,
    val coverThumbnailUrl: String?,
    val state: String = "DONE",
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val currentVideoTitle: String? = null,
    val currentVideoIndex: Int = 0,
    val currentVideoProgress: Int = 0
)
