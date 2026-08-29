package com.nera.musicplayer

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nera.musicplayer.data.AppDatabase
import com.nera.musicplayer.data.ListenEvent
import com.nera.musicplayer.data.MusicRepository
import com.nera.musicplayer.data.PlaylistRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private const val TAG = "PlaylistGeneratorVerification"

/**
 * Verifies PlaylistRepository.regenerateGeneratedPlaylists() end-to-end against the real
 * on-device library. Needs a real Context/Room DB, so this is androidTest rather than a plain
 * unit test - the pure clustering/naming math is already covered by ClusterEngineTest on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class PlaylistGeneratorVerificationTest {

    /**
     * The AppDatabase version bump that shipped alongside this feature destructively wipes
     * tracks/playlists/features on first open (no real migration exists pre-release, per
     * established project convention) but leaves the actual copied audio files in
     * filesDir/music/ untouched. Reconstructs one Track per distinct real file (duplicate
     * imports of the same source produce byte-identical copies, so grouping by file size
     * dedupes correctly) by running them back through the real import pipeline, rather than
     * needing to redo the SAF picker flow or re-download anything.
     */
    @Before
    fun ensureLibraryImported() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val trackDao = AppDatabase.getInstance(context).trackDao()
        if (trackDao.getAllWithFeatures().isNotEmpty()) return@runBlocking

        val musicDir = File(context.filesDir, "music")
        val mp3Files = musicDir.listFiles { f -> f.extension == "mp3" }?.toList().orEmpty()
        val distinctFiles = mp3Files.groupBy { it.length() }.values.map { it.first() }
        Log.d(TAG, "Library empty after schema migration - reimporting ${distinctFiles.size} distinct real tracks from filesDir/music")
        MusicRepository(context).importTracks(distinctFiles.map { Uri.fromFile(it) })
    }

    @Test
    fun regenerateAndLogClusters() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val playlistDao = AppDatabase.getInstance(context).playlistDao()
        val repository = PlaylistRepository(context)

        repository.regenerateGeneratedPlaylists()

        val generated = playlistDao.observeAll().first().filter { it.isGenerated }
        Log.d(TAG, "Generated ${generated.size} playlists against the real library:")
        for (playlist in generated) {
            val tracks = playlistDao.observeTracksInPlaylist(playlist.id).first()
            Log.d(TAG, "  '${playlist.name}' (id=${playlist.id}): ${tracks.joinToString { "${it.title}(id=${it.id})" }}")
        }
    }

    /**
     * Demonstrates the listening-history weighting without waiting out real multi-minute
     * playback. The real library currently has exactly 5 distinct tracks, so the production
     * k=5 always yields 5 singleton clusters (nothing to reorder within) - regenerateGeneratedPlaylists
     * takes an optional k specifically so this can force k=2 and get genuine multi-track
     * clusters to test the ordering against. Picks whichever track currently sorts last within
     * a multi-track cluster, gives it two synthetic 80%+ listen events directly via the DAO
     * (the event-recording pipeline itself was already verified on-device against real playback
     * in the earlier listening-tracker milestone - this test only checks that the *playlist
     * generator* correctly reads and applies those counts), regenerates again, and asserts that
     * track is now first.
     */
    @Test
    fun boostedListenCountMovesTrackToFront() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getInstance(context)
        val playlistDao = db.playlistDao()
        val listenEventDao = db.listenEventDao()
        val repository = PlaylistRepository(context)

        repository.regenerateGeneratedPlaylists(k = 2)
        val before = playlistDao.observeAll().first().filter { it.isGenerated }
            .associateWith { playlistDao.observeTracksInPlaylist(it.id).first() }
        val (targetPlaylist, tracksBefore) = before.entries.first { it.value.size >= 2 }
        val trackToBoost = tracksBefore.last()
        Log.d(
            TAG,
            "Before boost, '${targetPlaylist.name}': ${tracksBefore.joinToString { "${it.title}(id=${it.id})" }} " +
                "- boosting '${trackToBoost.title}' (id=${trackToBoost.id}), currently last"
        )

        listenEventDao.insert(ListenEvent(trackId = trackToBoost.id, timestamp = System.currentTimeMillis(), completionPercent = 95))
        listenEventDao.insert(ListenEvent(trackId = trackToBoost.id, timestamp = System.currentTimeMillis(), completionPercent = 90))

        repository.regenerateGeneratedPlaylists(k = 2)
        val samePlaylistAfter = playlistDao.observeAll().first().first { it.isGenerated && it.name == targetPlaylist.name }
        val tracksAfter = playlistDao.observeTracksInPlaylist(samePlaylistAfter.id).first()
        val newPosition = tracksAfter.indexOfFirst { it.id == trackToBoost.id }
        Log.d(
            TAG,
            "After boost, '${samePlaylistAfter.name}': ${tracksAfter.joinToString { "${it.title}(id=${it.id})" }} " +
                "- boosted track now at position $newPosition"
        )

        assertEquals("Boosted track should sort first within its cluster", 0, newPosition)
    }
}
