package com.nera.musicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.nera.musicplayer.ui.theme.AppTheme
import com.nera.musicplayer.ui.theme.ThemePreferences
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val themePreferences = ThemePreferences(application)

    val theme: StateFlow<AppTheme> = themePreferences.theme

    fun setTheme(theme: AppTheme) {
        themePreferences.setTheme(theme)
    }
}
