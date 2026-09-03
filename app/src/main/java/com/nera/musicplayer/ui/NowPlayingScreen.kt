package com.nera.musicplayer.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val VinylDiscColor = Color(0xFF161616)
private val VinylGrooveColor = Color.White
private const val VINYL_LABEL_FRACTION = 0.42f
private const val VINYL_SPINDLE_FRACTION = 0.05f

/**
 * Full-screen playback view - large album art (or a themed placeholder when the track has none),
 * title/artist, seek bar with time counter, and the same transport controls as the compact
 * PlaybackBar, just bigger. Opened by tapping a track; the compact bar keeps showing on every
 * other screen since playback state lives in the shared PlayerViewModel, not here.
 *
 * When the "Vinyl record effect" setting is on, the art renders as a spinning vinyl disc (circular
 * label, grooved ring, spindle hole) over a background that glows with colors extracted from the
 * art via the Palette API, pulsing on the track's own BPM when known (or a gentle ambient pulse
 * otherwise). Off, it's the original static square art with a plain themed background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by playerViewModel.uiState.collectAsState()
    val vinylEffectEnabled by settingsViewModel.vinylEffectEnabled.collectAsState()
    BackHandler(onBack = onBack)

    val artRotation = remember { Animatable(0f) }
    LaunchedEffect(state.isPlaying, vinylEffectEnabled) {
        if (state.isPlaying && vinylEffectEnabled) {
            artRotation.animateTo(
                targetValue = artRotation.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 20_000, easing = LinearEasing)
                )
            )
        }
    }

    var paletteColors by remember { mutableStateOf<Pair<Color, Color>?>(null) }
    LaunchedEffect(state.albumArtUri, vinylEffectEnabled) {
        val uri = state.albumArtUri
        paletteColors = if (vinylEffectEnabled && uri != null) {
            extractPaletteColors(uri.path)
        } else {
            null
        }
    }

    // Beat-synced when the track's BPM is known (analyzed at import time), otherwise a gentle
    // ambient breathing glow. Either way it freezes in place on pause, same as the disc rotation.
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(state.isPlaying, vinylEffectEnabled, state.bpm) {
        if (!state.isPlaying || !vinylEffectEnabled) return@LaunchedEffect
        val bpm = state.bpm
        if (bpm != null && bpm > 0f) {
            val beatMs = (60_000f / bpm).toLong().coerceIn(200L, 2000L)
            val attackMs = (beatMs / 6).coerceAtLeast(40L)
            val releaseMs = (beatMs - attackMs).coerceAtLeast(40L)
            while (true) {
                pulse.animateTo(1f, tween(attackMs.toInt(), easing = LinearEasing))
                pulse.animateTo(0.25f, tween(releaseMs.toInt(), easing = FastOutSlowInEasing))
            }
        } else {
            while (true) {
                pulse.animateTo(0.85f, tween(2600, easing = FastOutSlowInEasing))
                pulse.animateTo(0.15f, tween(2600, easing = FastOutSlowInEasing))
            }
        }
    }

    // Tracks the disc's on-screen center (relative to this outer Box) so the glow can be centered
    // on it directly, rather than on the screen's own center - which sits lower than the disc once
    // the title/slider/controls below it are accounted for.
    var outerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var discCenter by remember { mutableStateOf(Offset.Unspecified) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { outerCoordinates = it }
    ) {
        val colors = paletteColors
        if (vinylEffectEnabled && colors != null) {
            val (primaryColor, secondaryColor) = colors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.10f + 0.30f * pulse.value),
                                secondaryColor.copy(alpha = 0.05f + 0.12f * pulse.value),
                                Color.Transparent
                            ),
                            center = discCenter
                        )
                    )
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .onGloballyPositioned { coordinates ->
                            outerCoordinates?.let { outer ->
                                discCenter = outer.localPositionOf(
                                    coordinates,
                                    Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                                )
                            }
                        }
                        .rotate(if (vinylEffectEnabled) artRotation.value else 0f)
                        .clip(if (vinylEffectEnabled) CircleShape else RoundedCornerShape(24.dp))
                        .background(if (vinylEffectEnabled) VinylDiscColor else MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (vinylEffectEnabled) {
                        VinylGrooves(modifier = Modifier.fillMaxSize())
                        Box(
                            modifier = Modifier
                                .fillMaxSize(VINYL_LABEL_FRACTION)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            AlbumArtOrPlaceholder(state = state, iconSize = 36.dp, modifier = Modifier.fillMaxSize())
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize(VINYL_SPINDLE_FRACTION)
                                .clip(CircleShape)
                                .background(Color.Black)
                        )
                    } else {
                        AlbumArtOrPlaceholder(state = state, iconSize = 96.dp, modifier = Modifier.fillMaxSize())
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = state.trackTitle ?: "No track loaded",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!state.artist.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.artist.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Slider(
                    value = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs.toFloat() else 0f,
                    onValueChange = { fraction ->
                        if (state.durationMs > 0) playerViewModel.seekTo((fraction * state.durationMs).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatMs(state.positionMs), style = MaterialTheme.typography.labelMedium)
                    Text(formatMs(state.durationMs), style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeAccent = MaterialTheme.colorScheme.secondary
                    val inactiveAccent = MaterialTheme.colorScheme.onSurfaceVariant

                    IconButton(onClick = { playerViewModel.toggleShuffle() }) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = if (state.shuffleEnabled) "Shuffle on" else "Shuffle off",
                            tint = if (state.shuffleEnabled) activeAccent else inactiveAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = { playerViewModel.skipToPrevious() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(40.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = { playerViewModel.togglePlayPause() }) {
                            Icon(
                                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    IconButton(onClick = { playerViewModel.skipToNext() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = { playerViewModel.cycleRepeatMode() }) {
                        Icon(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = repeatLabel(state.repeatMode),
                            tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) inactiveAccent else activeAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumArtOrPlaceholder(state: PlayerUiState, iconSize: Dp, modifier: Modifier = Modifier) {
    if (state.albumArtUri != null) {
        AsyncImage(
            model = state.albumArtUri,
            contentDescription = "Album art for ${state.trackTitle ?: "current track"}",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/** Thin, low-contrast concentric circles etched into the ring outside the label, for vinyl groove texture. */
@Composable
private fun VinylGrooves(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f
        val innerRadius = maxRadius * (VINYL_LABEL_FRACTION / 2f) * 1.1f
        val outerRadius = maxRadius * 0.96f
        val grooveCount = 16
        for (i in 0 until grooveCount) {
            val t = i / (grooveCount - 1).toFloat()
            val radius = innerRadius + (outerRadius - innerRadius) * t
            drawCircle(
                color = VinylGrooveColor.copy(alpha = 0.05f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/** Decodes the track's extracted-art file and pulls two representative colors via the Palette API. Null on any failure (missing path, decode failure, no usable swatch). */
private suspend fun extractPaletteColors(filePath: String?): Pair<Color, Color>? = withContext(Dispatchers.IO) {
    if (filePath == null) return@withContext null
    val bitmap = try {
        BitmapFactory.decodeFile(filePath)
    } catch (_: Exception) {
        null
    } ?: return@withContext null

    val palette = Palette.from(bitmap).generate()
    bitmap.recycle()

    val primary = palette.vibrantSwatch?.rgb
        ?: palette.dominantSwatch?.rgb
        ?: palette.mutedSwatch?.rgb
        ?: return@withContext null
    val secondary = palette.darkVibrantSwatch?.rgb
        ?: palette.darkMutedSwatch?.rgb
        ?: palette.mutedSwatch?.rgb
        ?: primary
    Color(primary) to Color(secondary)
}
