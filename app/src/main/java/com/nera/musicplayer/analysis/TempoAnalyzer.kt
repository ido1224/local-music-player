package com.nera.musicplayer.analysis

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.beatroot.BeatRootOnsetEventHandler
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.UniversalAudioInputStream
import be.tarsos.dsp.onsets.ComplexOnsetDetector
import be.tarsos.dsp.onsets.OnsetHandler
import java.io.ByteArrayInputStream

/**
 * TarsosDSP tempo detection, validated in TarsosDspSpikeTest (androidTest)
 * against two genres before being promoted here: 0.3% error on a four-on-the-
 * floor progressive house track, 0.0% error on a syncopated/half-time trap
 * track. ComplexOnsetDetector alone (or PercussionOnsetDetector) is NOT
 * sufficient - raw onset counts under/over-count wildly on real music. Only
 * BeatRootOnsetEventHandler's actual induction + multi-agent beat-tracking
 * algorithm, run as a post-processing step over the raw onsets, produced
 * usable results. Do not simplify this back to a raw onset detector without
 * re-validating - see CLAUDE.md's TarsosDSP spike section for the full trail.
 */
object TempoAnalyzer {

    private const val BUFFER_SIZE = 1024
    private const val OVERLAP = BUFFER_SIZE / 2 // 50%

    /** Returns the estimated tempo in BPM, or null if too few beats were found to estimate one. */
    fun estimateBpm(decoded: DecodedAudio): Float? {
        val format = TarsosDSPAudioFormat(decoded.sampleRate.toFloat(), 16, 1, true, false)
        val stream = UniversalAudioInputStream(ByteArrayInputStream(decoded.pcm16Mono), format)
        val dispatcher = AudioDispatcher(stream, BUFFER_SIZE, OVERLAP)

        val beatRootHandler = BeatRootOnsetEventHandler()
        val onsetDetector = ComplexOnsetDetector(BUFFER_SIZE).apply {
            setHandler(beatRootHandler)
        }
        dispatcher.addAudioProcessor(onsetDetector)

        // Pass 1: collect raw onsets.
        dispatcher.run()

        // Pass 2 (post-processing): BeatRoot's beat-tracking algorithm over the onset list.
        val beatTimestamps = mutableListOf<Double>()
        beatRootHandler.trackBeats(OnsetHandler { time, _ -> beatTimestamps.add(time) })

        if (beatTimestamps.size < 2) return null

        val intervals = beatTimestamps.zipWithNext { a, b -> b - a }.sorted()
        val medianIntervalSeconds = intervals[intervals.size / 2]
        if (medianIntervalSeconds <= 0.0) return null

        return (60.0 / medianIntervalSeconds).toFloat()
    }
}
