package com.vortex.tts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.vortex.tts.data.ApiService
import com.vortex.tts.data.SecurePrefsManager
import com.vortex.tts.databinding.FragmentKeyBinding
import kotlinx.coroutines.launch

class KeyFragment : Fragment() {
    private var _b: FragmentKeyBinding? = null
    private val b get() = _b!!
    private val api = ApiService()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentKeyBinding.inflate(inflater, container, false)
        val prefs = SecurePrefsManager(requireContext())
        b.etApiKey.setText(prefs.getApiKey() ?: "")

        b.btnSaveKey.setOnClickListener {
            val k = b.etApiKey.text.toString().trim()
            if (k.isBlank()) { b.tvKeyStatus.text = "کلید خالی است"; b.tvKeyStatus.visibility = View.VISIBLE; return@setOnClickListener }
            if (!k.startsWith("AQ.")) { b.tvKeyStatus.text = "فرمت کلید باید AQ.Ab8... باشد"; b.tvKeyStatus.visibility = View.VISIBLE; return@setOnClickListener }
            prefs.saveApiKey(k)
            b.tvKeyStatus.text = "ذخیره شد ✓ (EncryptedSharedPreferences)"
            b.tvKeyStatus.visibility = View.VISIBLE
        }

        b.btnTest.setOnClickListener {
            val k = b.etApiKey.text.toString().trim().ifBlank { prefs.getApiKey() ?: "" }
            if (k.isBlank()) { b.tvKeyStatus.text = "کلید تنظیم نشده"; b.tvKeyStatus.visibility = View.VISIBLE; return@setOnClickListener }
            b.tvKeyStatus.text = "در حال تست… (GET /v1beta/models)"
            b.tvKeyStatus.visibility = View.VISIBLE
            b.btnTest.isEnabled = false
            lifecycleScope.launch {
                val res = api.listModels(k)
                b.btnTest.isEnabled = true
                if (res.isSuccess) {
                    prefs.saveApiKey(k)
                    val models = res.getOrNull() ?: emptyList()
                    b.tvKeyStatus.text = "✓ کلید معتبر — مدل‌ها: " + models.take(5).joinToString(", ")
                } else {
                    b.tvKeyStatus.text = "✗ خطا: " + (res.exceptionOrNull()?.message ?: "نامشخص")
                }
            }
        }
        return b.root
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
