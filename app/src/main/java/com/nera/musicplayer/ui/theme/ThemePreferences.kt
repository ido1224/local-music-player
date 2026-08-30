package com.nera.musicplayer.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppTheme {
    DARK,
    LIGHT
}

private const val PREFS_NAME = "nera_settings"
private const val KEY_THEME = "app_theme"

/** Plain SharedPreferences is enough for a single persisted enum choice - no need for DataStore. */
class ThemePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        if (prefs.getString(KEY_THEME, null) == AppTheme.LIGHT.name) AppTheme.LIGHT else AppTheme.DARK
    )
    val theme: StateFlow<AppTheme> = _theme

    fun setTheme(theme: AppTheme) {
        _theme.value = theme
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }
}
