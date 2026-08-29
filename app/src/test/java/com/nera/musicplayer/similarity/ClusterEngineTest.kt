package com.nera.musicplayer.similarity

import com.nera.musicplayer.data.Track
import com.nera.musicplayer.data.TrackWithFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun fixture(id: Long, title: String, bpm: Float?, energy: Float?): TrackWithFeatures =
    TrackWithFeatures(
        track = Track(
            id = id,
            uri = "file:///$id.mp3",
            title = title,
            artist = null,
            album = null,
            genre = null,
            year = null,
            durationMs = 200_000,
            dateAdded = id,
            fileSizeBytes = 0
        ),
        bpm = bpm,
        energy = energy
    )

/**
 * Fixtures are the real bpm/energy values pulled from the on-device library on 2026-08-29
 * (5 real NCS tracks, each present twice from two separate energy-extraction rounds), so this
 * test doubles as a regression check against the actual library shape, not just synthetic data.
 */
private val fireflyA = fixture(1, "Firefly", 130.4348f, 0.17295f)
private val jpbA = fixture(2, "JPB-High", 150.0f, 0.31553f)
private val makeMeMoveA = fixture(3, "MakeMeMove", 190.4762f, 0.21327f)
private val onAndOnA = fixture(4, "On&On", 173.6334f, 0.23764f)
private val skyHighA = fixture(5, "SkyHigh", 127.6596f, 0.33963f)
private val fireflyB = fixture(6, "Firefly", 130.4348f, 0.26372f)
private val jpbB = fixture(7, "JPB-High", 150.0f, 0.29473f)
private val makeMeMoveB = fixture(8, "MakeMeMove", 190.4762f, 0.21549f)
private val onAndOnB = fixture(9, "On&On", 173.6334f, 0.27010f)
private val skyHighB = fixture(10, "SkyHigh", 127.6596f, 0.34992f)

private val library = listOf(
    fireflyA, jpbA, makeMeMoveA, onAndOnA, skyHighA,
    fireflyB, jpbB, makeMeMoveB, onAndOnB, skyHighB
)

class ClusterEngineTest {

    @Test
    fun `k=5 recovers the five real track pairs`() {
        val clusters = ClusterEngine.cluster(library, k = 5)

        assertEquals(5, clusters.size)
        val titleSets = clusters.map { cluster -> cluster.tracks.map { it.track.title }.toSet() }
        // Each of the 5 real tracks has a near-identical duplicate (same bpm, close energy) from
        // an earlier energy-extraction round; the nearest neighbor for any of them should always
        // be its own duplicate, so k=5 should isolate each pair into its own cluster.
        assertTrue(titleSets.contains(setOf("Firefly")))
        assertTrue(titleSets.contains(setOf("JPB-High")))
        assertTrue(titleSets.contains(setOf("MakeMeMove")))
        assertTrue(titleSets.contains(setOf("On&On")))
        assertTrue(titleSets.contains(setOf("SkyHigh")))
        clusters.forEach { assertEquals(2, it.tracks.size) }
    }

    @Test
    fun `tracks missing bpm or energy are never assigned`() {
        val incomplete = fixture(99, "NoBpm", bpm = null, energy = 0.5f)
        val clusters = ClusterEngine.cluster(library + incomplete, k = 5)
        val allAssignedIds = clusters.flatMap { it.tracks }.map { it.track.id }
        assertTrue(99L !in allAssignedIds)
    }

    @Test
    fun `naming matches expected genre-plausible labels`() {
        val clusters = ClusterEngine.cluster(library, k = 5)
        val namesByTitle = clusters.associate { cluster ->
            cluster.tracks.first().track.title to ClusterEngine.nameFor(cluster, library)
        }

        // Hand-computed from the same fixture values against the library's own bpm/energy range
        // (127.66-190.48 bpm, 0.173-0.350 energy), tercile-bucketed — see CLAUDE.md for the maths.
        assertEquals("Calm / Slow", namesByTitle["Firefly"])
        assertEquals("High Energy / Mid-Tempo", namesByTitle["JPB-High"])
        assertEquals("Calm / Fast", namesByTitle["MakeMeMove"])
        assertEquals("Moderate Energy / Fast", namesByTitle["On&On"])
        assertEquals("High Energy / Slow", namesByTitle["SkyHigh"])
    }

    @Test
    fun `empty candidate list produces no clusters`() {
        assertTrue(ClusterEngine.cluster(emptyList(), k = 5).isEmpty())
    }
}
