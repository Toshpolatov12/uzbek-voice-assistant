package uz.voiceassistant.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import uz.voiceassistant.service.WakeWordService

class SpeechManager(
    private val context: Context,
    private val fallbackLanguage: String = "en-US"
) {
    private val tag = "SpeechManager"
    private val mainHandler = Handler(Looper.getMainLooper())
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
        mainHandler.post { initRecognizer() }
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(tag, "SpeechRecognizer is not available on this device.")
            onError?.invoke(-2, "Qurilmada ovoz aniqlash (Google Speech) xizmati topilmadi.")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to create SpeechRecognizer", e)
            onError?.invoke(-1, "Ovoz xizmatini yaratishda xatolik: ${e.message}")
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                mainHandler.post { onReadyForSpeech?.invoke() }
            }

            override fun onBeginningOfSpeech() {
                mainHandler.post { onBeginningOfSpeech?.invoke() }
            }

            override fun onRmsChanged(rmsdB: Float) {
                mainHandler.post { onRmsChanged?.invoke(rmsdB) }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                WakeWordService.resume()
                Log.w(tag, "SpeechRecognizer error code: $error (isFallbackMode=$isFallbackMode)")

                // Auto fallback to fallbackLanguage if primary uz-UZ fails to recognize or connect
                if (!isFallbackMode && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    isFallbackMode = true
                    mainHandler.post {
                        onFallbackWarning?.invoke("O'zbek tili aniqlanmadi, zaxira tiliga ($fallbackLanguage) o'tildi.")
                        startListening(useFallback = true)
                    }
                    return
                }

                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Mikrofon boshqa xizmat tomonidan band"
                    SpeechRecognizer.ERROR_CLIENT -> "Ovoz aniqlash xizmati kutmoqda"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon ruxsati berilmagan"
                    SpeechRecognizer.ERROR_NETWORK -> "Internet aloqasi talab qilinadi"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tarmoq kutish vaqti tugadi"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Ovoz tushunarsiz bo'ldi, qaytadan gapiring"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Ovoz xizmati band, qayta urinilmoqda"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Gapirishingiz kutilmoqda..."
                    else -> "Ovoz aniqlashda xatolik ($error)"
                }
                mainHandler.post { onError?.invoke(error, errorMessage) }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                WakeWordService.resume()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull().orEmpty()
                mainHandler.post {
                    if (recognizedText.isNotBlank()) {
                        onResults?.invoke(recognizedText)
                    } else {
                        onError?.invoke(SpeechRecognizer.ERROR_NO_MATCH, "Hech narsa aniqlanmadi.")
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    mainHandler.post { onPartialResults?.invoke(text) }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun startListening(useFallback: Boolean = false) {
        mainHandler.post {
            // Free the microphone hardware from background wake word listener
            WakeWordService.pause()

            if (speechRecognizer == null) {
                initRecognizer()
            }

            val targetLanguage = if (useFallback || isFallbackMode) fallbackLanguage else "uz-UZ"

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLanguage)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLanguage)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2500L)
            }

            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(tag, "Exception while starting speech recognition", e)
                WakeWordService.resume()
                onError?.invoke(-1, e.localizedMessage ?: "Ovoz aniqlashni ishga tushirib bo'lmadi")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(tag, "Error stopping listener", e)
            }
            isListening = false
            WakeWordService.resume()
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e(tag, "Error destroying listener", e)
            }
            speechRecognizer = null
            isListening = false
            WakeWordService.resume()
        }
    }
}
