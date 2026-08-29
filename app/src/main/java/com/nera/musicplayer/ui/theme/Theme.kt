package com.nera.musicplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val NeraDarkColors = darkColorScheme(
    primary = NeraPurple,
    onPrimary = Color.White,
    primaryContainer = NeraPurpleContainerDark,
    onPrimaryContainer = NeraPurple,
    secondary = NeraCyan,
    onSecondary = Color(0xFF00232B),
    secondaryContainer = NeraCyanContainerDark,
    onSecondaryContainer = NeraCyan,
    tertiary = NeraMagenta,
    onTertiary = Color.White,
    tertiaryContainer = NeraMagentaContainerDark,
    onTertiaryContainer = NeraMagenta,
    background = NeraDarkBackground,
    onBackground = Color(0xFFEAE0F5),
    surface = NeraDarkSurface,
    onSurface = Color(0xFFEAE0F5),
    surfaceVariant = NeraDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBBEDE)
)

private val NeraLightColors = lightColorScheme(
    primary = Color(0xFF7B00E0),
    onPrimary = Color.White,
    primaryContainer = NeraPurpleContainerLight,
    onPrimaryContainer = Color(0xFF3A0A66),
    secondary = Color(0xFF00838F),
    onSecondary = Color.White,
    secondaryContainer = NeraCyanContainerLight,
    onSecondaryContainer = Color(0xFF00474D),
    tertiary = Color(0xFFC2007E),
    onTertiary = Color.White,
    tertiaryContainer = NeraMagentaContainerLight,
    onTertiaryContainer = Color(0xFF5C0035)
)

/**
 * Material You dynamic color where available (API 31+), with the brand accent trio
 * (purple/cyan/magenta) forced back on top of it - dynamic color governs the neutral
 * surfaces/background so the app still feels tied to the system wallpaper, but the accent
 * colors used for buttons/badges/highlights stay fixed rather than drifting with whatever
 * hue the user's wallpaper happens to produce.
 */
@Composable
fun NeraMusicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            val brand = if (darkTheme) NeraDarkColors else NeraLightColors
            dynamic.copy(
                primary = brand.primary,
                onPrimary = brand.onPrimary,
                primaryContainer = brand.primaryContainer,
                onPrimaryContainer = brand.onPrimaryContainer,
                secondary = brand.secondary,
                onSecondary = brand.onSecondary,
                secondaryContainer = brand.secondaryContainer,
                onSecondaryContainer = brand.onSecondaryContainer,
                tertiary = brand.tertiary,
                onTertiary = brand.onTertiary,
                tertiaryContainer = brand.tertiaryContainer,
                onTertiaryContainer = brand.onTertiaryContainer
            )
        }
        darkTheme -> NeraDarkColors
        else -> NeraLightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
