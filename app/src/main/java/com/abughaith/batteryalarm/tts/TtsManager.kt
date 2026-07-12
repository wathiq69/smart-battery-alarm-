package com.abughaith.batteryalarm.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class TtsManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "TtsManager"
        @Volatile private var instance: TtsManager? = null
        fun getInstance(context: Context): TtsManager {
            return instance ?: synchronized(this) {
                instance ?: TtsManager(context.applicationContext).also { instance = it }
            }
        }
    }
    private var tts: TextToSpeech? = null
    private val isReady = AtomicBoolean(false)
    private val pendingQueue = mutableListOf<String>()
    var onUtteranceDone: (() -> Unit)? = null

    private fun initTts() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val tts = this.tts ?: return@TextToSpeech
                val result = tts.setLanguage(Locale("ar"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US)
                }
                trySelectFemaleVoice(tts)
                tts.setSpeechRate(0.95f)
                tts.setPitch(1.15f)
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        onUtteranceDone?.invoke()
                        // إغلاق TTS بعد الانتهاء لتوفير الذاكرة والبطارية
                        shutdown()
                    }
                    override fun onError(utteranceId: String?) {
                        shutdown()
                    }
                })
                isReady.set(true)
                synchronized(pendingQueue) {
                    if (pendingQueue.isNotEmpty()) {
                        val toSpeak = pendingQueue.toList()
                        pendingQueue.clear()
                        toSpeak.forEach { speakNow(it) }
                    }
                }
            }
        }
    }

    private fun trySelectFemaleVoice(tts: TextToSpeech) {
        try {
            val voices = tts.voices ?: return
            val femaleVoice = voices.firstOrNull { v ->
                val lang = v.locale?.language ?: ""
                lang == "ar" && (
                    v.name.contains("female", ignoreCase = true) ||
                    v.name.contains("ar-xa", ignoreCase = true) ||
                    v.name.contains("zira", ignoreCase = true)
                )
            }
            if (femaleVoice != null) { tts.setVoice(femaleVoice) }
        } catch (e: Exception) {}
    }

    fun speakNow(text: String) {
        if (text.isBlank()) return
        if (!isReady.get() || tts == null) {
            synchronized(pendingQueue) { pendingQueue.add(text) }
            initTts()
            return
        }
        try {
            tts?.stop()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "msg_" + System.currentTimeMillis())
        } catch (e: Exception) {}
    }

    fun stop() { try { tts?.stop() } catch (_: Exception) {} }
    fun shutdown() {
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        isReady.set(false)
        synchronized(pendingQueue) { pendingQueue.clear() }
    }
    fun isReady(): Boolean = isReady.get() && tts != null
}
