package com.nera.musicplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrackAudioFeaturesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(features: TrackAudioFeatures)

    @Query("SELECT * FROM track_audio_features WHERE trackId = :trackId")
    suspend fun getForTrack(trackId: Long): TrackAudioFeatures?
}
