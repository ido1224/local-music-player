package com.nera.musicplayer

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.beatroot.BeatRootOnsetEventHandler
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.UniversalAudioInputStream
import be.tarsos.dsp.onsets.ComplexOnsetDetector
import be.tarsos.dsp.onsets.OnsetHandler
import be.tarsos.dsp.onsets.PercussionOnsetDetector
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import kotlin.math.abs

private const val TAG = "TarsosDspSpike"

/**
 * Standalone feasibility spike: does TarsosDSP build/link against this project's
 * Kotlin/Gradle/Android setup, and do its beat/onset detectors produce usable
 * results on real audio? Not wired into the app - androidTest-only dependency.
 *
 * Assets exercised:
 *  - test_tone_2.wav: sustained pure sine tone (reused from playback milestones).
 *    No percussive content - a zero onset count is the correct result.
 *  - click_track_120bpm.wav: synthetic noise-burst clicks at exactly 120 BPM
 *    ground truth, generated for this spike. PercussionOnsetDetector with
 *    bufferOverlap=0 found only every other click (60.1 BPM, an octave error).
 *  - ncs_clip.wav: 8 real seconds (60s-68s) of "Elektronomia - Sky High"
 *    [NCS Release] - royalty-free, downloaded directly from ncs.io with the
 *    user's explicit go-ahead, safe to keep in the repo long-term (unlike the
 *    earlier W&W commercial-track clip this replaces, which has been deleted).
 *    Documented tempo (Tunebat/SongBPM, cross-checked against the search
 *    results used to pick this track): ~128 BPM, progressive house with a clear
 *    four-on-the-floor kick.
 *  - ncs_clip_trap.wav: 8 real seconds (45s-53s) of "JPB - High" [NCS Release] -
 *    royalty-free, downloaded directly from ncs.io with explicit go-ahead. Trap,
 *    not four-on-the-floor - syncopated hi-hats, half-time feel. Documented
 *    tempo has the classic trap half/double-time ambiguity: 75 BPM felt / 150
 *    BPM measured (both readings are "correct" - this is a real property of the
 *    genre, not test noise), so a match against either counts as success.
 */
@RunWith(AndroidJUnit4::class)
class TarsosDspSpikeTest {

    @Test
    fun detectTempoFromExistingTestAudio() {
        val onsets = runPercussionDetection("test_tone_2.wav", bufferSize = 1024, overlap = 0)
        Log.i(TAG, "[sine tone, 0% overlap] Onsets detected: ${onsets.size} at times(s)=$onsets")
        logTempoEstimate(onsets, expectedBpm = null)
    }

    @Test
    fun detectTempoFromSyntheticClickTrack() {
        val onsets = runPercussionDetection("click_track_120bpm.wav", bufferSize = 1024, overlap = 0)
        Log.i(TAG, "[click track, 0% overlap] Onsets detected: ${onsets.size} at times(s)=$onsets")
        logTempoEstimate(onsets, expectedBpm = 120.0)
    }

    /**
     * The actual validation: TarsosDSP's intended API for beat tracking, not raw
     * onset detection. BeatRootOnsetEventHandler is an OnsetHandler itself - it
     * passively collects onsets from an underlying detector during one pass over
     * the audio, then trackBeats() runs BeatRoot's induction + multi-agent beat
     * tracking algorithm on that onset list as a post-processing step. This is
     * TarsosDSP's real answer to "what's the tempo," as opposed to the naive
     * "take the median gap between raw onsets" math used in the earlier tests
     * above (kept only as regression reference against synthetic signals).
     */
    @Test
    fun detectBeatsFromRealMusicClip_beatRoot() {
        val beats = runBeatRoot("ncs_clip.wav", bufferSize = 1024, overlap = 512)
        Log.i(TAG, "[ncs clip, BeatRoot] Beats detected: ${beats.size} at times(s)=$beats")
        logTempoEstimate(beats, expectedBpm = 128.0)
    }

    /** Second validation track: trap, not four-on-the-floor. See class doc for the half/double-time note. */
    @Test
    fun detectBeatsFromTrapClip_beatRoot() {
        val beats = runBeatRoot("ncs_clip_trap.wav", bufferSize = 1024, overlap = 512)
        Log.i(TAG, "[trap clip, BeatRoot] Beats detected: ${beats.size} at times(s)=$beats")
        logTempoEstimateAnyOf(beats, expectedBpms = listOf(75.0, 150.0))
    }

    private fun runBeatRoot(assetName: String, bufferSize: Int, overlap: Int): List<Double> {
        val (wav, bytes) = loadWav(assetName)
        val dispatcher = buildDispatcher(wav, bytes, bufferSize, overlap)

        val beatRootHandler = BeatRootOnsetEventHandler()
        val onsetDetector = ComplexOnsetDetector(bufferSize).apply {
            setHandler(beatRootHandler)
        }
        dispatcher.addAudioProcessor(onsetDetector)

        // Pass 1: collect raw onsets via ComplexOnsetDetector, fed to BeatRoot.
        dispatcher.run()

        // Pass 2 (post-processing, not real-time): BeatRoot's own beat-tracking
        // algorithm over the collected onset list.
        val beatTimestamps = mutableListOf<Double>()
        beatRootHandler.trackBeats(OnsetHandler { time, _ -> beatTimestamps.add(time) })
        return beatTimestamps
    }

