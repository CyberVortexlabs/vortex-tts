package com.vortex.tts.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.vortex.tts.MainActivity
import com.vortex.tts.R
import com.vortex.tts.data.GeminiClient
import com.vortex.tts.data.GeminiError
import com.vortex.tts.data.GeminiRepository
import com.vortex.tts.data.GeminiResult
import com.vortex.tts.data.SecureStorage
import com.vortex.tts.databinding.FragmentKeyBinding
import com.vortex.tts.model.GeminiModel
import com.vortex.tts.model.SupportedTtsModels
import kotlinx.coroutines.launch

class KeyFragment : Fragment() {
    interface Callbacks {
        fun onConnectionSuccess()
    }

    private var _binding: FragmentKeyBinding? = null
    private val binding get() = _binding!!

    private lateinit var secureStorage: SecureStorage
    private val repository by lazy { GeminiRepository(GeminiClient.api) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        secureStorage = SecureStorage(requireContext())
        binding.apiKeyEditText.setText(secureStorage.getApiKey().orEmpty())
        // Smooth UX: a previously saved key lets the user skip straight ahead
        // without re-testing, while still allowing a fresh test anytime.
        if (!secureStorage.getApiKey().isNullOrBlank()) {
            binding.continueButton.visibility = View.VISIBLE
            showStatus("کلید ذخیره‌شده یافت شد؛ می‌توانید ادامه دهید یا اتصال را دوباره تست کنید.", true)
        }
        binding.backButton.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.testButton.setOnClickListener { testConnection() }
        binding.continueButton.setOnClickListener {
            if (secureStorage.getApiKey().isNullOrBlank()) {
                showStatus("ابتدا اتصال را با یک کلید معتبر تست کنید.", false)
            } else {
                (activity as? Callbacks)?.onConnectionSuccess()
            }
        }
    }

    private fun testConnection() {
        val apiKey = binding.apiKeyEditText.text?.toString()?.trim().orEmpty()
        if (apiKey.isBlank()) {
            binding.apiKeyLayout.error = "کلید API را وارد کنید."
            return
        }
        binding.apiKeyLayout.error = null
        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = repository.listAvailableTtsModels(apiKey)) {
                is GeminiResult.Success -> {
                    secureStorage.saveApiKey(apiKey)
                    renderModels(result.value)
                    showStatus("اتصال موفق بود و مدل‌های TTS در دسترس هستند.", true)
                }
                is GeminiResult.Failure -> {
                    renderModels(emptyList())
                    showStatus(errorMessage(result.error), false)
                }
            }
            setLoading(false)
        }
    }

    private fun renderModels(models: List<GeminiModel>) {
        binding.modelsContainer.visibility = if (models.isEmpty()) View.GONE else View.VISIBLE
        binding.continueButton.visibility = if (models.isEmpty()) View.GONE else View.VISIBLE
        binding.modelsList.removeAllViews()
        models.sortedBy { SupportedTtsModels.ids.indexOf(it.name?.substringAfterLast('/')) }
            .forEach { model ->
                val card = MaterialCardView(requireContext()).apply {
                    radius = 14f
                    strokeWidth = 1
                    strokeColor = ContextCompat.getColor(context, R.color.cosmic_border)
                    setCardBackgroundColor(ContextCompat.getColor(context, R.color.cosmic_surface))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                }
                val label = MaterialTextView(requireContext()).apply {
                    val id = model.name?.substringAfterLast('/') ?: ""
                    text = "${SupportedTtsModels.title(id)}\n$id"
                    setTextColor(ContextCompat.getColor(context, R.color.cosmic_text))
                    textSize = 13f
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    typeface = ResourcesCompat.getFont(context, R.font.vazirmatn_medium)
                }
                card.addView(label)
                binding.modelsList.addView(card)
            }
    }

    private fun showStatus(message: String, success: Boolean) {
        binding.statusText.text = message
        binding.statusText.setTextColor(
            ContextCompat.getColor(requireContext(), if (success) R.color.cosmic_success else R.color.cosmic_error)
        )
        binding.statusText.visibility = View.VISIBLE
    }

    private fun errorMessage(error: GeminiError): String = when (error) {
        GeminiError.INVALID_KEY -> "کلید API نامعتبر است."
        GeminiError.FORBIDDEN -> "دسترسی به API رد شد. کلید یا مجوز پروژه را بررسی کنید."
        GeminiError.NOT_FOUND -> "مدل‌های TTS موردنظر در دسترس نیستند."
        GeminiError.RATE_LIMITED -> "محدودیت درخواست API فعال شده است. کمی بعد دوباره تلاش کنید."
        GeminiError.BAD_REQUEST -> "درخواست API نامعتبر بود."
        GeminiError.NETWORK -> "اتصال شبکه برقرار نشد. اینترنت را بررسی کنید."
        else -> "خطای API رخ داد. دوباره تلاش کنید."
    }

    private fun setLoading(loading: Boolean) {
        binding.testButton.isEnabled = !loading
        binding.apiKeyEditText.isEnabled = !loading
        binding.testProgress.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) binding.statusText.visibility = View.GONE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = KeyFragment()
    }
}
