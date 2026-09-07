package com.vortex.tts.ui

import android.content.Context
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vortex.tts.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class TtsViewModel(private val ctx: Context) : ViewModel() {
    private val prefs = SecurePrefsManager(ctx)
    private val repo = TtsRepository(ctx)
    private val api = ApiService()

    private val _keyState = MutableStateFlow<KeyState>(KeyState.Idle)
    val keyState: StateFlow<KeyState> = _keyState
    private val _genState = MutableStateFlow<GenState>(GenState.Idle)
    val genState: StateFlow<GenState> = _genState
    private val _model = MutableStateFlow(prefs.getModel())
    val model: StateFlow<String> = _model
    private val _voice = MutableStateFlow(prefs.getVoice())
    val voice: StateFlow<String> = _voice

    private var player: MediaPlayer? = null
    var lastFile: File? = null
        private set

    fun setModel(v: String) { _model.value = v; prefs.saveModel(v) }
    fun setVoice(v: String) { _voice.value = v; prefs.saveVoice(v) }

    fun testKey(key: String) {
        val k = key.trim()
        if (k.isBlank()) { _keyState.value = KeyState.Error("کلید خالی است"); return }
        _keyState.value = KeyState.Loading
        viewModelScope.launch {
            val r = api.listModels(k)
            if (r.isSuccess) { prefs.saveApiKey(k); _keyState.value = KeyState.Success(r.getOrNull() ?: emptyList()) }
            else _keyState.value = KeyState.Error(r.exceptionOrNull()?.message ?: "خطا")
        }
    }

    fun generate(text: String, style: String?, loud: LoudnessMode) {
        if (text.isBlank()) { _genState.value = GenState.Error("متن خالی است"); return }
        if (!prefs.hasApiKey()) { _genState.value = GenState.Error("کلید تنظیم نشده (AQ.Ab8...)"); return }
        _genState.value = GenState.Loading
        viewModelScope.launch {
            val r = repo.generate(text, _voice.value, _model.value, style?.takeIf { it.isNotBlank() }, loud)
            if (r.isSuccess) {
                val v = r.getOrNull()!!
                lastFile = v.mp3 ?: v.wav
                _genState.value = GenState.Success(lastFile!!, v.savedUri, v.wavToMp3)
            } else _genState.value = GenState.Error(r.exceptionOrNull()?.message ?: "خطا")
        }
    }

    fun play() {
        val f = lastFile ?: return
        try { player?.release(); player = MediaPlayer().apply { setDataSource(f.absolutePath); prepare(); start() } }
        catch (e: Exception) { _genState.value = GenState.Error("پخش ناموفق: ${e.message}") }
    }
    fun stop() { player?.stop(); player?.release(); player = null }
    override fun onCleared() { player?.release(); super.onCleared() }

    sealed class KeyState { object Idle : KeyState(); object Loading : KeyState(); data class Success(val models: List<String>) : KeyState(); data class Error(val msg: String) : KeyState() }
    sealed class GenState { object Idle : GenState(); object Loading : GenState(); data class Success(val file: File, val uri: String?, val mp3: Boolean) : GenState(); data class Error(val msg: String) : GenState() }
}
