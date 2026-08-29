package com.nera.musicplayer.analysis

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Much simpler than tempo detection - plain RMS amplitude, linearly normalized
 * against full-scale 16-bit amplitude. No loudness curve (dBFS/LUFS/A-weighting)
 * - a straightforward relative intensity measure is all that's needed here, not
 * a perceptual loudness model.
 *
 * Samples three short windows spread across the track (~25%/50%/75% of duration)
 * and averages their RMS, rather than trusting a single fixed window. A single
 * sample point is too noisy: it can land on a quiet breakdown in one track and a
 * loud drop in another, even within the same genre, producing a misleading
 * "energy" figure that doesn't reflect the track as a whole (found via real
 * validation - see CLAUDE.md). Bounded like tempo analysis: this only seeks to
 * three points and decodes a few seconds at each, never the whole file.
 */
object EnergyAnalyzer {

    private const val MAX_AMPLITUDE = 32768.0
    private const val WINDOW_DURATION_MS = 3_000L
    private val SAMPLE_FRACTIONS = listOf(0.25, 0.5, 0.75)

    fun computeNormalizedRms(filePath: String): Float {
        val durationMs = AudioDecoder.getDurationMs(filePath)

        // Too short for three distinct, non-overlapping windows - just sample from the start.
        val startPositionsMs = if (durationMs != null && durationMs > WINDOW_DURATION_MS * SAMPLE_FRACTIONS.size * 2) {
            SAMPLE_FRACTIONS.map { fraction -> (durationMs * fraction).toLong() }
        } else {
            listOf(0L)
        }

        val rmsPerWindow = startPositionsMs.mapNotNull { startMs ->
            AudioDecoder.decodeWindowAt(filePath, startMs, WINDOW_DURATION_MS)?.let(::rmsOf)
        }
        if (rmsPerWindow.isEmpty()) return 0f

        val averageRms = rmsPerWindow.average()
        return (averageRms / MAX_AMPLITUDE).toFloat().coerceIn(0f, 1f)
    }

    private fun rmsOf(decoded: DecodedAudio): Double {
        val sampleCount = decoded.pcm16Mono.size / 2
        if (sampleCount == 0) return 0.0

        val buffer = ByteBuffer.wrap(decoded.pcm16Mono).order(ByteOrder.LITTLE_ENDIAN)
        var sumOfSquares = 0.0
        repeat(sampleCount) {
            val sample = buffer.short.toDouble()
            sumOfSquares += sample * sample
        }
        return sqrt(sumOfSquares / sampleCount)
    }
}
