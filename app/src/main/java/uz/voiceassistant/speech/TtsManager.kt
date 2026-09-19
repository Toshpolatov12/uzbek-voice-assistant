package uz.voiceassistant.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

class TtsManager(
    private val context: Context,
    private val onInitComplete: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private val tag = "TtsManager"
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isUsingFallback = false
    private var currentFallbackLocale = Locale.US

    var onWarningListener: ((String) -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val uzbekLocale = Locale("uz", "UZ")
            val uzResult = tts?.setLanguage(uzbekLocale)

            if (uzResult == TextToSpeech.LANG_MISSING_DATA || uzResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(tag, "Uzbek TTS locale not supported on this device. Switching to fallback.")
                isUsingFallback = true
                val fallbackResult = tts?.setLanguage(currentFallbackLocale)
                if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.US
                }
                onWarningListener?.invoke("Qurilmada O'zbek tili ovoz paketi (TTS) topilmadi. Zaxira tiliga (${currentFallbackLocale.displayLanguage}) o'tildi.")
            } else {
                isUsingFallback = false
            }

            tts?.setPitch(1.0f)
            tts?.setSpeechRate(1.0f)
            isInitialized = true
            onInitComplete?.invoke(true)
        } else {
            Log.e(tag, "Failed to initialize TextToSpeech engine.")
            isInitialized = false
            onInitComplete?.invoke(false)
        }
    }

    fun setFallbackLanguage(localeCode: String) {
        currentFallbackLocale = when (localeCode) {
            "en-US", "en" -> Locale.US
            "ru-RU", "ru" -> Locale("ru", "RU")
            "tr-TR", "tr" -> Locale("tr", "TR")
            else -> Locale.US
        }
        if (isUsingFallback && isInitialized) {
            tts?.setLanguage(currentFallbackLocale)
        }
    }

    fun speak(
        text: String,
        flushQueue: Boolean = true,
        onDone: (() -> Unit)? = null
    ) {
        if (!isInitialized || tts == null) {
            Log.w(tag, "TTS not ready yet: $text")
            onDone?.invoke()
            return
        }

        val targetUtteranceId = UUID.randomUUID().toString()

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}

            override fun onDone(id: String?) {
                if (id == targetUtteranceId) {
                    onDone?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == targetUtteranceId) {
                    onDone?.invoke()
                }
            }
        })

        val queueMode = if (flushQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, queueMode, null, targetUtteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
