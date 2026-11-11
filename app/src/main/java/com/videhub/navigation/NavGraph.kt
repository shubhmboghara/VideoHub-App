package com.videhub.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.videhub.ui.screens.*

@Composable
fun NavGraph(
    navController: NavHostController,
    sharedViewModel: com.videhub.viewmodel.MainViewModel,
    isDarkMode: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onNavigateToPlayer: (String, String, String, Boolean, Boolean) -> Unit,
    mediaPlayer: androidx.media3.common.Player? = null
) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                sharedViewModel = sharedViewModel,
                onVideoClick = { url, t, thumb -> onNavigateToPlayer(url, t, thumb, false, false) },
                onChannelClick = { ch -> navController.navigate(Screen.Channel.createRoute(ch)) },
                onSearchClick = { navController.navigate(Screen.Explore.route) },
                onPlaylistClick = { playlistUrl -> navController.navigate(Screen.OnlinePlaylist.createRoute(playlistUrl)) }
            )
        }
        composable(Screen.Explore.route) {
            ExploreScreen(
                sharedViewModel = sharedViewModel,
                onVideoClick = { url, t, thumb -> onNavigateToPlayer(url, t, thumb, false, false) },
                onChannelClick = { ch -> navController.navigate(Screen.Channel.createRoute(ch)) },
                onPlaylistClick = { playlistUrl -> navController.navigate(Screen.OnlinePlaylist.createRoute(playlistUrl)) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Subscriptions.route) {
            SubscriptionsScreen(
                sharedViewModel = sharedViewModel,
                onVideoClick = { url, t, thumb -> onNavigateToPlayer(url, t, thumb, false, false) },
                onChannelClick = { ch -> navController.navigate(Screen.Channel.createRoute(ch)) },
                onSearchClick = { navController.navigate(Screen.Explore.route) }
            )
        }
        composable(Screen.Downloads.route) {
            DownloadsScreen(
                navController = navController,
                onVideoClick = { videoUrl, t, thumb, isMusic -> onNavigateToPlayer(videoUrl, t, thumb, isMusic, false) }
            )
        }
        composable(Screen.Library.route) {
            LibraryScreen(
                sharedViewModel = sharedViewModel,
                onNavigate = { route -> navController.navigate(route) },
                onWatchLaterClick = { navController.navigate("watch_later") },
                onSearchClick = { navController.navigate(Screen.Explore.route) }
            )
        }
        composable(
            route = Screen.Player.ROUTE,
            arguments = listOf(
                navArgument("videoUrl") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("thumbnailUrl") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("isMusicMode") { type = NavType.BoolType; defaultValue = false },
                navArgument("isFullscreen") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val videoUrl = backStackEntry.arguments?.getString("videoUrl")
            val title = backStackEntry.arguments?.getString("title")
            val thumbnailUrl = backStackEntry.arguments?.getString("thumbnailUrl")
            val isMusicMode = backStackEntry.arguments?.getBoolean("isMusicMode") ?: false
            val isFullscreen = backStackEntry.arguments?.getBoolean("isFullscreen") ?: false
            PlayerScreen(
                sharedViewModel = sharedViewModel,
                videoUrl = videoUrl ?: "",
                title = title ?: "",
                thumbnailUrl = thumbnailUrl ?: "",
                onBack = { navController.popBackStack() },
                onVideoPlay = { url, t, thumb, isM, isFs -> onNavigateToPlayer(url, t, thumb, isM, isFs) },
                initialFullscreen = isFullscreen,
                onChannelClick = { ch -> navController.navigate(Screen.Channel.createRoute(ch)) },
                forceMusicMode = isMusicMode,
                mediaPlayer = mediaPlayer
            )
        }
        composable(
            route = Screen.OnlinePlaylist.ROUTE,
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            OnlinePlaylistScreen(
                playlistUrl = url,
                onVideoClick = { videoUrl, t, thumb -> onNavigateToPlayer(videoUrl, t, thumb, false, false) }
            )
        }
        composable(
            route = Screen.DownloadedPlaylistDetail.ROUTE,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            if (id.equals("WL", ignoreCase = true)) {
                com.videhub.ui.screens.WatchLaterScreen(
                    onVideoClick = { videoUrl, t, thumb -> onNavigateToPlayer(videoUrl, t, thumb, false, false) }
                )
            } else {
                DownloadedPlaylistDetailScreen(
                    playlistId = id,
                    onVideoClick = { videoUrl, t, thumb, isMusic -> onNavigateToPlayer(videoUrl, t, thumb, isMusic, false) }
                )
            }
        }
        composable(
            route = Screen.Channel.ROUTE,
            arguments = listOf(navArgument("channelId") { type = NavType.StringType })
        ) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
            ChannelScreen(
                sharedViewModel = sharedViewModel,
                channelId = channelId,
                onBack = { navController.popBackStack() },
                onVideoClick = { videoUrl, t, thumb -> onNavigateToPlayer(videoUrl, t, thumb, false, false) },
                onPlaylistClick = { playlistUrl -> navController.navigate(Screen.OnlinePlaylist.createRoute(playlistUrl)) },
                onAboutClick = {
                    val encodedId = try { java.net.URLEncoder.encode(channelId, "UTF-8") } catch (e: Exception) { channelId }
                    navController.navigate("channel_bio?channelId=$encodedId")
                },
                onSearchClick = { navController.navigate(Screen.Explore.route) }
            )
        }
        composable(
            route = "channel_bio?channelId={channelId}",
            arguments = listOf(navArgument("channelId") {
                type = NavType.StringType
                defaultValue = ""
            })
        ) { backStackEntry ->
            val navChannelId = backStackEntry.arguments?.getString("channelId") ?: ""
            ChannelAboutScreen(
                sharedViewModel = sharedViewModel,
                channelId = navChannelId.ifBlank { sharedViewModel.currentChannelId },
                onBack = { navController.popBackStack() },
                onChannelClick = { ch -> navController.navigate(Screen.Channel.createRoute(ch)) },
                onSearchClick = { navController.navigate(Screen.Explore.route) }
            )
        }
        composable("history") {
            HistoryScreen(
                onVideoClick = { videoUrl, t, thumb -> onNavigateToPlayer(videoUrl, t, thumb, false, false) }
            )
        }
        composable("watch_later") {
            com.videhub.ui.screens.WatchLaterScreen(
                onVideoClick = { videoUrl, t, thumb -> onNavigateToPlayer(videoUrl, t, thumb, false, false) },
                onSearchClick = { navController.navigate(Screen.Explore.route) }
            )
        }
        composable("playlists") {
            PlaylistsScreen(
                onPlaylistClick = { id -> navController.navigate("custom_playlist/$id") },
                onNavigate = { route -> navController.navigate(route) },
                onVideoClick = { videoUrl, t, thumb -> onNavigateToPlayer(videoUrl, t, thumb, false, false) }
            )
        }
        composable(
            route = "custom_playlist/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.IntType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("playlistId") ?: 0
            PlaylistDetailScreen(
                playlistId = id,
                onVideoClick = { videoUrl, t, thumb -> onNavigateToPlayer(videoUrl, t, thumb, false, false) },
                onSearchClick = { navController.navigate(Screen.Explore.route) }
            )
        }
        composable("liked_videos") {
            LikedVideosScreen(
                onVideoClick = { videoUrl, t, thumb -> onNavigateToPlayer(videoUrl, t, thumb, false, false) }
            )
        }
        composable("proxy_settings") {
            ProxySettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("about") {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
