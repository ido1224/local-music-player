package com.nera.musicplayer.similarity

import com.nera.musicplayer.data.TrackWithFeatures
import kotlin.math.abs
import kotlin.math.max

/**
 * V1 similarity: BPM + energy only, weighted equally. BPM comparison is
 * octave-aware (treats half-time/double-time as a near-match) since the
 * tempo analyzer is known to report either the felt or measured tempo for
 * some genres (see CLAUDE.md's TarsosDSP validation notes).
 */
object SimilarityEngine {

    private const val BPM_WEIGHT = 0.5f
    private const val ENERGY_WEIGHT = 0.5f

    /**
     * Fraction-of-tempo distance in [0, 1], taking the best of the direct, doubled, and halved
     * comparisons. Internal (not private) so [com.nera.musicplayer.similarity.ClusterEngine] can
     * reuse the same octave-aware metric instead of duplicating it.
     */
    internal fun normalizedBpmDistance(bpm1: Float, bpm2: Float): Float {
        val minDiff = minOf(
            abs(bpm1 - bpm2),
            abs(bpm1 - 2f * bpm2),
            abs(2f * bpm1 - bpm2)
        )
        val reference = max(bpm1, bpm2)
        if (reference <= 0f) return 0f
        return (minDiff / reference).coerceIn(0f, 1f)
    }

    internal fun normalizedEnergyDistance(energy1: Float, energy2: Float): Float =
        abs(energy1 - energy2).coerceIn(0f, 1f)

    /** 0 (identical) to 1 (maximally different), or null if either track is missing bpm/energy. */
    fun distance(a: TrackWithFeatures, b: TrackWithFeatures): Float? {
        val bpm1 = a.bpm ?: return null
        val bpm2 = b.bpm ?: return null
        val energy1 = a.energy ?: return null
        val energy2 = b.energy ?: return null
        return BPM_WEIGHT * normalizedBpmDistance(bpm1, bpm2) + ENERGY_WEIGHT * normalizedEnergyDistance(energy1, energy2)
    }

    /**
     * Ranks [candidates] by similarity to [seed], most similar first, capped at [limit].
     * Excludes the seed itself and any candidate missing bpm/energy (rather than scoring
     * them as maximally different). Returns an empty list if [seed] itself lacks bpm/energy,
     * since no distance can be computed against it.
     */
    fun rankSimilar(seed: TrackWithFeatures, candidates: List<TrackWithFeatures>, limit: Int): List<TrackWithFeatures> {
        if (seed.bpm == null || seed.energy == null) return emptyList()
        return candidates
            .filter { it.track.id != seed.track.id }
            .mapNotNull { candidate -> distance(seed, candidate)?.let { candidate to it } }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
    }
}
