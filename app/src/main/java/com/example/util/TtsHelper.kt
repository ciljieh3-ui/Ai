package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentlySpeakingMessageId = MutableStateFlow<Long?>(null)
    val currentlySpeakingMessageId: StateFlow<Long?> = _currentlySpeakingMessageId.asStateFlow()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            isInitialized = true
        }
    }

    fun speak(messageId: Long, text: String) {
        if (!isInitialized || tts == null) return

        if (_currentlySpeakingMessageId.value == messageId && _isSpeaking.value) {
            stop()
            return
        }

        stop()
        _currentlySpeakingMessageId.value = messageId
        _isSpeaking.value = true

        // Clean markdown characters for pleasant speech output
        val cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), "Blok kode.")
            .replace(Regex("[#*`_~]"), "")
            .trim()

        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "aura_utterance_$messageId")
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        _currentlySpeakingMessageId.value = null
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
