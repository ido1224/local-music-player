package com.nera.musicplayer.data

import android.content.Context
import com.nera.musicplayer.similarity.ClusterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Fixed cluster count for generated playlists; "~4-5" per the design brief, pinned at the upper end. */
private const val GENERATED_CLUSTER_COUNT = 5

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
     * playlists are untouched. Called on demand, not reactively.
     */
    suspend fun regenerateGeneratedPlaylists(k: Int = GENERATED_CLUSTER_COUNT) = withContext(Dispatchers.IO) {
        val candidates = trackDao.getAllWithFeatures().filter { it.bpm != null && it.energy != null }
        playlistDao.deleteGeneratedPlaylists()
        if (candidates.isEmpty()) return@withContext

        val listenCounts = listenEventDao.highCompletionCountsByTrack().associate { it.trackId to it.count }
        val clusters = ClusterEngine.cluster(candidates, k = k)
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
