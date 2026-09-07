package com.vortex.tts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vortex.tts.databinding.FragmentKeyBinding

class KeyFragment : Fragment() {
    private var _b: FragmentKeyBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentKeyBinding.inflate(inflater, container, false)
        val masterKey = MasterKey.Builder(requireContext()).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(requireContext(), "vortex_tts", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        b.etApiKey.setText(prefs.getString("api_key", ""))
        b.btnSaveKey.setOnClickListener {
            prefs.edit().putString("api_key", b.etApiKey.text.toString().trim()).apply()
            b.tvKeyStatus.text = "ذخیره شد ✓"
            b.tvKeyStatus.visibility = View.VISIBLE
        }
        b.btnTest.setOnClickListener {
            b.tvKeyStatus.text = "در حال تست…"
            b.tvKeyStatus.visibility = View.VISIBLE
            // TODO: call Gemini list models via OkHttp
        }
        return b.root
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
