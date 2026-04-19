package com.gemmaremember.app.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.*

class VoiceManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false
    var enabled = true

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            // Try to use Neural2-C voice
            tts?.voices?.find { it.name.contains("cmg", ignoreCase = true) }?.let {
                tts?.voice = it
            }
            isReady = true
        }
    }

    fun speak(text: String) {
        if (!enabled || !isReady || text.isBlank()) return
        tts?.stop()

        val isNight = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 21

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, if (isNight) 0.7f else 1.0f)
        }

        tts?.setSpeechRate(if (isNight) 0.75f else 0.95f)
        tts?.setPitch(if (isNight) 0.9f else 1.0f)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString())
    }

    fun stop() { tts?.stop() }
    fun toggle() { enabled = !enabled; if (!enabled) stop() }
    fun shutdown() { tts?.shutdown() }
}
