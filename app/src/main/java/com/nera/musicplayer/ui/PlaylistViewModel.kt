package com.nera.musicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nera.musicplayer.data.Playlist
import com.nera.musicplayer.data.PlaylistRepository
import com.nera.musicplayer.data.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlaylistRepository(application)

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist

    private val _isRegenerating = MutableStateFlow(false)
    val isRegenerating: StateFlow<Boolean> = _isRegenerating

    val selectedPlaylistTracks: StateFlow<List<Track>> = _selectedPlaylist
        .flatMapLatest { playlist ->
            if (playlist == null) flowOf(emptyList()) else repository.tracksInPlaylist(playlist.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createPlaylist(name.trim()) }
    }

    fun renameSelectedPlaylist(newName: String) {
        val playlist = _selectedPlaylist.value ?: return
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renamePlaylist(playlist, newName.trim())
            _selectedPlaylist.value = playlist.copy(name = newName.trim())
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
            if (_selectedPlaylist.value?.id == playlist.id) _selectedPlaylist.value = null
        }
    }

    fun addTrackToSelectedPlaylist(trackId: Long) {
        val playlistId = _selectedPlaylist.value?.id ?: return
        viewModelScope.launch { repository.addTrackToPlaylist(playlistId, trackId) }
    }

    fun removeTrackFromSelectedPlaylist(trackId: Long) {
        val playlistId = _selectedPlaylist.value?.id ?: return
        viewModelScope.launch { repository.removeTrackFromPlaylist(playlistId, trackId) }
    }

    fun moveTrack(fromPosition: Int, toPosition: Int) {
        val playlistId = _selectedPlaylist.value?.id ?: return
        viewModelScope.launch { repository.moveTrack(playlistId, fromPosition, toPosition) }
    }

    /** Clusters the library into fresh AI playlists on demand; never runs on its own. */
    fun regenerateGeneratedPlaylists() {
        viewModelScope.launch {
            _isRegenerating.value = true
            try {
                repository.regenerateGeneratedPlaylists()
            } finally {
                _isRegenerating.value = false
            }
        }
    }
}
