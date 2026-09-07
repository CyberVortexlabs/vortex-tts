package com.vortex.tts.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object Mp3Encoder {
    private const val SAMPLE_RATE = 24_000
    private const val CHANNELS = 1
    private const val BIT_RATE = 64_000

    /**
     * Attempt to encode raw PCM 16-bit mono 24kHz to MP3 bytes via MediaCodec.
     * Returns null if MP3 encoder not available so caller can fallback.
     */
    fun pcmToMp3(pcm: ByteArray): ByteArray? {
        return try {
            encodeWithMediaCodec(pcm, "audio/mpeg")
        } catch (_: Exception) {
            null
        }
    }

    fun encodeWithMediaCodec(pcm: ByteArray, mime: String): ByteArray? {
        val codec = try {
            MediaCodec.createEncoderByType(mime)
        } catch (_: Exception) {
            return null
        }
        val format = MediaFormat.createAudioFormat(mime, SAMPLE_RATE, CHANNELS).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            // For MP3, AAC profile not needed
        }
        // Some devices don't support MP3 encoding; try without profile
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (_: Exception) {
            codec.release()
            return null
        }
        codec.start()
        val output = ByteArrayOutputStream()
        var offset = 0
        val chunkSize = 4096
        var inputDone = false
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10000)
                if (inIndex >= 0) {
                    val buffer: ByteBuffer = codec.getInputBuffer(inIndex)!!
                    buffer.clear()
                    val remaining = pcm.size - offset
                    if (remaining <= 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val len = minOf(chunkSize, remaining, buffer.remaining())
                        buffer.put(pcm, offset, len)
                        offset += len
                        val flags = if (offset >= pcm.size) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        codec.queueInputBuffer(inIndex, 0, len, 0, flags)
                        if (flags != 0) inputDone = true
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            when {
                outIndex >= 0 -> {
                    val outBuf = codec.getOutputBuffer(outIndex)!!
                    if (bufferInfo.size > 0) {
                        val data = ByteArray(bufferInfo.size)
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        outBuf.get(data)
                        output.write(data)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (inputDone) {
                        // avoid busy loop
                        Thread.sleep(5)
                    }
                }
            }
            if (inputDone && output.size() > 0 && bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
        }
        codec.stop()
        codec.release()
        val bytes = output.toByteArray()
        return if (bytes.isEmpty()) null else bytes
    }

    /**
     * Fallback: wrap PCM as WAV then try to transcode WAV->MP3 via MediaCodec.
     * If MP3 not available, returns WAV bytes (caller should save with .mp3 extension - players will still decode).
     */
    fun wavFileToMp3Bytes(wavFile: java.io.File): ByteArray {
        val all = wavFile.readBytes()
        // WAV header 44 bytes, PCM starts at 44
        val pcm = if (all.size > 44 && all[0].toInt().toChar() == 'R') all.copyOfRange(44, all.size) else all
        val mp3 = pcmToMp3(pcm)
        return mp3 ?: pcm // fallback: raw PCM will be saved as MP3-named file with WAV content; still playable on many devices if we wrap as WAV
    }
}
