package uz.voiceassistant.command

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NativeActionExecutor(private val context: Context) {

    private val tag = "NativeActionExecutor"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    fun execute(command: NativeCommand): String {
        return when (command) {
            is NativeCommand.GetTime -> getCurrentTimeFeedback()
            is NativeCommand.Flashlight -> toggleFlashlight(command.enable)
            is NativeCommand.Volume -> adjustVolume(command.action)
            is NativeCommand.OpenSettings -> openSettingsPanel(command.target)
            is NativeCommand.SetAlarm -> setAlarm(command.hour, command.minute, command.message)
            is NativeCommand.CallContact -> makePhoneCall(command.name)
            is NativeCommand.SendSms -> sendSms(command.recipient, command.message)
            is NativeCommand.OpenApp -> openAppByName(command.appName)
            is NativeCommand.Unknown -> ""
        }
    }

    private fun getCurrentTimeFeedback(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = sdf.format(Date())
        return "Hozir soat $currentTime."
    }

    private fun toggleFlashlight(enable: Boolean): String {
        if (cameraManager == null) return "Chiroqni (fonar) boshqarib bo'lmadi."

        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, enable)
                if (enable) "Chiroq yoqildi." else "Chiroq o'chirildi."
            } else {
                "Qurilmada fonar (chiroq) topilmadi."
            }
        } catch (e: CameraAccessException) {
            Log.e(tag, "Camera error while toggling flashlight", e)
            "Chiroqqa ulanishda xatolik yuz berdi."
        }
    }

    private fun adjustVolume(action: NativeCommand.VolumeAction): String {
        if (audioManager == null) return "Ovozni sozlab bo'lmadi."

        return when (action) {
            NativeCommand.VolumeAction.UP -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI
                )
                "Ovoz balandlatildi."
            }
            NativeCommand.VolumeAction.DOWN -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
                "Ovoz pasaytirildi."
            }
            NativeCommand.VolumeAction.MUTE -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_MUTE,
                    AudioManager.FLAG_SHOW_UI
                )
                "Ovoz o'chirildi."
            }
        }
    }

    private fun openSettingsPanel(target: NativeCommand.SettingsTarget): String {
        val action = when (target) {
            NativeCommand.SettingsTarget.WIFI -> Settings.ACTION_WIFI_SETTINGS
            NativeCommand.SettingsTarget.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
        }
        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            when (target) {
                NativeCommand.SettingsTarget.WIFI -> "Wi-Fi sozlamalari ochilmoqda."
                NativeCommand.SettingsTarget.BLUETOOTH -> "Bluetooth sozlamalari ochilmoqda."
            }
        } catch (e: Exception) {
            Log.e(tag, "Error opening settings", e)
            "Sozlamalarni ochib bo'lmadi."
        }
    }

    private fun setAlarm(hour: Int, minute: Int, message: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            val minFormatted = if (minute < 10) "0$minute" else "$minute"
            "Budilnik soat $hour:$minFormatted ga qo'yildi."
        } catch (e: Exception) {
            Log.e(tag, "Error setting alarm", e)
            "Budilnik o'rnatib bo'lmadi."
        }
    }

    private fun makePhoneCall(name: String): String {
        val phoneNumber = resolvePhoneNumber(name)
        if (phoneNumber.isNullOrBlank()) {
            return "Kontaktlar orasidan \"$name\" topilmadi."
        }

        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(callIntent)
            "$name ga qo'ng'iroq qilinmoqda."
        } catch (e: SecurityException) {
            // Fallback to ACTION_DIAL if CALL_PHONE permission is missing
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            "$name raqami terish oynasiga kiritildi."
        } catch (e: Exception) {
            Log.e(tag, "Error initiating phone call", e)
            "Qo'ng'iroq qilib bo'lmadi."
        }
    }

    private fun sendSms(recipient: String, message: String): String {
        val phoneNumber = resolvePhoneNumber(recipient) ?: recipient

        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber")
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(smsIntent)
            "$recipient ga xabar yuborish oynasi ochildi."
        } catch (e: Exception) {
            Log.e(tag, "Error sending SMS", e)
            "SMS yuborib bo'lmadi."
        }
    }

    private fun openAppByName(appName: String): String {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)

        val targetApp = resolveInfos.firstOrNull { resolveInfo ->
            val label = resolveInfo.loadLabel(packageManager).toString().lowercase(Locale.ROOT)
            val pkg = resolveInfo.activityInfo.packageName.lowercase(Locale.ROOT)
            val query = appName.lowercase(Locale.ROOT)
            label.contains(query) || pkg.contains(query)
        }

        return if (targetApp != null) {
            val launchIntent = packageManager.getLaunchIntentForPackage(targetApp.activityInfo.packageName)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(launchIntent)
                val label = targetApp.loadLabel(packageManager)
                "$label ilovasi ochilmoqda."
            } else {
                "Ilovani ishga tushirib bo'lmadi."
            }
        } else {
            "Qurilmangizda \"$appName\" nomli ilova topilmadi."
        }
    }

    private fun resolvePhoneNumber(contactName: String): String? {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$contactName%"),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex != -1) {
                    return it.getString(numberIndex)
                }
            }
        }
        return null
    }
}
