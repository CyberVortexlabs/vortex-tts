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
    private var editingId: String? = null

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

        binding.backButton.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.testButton.setOnClickListener { testAndSave() }
        binding.cancelEditButton.setOnClickListener { cancelEdit() }
        binding.continueButton.setOnClickListener {
            if (secureStorage.getApiKeys().isEmpty()) {
                showStatus("ابتدا حداقل یک کلید اضافه کنید.", false)
            } else {
                (activity as? Callbacks)?.onConnectionSuccess()
            }
        }

        refreshList()

        if (secureStorage.getApiKeys().isNotEmpty()) {
            binding.continueButton.visibility = View.VISIBLE
            showStatus("کلید‌های ذخیره‌شده: ${secureStorage.getApiKeys().size} مورد", true)
        }
    }

    private fun refreshList() {
        val keys = secureStorage.getApiKeys()
        val activeId = secureStorage.getActiveKeyId()
        binding.keysList.removeAllViews()
        binding.keysContainer.visibility = if (keys.isEmpty()) View.GONE else View.VISIBLE
        binding.continueButton.visibility = if (keys.isEmpty()) View.GONE else View.VISIBLE

        if (keys.isEmpty()) {
            binding.emptyKeysText.visibility = View.VISIBLE
            return
        }
        binding.emptyKeysText.visibility = View.GONE

        keys.forEach { entry ->
            val isActive = entry.id == activeId
            val card = MaterialCardView(requireContext()).apply {
                radius = 14f
                strokeWidth = if (isActive) 2 else 1
                strokeColor = ContextCompat.getColor(
                    context,
                    if (isActive) R.color.cosmic_success else R.color.cosmic_border
                )
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.cosmic_surface))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                isClickable = true
                setOnClickListener {
                    secureStorage.setActiveKeyId(entry.id)
                    refreshList()
                    showStatus("کلید فعال: ${entry.name}", true)
                }
            }
            val inner = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            val title = MaterialTextView(requireContext()).apply {
                text = "${entry.name} ${if (isActive) "● فعال" else ""}"
                setTextColor(ContextCompat.getColor(context, if (isActive) R.color.cosmic_success else R.color.cosmic_text))
                textSize = 13f
                typeface = ResourcesCompat.getFont(context, R.font.vazirmatn_bold)
            }
            val masked = MaterialTextView(requireContext()).apply {
                text = maskKey(entry.key)
                setTextColor(ContextCompat.getColor(context, R.color.cosmic_text_secondary))
                textSize = 11f
                typeface = ResourcesCompat.getFont(context, R.font.vazirmatn_regular)
            }
            val actions = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }
            val editBtn = androidx.appcompat.widget.AppCompatButton(requireContext()).apply {
                text = "ویرایش"
                textSize = 11f
                setOnClickListener { startEdit(entry.id) }
            }
            val delBtn = androidx.appcompat.widget.AppCompatButton(requireContext()).apply {
                text = "حذف"
                textSize = 11f
                setOnClickListener { deleteKey(entry.id) }
            }
            actions.addView(editBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
            actions.addView(delBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            inner.addView(title)
            inner.addView(masked)
            inner.addView(actions)
            card.addView(inner)
            binding.keysList.addView(card)
        }
        renderModels(emptyList())
    }

    private fun startEdit(id: String) {
        val entry = secureStorage.getApiKeys().firstOrNull { it.id == id } ?: return
        editingId = id
        binding.keyNameEditText.setText(entry.name)
        binding.apiKeyEditText.setText(entry.key)
        binding.testButton.text = "ذخیره تغییرات"
        binding.cancelEditButton.visibility = View.VISIBLE
        showStatus("در حال ویرایش: ${entry.name}", true)
    }

    private fun cancelEdit() {
        editingId = null
        binding.keyNameEditText.text?.clear()
        binding.apiKeyEditText.text?.clear()
        binding.apiKeyLayout.error = null
        binding.keyNameLayout.error = null
        binding.testButton.text = getString(R.string.test_connection)
        binding.cancelEditButton.visibility = View.GONE
        showStatus("ویرایش لغو شد.", true)
    }

    private fun deleteKey(id: String) {
        secureStorage.deleteApiKey(id)
        if (editingId == id) cancelEdit()
        refreshList()
        showStatus("کلید حذف شد.", true)
    }

    private fun testAndSave() {
        val name = binding.keyNameEditText.text?.toString()?.trim().orEmpty()
        val apiKey = binding.apiKeyEditText.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            binding.keyNameLayout.error = "نام کلید را وارد کنید."
            return
        }
        if (apiKey.isBlank()) {
            binding.apiKeyLayout.error = "کلید API را وارد کنید."
            return
        }
        binding.keyNameLayout.error = null
        binding.apiKeyLayout.error = null
        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = repository.listAvailableTtsModels(apiKey)) {
                is GeminiResult.Success -> {
                    if (editingId != null) {
                        secureStorage.updateApiKey(editingId!!, name, apiKey)
                        showStatus("کلید ویرایش شد و اتصال موفق بود.", true)
                    } else {
                        val entry = secureStorage.addApiKey(name, apiKey)
                        secureStorage.setActiveKeyId(entry.id)
                        showStatus("کلید اضافه شد و اتصال موفق بود.", true)
                    }
                    renderModels(result.value)
                    editingId = null
                    binding.keyNameEditText.text?.clear()
                    binding.apiKeyEditText.text?.clear()
                    binding.testButton.text = getString(R.string.test_connection)
                    binding.cancelEditButton.visibility = View.GONE
                    refreshList()
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

    private fun maskKey(key: String): String {
        if (key.length <= 8) return "••••"
        return key.take(4) + "••••" + key.takeLast(4)
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
        binding.keyNameEditText.isEnabled = !loading
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
