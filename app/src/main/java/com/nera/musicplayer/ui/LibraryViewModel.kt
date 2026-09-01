package com.nera.musicplayer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nera.musicplayer.data.ImportSummary
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

    /** (current, total) while a folder scan/import is running; null otherwise. */
    private val _importProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val importProgress: StateFlow<Pair<Int, Int>?> = _importProgress

    private val _lastImportSummary = MutableStateFlow<ImportSummary?>(null)
    val lastImportSummary: StateFlow<ImportSummary?> = _lastImportSummary

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

    /** Recursively imports every audio file found under [treeUri], skipping title+artist duplicates. */
    fun importFolder(treeUri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importProgress.value = 0 to 0
            try {
                val summary = repository.importFromTree(treeUri) { current, total ->
                    _importProgress.value = current to total
                }
                _lastImportSummary.value = summary
            } finally {
                _isImporting.value = false
                _importProgress.value = null
            }
        }
    }

    /**
     * Scans the NeraMusicImport drop folder (see MusicRepository.scanImportFolder) - the
     * no-picker-needed bypass for devices whose SAF folder picker has no working confirm action.
     * [showEmptyResult] suppresses the summary dialog for the automatic on-launch scan (an empty
     * drop folder is the common case and shouldn't nag the user every startup), but a manually
     * triggered "Rescan library" tap always reports back, even a 0/0 result.
     */
    fun rescanLibrary(showEmptyResult: Boolean = true) {
        viewModelScope.launch {
            _isImporting.value = true
            _importProgress.value = 0 to 0
            try {
                val summary = repository.scanImportFolder { current, total ->
                    _importProgress.value = current to total
                }
                if (showEmptyResult || summary.imported > 0 || summary.skippedDuplicates > 0) {
                    _lastImportSummary.value = summary
                }
            } finally {
                _isImporting.value = false
                _importProgress.value = null
            }
        }
    }

    fun clearImportSummary() {
        _lastImportSummary.value = null
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
