package com.nera.musicplayer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<Track>>

    @Query(
        """
        SELECT tracks.*, track_audio_features.bpm as bpm, track_audio_features.energy as energy FROM tracks
        LEFT JOIN track_audio_features ON tracks.id = track_audio_features.trackId
        ORDER BY tracks.dateAdded DESC
        """
    )
    fun observeAllWithFeaturesByDateAdded(): Flow<List<TrackWithFeatures>>

    @Query(
        """
        SELECT tracks.*, track_audio_features.bpm as bpm, track_audio_features.energy as energy FROM tracks
        LEFT JOIN track_audio_features ON tracks.id = track_audio_features.trackId
        ORDER BY track_audio_features.bpm IS NULL, track_audio_features.bpm ASC
        """
    )
    fun observeAllWithFeaturesByTempo(): Flow<List<TrackWithFeatures>>

    @Query(
        """
        SELECT tracks.*, track_audio_features.bpm as bpm, track_audio_features.energy as energy FROM tracks
        LEFT JOIN track_audio_features ON tracks.id = track_audio_features.trackId
        ORDER BY track_audio_features.energy IS NULL, track_audio_features.energy ASC
        """
    )
    fun observeAllWithFeaturesByEnergy(): Flow<List<TrackWithFeatures>>

    @Query(
        """
        SELECT tracks.*, track_audio_features.bpm as bpm, track_audio_features.energy as energy FROM tracks
        LEFT JOIN track_audio_features ON tracks.id = track_audio_features.trackId
        ORDER BY tracks.title COLLATE NOCASE ASC
        """
    )
    fun observeAllWithFeaturesByTitle(): Flow<List<TrackWithFeatures>>

    /** One-shot (non-Flow) fetch, for batch jobs like playlist generation that don't need live updates. */
    @Query(
        """
        SELECT tracks.*, track_audio_features.bpm as bpm, track_audio_features.energy as energy FROM tracks
        LEFT JOIN track_audio_features ON tracks.id = track_audio_features.trackId
        """
    )
    suspend fun getAllWithFeatures(): List<TrackWithFeatures>

    /** For duplicate detection during import - cheap projection, no need to load full Track rows. */
    @Query("SELECT title, artist FROM tracks")
    suspend fun getAllTitleArtists(): List<TrackTitleArtist>

    /** For the one-time album-art backfill (see MusicRepository.backfillAlbumArtIfNeeded) - not used for anything reactive. */
    @Query("SELECT * FROM tracks WHERE albumArtPath IS NULL")
    suspend fun getTracksMissingAlbumArt(): List<Track>

    @Query("UPDATE tracks SET albumArtPath = :albumArtPath WHERE id = :trackId")
    suspend fun updateAlbumArtPath(trackId: Long, albumArtPath: String?)

    @Insert
    suspend fun insert(track: Track): Long

    @Delete
    suspend fun delete(track: Track)
}
