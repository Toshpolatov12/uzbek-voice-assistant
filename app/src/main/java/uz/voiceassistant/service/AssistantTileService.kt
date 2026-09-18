package uz.voiceassistant.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import uz.voiceassistant.ui.AssistantSessionActivity

/**
 * AssistantTileService provides a one-tap Quick Settings tile in the Android notification shade
 * to instantly invoke the Uzbek Voice Assistant.
 */
class AssistantTileService : TileService() {

    private val tag = "AssistantTileService"

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Ovozli Yordamchi"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        Log.i(tag, "Quick Settings tile clicked.")

        val intent = Intent(this, AssistantSessionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AssistantSessionActivity.EXTRA_AUTO_START_LISTENING, true)
        }

        if (isLocked) {
            unlockAndRun {
                launchAssistant(intent)
            }
        } else {
            launchAssistant(intent)
        }
    }

    private fun launchAssistant(intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val pendingIntent = android.app.PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch assistant from tile", e)
        }
    }
}
