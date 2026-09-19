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
import java.util.concurrent.TimeUnit

class GeminiVisionClient(
    private val apiKeyProvider: () -> String,
    private val modelProvider: () -> String = { "gemini-3.5-flash" }
) {
    private val tag = "GeminiVisionClient"

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
            return@withContext Result.failure(IllegalStateException("Gemini API kaliti kiritilmagan. Ilova sozlamalaridan kalitni kiriting."))
        }

        val model = modelProvider().trim().ifBlank { "gemini-3.5-flash" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

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
            - "scroll": Scroll the screen (down or up). "text": "down" or "up".
            - "back": Navigate back.
            - "home": Go to home screen.
            - "done": Task has been completely accomplished.
            
            SAFETY CHECK:
            If this action involves payment/purchasing, deleting data, resetting phone, or changing critical security/passwords, set "is_sensitive": true. Otherwise set false.
            
            OUTPUT FORMAT:
            You MUST respond with valid raw JSON only (no markdown quotes, no explanations outside json):
            {
              "action": "tap" | "type" | "scroll" | "back" | "home" | "done",
              "target_bounds": [x1, y1, x2, y2] | null,
              "text": "..." | null,
              "reasoning_for_user": "Short explanation in Uzbek describing this step to the user",
              "is_sensitive": false
            }
        """.trimIndent()

        val contentsArray = JSONArray()
        val partsArray = JSONArray()

        // 1. Text prompt part
        val promptPart = JSONObject().apply {
            val userMessage = """
                Interactive Elements Hierarchy:
                $interactiveElementsJson

                Analyze the screen and determine the single next action to achieve the user's goal: "$userCommand".
                Respond ONLY with strict JSON.
            """.trimIndent()
            put("text", "$systemPrompt\n\n$userMessage")
        }
        partsArray.put(promptPart)

        // 2. Inline Image part (if screenshot available)
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

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                Log.e(tag, "Gemini API error ($responseBody)")
                return@withContext Result.failure(Exception("Gemini API xatosi (${response.code}): $responseBody"))
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawReplyText = parts?.optJSONObject(0)?.optString("text").orEmpty()

            val action = parseAgentReply(rawReplyText)
            Result.success(action)
        } catch (e: Exception) {
            Log.e(tag, "Exception during Gemini Vision call", e)
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
}
