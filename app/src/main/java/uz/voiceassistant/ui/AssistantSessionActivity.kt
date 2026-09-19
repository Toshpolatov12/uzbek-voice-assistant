package uz.voiceassistant.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import uz.voiceassistant.VoiceAssistantApp
import uz.voiceassistant.command.CommandParser
import uz.voiceassistant.command.NativeActionExecutor
import uz.voiceassistant.command.NativeCommand
import uz.voiceassistant.service.ScreenAgentService
import uz.voiceassistant.speech.SpeechManager
import uz.voiceassistant.speech.TtsManager
import uz.voiceassistant.ui.theme.UzbekVoiceAssistantTheme

class AssistantSessionActivity : ComponentActivity() {

    companion object {
        const val EXTRA_AUTO_START_LISTENING = "extra_auto_start_listening"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechManager: SpeechManager? = null
    private var ttsManager: TtsManager? = null
    private lateinit var actionExecutor: NativeActionExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = applicationContext as VoiceAssistantApp
        actionExecutor = NativeActionExecutor(this)
        ttsManager = TtsManager(this)
        ttsManager?.setFallbackLanguage(app.settingsManager.fallbackLanguage)

        setContent {
            UzbekVoiceAssistantTheme {
                var statusText by remember { mutableStateOf("Eshitmoqdaman... Gapiring") }
                var recognizedText by remember { mutableStateOf("") }
                var isListening by remember { mutableStateOf(false) }
                var rmsDb by remember { mutableFloatStateOf(0f) }

                fun startListening() {
                    speechManager?.destroy()
                    isListening = true
                    statusText = "Eshitmoqdaman... Gapiring"
                    recognizedText = ""

                    speechManager = SpeechManager(this@AssistantSessionActivity, app.settingsManager.fallbackLanguage).apply {
                        onRmsChanged = { rms ->
                            rmsDb = rms
                        }

                        onPartialResults = { partial ->
                            recognizedText = partial
                        }

                        onResults = { finalResult ->
                            isListening = false
                            recognizedText = finalResult
                            statusText = "Buyruq bajarilmoqda..."
                            handleCommand(finalResult) { feedback ->
                                statusText = feedback
                            }
                        }

                        onError = { _, errorMsg ->
                            isListening = false
                            statusText = errorMsg
                            mainHandler.postDelayed({ finish() }, 2000)
                        }

                        onFallbackWarning = { warning ->
                            statusText = warning
                        }
                    }

                    speechManager?.startListening()
                }

                // Initial start after composition settles and activity is active
                LaunchedEffect(Unit) {
                    startListening()
                }

                VoiceAssistantOverlay(
                    statusText = statusText,
                    recognizedText = recognizedText,
                    isListening = isListening,
                    rmsDb = rmsDb,
                    onDismiss = {
                        speechManager?.stopListening()
                        finish()
                    },
                    onMicClick = {
                        if (isListening) {
                            speechManager?.stopListening()
                            isListening = false
                        } else {
                            startListening()
                        }
                    },
                    onTextSubmit = { typedCommand ->
                        speechManager?.stopListening()
                        isListening = false
                        recognizedText = typedCommand
                        statusText = "Buyruq bajarilmoqda..."
                        handleCommand(typedCommand) { feedback ->
                            statusText = feedback
                        }
                    }
                )
            }
        }
    }

    private fun handleCommand(commandText: String, onStatusUpdate: (String) -> Unit) {
        val nativeCmd = CommandParser.parse(commandText)

        if (nativeCmd !is NativeCommand.Unknown) {
            val feedback = actionExecutor.execute(nativeCmd)
            if (feedback.isNotBlank()) {
                onStatusUpdate(feedback)
                ttsManager?.speak(feedback) {
                    mainHandler.postDelayed({ finish() }, 1500)
                }
            } else {
                mainHandler.postDelayed({ finish() }, 1000)
            }
        } else {
            // Universal Fallback: Screen Agent
            if (ScreenAgentService.isEnabled()) {
                val startingMessage = "Tushundim, ekranni boshqarishni boshlayapman."
                onStatusUpdate(startingMessage)
                ttsManager?.speak(startingMessage) {
                    mainHandler.post {
                        finish() // Finish overlay so agent has unobstructed view
                        ScreenAgentService.executeTask(applicationContext, commandText)
                    }
                }
            } else {
                val errorMsg = "Maxsus imkoniyatlar xizmati yoqilmagan. Iltimos, sozlamalardan yoqing."
                onStatusUpdate(errorMsg)
                ttsManager?.speak(errorMsg) {
                    mainHandler.postDelayed({ finish() }, 2500)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechManager?.stopListening()
        speechManager?.destroy()
        speechManager = null
        ttsManager?.shutdown()
    }
}
