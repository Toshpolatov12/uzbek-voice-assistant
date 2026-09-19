package uz.voiceassistant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class AiProvider(val displayName: String) {
    GEMINI("Google Gemini (AI Studio)"),
    OPENAI("OpenAI (ChatGPT gpt-4o)"),
    CUSTOM("Boshqa AI / OpenRouter / Groq / Custom")
}

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
        context.getSharedPreferences(FALLBACK_PREFS_FILE_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREFS_FILE_NAME = "encrypted_assistant_settings"
        private const val FALLBACK_PREFS_FILE_NAME = "assistant_settings"

        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_CUSTOM_ENDPOINT = "custom_endpoint"
        private const val KEY_CUSTOM_MODEL = "custom_model"
        private const val KEY_WAKEWORD_ENABLED = "wakeword_enabled"
        private const val KEY_FALLBACK_LANGUAGE = "fallback_language"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_CONFIRM_SENSITIVE = "confirm_sensitive"

        const val DEFAULT_FALLBACK_LANG = "ru-RU"
        const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash"
        const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
        const val DEFAULT_CUSTOM_ENDPOINT = "https://api.openai.com/v1/chat/completions"
    }

    var apiKey: String
        get() = sharedPreferences.getString(KEY_API_KEY, "").orEmpty()
        set(value) = sharedPreferences.edit().putString(KEY_API_KEY, value.trim()).apply()

    // Backward compatibility alias for existing calls
    var geminiApiKey: String
        get() = apiKey
        set(value) { apiKey = value }

    var aiProvider: AiProvider
        get() {
            val name = sharedPreferences.getString(KEY_AI_PROVIDER, AiProvider.GEMINI.name)
            return try {
                AiProvider.valueOf(name ?: AiProvider.GEMINI.name)
            } catch (e: Exception) {
                AiProvider.GEMINI
            }
        }
        set(value) = sharedPreferences.edit().putString(KEY_AI_PROVIDER, value.name).apply()

    var customEndpoint: String
        get() = sharedPreferences.getString(KEY_CUSTOM_ENDPOINT, DEFAULT_CUSTOM_ENDPOINT) ?: DEFAULT_CUSTOM_ENDPOINT
        set(value) = sharedPreferences.edit().putString(KEY_CUSTOM_ENDPOINT, value.trim()).apply()

    var customModel: String
        get() = sharedPreferences.getString(KEY_CUSTOM_MODEL, DEFAULT_OPENAI_MODEL) ?: DEFAULT_OPENAI_MODEL
        set(value) = sharedPreferences.edit().putString(KEY_CUSTOM_MODEL, value.trim()).apply()

    var geminiModel: String
        get() = sharedPreferences.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL) ?: DEFAULT_GEMINI_MODEL
        set(value) = sharedPreferences.edit().putString(KEY_GEMINI_MODEL, value.trim()).apply()

    var isWakeWordEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_WAKEWORD_ENABLED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_WAKEWORD_ENABLED, value).apply()

    var fallbackLanguage: String
        get() = sharedPreferences.getString(KEY_FALLBACK_LANGUAGE, DEFAULT_FALLBACK_LANG) ?: DEFAULT_FALLBACK_LANG
        set(value) = sharedPreferences.edit().putString(KEY_FALLBACK_LANGUAGE, value).apply()

    var isSensitiveConfirmationEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_CONFIRM_SENSITIVE, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_CONFIRM_SENSITIVE, value).apply()
}
