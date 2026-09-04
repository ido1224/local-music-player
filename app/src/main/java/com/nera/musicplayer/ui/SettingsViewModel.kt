package com.nera.musicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.nera.musicplayer.ui.theme.AppTheme
import com.nera.musicplayer.ui.theme.ThemePreferences
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val themePreferences = ThemePreferences(application)
    private val libraryDisplayPreferences = LibraryDisplayPreferences(application)

    val theme: StateFlow<AppTheme> = themePreferences.theme
    val showAnalysisBadges: StateFlow<Boolean> = libraryDisplayPreferences.showAnalysisBadges
    val vinylEffectEnabled: StateFlow<Boolean> = libraryDisplayPreferences.vinylEffectEnabled
    val bassPulseGlowEnabled: StateFlow<Boolean> = libraryDisplayPreferences.bassPulseGlowEnabled

    fun setTheme(theme: AppTheme) {
        themePreferences.setTheme(theme)
    }

    fun setShowAnalysisBadges(show: Boolean) {
        libraryDisplayPreferences.setShowAnalysisBadges(show)
    }

    fun setVinylEffectEnabled(enabled: Boolean) {
        libraryDisplayPreferences.setVinylEffectEnabled(enabled)
    }

    fun setBassPulseGlowEnabled(enabled: Boolean) {
        libraryDisplayPreferences.setBassPulseGlowEnabled(enabled)
    }
}
