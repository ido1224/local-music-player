package com.nera.musicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
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
import com.nera.musicplayer.data.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    playlistViewModel: PlaylistViewModel,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit = {}
) {
    val playlist by playlistViewModel.selectedPlaylist.collectAsState()
    val playlistTracks by playlistViewModel.selectedPlaylistTracks.collectAsState()
    val allTracks by libraryViewModel.tracks.collectAsState()
    val playerState by playerViewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add tracks")
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            if (playlist?.isGenerated == true) {
                                DropdownMenuItem(
                                    text = { Text("Promote to manual") },
                                    onClick = {
                                        showOverflowMenu = false
                                        playlistViewModel.promoteSelectedPlaylistToManual()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    showOverflowMenu = false
                                    showRenameDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    showOverflowMenu = false
                                    playlist?.let { playlistViewModel.deletePlaylist(it) }
                                    onBack()
                                }
                            )
                        }
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
            if (playlistTracks.isEmpty()) {
                Text(
                    text = "No tracks in this playlist yet. Tap + to add some.",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(playlistTracks) { index, track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playerViewModel.playQueue(playlistTracks, index)
                                    onOpenNowPlaying()
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val subtitle = listOfNotNull(track.artist, track.album).joinToString(" — ")
                            ListItem(
                                headlineContent = { Text(track.title) },
                                supportingContent = { if (subtitle.isNotEmpty()) Text(subtitle) },
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { playlistViewModel.moveTrack(index, index - 1) },
                                enabled = index > 0
                            ) { Text("Up") }
                            TextButton(
                                onClick = { playlistViewModel.moveTrack(index, index + 1) },
                                enabled = index < playlistTracks.lastIndex
                            ) { Text("Down") }
                            TextButton(onClick = {
                                playlistViewModel.removeTrackFromSelectedPlaylist(track.id)
                            }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        var name by remember { mutableStateOf(playlist?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    playlistViewModel.renameSelectedPlaylist(name)
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddDialog) {
        val playlistTrackIds = playlistTracks.map { it.id }.toSet()
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add tracks") },
            text = {
                if (allTracks.isEmpty()) {
                    Text("No tracks in your library yet. Import some from the Home screen.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(allTracks, key = { it.id }) { track ->
                            AddTrackRow(
                                track = track,
                                alreadyAdded = track.id in playlistTrackIds,
                                onToggle = {
                                    if (track.id in playlistTrackIds) {
                                        playlistViewModel.removeTrackFromSelectedPlaylist(track.id)
                                    } else {
                                        playlistViewModel.addTrackToSelectedPlaylist(track.id)
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun AddTrackRow(track: Track, alreadyAdded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = track.title, modifier = Modifier.weight(1f).padding(8.dp))
        TextButton(onClick = onToggle) {
            Text(if (alreadyAdded) "Added" else "Add")
        }
    }
}
