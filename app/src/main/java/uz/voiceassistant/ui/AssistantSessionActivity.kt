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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uz.voiceassistant.VoiceAssistantApp
import uz.voiceassistant.agent.UniversalAiClient
import uz.voiceassistant.command.CommandParser
import uz.voiceassistant.command.NativeActionExecutor
import uz.voiceassistant.command.NativeCommand
import uz.voiceassistant.data.AiProvider
import uz.voiceassistant.service.ScreenAgentService
import uz.voiceassistant.speech.AudioRecorderManager
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
    private var audioRecorderManager: AudioRecorderManager? = null
    private lateinit var aiClient: UniversalAiClient
    private lateinit var actionExecutor: NativeActionExecutor
    private var audioJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = applicationContext as VoiceAssistantApp
        actionExecutor = NativeActionExecutor(this)
        ttsManager = TtsManager(this)
        ttsManager?.setFallbackLanguage(app.settingsManager.fallbackLanguage)

        audioRecorderManager = AudioRecorderManager(this)

        aiClient = UniversalAiClient(
            providerProvider = { app.settingsManager.aiProvider },
            apiKeyProvider = { app.settingsManager.apiKey },
            modelProvider = {
                when (app.settingsManager.aiProvider) {
                    AiProvider.GEMINI -> app.settingsManager.geminiModel
                    AiProvider.OPENAI -> app.settingsManager.customModel.ifBlank { "gpt-4o-mini" }
                    AiProvider.CUSTOM -> app.settingsManager.customModel
                }
            },
            customEndpointProvider = { app.settingsManager.customEndpoint }
        )

        setContent {
            UzbekVoiceAssistantTheme {
                var statusText by remember { mutableStateOf("Eshitmoqdaman... Gapiring") }
                var recognizedText by remember { mutableStateOf("") }
                var isListening by remember { mutableStateOf(false) }
                var rmsDb by remember { mutableFloatStateOf(0f) }

                fun stopListeningAndProcess() {
                    val hasKey = app.settingsManager.apiKey.isNotBlank()
                    if (hasKey && audioRecorderManager?.isRecording == true) {
                        audioJob?.cancel()
                        isListening = false
                        statusText = "Ovoz aniqlanmoqda..."
                        rmsDb = 0f

                        val audioBytes = audioRecorderManager?.stopRecording()
                        if (audioBytes == null || audioBytes.isEmpty()) {
                            statusText = "Ovoz eshitilmadi. Qaytadan gapiring yoki yozing."
                            return
                        }

                        lifecycleScope.launch {
                            val result = aiClient.transcribeAudio(audioBytes)
                            result.onSuccess { transcribed ->
                                recognizedText = transcribed
                                statusText = "Buyruq bajarilmoqda..."
                                handleCommand(transcribed) { feedback ->
                                    statusText = feedback
                                }
                            }.onFailure { err ->
                                statusText = err.message ?: "Ovozni aniqlashda xatolik yuz berdi"
                                mainHandler.postDelayed({
                                    statusText = "Mikrofonni bosing yoki matn yozing"
                                }, 3000)
                            }
                        }
                    } else {
                        speechManager?.stopListening()
                        isListening = false
                    }
                }

                fun startListening() {
                    val hasKey = app.settingsManager.apiKey.isNotBlank()
                    recognizedText = ""

                    if (hasKey) {
                        speechManager?.stopListening()
                        speechManager?.destroy()
                        audioJob?.cancel()

                        val started = audioRecorderManager?.startRecording() ?: false
                        if (!started) {
                            statusText = "Mikrofonni ishga tushirib bo'lmadi"
                            isListening = false
                            return
                        }

                        isListening = true
                        statusText = "Eshitmoqdaman... Gapiring\n(Tugagach mikrofonni bosing)"

                        audioJob = lifecycleScope.launch {
                            val startTime = System.currentTimeMillis()
                            val maxDurationMs = 5500L

                            while (isActive && audioRecorderManager?.isRecording == true) {
                                val amp = audioRecorderManager?.getMaxAmplitude() ?: 0
                                rmsDb = (amp / 32767f) * 10f

                                if (System.currentTimeMillis() - startTime > maxDurationMs) {
                                    break
                                }
                                delay(60)
                            }

                            if (isActive && audioRecorderManager?.isRecording == true) {
                                stopListeningAndProcess()
                            }
                        }
                    } else {
                        speechManager?.destroy()
                        isListening = true
                        statusText = "Eshitmoqdaman... Gapiring"

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
                }

                LaunchedEffect(Unit) {
                    startListening()
                }

                VoiceAssistantOverlay(
                    statusText = statusText,
                    recognizedText = recognizedText,
                    isListening = isListening,
                    rmsDb = rmsDb,
                    onDismiss = {
                        audioJob?.cancel()
                        audioRecorderManager?.stopRecording()
                        speechManager?.stopListening()
                        finish()
                    },
                    onMicClick = {
                        if (isListening) {
                            stopListeningAndProcess()
                        } else {
                            startListening()
                        }
                    },
                    onTextSubmit = { typedCommand ->
                        audioJob?.cancel()
                        audioRecorderManager?.stopRecording()
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
                mainHandler.postDelayed({ finish() }, 1500)
            } else {
                mainHandler.postDelayed({ finish() }, 1000)
            }
        } else {
            // Universal Fallback: Screen Agent
            if (ScreenAgentService.isEnabled()) {
                onStatusUpdate("Vazifa bajarilmoqda...")
                finish() // Finish overlay immediately so agent has unobstructed view
                ScreenAgentService.executeTask(applicationContext, commandText)
            } else {
                val errorMsg = "Maxsus imkoniyatlar xizmati yoqilmagan. Sozlamalardan yoqing."
                onStatusUpdate(errorMsg)
                mainHandler.postDelayed({ finish() }, 2500)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioJob?.cancel()
        audioRecorderManager?.stopRecording()
        audioRecorderManager = null
        speechManager?.stopListening()
        speechManager?.destroy()
        speechManager = null
        ttsManager?.shutdown()
    }
}
