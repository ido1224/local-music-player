package com.nera.musicplayer.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val PREFS_NAME = "nera_settings"
private const val KEY_SHOW_ANALYSIS_BADGES = "show_analysis_badges"
private const val KEY_VINYL_EFFECT_ENABLED = "vinyl_effect_enabled"
private const val KEY_BASS_PULSE_GLOW_ENABLED = "bass_pulse_glow_enabled"

/** Same pattern as ThemePreferences - a single persisted boolean via plain SharedPreferences. */
class LibraryDisplayPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _showAnalysisBadges = MutableStateFlow(prefs.getBoolean(KEY_SHOW_ANALYSIS_BADGES, true))
    val showAnalysisBadges: StateFlow<Boolean> = _showAnalysisBadges

    /** Gates the whole Now Playing vinyl look: rotation, the circular label/groove-ring/spindle disc, and (when also enabled) the bass-pulse background glow. */
    private val _vinylEffectEnabled = MutableStateFlow(prefs.getBoolean(KEY_VINYL_EFFECT_ENABLED, true))
    val vinylEffectEnabled: StateFlow<Boolean> = _vinylEffectEnabled

    /**
     * Independent sub-toggle for the bass-driven glow, only meaningful while [vinylEffectEnabled]
     * is on - the pulse has no disc to glow around otherwise. Defaults on (matches the pulse's
     * prior always-on-with-vinyl behavior). Forced off whenever vinyl is turned off, see
     * [setVinylEffectEnabled], so it never sits "on" while inert/unreachable in Settings.
     */
    private val _bassPulseGlowEnabled = MutableStateFlow(prefs.getBoolean(KEY_BASS_PULSE_GLOW_ENABLED, true))
    val bassPulseGlowEnabled: StateFlow<Boolean> = _bassPulseGlowEnabled

    fun setShowAnalysisBadges(show: Boolean) {
        _showAnalysisBadges.value = show
        prefs.edit().putBoolean(KEY_SHOW_ANALYSIS_BADGES, show).apply()
    }

    fun setVinylEffectEnabled(enabled: Boolean) {
        _vinylEffectEnabled.value = enabled
        prefs.edit().putBoolean(KEY_VINYL_EFFECT_ENABLED, enabled).apply()
        if (!enabled && _bassPulseGlowEnabled.value) {
            setBassPulseGlowEnabled(false)
        }
    }

    fun setBassPulseGlowEnabled(enabled: Boolean) {
        _bassPulseGlowEnabled.value = enabled
        prefs.edit().putBoolean(KEY_BASS_PULSE_GLOW_ENABLED, enabled).apply()
    }
}
