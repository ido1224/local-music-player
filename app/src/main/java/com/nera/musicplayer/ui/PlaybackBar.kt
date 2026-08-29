package com.nera.musicplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

@Composable
fun PlaybackBar(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSkipPrevious: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeat: () -> Unit = {}
) {
    Surface {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(
                text = state.trackTitle ?: "No track loaded",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Slider(
                value = if (state.durationMs > 0) {
                    state.positionMs.toFloat() / state.durationMs.toFloat()
                } else 0f,
                onValueChange = onSeekFraction
            )
            Row {
                TextButton(onClick = onToggleShuffle) {
                    Text(if (state.shuffleEnabled) "Shuffle: On" else "Shuffle: Off")
                }
                TextButton(onClick = onSkipPrevious) { Text("Prev") }
                TextButton(onClick = onTogglePlayPause) {
                    Text(if (state.isPlaying) "Pause" else "Play")
                }
                TextButton(onClick = onSkipNext) { Text("Next") }
                TextButton(onClick = onCycleRepeat) { Text(repeatLabel(state.repeatMode)) }
            }
        }
    }
}

private fun repeatLabel(@Player.RepeatMode repeatMode: Int): String = when (repeatMode) {
    Player.REPEAT_MODE_ONE -> "Repeat: One"
    Player.REPEAT_MODE_ALL -> "Repeat: All"
    else -> "Repeat: Off"
}
