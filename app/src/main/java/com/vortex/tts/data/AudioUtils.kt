package com.vortex.tts.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PCM 24kHz 16-bit mono -> WAV (44-byte RIFF) -> MP3/M4A via MediaCodec
 * Save to Downloads/VortexTTS via MediaStore
 */
object AudioUtils {
    const val SAMPLE_RATE = 24000
    const val CHANNELS = 1
    const val BITS = 16

    fun pcmToWav(pcm: ByteArray, sr: Int = SAMPLE_RATE, ch: Int = CHANNELS, bits: Int = BITS): ByteArray {
        val byteRate = sr * ch * bits / 8
        val blockAlign = ch * bits / 8
        val out = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()); out.putInt(36 + pcm.size); out.put("WAVE".toByteArray())
        out.put("fmt ".toByteArray()); out.putInt(16); out.putShort(1); out.putShort(ch.toShort())
        out.putInt(sr); out.putInt(byteRate); out.putShort(blockAlign.toShort()); out.putShort(bits.toShort())
        out.put("data".toByteArray()); out.putInt(pcm.size); out.put(pcm)
        return out.array()
    }

    fun pcmToWavFile(pcm: ByteArray, out: File) { FileOutputStream(out).use { it.write(pcmToWav(pcm)) } }

    fun wavToMp3ViaMediaCodec(wavFile: File, outMp3: File): Boolean {
        return try {
            val pcm = extractPcm(wavFile.readBytes())
            val mime = when {
                hasEncoder("audio/mpeg") -> "audio/mpeg"
                hasEncoder("audio/mp4a-latm") -> "audio/mp4a-latm"
                else -> return false
            }
            val fmt = MediaFormat.createAudioFormat(mime, SAMPLE_RATE, CHANNELS).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val codec = MediaCodec.createEncoderByType(mime)
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val muxer = if (mime == "audio/mp4a-latm") MediaMuxer(outMp3.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) else null
            encodePcm(codec, muxer, pcm, outMp3)
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    private fun hasEncoder(mime: String) = try { MediaCodec.createEncoderByType(mime); true } catch (_: Exception) { false }
    private fun extractPcm(wav: ByteArray) = if (wav.size > 44 && String(wav.sliceArray(0..3)) == "RIFF") wav.copyOfRange(44, wav.size) else wav

    private fun encodePcm(codec: MediaCodec, muxer: MediaMuxer?, pcm: ByteArray, outFile: File) {
        var track = -1; var started = false
        val chunk = 2048; var off = 0; var pts = 0L
        val perChunkUs = (chunk / 2 * 1_000_000L) / SAMPLE_RATE
        val rawOut = if (muxer == null) FileOutputStream(outFile) else null
        val info = MediaCodec.BufferInfo()
        var inDone = false; var outDone = false
        while (!outDone) {
            if (!inDone) {
                val idx = codec.dequeueInputBuffer(10000)
                if (idx >= 0) {
                    val buf = codec.getInputBuffer(idx)!!
                    buf.clear()
                    if (pcm.size - off <= 0) {
                        codec.queueInputBuffer(idx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true
                    } else {
                        val n = minOf(chunk, pcm.size - off, buf.remaining())
                        buf.put(pcm, off, n); off += n
                        codec.queueInputBuffer(idx, 0, n, pts, 0); pts += perChunkUs
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 10000)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxer != null) { track = muxer.addTrack(codec.outputFormat); muxer.start(); started = true }
                }
                outIdx >= 0 -> {
                    val buf = codec.getOutputBuffer(outIdx)!!
                    if (info.size > 0) {
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        if (muxer != null && started) muxer.writeSampleData(track, buf, info)
                        else { val b = ByteArray(info.size); buf.get(b); rawOut?.write(b) }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true
                }
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (inDone) Thread.sleep(5)
            }
        }
        codec.stop(); codec.release()
        muxer?.let { if (started) it.stop(); it.release() }
        rawOut?.close()
    }

    // LAME fallback: native libmp3lame.so + JNI LameEncoder.encode(pcm, sr, ch, outMp3)
    // ffmpeg: ffmpeg -f s16le -ar 24000 -ac 1 -i input.pcm -codec:a libmp3lame -qscale:a 2 output.mp3

    fun saveToDownloads(ctx: Context, src: File, name: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, if (name.endsWith(".mp3")) "audio/mpeg" else if (name.endsWith(".m4a")) "audio/mp4" else "audio/wav")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VortexTTS")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
                ctx.contentResolver.openOutputStream(uri)?.use { o -> src.inputStream().use { it.copyTo(o) } }
                cv.clear(); cv.put(MediaStore.Downloads.IS_PENDING, 0)
                ctx.contentResolver.update(uri, cv, null, null)
                uri.toString()
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VortexTTS")
                if (!dir.exists()) dir.mkdirs()
                val d = File(dir, name); src.copyTo(d, true); d.absolutePath
            }
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    fun tempDir(ctx: Context) = File(ctx.cacheDir, "vortex_tts").apply { mkdirs() }
}
