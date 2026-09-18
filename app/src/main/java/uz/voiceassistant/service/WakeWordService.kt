package uz.voiceassistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import uz.voiceassistant.R
import uz.voiceassistant.VoiceAssistantApp
import uz.voiceassistant.ui.AssistantSessionActivity
import uz.voiceassistant.ui.MainActivity
import uz.voiceassistant.wakeword.WakeWordDetector

/**
 * Foreground Service for hands-free wake word detection ("Salom Yordamchi").
 *
 * PRIVACY & OS REQUIREMENT NOTE:
 * Android strictly requires a persistent user-visible notification while the microphone is active
 * in the background (foregroundServiceType="microphone"). This is a mandatory OS privacy requirement
 * enforced by Google to prevent covert listening, and it CANNOT be hidden or bypassed. The notification
 * reassures the user that the microphone is actively monitoring exclusively for the local wake word.
 */
class WakeWordService : Service() {

    private val tag = "WakeWordService"
    private var detector: WakeWordDetector? = null

    companion object {
        private const val NOTIFICATION_ID = 2001
        const val ACTION_START = "uz.voiceassistant.action.START_WAKEWORD"
        const val ACTION_STOP = "uz.voiceassistant.action.STOP_WAKEWORD"

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        initDetector()
    }

    private fun initDetector() {
        detector = WakeWordDetector(this) {
            Log.i(tag, "Wake word triggered! Invoking assistant...")
            launchAssistant()
        }
    }

    private fun launchAssistant() {
        val intent = Intent(this, AssistantSessionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AssistantSessionActivity.EXTRA_AUTO_START_LISTENING, true)
        }
        startActivity(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundWithNotification()
                detector?.start()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, VoiceAssistantApp.CHANNEL_WAKEWORD_ID)
            .setContentTitle(getString(R.string.wakeword_notification_title))
            .setContentText(getString(R.string.wakeword_notification_text))
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.stop()
        detector?.release()
        detector = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
