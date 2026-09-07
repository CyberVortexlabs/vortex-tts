package com.vortex.tts.audio

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object PcmToWav {
    const val SAMPLE_RATE = 24_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16

    @Throws(IOException::class)
    fun toWavFile(pcm: ByteArray, output: File) {
        require(pcm.isNotEmpty()) { "PCM data is empty" }
        FileOutputStream(output).use { out ->
            writeHeader(out, pcm.size, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE)
            out.write(pcm)
            out.flush()
        }
    }

    fun toWavBytes(pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(44 + pcm.size)
        writeHeader(out, pcm.size, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE)
        out.write(pcm)
        return out.toByteArray()
    }

    private fun writeHeader(
        out: java.io.OutputStream,
        pcmLength: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val riffChunkSize = 36 + pcmLength

        out.writeAscii("RIFF")
        out.writeLittleEndianInt(riffChunkSize)
        out.writeAscii("WAVE")
        out.writeAscii("fmt ")
        out.writeLittleEndianInt(16)
        out.writeLittleEndianShort(1)
        out.writeLittleEndianShort(channels)
        out.writeLittleEndianInt(sampleRate)
        out.writeLittleEndianInt(byteRate)
        out.writeLittleEndianShort(blockAlign)
        out.writeLittleEndianShort(bitsPerSample)
        out.writeAscii("data")
        out.writeLittleEndianInt(pcmLength)
    }

    private fun java.io.OutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun java.io.OutputStream.writeLittleEndianInt(value: Int) {
        write(byteArrayOf(
            (value and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 24) and 0xFF).toByte()
        ))
    }

    private fun java.io.OutputStream.writeLittleEndianShort(value: Int) {
        write(byteArrayOf(
            (value and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte()
        ))
    }
}
