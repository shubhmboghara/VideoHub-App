package com.videhub.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home_feed")
    object Explore : Screen("explore_feed")
    object Subscriptions : Screen("subscriptions_feed")
    object Library : Screen("library")
    object Downloads : Screen("downloads_tab")

    data class Player(val videoUrl: String) : Screen("player_view?videoUrl={videoUrl}&title={title}&thumbnailUrl={thumbnailUrl}&isMusicMode={isMusicMode}&isFullscreen={isFullscreen}") {
        companion object {
            const val ROUTE = "player_view?videoUrl={videoUrl}&title={title}&thumbnailUrl={thumbnailUrl}&isMusicMode={isMusicMode}&isFullscreen={isFullscreen}"
            fun createRoute(videoUrl: String, title: String = "", thumbnailUrl: String = "", isMusicMode: Boolean = false, isFullscreen: Boolean = false) = 
                "player_view?videoUrl=$videoUrl&title=$title&thumbnailUrl=$thumbnailUrl&isMusicMode=$isMusicMode&isFullscreen=$isFullscreen"
        }
    }

    data class OnlinePlaylist(val url: String) : Screen("online_playlist?url={url}") {
        companion object {
            const val ROUTE = "online_playlist?url={url}"
            fun createRoute(url: String) = "online_playlist?url=${android.net.Uri.encode(url)}"
        }
    }
    data class DownloadedPlaylistDetail(val id: String) : Screen("downloaded_playlist_detail?id={id}") {
        companion object {
            const val ROUTE = "downloaded_playlist_detail?id={id}"
            fun createRoute(id: String) = "downloaded_playlist_detail?id=${android.net.Uri.encode(id)}"
        }
    }

    data class Channel(val channelId: String) : Screen("channel?channelId={channelId}") {
        companion object {
            const val ROUTE = "channel?channelId={channelId}"
            fun createRoute(channelId: String) = "channel?channelId=${android.net.Uri.encode(channelId)}"
        }
    }
}
