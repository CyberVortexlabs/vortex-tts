package com.vortex.tts

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.vortex.tts.data.SecureStorage
import com.vortex.tts.databinding.ActivityMainBinding
import com.vortex.tts.ui.KeyFragment
import com.vortex.tts.ui.TtsFragment

class MainActivity : AppCompatActivity(), KeyFragment.Callbacks {
    private lateinit var binding: ActivityMainBinding
    private lateinit var secureStorage: SecureStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        secureStorage = SecureStorage(this)

        if (savedInstanceState == null) {
            showInitialScreen()
        }
    }

    private fun showInitialScreen() {
        if (secureStorage.getApiKey() != null) {
            showTts()
        } else {
            showKeyScreen()
        }
    }

    private fun showKeyScreen() {
        supportFragmentManager.beginTransaction()
            .replace(binding.root.id, KeyFragment.newInstance())
            .commit()
    }

    override fun onConnectionSuccess() {
        showTts()
    }

    fun showTts() {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(binding.root.id, TtsFragment.newInstance())
            .addToBackStack(null)
            .commit()
    }

    fun openKeyScreen() {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(binding.root.id, KeyFragment.newInstance())
            .addToBackStack(null)
            .commit()
    }

}
