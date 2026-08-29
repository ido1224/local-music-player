package com.nera.musicplayer.data

import androidx.room.Embedded

data class TrackWithFeatures(
    @Embedded val track: Track,
    val bpm: Float?,
    val energy: Float?
)
