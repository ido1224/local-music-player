package com.nera.musicplayer.analysis

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DecodedAudio(val pcm16Mono: ByteArray, val sampleRate: Int)

/**
 * Decodes windows of an arbitrary audio file (whatever format Android's
 * platform decoders support - MP3, AAC, FLAC, WAV, OGG, ...) to mono 16-bit
 * PCM, for feeding into TarsosDSP. Not tied to WAV like the androidTest spike
 * assets were - real imported library files can be anything Media3 can play.
 */
object AudioDecoder {

    /**
     * Continuous window for tempo analysis, which needs an uninterrupted onset
     * timeline (BeatRoot's induction relies on recurring inter-onset intervals -
     * three disjoint snippets would fragment that and break tempo detection).
     *
     * @param skipIntoTrackMs how far to seek in before starting to decode, to skip a likely
     *   silent/quiet intro on longer tracks. Ignored if the track is too short for this to make sense.
     * @param maxDurationMs stop decoding once this much source audio has been read, even if the
     *   track continues - keeps analysis time bounded regardless of track length.
     */
    fun decodeMonoPcm16(filePath: String, skipIntoTrackMs: Long = 20_000L, maxDurationMs: Long = 20_000L): DecodedAudio? {
        val durationUs = getDurationUs(filePath) ?: 0L
        val skipUs = skipIntoTrackMs * 1000L
        val seekToUs = if (durationUs > skipUs * 3) skipUs else 0L
        return decodeWindow(filePath, seekToUs, maxDurationMs)
    }

    /**
     * Short window starting at an explicit position - used for energy's multi-point
     * sampling, where the caller (not a heuristic here) decides where to look.
     */
    fun decodeWindowAt(filePath: String, startMs: Long, maxDurationMs: Long): DecodedAudio? =
        decodeWindow(filePath, startMs * 1000L, maxDurationMs)

    /** Track duration in ms, or null if it can't be determined without a full decode. */
    fun getDurationMs(filePath: String): Long? = getDurationUs(filePath)?.let { it / 1000L }

    private fun getDurationUs(filePath: String): Long? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(filePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/") && format.containsKey(MediaFormat.KEY_DURATION)) {
                    return format.getLong(MediaFormat.KEY_DURATION)
                }
            }
            return null
        } finally {
            extractor.release()
        }
    }

    private fun decodeWindow(filePath: String, seekToUs: Long, maxDurationMs: Long): DecodedAudio? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(filePath)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = candidate
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null

            extractor.selectTrack(trackIndex)
            if (seekToUs > 0L) {
                extractor.seekTo(seekToUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val maxBytes = (maxDurationMs * sampleRate / 1000L) * 2L * channelCount

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()

                val pcmOut = ByteArrayOutputStream()
                val bufferInfo = MediaCodec.BufferInfo()
                var sawInputEos = false
                var sawOutputEos = false

                while (!sawOutputEos && pcmOut.size() < maxBytes) {
                    if (!sawInputEos) {
                        val inIndex = codec.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inIndex)!!
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outIndex >= 0) {
                        if (bufferInfo.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(outIndex)!!
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.get(chunk)
                            pcmOut.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    }
                }

                val mono = downmixToMono(pcmOut.toByteArray(), channelCount)
                return DecodedAudio(mono, sampleRate)
            } finally {
                codec.stop()
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun downmixToMono(pcm: ByteArray, channelCount: Int): ByteArray {
        if (channelCount <= 1) return pcm

        val frameCount = pcm.size / (2 * channelCount)
        val input = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        val output = ByteBuffer.allocate(frameCount * 2).order(ByteOrder.LITTLE_ENDIAN)

        repeat(frameCount) {
            var sum = 0
            repeat(channelCount) { sum += input.short.toInt() }
            output.putShort((sum / channelCount).toShort())
        }
        return output.array()
    }
}
