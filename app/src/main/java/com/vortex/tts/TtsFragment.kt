package com.vortex.tts

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.vortex.tts.databinding.FragmentTtsBinding

class TtsFragment : Fragment() {
    private var _b: FragmentTtsBinding? = null
    private val b get() = _b!!
    private var voiceMode = "auto"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentTtsBinding.inflate(inflater, container, false)

        b.etTtsText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                b.tvCharCount.text = "${s?.length ?: 0} / 5000"
            }
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
        }
        b.chipAuto.setOnClickListener { select("auto") }
        b.chipFemale.setOnClickListener { select("female") }
        b.chipMale.setOnClickListener { select("male") }

        b.btnPlay.setOnClickListener {
            b.progressTts.visibility = View.VISIBLE
            // TODO: google-genai REST -> PCM -> WAV -> ExoPlayer
        }
        b.btnSaveAudio.setOnClickListener {
            // TODO: save WAV/MP3 to MediaStore
        }
        return b.root
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
