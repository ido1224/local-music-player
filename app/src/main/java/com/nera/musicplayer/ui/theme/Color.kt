package com.nera.musicplayer.ui.theme

import androidx.compose.ui.graphics.Color

// Bold EDM/bass-adjacent accent trio. Kept as fixed brand colors and layered on top of dynamic
// color when available, rather than letting wallpaper-derived Material You colors replace them -
// see Theme.kt.
val NeraPurple = Color(0xFFB026FF)
val NeraPurpleContainerDark = Color(0xFF3A0A66)
val NeraCyan = Color(0xFF22F0FF)
val NeraCyanContainerDark = Color(0xFF00474D)
val NeraMagenta = Color(0xFFFF2E9F)
val NeraMagentaContainerDark = Color(0xFF5C0035)

val NeraPurpleContainerLight = Color(0xFFE9D1FF)
val NeraCyanContainerLight = Color(0xFFC6F7FA)
val NeraMagentaContainerLight = Color(0xFFFFD3EA)

// Dark base: near-black with a faint purple tint rather than pure neutral gray, so the accent
// colors read as "against a dark base" rather than a generic Material dark theme.
val NeraDarkBackground = Color(0xFF0E0A14)
val NeraDarkSurface = Color(0xFF15101E)
val NeraDarkSurfaceVariant = Color(0xFF241C33)

// Light base: deliberately low-contrast - soft lavender-white surfaces and a dark plum-gray for
// text/icons instead of the stark white/pure-black a default Material light scheme would use.
val NeraLightBackground = Color(0xFFF3EEFA)
val NeraLightSurface = Color(0xFFF9F5FD)
val NeraLightSurfaceVariant = Color(0xFFE7DEF2)
val NeraLightOnSurface = Color(0xFF3A3346)
val NeraLightOnSurfaceVariant = Color(0xFF5B5268)
