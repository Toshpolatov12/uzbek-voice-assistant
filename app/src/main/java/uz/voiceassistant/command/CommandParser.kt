package uz.voiceassistant.command

import java.util.Locale
import java.util.regex.Pattern

sealed class NativeCommand {
    object GetTime : NativeCommand()
    data class Flashlight(val enable: Boolean) : NativeCommand()
    enum class VolumeAction { UP, DOWN, MUTE }
    data class Volume(val action: VolumeAction) : NativeCommand()
    enum class SettingsTarget { WIFI, BLUETOOTH }
    data class OpenSettings(val target: SettingsTarget) : NativeCommand()
    data class SetAlarm(val hour: Int, val minute: Int, val message: String = "Budilnik") : NativeCommand()
    data class CallContact(val name: String) : NativeCommand()
    data class SendSms(val recipient: String, val message: String) : NativeCommand()
    data class OpenApp(val appName: String) : NativeCommand()
    data class Unknown(val rawText: String) : NativeCommand()
}

object CommandParser {

    fun parse(input: String): NativeCommand {
        val text = cleanText(input)

        // 1. Time query
        if (text.contains("soat necha") ||
            text.contains("vaqt qancha") ||
            text.contains("vaqt nechchi") ||
            text.contains("soat nechchi") ||
            text.contains("hozirgi vaqt") ||
            text.contains("soatni ayt")
        ) {
            return NativeCommand.GetTime
        }

        // 2. Flashlight
        if (text.contains("fonarni yoq") ||
            text.contains("chiroqni yoq") ||
            text.contains("fonar yoqilsin") ||
            text.contains("chiroq yoqilsin") ||
            text == "fonar yoq" ||
            text == "chiroq yoq"
        ) {
            return NativeCommand.Flashlight(enable = true)
        }

        if (text.contains("fonarni o'chir") ||
            text.contains("chiroqni o'chir") ||
            text.contains("fonar o'chsin") ||
            text.contains("chiroq o'chsin") ||
            text == "fonar o'chir" ||
            text == "chiroq o'chir"
        ) {
            return NativeCommand.Flashlight(enable = false)
        }

        // 3. Volume
        if (text.contains("ovozni balandlat") ||
            text.contains("ovozni kotar") ||
            text.contains("ovozni ko'tar") ||
            text.contains("ovozni oshir") ||
            text.contains("ovozni balandroq")
        ) {
            return NativeCommand.Volume(NativeCommand.VolumeAction.UP)
        }

        if (text.contains("ovozni pasaytir") ||
            text.contains("ovozni sekinlat") ||
            text.contains("ovozni kamaytir") ||
            text.contains("ovozni sekinroq")
        ) {
            return NativeCommand.Volume(NativeCommand.VolumeAction.DOWN)
        }

        if (text.contains("ovozni o'chir") ||
            text.contains("ovozni ochir") ||
            text.contains("ovozsiz rejim")
        ) {
            return NativeCommand.Volume(NativeCommand.VolumeAction.MUTE)
        }

        // 4. Settings Shortcuts
        if (text.contains("wifi ni och") ||
            text.contains("vayfayni och") ||
            text.contains("vayfay sozlamalari") ||
            text.contains("wifi sozlamalari") ||
            text == "wifi" || text == "vayfay"
        ) {
            return NativeCommand.OpenSettings(NativeCommand.SettingsTarget.WIFI)
        }

        if (text.contains("bluetooth ni och") ||
            text.contains("blyutuzni och") ||
            text.contains("bluetooth sozlamalari") ||
            text.contains("blyutuz sozlamalari") ||
            text == "bluetooth" || text == "blyutuz"
        ) {
            return NativeCommand.OpenSettings(NativeCommand.SettingsTarget.BLUETOOTH)
        }

        // 5. Alarm (Budilnik)
        // Matches e.g.: "budilnikni 07:30 ga qo'y", "soat 8 da uyg'ot", "meni 7:00 da uyg'ot"
        val alarmPatternTime = Pattern.compile("(?:budilnikni|soat|meni soat)?\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(?:ga|da)?\\s*(?:qo'y|qoy|ornat|o'rnat|uygot|uyg'ot)", Pattern.CASE_INSENSITIVE)
        val alarmMatcher = alarmPatternTime.matcher(text)
        if (alarmMatcher.find()) {
            val hour = alarmMatcher.group(1)?.toIntOrNull() ?: 8
            val minute = alarmMatcher.group(2)?.toIntOrNull() ?: 0
            return NativeCommand.SetAlarm(hour = hour, minute = minute)
        }

        // 6. Send SMS
        // Matches e.g.: "Ali ga xabar yoz: bugun kelasanmi", "Dadamga sms yubor: yo'ldaman"
        val smsPattern = Pattern.compile("(.+?)(?:ga|qaysi)?\\s*(?:xabar|sms)\\s*(?:yoz|yubor)[:\\s]+(.+)", Pattern.CASE_INSENSITIVE)
        val smsMatcher = smsPattern.matcher(text)
        if (smsMatcher.find()) {
            val rawName = smsMatcher.group(1).orEmpty().trim()
            val message = smsMatcher.group(2).orEmpty().trim()
            val cleanName = stripCaseSuffix(rawName)
            return NativeCommand.SendSms(recipient = cleanName, message = message)
        }

        // 7. Call contact
        // Matches e.g.: "Aliga qo'ng'iroq qil", "Dadamga tel qil", "Sobirni ter", "qo'ng'iroq qil: Ali"
        val callPattern = Pattern.compile("(.+?)(?:ga|ni)?\\s*(?:qo'ng'iroq qil|qongiroq qil|tel qil|ter|chaqir)$", Pattern.CASE_INSENSITIVE)
        val callMatcher = callPattern.matcher(text)
        if (callMatcher.find()) {
            val candidate = callMatcher.group(1).orEmpty().trim()
            if (candidate.isNotBlank() && !candidate.contains("ilova") && !candidate.contains("dastur")) {
                val cleanName = stripCaseSuffix(candidate)
                return NativeCommand.CallContact(name = cleanName)
            }
        }

        // 8. Open App
        // Matches e.g.: "Telegramni och", "YouTube ilovasini och", "Kamerani och", "Sozlamalarni och"
        val openAppPattern = Pattern.compile("(.+?)(?:ni|i)?\\s*(?:ilovasini|dasturini)?\\s*och$", Pattern.CASE_INSENSITIVE)
        val appMatcher = openAppPattern.matcher(text)
        if (appMatcher.find()) {
            val appRaw = appMatcher.group(1).orEmpty().trim()
            val cleanApp = stripCaseSuffix(appRaw)
            if (cleanApp.isNotBlank()) {
                return NativeCommand.OpenApp(appName = cleanApp)
            }
        }

        return NativeCommand.Unknown(rawText = input)
    }

    private fun cleanText(text: String): String {
        return text.trim()
            .lowercase(Locale.ROOT)
            .replace("‘", "'")
            .replace("’", "'")
            .replace("`", "'")
    }

    /**
     * Strips Uzbek noun suffixes like -ga, -ka, -qa, -ni, -ning
     */
    private fun stripCaseSuffix(name: String): String {
        var clean = name.trim()
        val suffixes = listOf("ga", "ka", "qa", "ni", "ning", "dan", "da")
        for (suffix in suffixes) {
            if (clean.endsWith(suffix) && clean.length > suffix.length + 2) {
                clean = clean.substring(0, clean.length - suffix.length).trim()
                break
            }
        }
        return clean
    }
}
