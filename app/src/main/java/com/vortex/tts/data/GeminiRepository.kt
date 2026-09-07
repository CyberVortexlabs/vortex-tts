package com.vortex.tts.data

import android.util.Base64
import com.vortex.tts.model.ContentRequest
import com.vortex.tts.model.GenerateContentRequest
import com.vortex.tts.model.GenerateContentResponse
import com.vortex.tts.model.GenerationConfig
import com.vortex.tts.model.GeminiModel
import com.vortex.tts.model.PrebuiltVoiceConfig
import com.vortex.tts.model.SupportedTtsModels
import com.vortex.tts.model.SpeechConfig
import com.vortex.tts.model.TextPart
import com.vortex.tts.model.VoiceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

sealed class GeminiResult<out T> {
    data class Success<T>(val value: T) : GeminiResult<T>()
    data class Failure(val error: GeminiError) : GeminiResult<Nothing>()
}

enum class GeminiError {
    INVALID_KEY,
    FORBIDDEN,
    NOT_FOUND,
    RATE_LIMITED,
    BAD_REQUEST,
    NETWORK,
    EMPTY_AUDIO,
    INVALID_BASE64,
    API,
    UNKNOWN
}

class GeminiRepository(private val api: GeminiApi) {

    suspend fun listAvailableTtsModels(apiKey: String): GeminiResult<List<GeminiModel>> = withContext(Dispatchers.IO) {
        try {
            val response = api.listModels(apiKey)
            if (!response.isSuccessful) return@withContext GeminiResult.Failure(mapHttp(response))
            val models = response.body()?.models.orEmpty()
                .filter { model ->
                    val id = model.name?.substringAfterLast('/')
                    id != null && id in SupportedTtsModels.ids
                }
            if (models.isEmpty()) GeminiResult.Failure(GeminiError.NOT_FOUND) else GeminiResult.Success(models)
        } catch (_: IOException) {
            GeminiResult.Failure(GeminiError.NETWORK)
        } catch (_: Exception) {
            GeminiResult.Failure(GeminiError.UNKNOWN)
        }
    }

    suspend fun generatePcm(
        apiKey: String,
        model: String,
        text: String,
        voiceName: String
    ): GeminiResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val isPersian = Regex("[\u0600-\u06FF]").containsMatchIn(text)
            val languageCode = if (isPersian) "fa-IR" else null
            val request = GenerateContentRequest(
                contents = listOf(ContentRequest(parts = listOf(TextPart(text)))),
                generationConfig = GenerationConfig(
                    responseModalities = listOf("AUDIO"),
                    speechConfig = SpeechConfig(
                        voiceConfig = VoiceConfig(
                            prebuiltVoiceConfig = PrebuiltVoiceConfig(voiceName)
                        ),
                        languageCode = languageCode
                    )
                )
            )
            val response = api.generateContent(model, apiKey, request)
            if (!response.isSuccessful) return@withContext GeminiResult.Failure(mapHttp(response))
            val data = findInlineAudio(response.body()) ?: return@withContext GeminiResult.Failure(GeminiError.EMPTY_AUDIO)
            try {
                GeminiResult.Success(Base64.decode(data, Base64.DEFAULT))
            } catch (_: IllegalArgumentException) {
                GeminiResult.Failure(GeminiError.INVALID_BASE64)
            }
        } catch (_: IOException) {
            GeminiResult.Failure(GeminiError.NETWORK)
        } catch (_: Exception) {
            GeminiResult.Failure(GeminiError.UNKNOWN)
        }
    }

    private fun findInlineAudio(response: GenerateContentResponse?): String? =
        response?.candidates.orEmpty()
            .asSequence()
            .flatMap { it.content?.parts.orEmpty().asSequence() }
            .mapNotNull { it.inlineData?.data }
            .firstOrNull { it.isNotBlank() }

    private fun <T> mapHttp(response: Response<T>): GeminiError = when (response.code()) {
        400 -> GeminiError.BAD_REQUEST
        401 -> GeminiError.INVALID_KEY
        403 -> GeminiError.FORBIDDEN
        404 -> GeminiError.NOT_FOUND
        429 -> GeminiError.RATE_LIMITED
        else -> GeminiError.API
    }
}
