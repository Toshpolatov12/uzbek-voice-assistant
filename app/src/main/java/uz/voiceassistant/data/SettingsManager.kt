package uz.voiceassistant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to standard SharedPreferences if Keystore error occurs on certain ROMs
        context.getSharedPreferences(FALLBACK_PREFS_FILE_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREFS_FILE_NAME = "encrypted_assistant_settings"
        private const val FALLBACK_PREFS_FILE_NAME = "assistant_settings"

        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_WAKEWORD_ENABLED = "wakeword_enabled"
        private const val KEY_FALLBACK_LANGUAGE = "fallback_language"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_CONFIRM_SENSITIVE = "confirm_sensitive"

        const val DEFAULT_FALLBACK_LANG = "ru-RU"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
    }

    var geminiApiKey: String
        get() = sharedPreferences.getString(KEY_GEMINI_API_KEY, "").orEmpty()
        set(value) = sharedPreferences.edit().putString(KEY_GEMINI_API_KEY, value.trim()).apply()

    var isWakeWordEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_WAKEWORD_ENABLED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_WAKEWORD_ENABLED, value).apply()

    var fallbackLanguage: String
        get() = sharedPreferences.getString(KEY_FALLBACK_LANGUAGE, DEFAULT_FALLBACK_LANG) ?: DEFAULT_FALLBACK_LANG
        set(value) = sharedPreferences.edit().putString(KEY_FALLBACK_LANGUAGE, value).apply()

    var geminiModel: String
        get() = sharedPreferences.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL) ?: DEFAULT_GEMINI_MODEL
        set(value) = sharedPreferences.edit().putString(KEY_GEMINI_MODEL, value).apply()

    var isSensitiveConfirmationEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_CONFIRM_SENSITIVE, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_CONFIRM_SENSITIVE, value).apply()
}
