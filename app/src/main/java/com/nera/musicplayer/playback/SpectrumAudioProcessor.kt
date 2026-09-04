package com.nera.musicplayer.playback

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import be.tarsos.dsp.util.fft.FFT
import be.tarsos.dsp.util.fft.HannWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Taps the live PCM stream inside ExoPlayer's own audio pipeline (via the
 * AudioProcessor chain in DefaultAudioSink) for real-time bass/mid/treble band
 * energy - no Visualizer API, which requires RECORD_AUDIO even when visualizing
 * this app's own audio session, per
 * https://developer.android.com/reference/android/media/audiofx/Visualizer.
 * This processor is a pure pass-through: output bytes equal input bytes,
 * unchanged. Analysis happens on the audio pipeline's own thread (not main),
 * so it must stay cheap - a single 1024-point FFT is well under one playback
 * buffer's worth of audio.
 *
 * [bassEnergy] drives the Now Playing glow pulse (see NowPlayingScreen). It's
 * normalized 0..1 via a decaying-peak tracker rather than a fixed scale, so it
 * adapts to each track's own loudness instead of needing per-track calibration.
 * [midEnergy]/[trebleEnergy] are computed and exposed the same way but not
 * consumed by any UI yet - kept for future use.
 *
 * Only one instance of this processor exists for the app's lifetime (created
 * once in PlaybackService.onCreate), so a companion-object StateFlow is a
 * simple, sufficient way to publish live values out to ViewModels - no IPC
 * needed since the service runs in the app's own process.
 */
class SpectrumAudioProcessor : BaseAudioProcessor() {

    companion object {
        private const val TAG = "AudioSpike"
        private const val FFT_SIZE = 1024
        private const val LOG_INTERVAL_MS = 150L

        /** Per-window decay of the running peak used to normalize bass - keeps normalization adapting to the current track's dynamics rather than a track that played minutes ago. */
        private const val PEAK_DECAY = 0.995f
        private const val PEAK_FLOOR = 5f

        private val _bassEnergy = MutableStateFlow(0f)
        val bassEnergy: StateFlow<Float> = _bassEnergy

        private val _midEnergy = MutableStateFlow(0f)
        val midEnergy: StateFlow<Float> = _midEnergy

        private val _trebleEnergy = MutableStateFlow(0f)
        val trebleEnergy: StateFlow<Float> = _trebleEnergy
    }

    private val fft = FFT(FFT_SIZE, HannWindow())
    private val fftBuffer = FloatArray(FFT_SIZE)
    private val amplitudes = FloatArray(FFT_SIZE / 2)
    private var fillIndex = 0
    private var lastLogTimeMs = 0L
    private var bassPeak = PEAK_FLOOR

    private var sampleRate = 0
    private var channelCount = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        fillIndex = 0
        // Pass-through: same format out as in, so this processor never alters playback.
        return inputAudioFormat
    }

    override fun onFlush() {
        // A new track (or a seek) shouldn't stay normalized against the previous
        // moment's loudness - reseed so the new material establishes its own peak.
        bassPeak = PEAK_FLOOR
        fillIndex = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        analyze(inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN))

        val output = replaceOutputBuffer(remaining)
        output.put(inputBuffer)
        output.flip()
    }

    private fun analyze(buffer: ByteBuffer) {
        if (channelCount <= 0) return
        val shorts = buffer.asShortBuffer()
        val frameCount = shorts.remaining() / channelCount
        for (frame in 0 until frameCount) {
            var sum = 0
            for (ch in 0 until channelCount) {
                sum += shorts.get(frame * channelCount + ch)
            }
            fftBuffer[fillIndex++] = (sum / channelCount) / 32768f
            if (fillIndex == FFT_SIZE) {
                fillIndex = 0
                processWindow()
            }
        }
    }

    private fun processWindow() {
        // In-place: fftBuffer becomes interleaved real/imag pairs. Safe to clobber -
        // every slot is overwritten by fresh time-domain samples before the next transform.
        fft.forwardTransform(fftBuffer)
        fft.modulus(fftBuffer, amplitudes)

        val bass = bandEnergy(20f, 250f)
        val mid = bandEnergy(250f, 4000f)
        val treble = bandEnergy(4000f, 16000f)

        bassPeak = maxOf(bass, bassPeak * PEAK_DECAY, PEAK_FLOOR)
        val normalizedBass = (bass / bassPeak).coerceIn(0f, 1f)

        _bassEnergy.value = normalizedBass
        _midEnergy.value = mid
        _trebleEnergy.value = treble

        val now = System.currentTimeMillis()
        if (now - lastLogTimeMs < LOG_INTERVAL_MS) return
        lastLogTimeMs = now
        Log.d(TAG, "bass=%.2f (norm=%.2f) mid=%.2f treble=%.2f".format(bass, normalizedBass, mid, treble))
    }

    private fun bandEnergy(loHz: Float, hiHz: Float): Float {
        // Bin 0 mixes in the Nyquist bin under this FFT's packed real-transform layout
        // (see FFT.modulus), so start from bin 1 to avoid that artifact.
        val loBin = (loHz * FFT_SIZE / sampleRate).toInt().coerceIn(1, amplitudes.size - 1)
        val hiBin = (hiHz * FFT_SIZE / sampleRate).toInt().coerceIn(loBin, amplitudes.size - 1)
        var sum = 0f
        for (i in loBin..hiBin) sum += amplitudes[i]
        return sum / (hiBin - loBin + 1)
    }
}
