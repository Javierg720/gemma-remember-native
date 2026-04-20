package com.gemmaremember.app.voice

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.*

class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mediaPlayer: MediaPlayer? = null
    var enabled = true
    var onSpeakingEnd: (() -> Unit)? = null

    private val cacheDir = File(context.cacheDir, "edge-tts").apply { mkdirs() }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        // Microsoft Edge TTS WebSocket endpoint
        private const val EDGE_VOICE = "en-US-EmmaMultilingualNeural"
        private const val EDGE_TTS_URL = "https://api.edge-tts.dev/tts" // Public relay
    }

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ttsReady = true
        }
    }

    fun speak(text: String) {
        if (!enabled || text.isBlank()) { onSpeakingEnd?.invoke(); return }
        stop()

        scope.launch {
            // Try Edge TTS first
            val audio = tryEdgeTTS(text)
            if (audio != null) {
                withContext(Dispatchers.Main) {
                    playAudio(audio)
                }
            } else {
                // Fallback to Android TTS
                withContext(Dispatchers.Main) {
                    fallbackSpeak(text)
                }
            }
        }
    }

    private suspend fun tryEdgeTTS(text: String): File? = withContext(Dispatchers.IO) {
        try {
            val hash = MessageDigest.getInstance("MD5")
                .digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val cached = File(cacheDir, "$hash.mp3")
            if (cached.exists()) return@withContext cached

            // Use edge-tts command line if available, or HTTP endpoint
            val process = Runtime.getRuntime().exec(arrayOf(
                "edge-tts",
                "--voice", EDGE_VOICE,
                "--text", text,
                "--write-media", cached.absolutePath
            ))
            val exitCode = withTimeoutOrNull(10000) {
                process.waitFor()
            }
            process.destroy()

            if (exitCode == 0 && cached.exists() && cached.length() > 0) cached
            else { cached.delete(); null }
        } catch (e: Exception) {
            null
        }
    }

    private fun playAudio(file: File) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            val night = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 21
            if (night) {
                setVolume(0.7f, 0.7f)
                playbackParams = playbackParams.setSpeed(0.9f)
            }
            setOnCompletionListener {
                it.release()
                mediaPlayer = null
                onSpeakingEnd?.invoke()
            }
            prepare()
            start()
        }
    }

    private fun fallbackSpeak(text: String) {
        if (!ttsReady) { onSpeakingEnd?.invoke(); return }

        val night = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 21
        tts?.setSpeechRate(if (night) 0.75f else 0.95f)
        tts?.setPitch(if (night) 0.9f else 1.0f)

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, if (night) 0.7f else 1.0f)
        }

        val utteranceId = UUID.randomUUID().toString()
        tts?.setOnUtteranceCompletedListener { onSpeakingEnd?.invoke() }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        mediaPlayer?.let { it.stop(); it.release(); mediaPlayer = null }
        tts?.stop()
    }

    fun toggle() { enabled = !enabled; if (!enabled) stop() }

    fun shutdown() {
        stop()
        tts?.shutdown()
        scope.cancel()
    }
}
