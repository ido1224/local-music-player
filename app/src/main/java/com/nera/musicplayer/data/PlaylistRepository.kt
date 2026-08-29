package com.nera.musicplayer.data

import android.content.Context
import com.nera.musicplayer.similarity.ClusterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Cluster count scales with library size instead of a fixed constant, so a small library gets
 * genuinely grouped playlists instead of one singleton per track (a real degenerate case found
 * during on-device verification with only 5 tracks and a fixed k=5). One cluster per 20 tracks,
 * clamped to [2, 5] at both ends.
 */
private const val MIN_GENERATED_CLUSTERS = 2
private const val MAX_GENERATED_CLUSTERS = 5
private const val TRACKS_PER_CLUSTER = 20

private fun computeClusterCount(candidateCount: Int): Int =
    (candidateCount / TRACKS_PER_CLUSTER).coerceIn(MIN_GENERATED_CLUSTERS, MAX_GENERATED_CLUSTERS)

class PlaylistRepository(context: Context) {

    private val playlistDao = AppDatabase.getInstance(context).playlistDao()
    private val trackDao = AppDatabase.getInstance(context).trackDao()
    private val listenEventDao = AppDatabase.getInstance(context).listenEventDao()

    val playlists: Flow<List<Playlist>> = playlistDao.observeAll()

    fun tracksInPlaylist(playlistId: Long): Flow<List<Track>> =
        playlistDao.observeTracksInPlaylist(playlistId)

    suspend fun createPlaylist(name: String) {
        val now = System.currentTimeMillis()
        playlistDao.insert(Playlist(name = name, dateCreated = now, dateModified = now))
    }

    suspend fun renamePlaylist(playlist: Playlist, newName: String) {
        playlistDao.update(playlist.copy(name = newName, dateModified = System.currentTimeMillis()))
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.delete(playlist)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        val position = playlistDao.countTracksInPlaylist(playlistId)
        playlistDao.insertPlaylistTrack(PlaylistTrack(playlistId, trackId, position))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun moveTrack(playlistId: Long, fromPosition: Int, toPosition: Int) {
        val entries = playlistDao.getPlaylistTracksOrdered(playlistId).toMutableList()
        if (fromPosition !in entries.indices || toPosition !in entries.indices) return
        val item = entries.removeAt(fromPosition)
        entries.add(toPosition, item)
        val reindexed = entries.mapIndexed { index, entry -> entry.copy(position = index) }
        playlistDao.updatePlaylistTracks(reindexed)
    }

    /**
     * Regenerates all AI playlists from scratch: clusters the library by BPM/energy, names each
     * cluster from its centroid, and orders each cluster's tracks by 80%+ listen count (most
     * listened first) as a tiebreak-free favoring rather than arbitrary inclusion order. Manual
     * playlists are untouched. Called on demand, not reactively. [k] defaults to null, meaning
     * "compute from the current library size" via [computeClusterCount]; callers (currently just
     * tests, to force a specific shape) can still pass an explicit value.
     */
    suspend fun regenerateGeneratedPlaylists(k: Int? = null) = withContext(Dispatchers.IO) {
        val candidates = trackDao.getAllWithFeatures().filter { it.bpm != null && it.energy != null }
        playlistDao.deleteGeneratedPlaylists()
        if (candidates.isEmpty()) return@withContext

        val effectiveK = k ?: computeClusterCount(candidates.size)
        val listenCounts = listenEventDao.highCompletionCountsByTrack().associate { it.trackId to it.count }
        val clusters = ClusterEngine.cluster(candidates, k = effectiveK)
        val now = System.currentTimeMillis()

        for (cluster in clusters) {
            val name = ClusterEngine.nameFor(cluster, candidates)
            val playlistId = playlistDao.insert(Playlist(name = name, dateCreated = now, dateModified = now, isGenerated = true))
            val ordered = cluster.tracks.sortedWith(
                compareByDescending<TrackWithFeatures> { listenCounts[it.track.id] ?: 0 }
                    .thenBy { ClusterEngine.distanceToCentroid(it, cluster.centroidBpm, cluster.centroidEnergy) }
            )
            val playlistTracks = ordered.mapIndexed { index, track -> PlaylistTrack(playlistId, track.track.id, index) }
            playlistDao.insertPlaylistTracks(playlistTracks)
        }
    }
}
