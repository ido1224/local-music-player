package com.nera.musicplayer.similarity

import com.nera.musicplayer.data.TrackWithFeatures
import kotlin.math.abs

/** A generated group of tracks with similar BPM/energy, plus the centroid it was named from. */
data class TrackCluster(
    val centroidBpm: Float,
    val centroidEnergy: Float,
    val tracks: List<TrackWithFeatures>
)

/**
 * Groups tracks into a fixed number of BPM/energy clusters via Lloyd's k-means, reusing
 * [SimilarityEngine]'s octave-aware BPM distance for both assignment and centroid naming.
 * Pure Kotlin, no Android dependency, so it's covered by a local JVM unit test rather than
 * an on-device one.
 */
object ClusterEngine {

    private data class Centroid(val bpm: Float, val energy: Float)

    private fun distance(track: TrackWithFeatures, centroid: Centroid): Float {
        val bpm = track.bpm ?: return Float.MAX_VALUE
        val energy = track.energy ?: return Float.MAX_VALUE
        return 0.5f * SimilarityEngine.normalizedBpmDistance(bpm, centroid.bpm) +
            0.5f * SimilarityEngine.normalizedEnergyDistance(energy, centroid.energy)
    }

    /** Public wrapper for the same metric, used to order tracks within a cluster deterministically. */
    fun distanceToCentroid(track: TrackWithFeatures, centroidBpm: Float, centroidEnergy: Float): Float =
        distance(track, Centroid(centroidBpm, centroidEnergy))

    /**
     * Folds [bpm] to whichever octave (as-is, doubled, or halved) sits closest to [reference],
     * so averaging an ambiguous track's raw BPM into a centroid doesn't produce a meaningless
     * midpoint between e.g. 90 and 180.
     */
    private fun foldToOctaveOf(bpm: Float, reference: Float): Float =
        floatArrayOf(bpm, bpm * 2f, bpm / 2f).minByOrNull { abs(it - reference) } ?: bpm

    /**
     * Clusters [candidates] into up to [k] groups by BPM/energy. Tracks missing bpm or energy
     * are filtered out here (excluded, not scored as maximally different — the same rule
     * [SimilarityEngine] uses) so callers can pass an unfiltered library safely. Centroid
     * seeding is deterministic farthest-point sampling (each new seed is the candidate farthest
     * from every seed chosen so far) rather than random or evenly-spaced: an evenly-spaced sort
     * order can waste two seeds on a pair of near-duplicate tracks that happen to land next to
     * each other, starving a genuinely distinct group of a centroid entirely — farthest-point
     * seeding actively avoids that by construction.
     */
    fun cluster(candidates: List<TrackWithFeatures>, k: Int, maxIterations: Int = 50): List<TrackCluster> {
        val candidates = candidates.filter { it.bpm != null && it.energy != null }
        if (candidates.isEmpty()) return emptyList()
        val effectiveK = minOf(k, candidates.size)

        val centroids = mutableListOf(Centroid(candidates[0].bpm!!, candidates[0].energy!!))
        while (centroids.size < effectiveK) {
            val next = candidates.maxByOrNull { track ->
                centroids.minOf { distance(track, it) }
            }!!
            centroids.add(Centroid(next.bpm!!, next.energy!!))
        }

        var assignments = IntArray(candidates.size) { -1 }
        for (iteration in 0 until maxIterations) {
            val newAssignments = IntArray(candidates.size) { i ->
                centroids.indices.minByOrNull { distance(candidates[i], centroids[it]) }!!
            }
            val stable = newAssignments.contentEquals(assignments)
            assignments = newAssignments
            if (stable) break

            for (c in centroids.indices) {
                val members = candidates.filterIndexed { i, _ -> assignments[i] == c }
                if (members.isEmpty()) {
                    // Standard k-means empty-cluster remedy: reseed with the point currently
                    // farthest from its assigned centroid, so no cluster silently disappears.
                    val farthestIndex = candidates.indices.maxByOrNull { distance(candidates[it], centroids[assignments[it]]) }
                    if (farthestIndex != null) {
                        centroids[c] = Centroid(candidates[farthestIndex].bpm!!, candidates[farthestIndex].energy!!)
                    }
                    continue
                }
                val newEnergy = members.map { it.energy!! }.average().toFloat()
                val newBpm = members.map { foldToOctaveOf(it.bpm!!, centroids[c].bpm) }.average().toFloat()
                centroids[c] = Centroid(newBpm, newEnergy)
            }
        }

        return centroids.indices
            .map { c -> TrackCluster(centroids[c].bpm, centroids[c].energy, candidates.filterIndexed { i, _ -> assignments[i] == c }) }
            .filter { it.tracks.isNotEmpty() }
    }

    /**
     * Names a cluster from its centroid relative to the full candidate set's BPM/energy range
     * (thirds of the observed range, not absolute thresholds — see CLAUDE.md for why: energy's
     * absolute scale isn't validated beyond the narrow band real music has landed in so far).
     */
    fun nameFor(cluster: TrackCluster, allCandidates: List<TrackWithFeatures>): String {
        val bpmMin = allCandidates.minOf { it.bpm!! }
        val bpmMax = allCandidates.maxOf { it.bpm!! }
        val energyMin = allCandidates.minOf { it.energy!! }
        val energyMax = allCandidates.maxOf { it.energy!! }
        val tempoLabel = tierLabel(cluster.centroidBpm, bpmMin, bpmMax, low = "Slow", mid = "Mid-Tempo", high = "Fast")
        val energyLabel = tierLabel(cluster.centroidEnergy, energyMin, energyMax, low = "Calm", mid = "Moderate Energy", high = "High Energy")
        return "$energyLabel / $tempoLabel"
    }

    private fun tierLabel(value: Float, min: Float, max: Float, low: String, mid: String, high: String): String {
        val range = max - min
        if (range <= 0f) return mid
        val fraction = (value - min) / range
        return when {
            fraction < 1f / 3f -> low
            fraction < 2f / 3f -> mid
            else -> high
        }
    }
}
