package com.nera.musicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nera.musicplayer.ui.theme.AppTheme
import com.nera.musicplayer.ui.theme.ThemePreferences
import com.nera.musicplayer.update.UpdateChecker
import com.nera.musicplayer.update.UpdateCheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class UpdateCheckUiState {
    data object Idle : UpdateCheckUiState()
    data object Checking : UpdateCheckUiState()
    data object UpToDate : UpdateCheckUiState()
    data class Available(val versionName: String, val releaseUrl: String) : UpdateCheckUiState()
    data class Error(val message: String) : UpdateCheckUiState()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val themePreferences = ThemePreferences(application)
    private val libraryDisplayPreferences = LibraryDisplayPreferences(application)

    val theme: StateFlow<AppTheme> = themePreferences.theme
    val showAnalysisBadges: StateFlow<Boolean> = libraryDisplayPreferences.showAnalysisBadges
    val vinylEffectEnabled: StateFlow<Boolean> = libraryDisplayPreferences.vinylEffectEnabled
    val bassPulseGlowEnabled: StateFlow<Boolean> = libraryDisplayPreferences.bassPulseGlowEnabled

    private val _updateCheckState = MutableStateFlow<UpdateCheckUiState>(UpdateCheckUiState.Idle)
    val updateCheckState: StateFlow<UpdateCheckUiState> = _updateCheckState

    init {
        // Silent check on launch (SettingsViewModel is constructed at app start regardless of
        // which screen is shown first, same as the other ViewModels in NeraApp) - a failure here
        // just leaves the state at Idle rather than surfacing an error nobody asked to see yet.
        // The manual "Check for updates" button in Settings re-runs the same function and does
        // show its result, including errors.
        checkForUpdate()
    }

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

    fun checkForUpdate() {
        _updateCheckState.value = UpdateCheckUiState.Checking
        viewModelScope.launch {
            _updateCheckState.value = when (val result = UpdateChecker.checkForUpdate()) {
                is UpdateCheckResult.UpToDate -> UpdateCheckUiState.UpToDate
                is UpdateCheckResult.UpdateAvailable ->
                    UpdateCheckUiState.Available(result.versionName, result.releaseUrl)
                is UpdateCheckResult.Error -> UpdateCheckUiState.Error(result.message)
            }
        }
    }
}
