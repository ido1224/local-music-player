package com.nera.musicplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val genre: String?,
    val year: Int?,
    val durationMs: Long,
    val dateAdded: Long,
    val fileSizeBytes: Long
)
