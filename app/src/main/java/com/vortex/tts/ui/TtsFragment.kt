package com.vortex.tts.ui

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.vortex.tts.MainActivity
import com.vortex.tts.R
import com.vortex.tts.audio.AudioPlayer
import com.vortex.tts.audio.PcmToWav
import com.vortex.tts.data.GeminiClient
import com.vortex.tts.data.GeminiError
import com.vortex.tts.data.GeminiRepository
import com.vortex.tts.data.GeminiResult
import com.vortex.tts.data.SecureStorage
import com.vortex.tts.databinding.FragmentTtsBinding
import com.vortex.tts.model.SupportedTtsModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TtsFragment : Fragment() {
    private var _binding: FragmentTtsBinding? = null
    private val binding get() = _binding!!
    private val repository by lazy { GeminiRepository(GeminiClient.api) }
    private lateinit var secureStorage: SecureStorage
    private val audioPlayer = AudioPlayer()

    private var generatedFile: File? = null
    private var generatedFingerprint: String? = null
    private var directVoice: String = VOICES.first()
    private var pendingSaveAfterPermission = false

    private val writePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingSaveAfterPermission) saveCurrentAudioToMediaStore()
        else if (!granted) showStatus("مجوز ذخیره‌سازی داده نشد.", false)
        pendingSaveAfterPermission = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTtsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        secureStorage = SecureStorage(requireContext())
        setupUi()
    }

    private fun setupUi() {
        val voiceAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            VOICES
        )
        binding.directVoiceSpinner.adapter = voiceAdapter
        binding.directVoiceSpinner.setSelection(0, false)
        binding.directVoiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                directVoice = VOICES[position]
                invalidateGeneratedAudio()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.textEditText.addTextChangedListener(SimpleTextWatcher {
            binding.charCounter.text = "${binding.textEditText.text?.length ?: 0} / 5000"
            invalidateGeneratedAudio()
        })
        binding.styleEditText.addTextChangedListener(SimpleTextWatcher { invalidateGeneratedAudio() })

        binding.chipHappy.setOnClickListener { setStyle("با لحن شاد") }
        binding.chipCalm.setOnClickListener { setStyle("با لحن آرام") }
        binding.chipEpic.setOnClickListener { setStyle("با لحن حماسی") }
        binding.chipFormal.setOnClickListener { setStyle("با لحن رسمی") }
        binding.chipSerious.setOnClickListener { setStyle("با صدای آرام و جدی") }
        binding.chipWhisper.setOnClickListener { setStyle("به صورت نجوا") }

        binding.voiceAutoChip.setOnClickListener { selectVoice("Kore") }
        binding.voiceFemaleChip.setOnClickListener { selectVoice("Kore") }
        binding.voiceMaleChip.setOnClickListener { selectVoice("Puck") }

        binding.loudAutoChip.setOnClickListener { invalidateGeneratedAudio() }
        binding.loudNormalChip.setOnClickListener { invalidateGeneratedAudio() }
        binding.loudLoudChip.setOnClickListener { invalidateGeneratedAudio() }

        binding.modelSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                SupportedTtsModels.title(SupportedTtsModels.FLASH),
                SupportedTtsModels.title(SupportedTtsModels.PRO)
            )
        )
        binding.modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                invalidateGeneratedAudio()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.playButton.setOnClickListener {
            if (audioPlayer.isPlaying()) {
                audioPlayer.stop()
                binding.playButton.text = getString(R.string.play)
            } else {
                generateAndPlay()
            }
        }

        binding.saveButton.setOnClickListener { generateAndSave() }
        binding.changeKeyButton.setOnClickListener { (activity as? MainActivity)?.openKeyScreen() }
        loadAvailableModels()
    }

    private fun loadAvailableModels() {
        val key = secureStorage.getApiKey() ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = repository.listAvailableTtsModels(key)) {
                is GeminiResult.Success -> {
                    val ids = result.value.mapNotNull { it.name?.substringAfterLast('/') }
                    updateModelSpinner(ids)
                }
                is GeminiResult.Failure -> Unit
            }
        }
    }

    private fun updateModelSpinner(ids: List<String>) {
        val safeIds = ids.filter { it in SupportedTtsModels.ids }
        if (safeIds.isEmpty()) return
        binding.modelSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            safeIds.map(SupportedTtsModels::title)
        )
        binding.modelSpinner.setSelection(0, false)
    }

    private fun setStyle(style: String) {
        binding.styleEditText.setText(style)
        binding.styleEditText.setSelection(binding.styleEditText.text?.length ?: 0)
    }

    private fun selectVoice(voice: String) {
        val index = VOICES.indexOf(voice).coerceAtLeast(0)
        binding.directVoiceSpinner.setSelection(index, true)
        directVoice = voice
        invalidateGeneratedAudio()
    }

    private fun selectedModel(): String {
        val title = binding.modelSpinner.selectedItem?.toString().orEmpty()
        return when (title) {
            SupportedTtsModels.title(SupportedTtsModels.PRO) -> SupportedTtsModels.PRO
            else -> SupportedTtsModels.FLASH
        }
    }

    private fun finalPrompt(): String {
        val text = binding.textEditText.text?.toString().orEmpty()
        val style = binding.styleEditText.text?.toString()?.trim().orEmpty()
        val loud = when {
            binding.loudLoudChip.isChecked -> "با صدای بلند و پرانرژی بخوان."
            else -> ""
        }
        return listOf(style, loud, text).filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun generateAndPlay() {
        if (binding.textEditText.text.isNullOrBlank()) {
            showStatus("متن ورودی خالی است.", false)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val file = ensureGeneratedAudio() ?: return@launch
            audioPlayer.play(
                file = file,
                onCompletion = {
                    _binding?.playButton?.text = getString(R.string.play)
                },
                onError = {
                    _binding?.let {
                        it.playButton.text = getString(R.string.play)
                        showStatus("پخش فایل صوتی با خطا مواجه شد.", false)
                    }
                }
            )
            binding.playButton.text = getString(R.string.stop)
        }
    }

    private fun generateAndSave() {
        if (binding.textEditText.text.isNullOrBlank()) {
            showStatus("متن ورودی خالی است.", false)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val file = ensureGeneratedAudio() ?: return@launch
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingSaveAfterPermission = true
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return@launch
            }
            saveWav(file)
        }
    }

    private fun saveCurrentAudioToMediaStore() {
        generatedFile?.let { saveWav(it) }
    }

    private fun saveWav(file: File) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
                val fileName = "VortexTTS_$stamp.wav"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "audio/wav")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VortexTTS")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = requireContext().contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Could not create MediaStore entry")
                    try {
                        resolver.openOutputStream(uri)?.use { output ->
                            FileInputStream(file).use { input -> input.copyTo(output) }
                        } ?: error("Could not open output stream")
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                        uri
                    } catch (e: Exception) {
                        resolver.delete(uri, null, null)
                        throw e
                    }
                } else {
                    val directory = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "VortexTTS"
                    ).apply { mkdirs() }
                    val destination = File(directory, fileName)
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination).use { output -> input.copyTo(output) }
                    }
                    Uri.fromFile(destination)
                }
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { showStatus("صدا در Downloads/VortexTTS ذخیره شد.", true) }
                    .onFailure { showStatus("ذخیره صدا ناموفق بود.", false) }
            }
        }
    }

    private suspend fun ensureGeneratedAudio(): File? {
        val key = secureStorage.getApiKey()
        if (key.isNullOrBlank()) {
            showStatus("کلید API پیدا نشد. ابتدا آن را تنظیم کنید.", false)
            return null
        }
        val text = finalPrompt()
        if (text.isBlank()) {
            showStatus("متن ورودی خالی است.", false)
            return null
        }
        val model = selectedModel()
        val fingerprint = sha256("$model\u0000$directVoice\u0000$text")
        generatedFile?.takeIf { it.exists() && fingerprint == generatedFingerprint }?.let { return it }

        setBusy(true)
        showStatus("در حال تولید صدا…", null)
        return try {
            when (val result = repository.generatePcm(key, model, text, directVoice)) {
                is GeminiResult.Success -> {
                    if (result.value.isEmpty() || result.value.size % 2 != 0) {
                        showStatus("داده صوتی دریافتی معتبر نیست.", false)
                        null
                    } else {
                        val dir = File(requireContext().cacheDir, "vortex_tts").apply { mkdirs() }
                        val file = File(dir, "generated_$fingerprint.wav")
                        withContext(Dispatchers.IO) { PcmToWav.toWavFile(result.value, file) }
                        generatedFile?.takeIf { it != file }?.delete()
                        generatedFile = file
                        generatedFingerprint = fingerprint
                        showStatus("صدای تولیدشده آماده است.", true)
                        file
                    }
                }
                is GeminiResult.Failure -> {
                    showStatus(errorMessage(result.error), false)
                    null
                }
            }
        } finally {
            setBusy(false)
        }
    }

    private fun invalidateGeneratedAudio() {
        generatedFingerprint = null
    }

    private fun setBusy(busy: Boolean) {
        _binding?.let {
            it.ttsProgress.visibility = if (busy) View.VISIBLE else View.GONE
            it.playButton.isEnabled = !busy
            it.saveButton.isEnabled = !busy
            it.textEditText.isEnabled = !busy
            it.styleEditText.isEnabled = !busy
            it.modelSpinner.isEnabled = !busy
            it.directVoiceSpinner.isEnabled = !busy
        }
    }

    private fun showStatus(message: String, success: Boolean?) {
        _binding?.let {
            it.operationStatus.text = message
            it.operationStatus.visibility = View.VISIBLE
            val color = when (success) {
                true -> R.color.cosmic_success
                false -> R.color.cosmic_error
                null -> R.color.cosmic_text_secondary
            }
            it.operationStatus.setTextColor(ContextCompat.getColor(requireContext(), color))
        }
    }

    private fun errorMessage(error: GeminiError): String = when (error) {
        GeminiError.INVALID_KEY -> "کلید API نامعتبر است."
        GeminiError.FORBIDDEN -> "دسترسی به API رد شد. کلید یا مجوز پروژه را بررسی کنید."
        GeminiError.NOT_FOUND -> "مدل انتخاب‌شده پیدا نشد."
        GeminiError.RATE_LIMITED -> "محدودیت درخواست API فعال شده است. کمی بعد دوباره تلاش کنید."
        GeminiError.BAD_REQUEST -> "درخواست ارسال‌شده نامعتبر است."
        GeminiError.NETWORK -> "اتصال اینترنت برقرار نشد یا زمان درخواست تمام شد."
        GeminiError.EMPTY_AUDIO -> "پاسخ API صوتی نداشت."
        GeminiError.INVALID_BASE64 -> "داده صوتی دریافتی قابل Decode نیست."
        GeminiError.API -> "Gemini یک خطای API برگرداند."
        GeminiError.UNKNOWN -> "خطای ناشناخته رخ داد. دوباره تلاش کنید."
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onDestroyView() {
        audioPlayer.release()
        generatedFile?.delete()
        generatedFile = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private val VOICES = listOf("Kore", "Puck", "Charon", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr")
        fun newInstance() = TtsFragment()
    }
}
