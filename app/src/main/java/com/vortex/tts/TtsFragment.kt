package com.vortex.tts

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.vortex.tts.data.*
import com.vortex.tts.databinding.FragmentTtsBinding
import kotlinx.coroutines.launch
import java.io.File

class TtsFragment : Fragment() {
    private var _b: FragmentTtsBinding? = null
    private val b get() = _b!!
    private var voiceMode = "auto"
    private var loudness = LoudnessMode.AUTO
    private var player: MediaPlayer? = null
    private var lastFile: File? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentTtsBinding.inflate(inflater, container, false)
        val prefs = SecurePrefsManager(requireContext())
        val repo = TtsRepository(requireContext())
        val api = ApiService()

        // Model spinner (if exists) else use prefs default
        val models = TtsModels.ALL
        try {
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, models)
            b.spinnerModel?.adapter = adapter
            b.spinnerModel?.setSelection(models.indexOf(prefs.getModel()).coerceAtLeast(0))
            b.spinnerModel?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) { prefs.saveModel(models[pos]) }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
        } catch (_: Exception) {}

        // Voice chips: map auto/female/male to actual voice names
        fun voiceFor(mode: String): String = when(mode) {
            "female" -> "Kore"
            "male" -> "Puck"
            else -> prefs.getVoice()
        }
        // Voice dropdown if exists
        try {
            val vAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, TtsVoice.names)
            b.spinnerVoice?.adapter = vAdapter
            b.spinnerVoice?.setSelection(TtsVoice.names.indexOf(prefs.getVoice()).coerceAtLeast(0))
            b.spinnerVoice?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) { prefs.saveVoice(TtsVoice.names[pos]) }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
        } catch (_: Exception) {}

        b.etTtsText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { b.tvCharCount.text = "${s?.length ?: 0} / 5000" }
            override fun afterTextChanged(s: Editable?) {}
        })

        fun select(mode: String) {
            voiceMode = mode
            val selBg = requireContext().getDrawable(R.drawable.btn_vortex_primary)
            val glassBg = requireContext().getDrawable(R.drawable.bg_glass_chip)
            b.chipAuto.background = if (mode == "auto") selBg else glassBg
            b.chipFemale.background = if (mode == "female") selBg else glassBg
            b.chipMale.background = if (mode == "male") selBg else glassBg
            b.chipAuto.setTextColor(if (mode == "auto") 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt())
            b.chipFemale.setTextColor(if (mode == "female") 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt())
            b.chipMale.setTextColor(if (mode == "male") 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt())
            loudness = when(mode) { "male" -> LoudnessMode.LOUD; "female" -> LoudnessMode.NORMAL; else -> LoudnessMode.AUTO }
        }
        b.chipAuto.setOnClickListener { select("auto") }
        b.chipFemale.setOnClickListener { select("female") }
        b.chipMale.setOnClickListener { select("male") }
        select("auto")

        b.btnPlay.setOnClickListener {
            val text = b.etTtsText.text.toString().trim()
            if (text.isBlank()) { Toast.makeText(requireContext(), "متن خالی است", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val style = try { b.etStylePrompt?.text?.toString()?.trim()?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }
            val model = try { b.spinnerModel?.selectedItem?.toString() ?: prefs.getModel() } catch (_: Exception) { prefs.getModel() }
            val voice = if (voiceMode == "auto") (try { b.spinnerVoice?.selectedItem?.toString() ?: prefs.getVoice() } catch(_: Exception){ prefs.getVoice() }) else voiceFor(voiceMode)

            b.progressTts.visibility = View.VISIBLE
            b.btnPlay.isEnabled = false
            b.tvTtsStatus.text = "در حال تولید…"
            b.tvTtsStatus.visibility = View.VISIBLE

            lifecycleScope.launch {
                try {
                    val res = repo.generate(text, voice, model, style, loudness)
                    b.progressTts.visibility = View.GONE
                    b.btnPlay.isEnabled = true
                    if (res.isSuccess) {
                        val r = res.getOrNull()!!
                        lastFile = r.mp3 ?: r.wav
                        b.tvTtsStatus.text = "✓ تولید شد: ${lastFile!!.name} (${lastFile!!.length()/1024}KB) — ذخیره: ${r.savedUri ?: "cache"}"
                        // Auto playback via MediaPlayer
                        try {
                            player?.release()
                            player = MediaPlayer().apply { setDataSource(lastFile!!.absolutePath); prepare(); start() }
                            Toast.makeText(requireContext(), "در حال پخش…", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) { b.tvTtsStatus.text = "فایل ساخته شد اما پخش ناموفق: ${e.message}" }
                        b.btnSaveAudio.visibility = View.VISIBLE
                        b.btnSaveAudio.setOnClickListener {
                            Toast.makeText(requireContext(), "ذخیره شد در Downloads/VortexTTS", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        b.tvTtsStatus.text = "✗ خطا: ${res.exceptionOrNull()?.message}"
                    }
                } catch (e: Exception) {
                    b.progressTts.visibility = View.GONE; b.btnPlay.isEnabled = true
                    b.tvTtsStatus.text = "✗ ${e.message}"
                }
            }
        }

        b.btnSaveAudio.setOnClickListener {
            val f = lastFile
            if (f == null) { Toast.makeText(requireContext(), "فایلی برای ذخیره نیست", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val uri = AudioUtils.saveToDownloads(requireContext(), f, f.name)
            Toast.makeText(requireContext(), if (uri != null) "ذخیره شد: $uri" else "ذخیره ناموفق", Toast.LENGTH_LONG).show()
        }

        return b.root
    }

    override fun onDestroyView() { player?.release(); player = null; super.onDestroyView(); _b = null }
}
