package com.videhub.ui.screens

import androidx.compose.runtime.Composable

/**
 * Replaced by the unified PlaylistDetailScreen.
 * Forwards to PlaylistDetailScreen for backwards compatibility.
 */
@Composable
fun DownloadedPlaylistDetailScreen(
    playlistId: String,
    onVideoClick: (String, String, String, Boolean) -> Unit
) {
    PlaylistDetailScreen(
        playlistId = playlistId,
        onVideoPlay = onVideoClick
    )
}
