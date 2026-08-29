package com.nera.musicplayer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nera.musicplayer.data.LibrarySortOrder
import com.nera.musicplayer.data.MusicRepository
import com.nera.musicplayer.data.Track
import com.nera.musicplayer.data.TrackWithFeatures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    val tracks: StateFlow<List<Track>> = repository.tracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sortOrder = MutableStateFlow(LibrarySortOrder.DATE_ADDED)
    val sortOrder: StateFlow<LibrarySortOrder> = _sortOrder

    val tracksWithFeatures: StateFlow<List<TrackWithFeatures>> = _sortOrder
        .flatMapLatest { order -> repository.observeTracksWithFeatures(order) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    fun setSortOrder(order: LibrarySortOrder) {
        _sortOrder.value = order
    }

    fun importTracks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            try {
                repository.importTracks(uris)
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun removeFromLibrary(track: Track) {
        viewModelScope.launch { repository.removeFromLibrary(track) }
    }

    fun deleteTrackFile(track: Track) {
        viewModelScope.launch { repository.deleteTrackFile(track) }
    }
}
