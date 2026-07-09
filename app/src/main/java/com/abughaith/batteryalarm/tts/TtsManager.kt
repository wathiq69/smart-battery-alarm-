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

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val tts = this.tts ?: return@TextToSpeech
                val result = tts.setLanguage(Locale("ar"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Arabic not supported, fallback to en-US")
                    tts.setLanguage(Locale.US)
                } else {
                    Log.d(TAG, "Arabic TTS ready")
                }
                trySelectFemaleVoice(tts)
                tts.setSpeechRate(0.95f)
                tts.setPitch(1.15f)
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { onUtteranceDone?.invoke() }
                    override fun onError(utteranceId: String?) {
                        Log.w(TAG, "TTS error for: $utteranceId")
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
            } else {
                Log.e(TAG, "TTS init failed with status=$status")
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
                    v.name.contains("woman", ignoreCase = true) ||
                    v.name.contains("ar-xa", ignoreCase = true) ||
                    v.name.contains("zira", ignoreCase = true)
                )
            }
            if (femaleVoice != null) {
                val res = tts.setVoice(femaleVoice)
                Log.d(TAG, "Female voice set: ${femaleVoice.name} result=$res")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not select female voice: ${e.message}")
        }
    }

    fun speakNow(text: String) {
        if (text.isBlank()) return
        if (!isReady.get() || tts == null) {
            synchronized(pendingQueue) { pendingQueue.add(text) }
            return
        }
        try {
            tts?.stop()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "msg_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(TAG, "speakNow error: ${e.message}")
        }
    }

    fun enqueue(text: String) {
        if (text.isBlank()) return
        if (!isReady.get() || tts == null) {
            synchronized(pendingQueue) { pendingQueue.add(text) }
            return
        }
        try {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "msg_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(TAG, "enqueue error: ${e.message}")
        }
    }

    fun stop() { try { tts?.stop() } catch (_: Exception) {} }

    fun shutdown() {
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        isReady.set(false)
        synchronized(pendingQueue) { pendingQueue.clear() }
    }

    fun isReady(): Boolean = isReady.get() && tts != null

    fun reinit() {
        shutdown()
        initTts()
    }
}
