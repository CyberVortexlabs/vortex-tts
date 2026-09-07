package com.vortex.tts.model

import com.google.gson.annotations.SerializedName

data class GenerateContentRequest(
    @SerializedName("contents") val contents: List<ContentRequest>,
    @SerializedName("generationConfig") val generationConfig: GenerationConfig
)

data class ContentRequest(
    @SerializedName("parts") val parts: List<TextPart>
)

data class TextPart(
    @SerializedName("text") val text: String
)

data class GenerationConfig(
    @SerializedName("responseModalities") val responseModalities: List<String>,
    @SerializedName("speechConfig") val speechConfig: SpeechConfig
)

data class SpeechConfig(
    @SerializedName("voiceConfig") val voiceConfig: VoiceConfig,
    @SerializedName("languageCode") val languageCode: String? = null
)

data class VoiceConfig(
    @SerializedName("prebuiltVoiceConfig") val prebuiltVoiceConfig: PrebuiltVoiceConfig
)

data class PrebuiltVoiceConfig(
    @SerializedName("voiceName") val voiceName: String
)

data class GenerateContentResponse(
    @SerializedName("candidates") val candidates: List<Candidate> = emptyList()
)

data class Candidate(
    @SerializedName("content") val content: ResponseContent? = null
)

data class ResponseContent(
    @SerializedName("parts") val parts: List<ResponsePart> = emptyList()
)

data class ResponsePart(
    @SerializedName("inlineData") val inlineData: InlineData? = null
)

data class InlineData(
    @SerializedName("mimeType") val mimeType: String? = null,
    @SerializedName("data") val data: String? = null
)
