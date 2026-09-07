# Vortex TTS

Vortex TTS — Gemini 2.5 TTS integration (Vortex Panel dark glass).

**Design:** vortex gradient `#1a0b2e → #0f2027`, glass cards `20dp` + `blur` orbs, Vazirmatn font, `ic_launcher` from vortex image.

**Screens:**
- **Key:** API Key `AQ…` input + Test + model list (RecyclerView)
- **TTS:** text 5000ch + voice chips (زن/مرد/خودکار) + loudness slider + Style Prompt + Play/Save

**Stack:** EncryptedSharedPreferences • google-genai REST • PCM→WAV • Material3

Build: `./gradlew assembleDebug`
