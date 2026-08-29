package com.nera.musicplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Track::class, Playlist::class, PlaylistTrack::class, ListenEvent::class, TrackAudioFeatures::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun listenEventDao(): ListenEventDao
    abstract fun trackAudioFeaturesDao(): TrackAudioFeaturesDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nera_music.db"
                )
                    // Pre-release schema, no user data to preserve yet.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
