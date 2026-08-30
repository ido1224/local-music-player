package com.nera.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nera.musicplayer.ui.NeraApp
import com.nera.musicplayer.ui.SettingsViewModel
import com.nera.musicplayer.ui.theme.AppTheme
import com.nera.musicplayer.ui.theme.NeraMusicPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val theme by settingsViewModel.theme.collectAsState()
            val isLight = theme == AppTheme.LIGHT

            // Status/nav bar icons default to light (for the dark theme); flip them dark when
            // the user picks the light theme, or they'd be invisible against the light base.
            LaunchedEffect(isLight) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = isLight
                controller.isAppearanceLightNavigationBars = isLight
            }

            // dynamicColor disabled here: the light theme is a deliberately curated
            // low-contrast look, not whatever Material You derives from the wallpaper.
            NeraMusicPlayerTheme(darkTheme = !isLight, dynamicColor = false) {
                NeraApp(settingsViewModel = settingsViewModel)
            }
        }
    }
}
