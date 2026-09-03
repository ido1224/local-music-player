package com.nera.musicplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

private sealed class Screen {
    data object Home : Screen()
    data object Playlists : Screen()
    data object PlaylistDetail : Screen()
    data object Settings : Screen()
    data object NowPlaying : Screen()
}

@Composable
fun NeraApp(
    playerViewModel: PlayerViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
    playlistViewModel: PlaylistViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    when (screen) {
        Screen.Home -> HomeScreen(
            playerViewModel = playerViewModel,
            libraryViewModel = libraryViewModel,
            settingsViewModel = settingsViewModel,
            onOpenPlaylists = { screen = Screen.Playlists },
            onOpenSettings = { screen = Screen.Settings },
            onOpenNowPlaying = { screen = Screen.NowPlaying }
        )

        Screen.Playlists -> PlaylistsScreen(
            playlistViewModel = playlistViewModel,
            onBack = { screen = Screen.Home },
            onOpenPlaylist = { playlist ->
                playlistViewModel.selectPlaylist(playlist)
                screen = Screen.PlaylistDetail
            }
        )

        Screen.PlaylistDetail -> PlaylistDetailScreen(
            playerViewModel = playerViewModel,
            libraryViewModel = libraryViewModel,
            playlistViewModel = playlistViewModel,
            onBack = { screen = Screen.Playlists },
            onOpenNowPlaying = { screen = Screen.NowPlaying }
        )

        Screen.Settings -> SettingsScreen(
            settingsViewModel = settingsViewModel,
            onBack = { screen = Screen.Home }
        )

        Screen.NowPlaying -> NowPlayingScreen(
            playerViewModel = playerViewModel,
            onBack = { screen = Screen.Home }
        )
    }
}
