package uz.voiceassistant.service

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.util.Log

class AssistantVoiceInteractionService : VoiceInteractionService() {

    private val tag = "VoiceInteractionService"

    override fun onReady() {
        super.onReady()
        Log.i(tag, "Uzbek Voice Assistant registered as system default assistant.")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.i(tag, "AssistantVoiceInteractionService shutdown.")
    }
}
