package com.vortex.tts.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * google-genai REST — generativelanguage.googleapis.com
 * generateContent with responseModalities AUDIO, list models for key test
 */
class ApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    companion object { const val BASE = "https://generativelanguage.googleapis.com/v1beta" }

    suspend fun listModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$BASE/models?key=${apiKey.trim()}").get().build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}: ${body.take(400)}"))
            val json = JSONObject(body)
            val arr = json.optJSONArray("models") ?: JSONArray()
            val names = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val n = arr.getJSONObject(i).optString("name","").removePrefix("models/")
                if (n.isNotBlank()) names.add(n)
            }
            if (names.isEmpty()) names.addAll(TtsModels.ALL)
            Result.success(names)
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * TTS generate — PCM 24kHz s16le mono base64 inlineData
     * stylePrompt optional appended to contents; loudness injected as hint
     */
    suspend fun generatePcm(
        apiKey: String, text: String, voice: String = "Kore",
        model: String = TtsModels.FLASH_PREVIEW_TTS,
        stylePrompt: String? = null, loudness: LoudnessMode = LoudnessMode.AUTO
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val finalText = buildPrompt(text, stylePrompt, loudness)
            val voiceName = TtsVoice.from(voice).voiceName
            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", finalText)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName))
                        })
                    })
                })
            }
            val req = Request.Builder()
                .url("$BASE/models/$model:generateContent?key=${apiKey.trim()}")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .header("Content-Type","application/json").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext Result.failure(Exception("TTS ${resp.code}: ${body.take(600)}"))
            val json = JSONObject(body)
            val cand = json.optJSONArray("candidates") ?: return@withContext Result.failure(Exception("No candidates"))
            if (cand.length()==0) return@withContext Result.failure(Exception("Empty candidates"))
            val parts = cand.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                ?: return@withContext Result.failure(Exception("No parts"))
            for (i in 0 until parts.length()) {
                val inline = parts.getJSONObject(i).optJSONObject("inlineData")
                if (inline != null) {
                    val b64 = inline.optString("data","")
                    if (b64.isNotBlank()) return@withContext Result.success(Base64.decode(b64, Base64.DEFAULT))
                }
            }
            Result.failure(Exception("No inlineData AUDIO. Body: ${body.take(600)}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun buildPrompt(text: String, style: String?, loud: LoudnessMode): String {
        val sb = StringBuilder()
        if (!style.isNullOrBlank()) { sb.append(style.trim()); sb.append("\n\n") }
        when(loud) {
            LoudnessMode.LOUD -> sb.append("[Speak loudly and clearly] ")
            LoudnessMode.NORMAL -> sb.append("[Speak at normal volume] ")
            else -> {}
        }
        sb.append(text.trim())
        return sb.toString()
    }
}
