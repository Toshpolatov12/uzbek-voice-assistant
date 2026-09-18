package uz.voiceassistant.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechManager(
    private val context: Context,
    private val fallbackLanguage: String = "ru-RU"
) {
    private val tag = "SpeechManager"
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isFallbackMode = false

    var onReadyForSpeech: (() -> Unit)? = null
    var onBeginningOfSpeech: (() -> Unit)? = null
    var onRmsChanged: ((Float) -> Unit)? = null
    var onPartialResults: ((String) -> Unit)? = null
    var onResults: ((String) -> Unit)? = null
    var onError: ((Int, String) -> Unit)? = null
    var onFallbackWarning: ((String) -> Unit)? = null

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }
        } else {
            Log.e(tag, "SpeechRecognizer is not available on this device.")
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                onReadyForSpeech?.invoke()
            }

            override fun onBeginningOfSpeech() {
                onBeginningOfSpeech?.invoke()
            }

            override fun onRmsChanged(rmsdB: Float) {
                onRmsChanged?.invoke(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                Log.w(tag, "SpeechRecognizer error: $error")

                if (!isFallbackMode && (error == SpeechRecognizer.ERROR_SERVER || error == SpeechRecognizer.ERROR_CLIENT)) {
                    // Try fallback language if primary uz-UZ fails to parse
                    isFallbackMode = true
                    onFallbackWarning?.invoke("O'zbek tili ovoz aniqlash tizimi topilmadi. Zaxira tiliga ($fallbackLanguage) o'tildi.")
                    startListening(useFallback = true)
                    return
                }

                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio yozib olishda xatolik"
                    SpeechRecognizer.ERROR_CLIENT -> "Qurilma ichki xatosi"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon ruxsati berilmagan"
                    SpeechRecognizer.ERROR_NETWORK -> "Internet tarmog'ida xatolik"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tarmoq kutish vaqti tugadi"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Ovoz tushunarsiz bo'ldi, qaytadan gapiring"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Ovoz aniqlash band"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Gapirish kutilmadi (vaqt tugadi)"
                    else -> "Noma'lum xatolik ($error)"
                }
                onError?.invoke(error, errorMessage)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull().orEmpty()
                if (recognizedText.isNotBlank()) {
                    onResults?.invoke(recognizedText)
                } else {
                    onError?.invoke(SpeechRecognizer.ERROR_NO_MATCH, "Hech narsa aniqlanmadi.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    onPartialResults?.invoke(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun startListening(useFallback: Boolean = false) {
        if (speechRecognizer == null) {
            initRecognizer()
        }

        val targetLanguage = if (useFallback || isFallbackMode) fallbackLanguage else "uz-UZ"

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLanguage)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLanguage)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, targetLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(tag, "Exception while starting speech recognition", e)
            onError?.invoke(-1, e.localizedMessage ?: "Ovoz aniqlashni ishga tushirib bo'lmadi")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping listener", e)
        }
        isListening = false
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(tag, "Error destroying listener", e)
        }
        speechRecognizer = null
        isListening = false
    }
}
