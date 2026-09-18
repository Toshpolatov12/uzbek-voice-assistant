package uz.voiceassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import uz.voiceassistant.data.SettingsManager

class VoiceAssistantApp : Application() {

    companion object {
        const val CHANNEL_WAKEWORD_ID = "channel_wakeword_service"
        const val CHANNEL_AGENT_ID = "channel_screen_agent_service"
        lateinit var instance: VoiceAssistantApp
            private set
    }

    lateinit var settingsManager: SettingsManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsManager = SettingsManager(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val wakeWordChannel = NotificationChannel(
                CHANNEL_WAKEWORD_ID,
                getString(R.string.wakeword_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.wakeword_notification_text)
                setShowBadge(false)
            }

            val agentChannel = NotificationChannel(
                CHANNEL_AGENT_ID,
                getString(R.string.agent_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.agent_notification_title)
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(wakeWordChannel)
            notificationManager.createNotificationChannel(agentChannel)
        }
    }
}