    private fun runPercussionDetection(assetName: String, bufferSize: Int, overlap: Int): List<Double> {
        val (wav, bytes) = loadWav(assetName)
        val onsetTimestamps = mutableListOf<Double>()
        val dispatcher = buildDispatcher(wav, bytes, bufferSize, overlap)
        val detector = PercussionOnsetDetector(
            wav.sampleRate.toFloat(),
            bufferSize,
            overlap,
            OnsetHandler { time, salience -> onsetTimestamps.add(time) }
        )
        dispatcher.addAudioProcessor(detector)
        dispatcher.run()
        return onsetTimestamps
    }

    private fun loadWav(assetName: String): Pair<WavInfo, ByteArray> {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bytes = context.assets.open(assetName).use { it.readBytes() }
        val wav = parseWavHeader(bytes)
        Log.i(
            TAG,
            "[$assetName] Parsed WAV: sampleRate=${wav.sampleRate} channels=${wav.channels} " +
                "bitsPerSample=${wav.bitsPerSample} dataSize=${wav.dataSize} bytes"
        )
        return wav to bytes
    }

    private fun buildDispatcher(wav: WavInfo, bytes: ByteArray, bufferSize: Int, overlap: Int): AudioDispatcher {
        val pcmStream = ByteArrayInputStream(bytes, wav.dataOffset, wav.dataSize)
        val format = TarsosDSPAudioFormat(wav.sampleRate.toFloat(), wav.bitsPerSample, wav.channels, true, false)
        val audioStream = UniversalAudioInputStream(pcmStream, format)
        return AudioDispatcher(audioStream, bufferSize, overlap)
    }

    private fun logTempoEstimate(timestamps: List<Double>, expectedBpm: Double?) {
        if (timestamps.size < 2) {
            Log.i(TAG, "Not enough events to estimate a tempo (need >= 2, got ${timestamps.size}).")
            return
        }

        val intervals = timestamps.zipWithNext { a, b -> b - a }.sorted()
        val medianIntervalSeconds = intervals[intervals.size / 2]
        val bpm = 60.0 / medianIntervalSeconds
        Log.i(TAG, "Estimated tempo: $bpm BPM (median of ${intervals.size} inter-event intervals)")

        if (expectedBpm != null) {
            val errorPercent = abs(bpm - expectedBpm) / expectedBpm * 100
            val verdict = if (errorPercent <= 5.0) "MATCH" else "MISMATCH"
            Log.i(TAG, "$verdict: expected ~$expectedBpm BPM, got $bpm BPM (${"%.1f".format(errorPercent)}% error)")
        }
    }

    /** Like [logTempoEstimate], but a match against ANY of [expectedBpms] counts as success (for genres with a real half/double-time ambiguity). */
    private fun logTempoEstimateAnyOf(timestamps: List<Double>, expectedBpms: List<Double>) {
        if (timestamps.size < 2) {
            Log.i(TAG, "Not enough events to estimate a tempo (need >= 2, got ${timestamps.size}).")
            return
        }

        val intervals = timestamps.zipWithNext { a, b -> b - a }.sorted()
        val medianIntervalSeconds = intervals[intervals.size / 2]
        val bpm = 60.0 / medianIntervalSeconds
        Log.i(TAG, "Estimated tempo: $bpm BPM (median of ${intervals.size} inter-event intervals)")

        val closest = expectedBpms.minBy { abs(bpm - it) }
        val errorPercent = abs(bpm - closest) / closest * 100
        val verdict = if (errorPercent <= 5.0) "MATCH" else "MISMATCH"
        Log.i(
            TAG,
            "$verdict: expected one of $expectedBpms BPM, got $bpm BPM " +
                "(closest was $closest, ${"%.1f".format(errorPercent)}% error)"
        )
    }
}

private data class WavInfo(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val dataOffset: Int,
    val dataSize: Int
)

/** Minimal RIFF/WAVE chunk parser - scans chunks rather than assuming a fixed 44-byte header. */
private fun parseWavHeader(bytes: ByteArray): WavInfo {
    fun u32(off: Int): Int =
        (bytes[off].toInt() and 0xFF) or
            ((bytes[off + 1].toInt() and 0xFF) shl 8) or
            ((bytes[off + 2].toInt() and 0xFF) shl 16) or
            ((bytes[off + 3].toInt() and 0xFF) shl 24)

    fun u16(off: Int): Int =
        (bytes[off].toInt() and 0xFF) or ((bytes[off + 1].toInt() and 0xFF) shl 8)

    fun ascii(off: Int, len: Int): String = String(bytes, off, len, Charsets.US_ASCII)

    require(ascii(0, 4) == "RIFF") { "Not a RIFF file" }
    require(ascii(8, 4) == "WAVE") { "Not a WAVE file" }

    var offset = 12
    var sampleRate = 0
    var channels = 0
    var bitsPerSample = 0
    var dataOffset = -1
    var dataSize = 0

    while (offset + 8 <= bytes.size) {
        val chunkId = ascii(offset, 4)
        val chunkSize = u32(offset + 4)
        val chunkDataStart = offset + 8

        when (chunkId) {
            "fmt " -> {
                channels = u16(chunkDataStart + 2)
                sampleRate = u32(chunkDataStart + 4)
                bitsPerSample = u16(chunkDataStart + 14)
            }
            "data" -> {
                dataOffset = chunkDataStart
                dataSize = chunkSize
            }
        }

        // Chunks are word-aligned: an odd-sized chunk has one padding byte after it.
        offset = chunkDataStart + chunkSize + (chunkSize % 2)
        if (dataOffset >= 0 && sampleRate > 0) break
    }

    check(dataOffset >= 0) { "No 'data' chunk found in WAV file" }
    return WavInfo(sampleRate, channels, bitsPerSample, dataOffset, dataSize)
}
