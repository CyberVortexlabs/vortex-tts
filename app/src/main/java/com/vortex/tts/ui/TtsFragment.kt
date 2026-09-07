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
import com.vortex.tts.audio.Mp3Encoder
import com.vortex.tts.audio.PcmToWav
import com.vortex.tts.data.ApiKeyEntry
import com.vortex.tts.data.GeminiClient
import com.vortex.tts.data.GeminiError
import com.vortex.tts.data.GeminiRepository
import com.vortex.tts.data.GeminiResult
import com.vortex.tts.data.SecureStorage
import com.vortex.tts.databinding.FragmentTtsBinding
import com.vortex.tts.model.GeminiVoices
import com.vortex.tts.model.SupportedTtsModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    private val previewPlayer = AudioPlayer()

    private var generatedFile: File? = null
    private var generatedFingerprint: String? = null
    private var directVoice: String = GeminiVoices.DEFAULT_VOICE
    private var pendingDownloadAfterPermission = false
    private var isSpinnerInit = false

    private val writePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingDownloadAfterPermission) saveCurrentAudioToMediaStore()
        else if (!granted) showStatus("مجوز ذخیره‌سازی داده نشد.", false)
        pendingDownloadAfterPermission = false
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
        updateKeyWarning()
        refreshKeySelector()
    }

    override fun onResume() {
        super.onResume()
        refreshKeySelector()
        updateKeyWarning()
    }

    private fun updateKeyWarning() {
        val hasKey = !secureStorage.getActiveApiKey().isNullOrBlank()
        _binding?.keyWarning?.visibility = if (hasKey) View.GONE else View.VISIBLE
    }

    private fun refreshKeySelector() {
        val keys = secureStorage.getApiKeys()
        val activeId = secureStorage.getActiveKeyId()
        if (keys.isEmpty()) {
            binding.keySelectorSpinner.visibility = View.GONE
            binding.keySelectorLabel.visibility = View.GONE
            return
        }
        binding.keySelectorSpinner.visibility = View.VISIBLE
        binding.keySelectorLabel.visibility = View.VISIBLE
        val names = keys.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, names)
        binding.keySelectorSpinner.adapter = adapter
        val activeIndex = keys.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
        isSpinnerInit = false
        binding.keySelectorSpinner.setSelection(activeIndex, false)
        binding.keySelectorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isSpinnerInit) { isSpinnerInit = true; return }
                val selected = keys.getOrNull(position) ?: return
                secureStorage.setActiveKeyId(selected.id)
                updateKeyWarning()
                loadAvailableModels()
                invalidateGeneratedAudio()
                showStatus("کلید فعال: ${selected.name}", true)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        // Ensure initial active is set
        isSpinnerInit = true
    }

    private fun activeKey(): String? = secureStorage.getActiveApiKey() ?: secureStorage.getApiKey()

    private fun setupUi() {
        val voiceAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            GeminiVoices.VOICES
        )
        binding.directVoiceSpinner.adapter = voiceAdapter
        binding.directVoiceSpinner.setSelection(GeminiVoices.VOICES.indexOf(GeminiVoices.DEFAULT_VOICE).coerceAtLeast(0), false)
        binding.directVoiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                directVoice = GeminiVoices.VOICES[position]
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

        binding.loudAutoChip.setOnClickListener { invalidateGeneratedAudio() }
        binding.loudNormalChip.setOnClickListener { invalidateGeneratedAudio() }
        binding.loudLoudChip.setOnClickListener { invalidateGeneratedAudio() }

        binding.previewVoiceButton.setOnClickListener { previewSelectedVoice() }

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

        binding.generateButton.setOnClickListener { generateOnly() }
        binding.bubblePlayButton.setOnClickListener { toggleBubblePlayback() }
        binding.downloadButton.setOnClickListener { downloadGenerated() }
        binding.changeKeyButton.setOnClickListener { (activity as? MainActivity)?.openKeyScreen() }
        loadAvailableModels()
    }

    private fun loadAvailableModels() {
        val key = activeKey() ?: return
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

    private fun selectedVoice(): String {
        val spinnerVoice = _binding?.directVoiceSpinner?.selectedItem?.toString()
        return if (spinnerVoice != null && spinnerVoice in GeminiVoices.VOICES) spinnerVoice else directVoice
    }

    private fun selectedModel(): String {
        val title = binding.modelSpinner.selectedItem?.toString().orEmpty()
        return when (title) {
            SupportedTtsModels.title(SupportedTtsModels.PRO) -> SupportedTtsModels.PRO
            else -> SupportedTtsModels.FLASH
        }
    }

    private fun finalPrompt(): String {
        val rawText = binding.textEditText.text?.toString().orEmpty()
        val style = binding.styleEditText.text?.toString()?.trim().orEmpty()
        val loud = when {
            binding.loudLoudChip.isChecked -> "با صدای بلند و پرانرژی بخوان."
            else -> ""
        }
        val persianRegex = Regex("[\u0600-\u06FF]")
        val text = if (persianRegex.containsMatchIn(rawText)) "با لحجه فارسی طبیعی و روان بخوان: $rawText" else rawText
        return listOf(style, loud, text).filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun containsPersian(s: String): Boolean = Regex("[\u0600-\u06FF]").containsMatchIn(s)

    private fun previewSelectedVoice() {
        if (previewPlayer.isPlaying()) {
            previewPlayer.stop()
            _binding?.previewVoiceButton?.text = getString(R.string.play)
            showPreviewStatus("پخش متوقف شد.", true)
            return
        }
        previewPlayer.stop()
        audioPlayer.stop()
        val voice = selectedVoice()
        val resName = GeminiVoices.rawResName(voice)
        val resId = resources.getIdentifier(resName, "raw", requireContext().packageName)
        if (resId != 0) {
            setPreviewBusy(false)
            showPreviewStatus("پیش‌نمایش $voice (آفلاین) در حال پخش…", true)
            previewPlayer.playRaw(
                context = requireContext(),
                resId = resId,
                onCompletion = {
                    _binding?.previewVoiceButton?.text = getString(R.string.play)
                    showPreviewStatus("پیش‌نمایش $voice (آفلاین) پخش شد.", true)
                },
                onError = {
                    _binding?.let {
                        showPreviewStatus("پخش پیش‌نمایش آفلاین ناموفق بود.", false)
                    }
                }
            )
            _binding?.previewVoiceButton?.text = getString(R.string.stop)
            return
        }
        val key = activeKey()
        if (key.isNullOrBlank()) {
            showPreviewStatus("پیش‌نمایش آفلاین یافت نشد و کلید API تنظیم نشده.", false)
            return
        }
        val model = selectedModel()
        val previewText = if (containsPersian(binding.textEditText.text?.toString().orEmpty())) {
            GeminiVoices.PREVIEW_TEXT
        } else {
            GeminiVoices.PREVIEW_TEXT_EN
        }
        val textToPreview = binding.textEditText.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let {
            if (it.length > 120) it.take(120) else it
        } ?: previewText

        viewLifecycleOwner.lifecycleScope.launch {
            setPreviewBusy(true)
            try {
                when (val result = repository.generatePcm(key, model, textToPreview, voice)) {
                    is GeminiResult.Success -> {
                        if (result.value.isEmpty() || result.value.size % 2 != 0) {
                            showPreviewStatus("داده صوتی معتبر نیست.", false)
                        } else {
                            val dir = File(requireContext().cacheDir, "vortex_tts_preview").apply { mkdirs() }
                            val file = File(dir, "preview_${voice}_${System.currentTimeMillis()}.wav")
                            withContext(Dispatchers.IO) { PcmToWav.toWavFile(result.value, file) }
                            playPreview(file)
                            showPreviewStatus("پیش‌نمایش $voice پخش شد.", true)
                        }
                    }
                    is GeminiResult.Failure -> {
                        showPreviewStatus(errorMessage(result.error), false)
                    }
                }
            } finally {
                setPreviewBusy(false)
            }
        }
    }

    private fun playPreview(file: File) {
        previewPlayer.stop()
        previewPlayer.play(
            file = file,
            onCompletion = {
                _binding?.previewVoiceButton?.text = getString(R.string.play)
            },
            onError = {
                _binding?.let {
                    showPreviewStatus("پخش پیش‌نمایش ناموفق بود.", false)
                }
            }
        )
    }

    private fun setPreviewBusy(busy: Boolean) {
        _binding?.let {
            it.voicePreviewProgress.visibility = if (busy) View.VISIBLE else View.GONE
            it.previewVoiceButton.isEnabled = !busy
            it.voicePreviewStatus.visibility = if (busy) View.VISIBLE else it.voicePreviewStatus.visibility
            if (busy) {
                it.voicePreviewStatus.text = "در حال تولید پیش‌نمایش..."
                it.voicePreviewStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.cosmic_text_secondary))
                it.voicePreviewStatus.visibility = View.VISIBLE
            }
        }
    }

    private fun showPreviewStatus(message: String, success: Boolean) {
        _binding?.let {
            it.voicePreviewStatus.text = message
            it.voicePreviewStatus.visibility = View.VISIBLE
            val color = if (success) R.color.cosmic_success else R.color.cosmic_error
            it.voicePreviewStatus.setTextColor(ContextCompat.getColor(requireContext(), color))
        }
    }

    private fun generateOnly() {
        if (binding.textEditText.text.isNullOrBlank()) {
            showStatus("متن ورودی خالی است.", false)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val file = ensureGeneratedAudio() ?: return@launch
            showVoiceBubble(file)
            showStatus("صدای تولیدشده آماده است. برای شنیدن، پخش را بزنید.", true)
        }
    }

    private fun toggleBubblePlayback() {
        val file = generatedFile?.takeIf { it.exists() }
        if (file == null) {
            showStatus("ابتدا صدا را تولید کنید.", false)
            return
        }
        if (audioPlayer.isPlaying()) {
            audioPlayer.stop()
            _binding?.bubblePlayButton?.text = getString(R.string.play)
        } else {
            audioPlayer.play(
                file = file,
                onCompletion = {
                    _binding?.bubblePlayButton?.text = getString(R.string.play)
                },
                onError = {
                    _binding?.let {
                        it.bubblePlayButton.text = getString(R.string.play)
                        showStatus("پخش فایل صوتی با خطا مواجه شد.", false)
                    }
                }
            )
            _binding?.bubblePlayButton?.text = getString(R.string.stop)
        }
    }

    private fun showVoiceBubble(file: File) {
        _binding?.let {
            it.voiceBubbleCard.visibility = View.VISIBLE
            it.bubblePlayButton.text = getString(R.string.play)
            it.bubbleTitle.text = getString(R.string.bubble_ready)
            it.bubbleStatus.text = "${getString(R.string.bubble_tap_to_play)} • ${formatDuration(file)}"
        }
    }

    private fun formatDuration(wavFile: File): String {
        val seconds = ((wavFile.length() - 44).coerceAtLeast(0) / 48000).toInt()
        return if (seconds >= 60) "%d:%02d".format(seconds / 60, seconds % 60)
        else "%d ثانیه".format(seconds)
    }

    private fun downloadGenerated() {
        val file = generatedFile?.takeIf { it.exists() }
        if (file == null) {
            showStatus("ابتدا صدا را تولید کنید.", false)
            return
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownloadAfterPermission = true
            writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        saveMp3(file)
    }

    private fun saveCurrentAudioToMediaStore() {
        generatedFile?.takeIf { it.exists() }?.let { saveMp3(it) }
    }

    private fun saveMp3(wavFile: File) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
                val fileName = "VortexTTS_$stamp.mp3"
                val mp3Bytes = withContext(Dispatchers.IO) {
                    val all = wavFile.readBytes()
                    val pcm = if (all.size > 44 && all[0].toInt().toChar() == 'R') all.copyOfRange(44, all.size) else all
                    Mp3Encoder.pcmToMp3(pcm) ?: all
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val effectiveMime = "audio/mpeg"
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, effectiveMime)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = requireContext().contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Could not create MediaStore entry")
                    try {
                        resolver.openOutputStream(uri)?.use { output ->
                            output.write(mp3Bytes)
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
                    val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val destination = File(directory, fileName)
                    java.io.FileOutputStream(destination).use { it.write(mp3Bytes) }
                    Uri.fromFile(destination)
                }
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { showStatus("صدا در Downloads دانلود شد (MP3).", true) }
                    .onFailure { showStatus("دانلود صدا ناموفق بود: ${it.message}", false) }
            }
        }
    }

    private suspend fun ensureGeneratedAudio(): File? {
        val key = activeKey()
        if (key.isNullOrBlank()) {
            showStatus("کلید API پیدا نشد. از بخش تنظیمات کلید را وارد کنید.", false)
            updateKeyWarning()
            return null
        }
        val text = finalPrompt()
        if (text.isBlank()) {
            showStatus("متن ورودی خالی است.", false)
            return null
        }
        val model = selectedModel()
        val voice = selectedVoice()
        val fingerprint = sha256("$model\u0000$voice\u0000$text")
        generatedFile?.takeIf { it.exists() && fingerprint == generatedFingerprint }?.let { return it }

        setBusy(true)
        showStatus("در حال تولید صدا…", null)
        return try {
            when (val result = repository.generatePcm(key, model, text, voice)) {
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
        audioPlayer.stop()
        previewPlayer.stop()
        _binding?.let {
            it.voiceBubbleCard.visibility = View.GONE
            it.bubblePlayButton.text = getString(R.string.play)
        }
    }

    private fun setBusy(busy: Boolean) {
        _binding?.let {
            it.ttsProgress.visibility = if (busy) View.VISIBLE else View.GONE
            it.generateButton.isEnabled = !busy
            it.bubblePlayButton.isEnabled = !busy
            it.downloadButton.isEnabled = !busy
            it.textEditText.isEnabled = !busy
            it.styleEditText.isEnabled = !busy
            it.modelSpinner.isEnabled = !busy
            it.directVoiceSpinner.isEnabled = !busy
            it.previewVoiceButton.isEnabled = !busy
            it.keySelectorSpinner.isEnabled = !busy
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
        previewPlayer.release()
        generatedFile?.delete()
        generatedFile = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = TtsFragment()
    }
}
