package uz.voiceassistant.service

import android.content.Intent
import android.speech.RecognitionService
import android.util.Log

/**
 * AssistantRecognitionService fulfills the system requirement for VoiceInteractionService.
 * It delegates standard recognition callbacks to the system or internal engine.
 */
class AssistantRecognitionService : RecognitionService() {

    private val tag = "AssistantRecognition"

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        Log.d(tag, "onStartListening called")
    }

    override fun onCancel(listener: Callback?) {
        Log.d(tag, "onCancel called")
    }

    override fun onStopListening(listener: Callback?) {
        Log.d(tag, "onStopListening called")
    }
}
