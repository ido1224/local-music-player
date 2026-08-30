package com.nera.musicplayer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nera.musicplayer.data.LibrarySortOrder
import com.nera.musicplayer.data.MusicRepository
import com.nera.musicplayer.data.Track
import com.nera.musicplayer.data.TrackWithFeatures
import com.nera.musicplayer.similarity.SimilarityEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val sortedTracks = _sortOrder.flatMapLatest { order -> repository.observeTracksWithFeatures(order) }

    /**
     * Search filters the already-sorted list in memory rather than round-tripping through Room
     * per keystroke - a title/artist substring match over a few hundred tracks is well under a
     * frame budget, so this stays snappy without needing a debounce or a DB-level LIKE query.
     */
    val tracksWithFeatures: StateFlow<List<TrackWithFeatures>> = combine(sortedTracks, _searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            list.filter { item ->
                item.track.title.contains(query, ignoreCase = true) ||
                    item.track.artist?.contains(query, ignoreCase = true) == true
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    fun setSortOrder(order: LibrarySortOrder) {
        _sortOrder.value = order
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
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

    /** Ranks the current library by similarity to [seed] using [SimilarityEngine], most similar first. */
    fun findSimilarTracks(seed: TrackWithFeatures, limit: Int = DEFAULT_SIMILAR_LIMIT): List<TrackWithFeatures> =
        SimilarityEngine.rankSimilar(seed, tracksWithFeatures.value, limit)

    companion object {
        private const val DEFAULT_SIMILAR_LIMIT = 20
    }
}
