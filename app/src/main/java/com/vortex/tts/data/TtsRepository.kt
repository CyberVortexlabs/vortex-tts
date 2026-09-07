package com.vortex.tts.data

import android.content.Context
import java.io.File

class TtsRepository(private val ctx: Context, private val api: ApiService = ApiService(), private val prefs: SecurePrefsManager = SecurePrefsManager(ctx)) {
    suspend fun testKey(key: String) = api.listModels(key)
    suspend fun generate(text: String, voice: String, model: String, style: String?, loud: LoudnessMode): Result<GenerateResult> {
        val key = prefs.getApiKey() ?: return Result.failure(Exception("API key not set"))
        val pcm = api.generatePcm(key, text, voice, model, style, loud).getOrElse { return Result.failure(it) }
        val tmp = AudioUtils.tempDir(ctx)
        val wav = File(tmp, "tts_${System.currentTimeMillis()}.wav")
        AudioUtils.pcmToWavFile(pcm, wav)
        var mp3: File? = null
        var ok = false
        val cand = File(tmp, wav.nameWithoutExtension + ".mp3")
        ok = AudioUtils.wavToMp3ViaMediaCodec(wav, cand)
        if (ok) mp3 = cand else {
            val m4a = File(tmp, wav.nameWithoutExtension + ".m4a")
            if (AudioUtils.wavToMp3ViaMediaCodec(wav, m4a)) mp3 = m4a
        }
        val name = "vortex_${System.currentTimeMillis()}." + (mp3?.extension ?: "wav")
        val uri = AudioUtils.saveToDownloads(ctx, mp3 ?: wav, name)
        return Result.success(GenerateResult(pcm, wav, mp3, uri, ok))
    }
    data class GenerateResult(val pcm: ByteArray, val wav: File, val mp3: File?, val savedUri: String?, val wavToMp3: Boolean)
}
