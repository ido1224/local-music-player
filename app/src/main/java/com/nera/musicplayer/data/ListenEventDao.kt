package com.nera.musicplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ListenEventDao {
    @Insert
    suspend fun insert(event: ListenEvent): Long

    @Query("SELECT COUNT(*) FROM listen_events")
    suspend fun count(): Int
}
