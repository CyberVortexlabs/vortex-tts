package com.vortex.tts.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun play(file: File, onCompletion: () -> Unit, onError: () -> Unit) {
        release()
        val player = MediaPlayer()
        mediaPlayer = player
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        player.setDataSource(file.absolutePath)
        player.setOnCompletionListener {
            it.reset()
            it.release()
            if (mediaPlayer === it) mediaPlayer = null
            onCompletion()
        }
        player.setOnErrorListener { mp, _, _ ->
            mp.reset()
            mp.release()
            if (mediaPlayer === mp) mediaPlayer = null
            onError()
            true
        }
        player.setOnPreparedListener { it.start() }
        player.prepareAsync()
    }

    fun stop() {
        mediaPlayer?.let { player ->
            runCatching { if (player.isPlaying) player.stop() }
            player.reset()
            player.release()
        }
        mediaPlayer = null
    }

    fun release() = stop()
}
