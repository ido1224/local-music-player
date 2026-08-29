package com.nera.musicplayer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nera.musicplayer.data.LibrarySortOrder
import com.nera.musicplayer.data.Track
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
    onOpenPlaylists: () -> Unit = {}
) {
    val playerState by playerViewModel.uiState.collectAsState()
    val tracks by libraryViewModel.tracksWithFeatures.collectAsState()
    val sortOrder by libraryViewModel.sortOrder.collectAsState()
    val isImporting by libraryViewModel.isImporting.collectAsState()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> libraryViewModel.importTracks(uris) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NERA Music Player") },
                actions = {
                    TextButton(onClick = {
                        libraryViewModel.setSortOrder(
                            when (sortOrder) {
                                LibrarySortOrder.DATE_ADDED -> LibrarySortOrder.BPM
                                LibrarySortOrder.BPM -> LibrarySortOrder.ENERGY
                                LibrarySortOrder.ENERGY -> LibrarySortOrder.DATE_ADDED
                            }
                        )
                    }) {
                        Text(
                            when (sortOrder) {
                                LibrarySortOrder.DATE_ADDED -> "Sort: Date"
                                LibrarySortOrder.BPM -> "Sort: BPM"
                                LibrarySortOrder.ENERGY -> "Sort: Energy"
                            }
                        )
                    }
                    TextButton(onClick = onOpenPlaylists) {
                        Text("Playlists")
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("audio/*")) }) {
                        Icon(Icons.Default.Add, contentDescription = "Import audio files")
                    }
                }
            )
        },
        bottomBar = {
            PlaybackBar(
                state = playerState,
                onTogglePlayPause = { playerViewModel.togglePlayPause() },
                onSeekFraction = { fraction ->
                    if (playerState.durationMs > 0) {
                        playerViewModel.seekTo((fraction * playerState.durationMs).toLong())
                    }
                },
                onSkipPrevious = { playerViewModel.skipToPrevious() },
                onSkipNext = { playerViewModel.skipToNext() },
                onToggleShuffle = { playerViewModel.toggleShuffle() },
                onCycleRepeat = { playerViewModel.cycleRepeatMode() }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isImporting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (tracks.isEmpty()) {
                    Text(
                        text = "No tracks yet. Tap + to import audio files.",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(tracks, key = { _, item -> item.track.id }) { index, item ->
                            TrackRow(
                                track = item.track,
                                bpm = item.bpm,
                                energy = item.energy,
                                onClick = { playerViewModel.playQueue(tracks.map { it.track }, index) },
                                onRemoveFromLibrary = { libraryViewModel.removeFromLibrary(item.track) },
                                onDeleteFile = { libraryViewModel.deleteTrackFile(item.track) },
                                onMoreLikeThis = if (item.bpm != null && item.energy != null) {
                                    {
                                        val similar = libraryViewModel.findSimilarTracks(item)
                                        val queue = listOf(item.track) + similar.map { it.track }
                                        playerViewModel.playQueue(queue, 0)
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: Track,
    bpm: Float?,
    energy: Float?,
    onClick: () -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onDeleteFile: () -> Unit,
    onMoreLikeThis: (() -> Unit)?
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val subtitle = listOfNotNull(track.artist, track.album).joinToString(" — ")
    Box {
        ListItem(
            headlineContent = { Text(track.title) },
            supportingContent = { if (subtitle.isNotEmpty()) Text(subtitle) },
            trailingContent = if (bpm != null || energy != null) {
                {
                    Column(horizontalAlignment = Alignment.End) {
                        if (bpm != null) Text("BPM: ${bpm.roundToInt()}")
                        if (energy != null) Text("Energy: ${"%.2f".format(energy)}")
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
        )
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (onMoreLikeThis != null) {
                DropdownMenuItem(
                    text = { Text("More like this") },
                    onClick = {
                        showMenu = false
                        onMoreLikeThis()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Remove from library") },
                onClick = {
                    showMenu = false
                    onRemoveFromLibrary()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete file") },
                onClick = {
                    showMenu = false
                    showDeleteConfirm = true
                }
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete file?") },
            text = { Text("This permanently deletes \"${track.title}\" from your device. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteFile()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
