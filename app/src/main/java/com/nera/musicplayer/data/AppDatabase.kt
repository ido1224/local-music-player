package com.nera.musicplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds Track.albumArtPath (nullable, defaults to null for every pre-existing row - see MusicRepository.backfillAlbumArtIfNeeded). */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracks ADD COLUMN albumArtPath TEXT")
    }
}

@Database(
    entities = [Track::class, Playlist::class, PlaylistTrack::class, ListenEvent::class, TrackAudioFeatures::class],
    version = 6,
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
                    // Real user data lives here now (see CLAUDE.md, 2026-09-03) - every version bump
                    // from here on needs an explicit Migration, not a destructive fallback. A missing
                    // migration should crash loudly during development, not silently wipe the library.
                    .addMigrations(MIGRATION_5_6)
                    .build().also { instance = it }
            }
    }
}
