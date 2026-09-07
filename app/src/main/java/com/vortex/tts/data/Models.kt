package com.vortex.tts.data

object TtsModels {
    const val FLASH_PREVIEW_TTS = "gemini-2.5-flash-preview-tts"
    const val PRO_PREVIEW_TTS = "gemini-2.5-pro-preview-tts"
    val ALL = listOf(FLASH_PREVIEW_TTS, PRO_PREVIEW_TTS)
}

enum class TtsVoice(val voiceName: String, val gender: String) {
    Kore("Kore","female"), Puck("Puck","male"), Charon("Charon","male"),
    Fenrir("Fenrir","male"), Aoede("Aoede","female"), Leda("Leda","female"),
    Orus("Orus","male"), Zephyr("Zephyr","female");
    companion object {
        fun from(name: String) = values().find { it.voiceName.equals(name,true) } ?: Kore
        val names get() = values().map { it.voiceName }
    }
}

enum class LoudnessMode { AUTO, NORMAL, LOUD }

data class GenerateRequest(
    val text: String,
    val voice: String = "Kore",
    val model: String = TtsModels.FLASH_PREVIEW_TTS,
    val stylePrompt: String? = null,
    val loudness: LoudnessMode = LoudnessMode.AUTO
)
