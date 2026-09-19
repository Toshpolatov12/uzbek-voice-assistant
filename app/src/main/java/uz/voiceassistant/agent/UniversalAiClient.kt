package uz.voiceassistant.agent

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import uz.voiceassistant.data.AiProvider
import java.util.concurrent.TimeUnit

class UniversalAiClient(
    private val providerProvider: () -> AiProvider,
    private val apiKeyProvider: () -> String,
    private val modelProvider: () -> String,
    private val customEndpointProvider: () -> String
) {
    private val tag = "UniversalAiClient"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun decideNextAction(
        userCommand: String,
        stepNumber: Int,
        screenshotBytes: ByteArray?,
        interactiveElementsJson: String,
        screenDimensions: Pair<Int, Int>
    ): Result<AgentStepAction> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("AI API kaliti kiritilmagan. Ilova sozlamalaridan kalitni kiriting.")
            )
        }

        val provider = providerProvider()

        val systemPrompt = """
            You are an Android UI Navigation Agent. The user wants to accomplish this task on their smartphone:
            Task (in Uzbek): "$userCommand"
            Current step: $stepNumber of 15.
            Screen resolution: ${screenDimensions.first}x${screenDimensions.second}.
            
            You are provided with:
            1. An optional current screenshot of the device screen.
            2. A compact list of interactive UI elements detected in the accessibility hierarchy with their bounding box [left, top, right, bottom], text, and contentDescription.
            
            Your goal is to decide the ONE next action to accomplish the user's task.
            
            ALLOWED ACTIONS:
            - "tap": Tap on a specific UI element or coordinate. Provide "target_bounds": [x1, y1, x2, y2].
            - "type": Input text into the currently focused or targeted editable field. Provide "text": "value to type" and optionally "target_bounds".
            - "open_app": Open an application directly by name. Provide "text": "appName" (e.g. "telegram", "youtube", "whatsapp").
            - "scroll": Scroll the screen (down or up). "text": "down" or "up".
            - "back": Navigate back.
            - "home": Go to home screen.
            - "done": Task has been completely accomplished.
            
            SAFETY CHECK:
            If this action involves payment/purchasing, deleting data, resetting phone, or changing critical security/passwords, set "is_sensitive": true. Otherwise set false.
            
            OUTPUT FORMAT:
            You MUST respond with valid raw JSON only (no markdown quotes, no explanations outside json):
            {
              "action": "tap" | "type" | "open_app" | "scroll" | "back" | "home" | "done",
              "target_bounds": [x1, y1, x2, y2] | null,
              "text": "..." | null,
              "reasoning_for_user": "Short explanation in Uzbek describing this step to the user",
              "is_sensitive": false
            }
        """.trimIndent()

        val userTextPrompt = """
            Interactive Elements Hierarchy:
            $interactiveElementsJson

            Analyze the screen and determine the single next action to achieve the user's goal: "$userCommand".
            Respond ONLY with strict JSON.
        """.trimIndent()

        return@withContext when (provider) {
            AiProvider.GEMINI -> callGeminiApi(apiKey, modelProvider(), systemPrompt, userTextPrompt, screenshotBytes)
            AiProvider.OPENAI -> callOpenAiCompatibleApi(
                endpoint = "https://api.openai.com/v1/chat/completions",
                apiKey = apiKey,
                model = modelProvider().ifBlank { "gpt-4o-mini" },
                systemPrompt = systemPrompt,
                userText = userTextPrompt,
                screenshotBytes = screenshotBytes
            )
            AiProvider.CUSTOM -> callOpenAiCompatibleApi(
                endpoint = customEndpointProvider().ifBlank { "https://api.openai.com/v1/chat/completions" },
                apiKey = apiKey,
                model = modelProvider().ifBlank { "gpt-4o-mini" },
                systemPrompt = systemPrompt,
                userText = userTextPrompt,
                screenshotBytes = screenshotBytes
            )
        }
    }

    private fun callGeminiApi(
        apiKey: String,
        modelName: String,
        systemPrompt: String,
        userText: String,
        screenshotBytes: ByteArray?
    ): Result<AgentStepAction> {
        val model = modelName.trim().ifBlank { "gemini-3.5-flash" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val contentsArray = JSONArray()
        val partsArray = JSONArray()

        val promptPart = JSONObject().apply {
            put("text", "$systemPrompt\n\n$userText")
        }
        partsArray.put(promptPart)

        if (screenshotBytes != null && screenshotBytes.isNotEmpty()) {
            val imagePart = JSONObject().apply {
                val inlineData = JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", Base64.encodeToString(screenshotBytes, Base64.NO_WRAP))
                }
                put("inline_data", inlineData)
            }
            partsArray.put(imagePart)
        }

        val contentObject = JSONObject().apply {
            put("role", "user")
            put("parts", partsArray)
        }
        contentsArray.put(contentObject)

        val requestBodyJson = JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("topP", 0.95)
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                Log.e(tag, "Gemini API error ($responseBody)")
                return Result.failure(Exception("Gemini xatosi (${response.code}): $responseBody"))
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawReplyText = parts?.optJSONObject(0)?.optString("text").orEmpty()

            Result.success(parseAgentReply(rawReplyText))
        } catch (e: Exception) {
            Log.e(tag, "Exception during Gemini call", e)
            Result.failure(e)
        }
    }

    private fun callOpenAiCompatibleApi(
        endpoint: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userText: String,
        screenshotBytes: ByteArray?
    ): Result<AgentStepAction> {
        val messagesArray = JSONArray()

        // System message
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        // User message with text and optional image
        val userContentArray = JSONArray()
        userContentArray.put(JSONObject().apply {
            put("type", "text")
            put("text", userText)
        })

        if (screenshotBytes != null && screenshotBytes.isNotEmpty()) {
            val b64 = Base64.encodeToString(screenshotBytes, Base64.NO_WRAP)
            userContentArray.put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:image/jpeg;base64,$b64")
                    put("detail", "low")
                })
            })
        }

        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", userContentArray)
        })

        val requestJson = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", 0.1)
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                Log.e(tag, "OpenAI-compatible API error ($responseBody)")
                return Result.failure(Exception("AI server xatosi (${response.code}): $responseBody"))
            }

            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.optJSONArray("choices")
            val firstChoice = choices?.optJSONObject(0)
            val message = firstChoice?.optJSONObject("message")
            val rawReplyText = message?.optString("content").orEmpty()

            Result.success(parseAgentReply(rawReplyText))
        } catch (e: Exception) {
            Log.e(tag, "Exception during OpenAI-compatible call", e)
            Result.failure(e)
        }
    }

    private fun parseAgentReply(rawText: String): AgentStepAction {
        var cleanJson = rawText.trim()
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.removePrefix("```json")
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.removePrefix("```")
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.removeSuffix("```")
        }
        cleanJson = cleanJson.trim()

        val json = JSONObject(cleanJson)
        val action = json.optString("action", "done")
        val reasoning = json.optString("reasoning_for_user", "Amal bajarilmoqda")
        val isSensitive = json.optBoolean("is_sensitive", false)
        val text = if (json.has("text") && !json.isNull("text")) json.getString("text") else null

        val boundsList = mutableListOf<Int>()
        val boundsJson = json.optJSONArray("target_bounds")
        if (boundsJson != null && boundsJson.length() == 4) {
            for (i in 0 until 4) {
                boundsList.add(boundsJson.getInt(i))
            }
        }

        return AgentStepAction(
            action = action,
            targetBounds = if (boundsList.size == 4) boundsList else null,
            text = text,
            reasoningForUser = reasoning,
            isSensitive = isSensitive
        )
    }

    suspend fun transcribeAudio(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp4"
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("API Kalit kiritilmagan."))
        }

        val primaryModel = modelProvider().trim().ifBlank { "gemini-2.5-flash" }
        val modelsToTry = listOf(primaryModel, "gemini-2.5-flash", "gemini-1.5-flash").distinct()

        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val partsArray = JSONArray().apply {
            put(JSONObject().apply {
                put("text", "Ushbu audio yozuvdagi inson aytgan gapni o'zbek tilida aniqlab, faqat aytilgan matnni qaytar (boshqa hech qanday so'z yoki belgilarsiz):")
            })
            put(JSONObject().apply {
                put("inline_data", JSONObject().apply {
                    put("mime_type", mimeType)
                    put("data", base64Audio)
                })
            })
        }

        val contentsArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("parts", partsArray)
            })
        }

        val requestBodyJson = JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.0)
            })
        }

        var lastError: Exception? = null

        for (m in modelsToTry) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseBody)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    var rawText = parts?.optJSONObject(0)?.optString("text").orEmpty().trim()

                    if (rawText.startsWith("\"") && rawText.endsWith("\"") && rawText.length >= 2) {
                        rawText = rawText.substring(1, rawText.length - 1).trim()
                    }

                    if (rawText.isNotBlank()) {
                        return@withContext Result.success(rawText)
                    }
                } else {
                    lastError = Exception("Ovozni aniqlashda xatolik (${response.code}): $responseBody")
                    if (response.code != 404) {
                        break // Non-404 error (e.g. auth or quota) - don't spam other models
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        Result.failure(lastError ?: Exception("Ovoz aniqlanmadi."))
    }
}
