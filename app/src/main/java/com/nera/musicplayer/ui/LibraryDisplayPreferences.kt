package com.nera.musicplayer.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val PREFS_NAME = "nera_settings"
private const val KEY_SHOW_ANALYSIS_BADGES = "show_analysis_badges"

/** Same pattern as ThemePreferences - a single persisted boolean via plain SharedPreferences. */
class LibraryDisplayPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _showAnalysisBadges = MutableStateFlow(prefs.getBoolean(KEY_SHOW_ANALYSIS_BADGES, true))
    val showAnalysisBadges: StateFlow<Boolean> = _showAnalysisBadges

    fun setShowAnalysisBadges(show: Boolean) {
        _showAnalysisBadges.value = show
        prefs.edit().putBoolean(KEY_SHOW_ANALYSIS_BADGES, show).apply()
    }
}
