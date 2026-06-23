package com.example.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class SpeechHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeech: String? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e("SpeechHelper", "TTS Initialization failed", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("tr", "TR"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("SpeechHelper", "Turkish language is not supported or missing data. Falling back to default.")
                tts?.setLanguage(Locale.getDefault())
            }
            isInitialized = true
            Log.d("SpeechHelper", "TTS Initialized successfully.")

            // Speak any text requested while we were initializing
            pendingSpeech?.let {
                speak(it)
                pendingSpeech = null
            }
        } else {
            Log.e("SpeechHelper", "TTS initialization failed with status: $status")
        }
    }

    fun speak(text: String) {
        if (!isInitialized) {
            pendingSpeech = text
            return
        }
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "BakkalTTS")
        } catch (e: Exception) {
            Log.e("SpeechHelper", "TTS Speak failed", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("SpeechHelper", "TTS Shutdown error", e)
        }
    }
}
