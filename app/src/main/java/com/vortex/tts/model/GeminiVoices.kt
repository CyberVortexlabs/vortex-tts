package com.vortex.tts.model

object GeminiVoices {
    const val DEFAULT_VOICE = "Kore"
    const val PREVIEW_TEXT = "سلام، این صدای آزمایشی ورتکس است"
    const val PREVIEW_TEXT_EN = "Hello, this is Vortex voice preview"
    val VOICES = listOf(
        "Achernar", "Achird", "Algenib", "Algieba", "Alnilam", "Aoede", "Autonoe", "Callirrhoe", "Charon", "Despina", "Enceladus", "Erinome", "Fenrir", "Gacrux", "Iapetus", "Kore", "Laomedeia", "Leda", "Orus", "Puck", "Pulcherrima", "Rasalgethi", "Sadachbia", "Sadaltager", "Schedar", "Sulafat", "Umbriel", "Vindemiatrix", "Zephyr", "Zubenelgenubi"
    )

    fun rawResName(voice: String): String = "preview_" + voice.lowercase()

    fun displayName(voice: String): String = voice
}
