package com.nera.musicplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dateCreated: Long,
    val dateModified: Long,
    /** True for playlists produced by the clustering engine; false for user-created ones. */
    val isGenerated: Boolean = false
)
