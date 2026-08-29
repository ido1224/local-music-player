package com.nera.musicplayer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * One-to-one with [Track]. Computed once at import time (see MusicRepository),
 * not re-derived on every play - analysis is comparatively expensive (a full
 * onset-detection + beat-tracking pass), so it's cached here rather than run
 * per-play.
 */
@Entity(
    tableName = "track_audio_features",
    foreignKeys = [
        ForeignKey(
            entity = Track::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackAudioFeatures(
    @PrimaryKey val trackId: Long,
    val bpm: Float?,
    /** Normalized RMS amplitude over the analysis window, in [0, 1]. Null if analysis failed. */
    val energy: Float?,
    val analyzedAt: Long
)
