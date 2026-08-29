package com.nera.musicplayer.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(context: Context) {

    private val playlistDao = AppDatabase.getInstance(context).playlistDao()

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
}
