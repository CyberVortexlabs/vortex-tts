package com.vortex.tts.model

import com.google.gson.annotations.SerializedName

data class GeminiModelListResponse(
    @SerializedName("models") val models: List<GeminiModel> = emptyList()
)

data class GeminiModel(
    @SerializedName("name") val name: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("supportedGenerationMethods") val supportedGenerationMethods: List<String> = emptyList()
)

object SupportedTtsModels {
    const val FLASH = "gemini-2.5-flash-preview-tts"
    const val PRO = "gemini-2.5-pro-preview-tts"

    val ids = listOf(FLASH, PRO)

    fun title(id: String): String = when (id) {
        FLASH -> "Gemini 2.5 Flash TTS"
        PRO -> "Gemini 2.5 Pro TTS"
        else -> id
    }
}
