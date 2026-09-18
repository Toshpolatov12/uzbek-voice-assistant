package uz.voiceassistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import uz.voiceassistant.VoiceAssistantApp
import uz.voiceassistant.agent.AgentStatus
import uz.voiceassistant.agent.AgentStepAction
import uz.voiceassistant.agent.GeminiVisionClient
import uz.voiceassistant.agent.UiElementNode
import uz.voiceassistant.speech.SpeechManager
import uz.voiceassistant.speech.TtsManager
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class ScreenAgentService : AccessibilityService() {

    private val tag = "ScreenAgentService"
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var agentJob: Job? = null

    private lateinit var ttsManager: TtsManager
    private lateinit var geminiClient: GeminiVisionClient
    private val screenshotExecutor = Executors.newSingleThreadExecutor()

    companion object {
        @Volatile
        var instance: ScreenAgentService? = null
            private set

        fun isEnabled(): Boolean = instance != null

        fun executeTask(context: Context, userCommand: String) {
            val service = instance
            if (service != null) {
                service.startAgentTask(userCommand)
            } else {
                Log.w("ScreenAgentService", "Accessibility service is not active")
                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(tag, "ScreenAgentService connected and active.")

        val app = applicationContext as VoiceAssistantApp
        ttsManager = TtsManager(this)
        ttsManager.setFallbackLanguage(app.settingsManager.fallbackLanguage)

        geminiClient = GeminiVisionClient(
            apiKeyProvider = { app.settingsManager.geminiApiKey },
            modelProvider = { app.settingsManager.geminiModel }
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {
        agentJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        agentJob?.cancel()
        ttsManager.shutdown()
    }

    fun startAgentTask(userCommand: String) {
        agentJob?.cancel()
        agentJob = serviceScope.launch {
            runAgentLoop(userCommand)
        }
    }

    private suspend fun runAgentLoop(userCommand: String) {
        ttsManager.speak("Ekranni ko'rib vazifani bajarishni boshlayapman: $userCommand")
        delay(1500)

        val maxSteps = 15
        var currentStep = 1

        val metrics = resources.displayMetrics
        val screenDimensions = Pair(metrics.widthPixels, metrics.heightPixels)

        while (currentStep <= maxSteps) {
            Log.i(tag, "Agent Step $currentStep of $maxSteps")

            // 1. Capture screen hierarchy and optional screenshot
            val interactiveNodes = collectInteractiveNodes()
            val nodesJson = buildCompactNodeTreeJson(interactiveNodes)
            val screenshotBytes = captureScreenshotJpeg()

            // 2. Query Gemini Vision
            val decisionResult = geminiClient.decideNextAction(
                userCommand = userCommand,
                stepNumber = currentStep,
                screenshotBytes = screenshotBytes,
                interactiveElementsJson = nodesJson,
                screenDimensions = screenDimensions
            )

            if (decisionResult.isFailure) {
                val errorMsg = decisionResult.exceptionOrNull()?.message ?: "Xatolik yuz berdi"
                Log.e(tag, "Gemini call failed: $errorMsg")
                ttsManager.speak("Kechirasiz, sun'iy intellekt bilan bog'lanishda xatolik yuz berdi: $errorMsg")
                break
            }

            val stepAction = decisionResult.getOrThrow()
            Log.i(tag, "Next Action: ${stepAction.action}, Reasoning: ${stepAction.reasoningForUser}")

            // 3. Spoken reasoning in Uzbek
            ttsManager.speak(stepAction.reasoningForUser)
            delay(1000)

            // 4. Check for sensitive confirmation requirement
            if (isSensitiveAction(stepAction)) {
                val confirmed = requestSpokenConfirmation()
                if (!confirmed) {
                    ttsManager.speak("Xavfsizlik talabiga binoan amal bekor qilindi.")
                    break
                }
            }

            // 5. Complete check
            if (stepAction.action.equals("done", ignoreCase = true)) {
                ttsManager.speak("Vazifa muvaffaqiyatli bajarildi!")
                break
            }

            // 6. Execute action
            val executed = executeAction(stepAction, screenDimensions)
            if (!executed) {
                Log.w(tag, "Failed to execute action: ${stepAction.action}")
            }

            delay(2000) // Allow UI transition to settle
            currentStep++
        }

        if (currentStep > maxSteps) {
            ttsManager.speak("Vazifa juda ko'p qadamdan iborat bo'lgani uchun to'xtatildi.")
        }
    }

    private fun isSensitiveAction(action: AgentStepAction): Boolean {
        val app = applicationContext as VoiceAssistantApp
        if (!app.settingsManager.isSensitiveConfirmationEnabled) return false

        if (action.isSensitive) return true

        val sensitiveKeywords = listOf(
            "to'lov", "tolov", "pay", "pul", "kartani", "o'chirish", "ochirish",
            "delete", "remove", "format", "reset", "parol", "password", "xavfsizlik", "pin"
        )
        val text = (action.reasoningForUser + " " + (action.text ?: "")).lowercase()
        return sensitiveKeywords.any { text.contains(it) }
    }

    private suspend fun requestSpokenConfirmation(): Boolean = suspendCancellableCoroutine { continuation ->
        ttsManager.speak(
            "Diqqat! Ushbu amal xavfsizlik, to'lov yoki o'chirish bilan bog'liq. Davom ettiraymi? Ha yoki Yo'q deb javob bering.",
            flushQueue = true
        ) {
            // Once TTS finishes speaking prompt, start temporary speech recognizer
            val app = applicationContext as VoiceAssistantApp
            val speechManager = SpeechManager(this, app.settingsManager.fallbackLanguage)

            speechManager.onResults = { answer ->
                speechManager.destroy()
                val isAffirmative = answer.lowercase().contains("ha") ||
                        answer.lowercase().contains("xa") ||
                        answer.lowercase().contains("yes") ||
                        answer.lowercase().contains("mayli") ||
                        answer.lowercase().contains("albatta")
                if (continuation.isActive) continuation.resume(isAffirmative)
            }

            speechManager.onError = { _, _ ->
                speechManager.destroy()
                if (continuation.isActive) continuation.resume(false)
            }

            speechManager.startListening()
        }
    }

    private suspend fun executeAction(action: AgentStepAction, screenDimensions: Pair<Int, Int>): Boolean {
        return when (action.action.lowercase()) {
            "tap" -> {
                val bounds = action.targetBounds
                val (x, y) = if (bounds != null && bounds.size == 4) {
                    val centerX = (bounds[0] + bounds[2]) / 2f
                    val centerY = (bounds[1] + bounds[3]) / 2f
                    Pair(centerX, centerY)
                } else {
                    Pair(screenDimensions.first / 2f, screenDimensions.second / 2f)
                }
                dispatchTapGesture(x, y)
            }
            "scroll" -> {
                val isScrollUp = action.text?.equals("up", ignoreCase = true) == true
                dispatchScrollGesture(screenDimensions, isScrollUp)
            }
            "type" -> {
                val textToType = action.text.orEmpty()
                typeTextIntoActiveOrTargetNode(textToType, action.targetBounds)
            }
            "back" -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            "home" -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            else -> false
        }
    }

    private suspend fun dispatchTapGesture(x: Float, y: Float): Boolean = suspendCancellableCoroutine { cont ->
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resume(false)
            }
        }, null)
    }

    private suspend fun dispatchScrollGesture(screenDimensions: Pair<Int, Int>, scrollUp: Boolean): Boolean = suspendCancellableCoroutine { cont ->
        val width = screenDimensions.first.toFloat()
        val height = screenDimensions.second.toFloat()

        val startX = width / 2f
        val endX = width / 2f
        val startY = if (scrollUp) height * 0.3f else height * 0.7f
        val endY = if (scrollUp) height * 0.7f else height * 0.3f

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 300)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resume(false)
            }
        }, null)
    }

    private fun typeTextIntoActiveOrTargetNode(text: String, targetBounds: List<Int>?): Boolean {
        val root = rootInActiveWindow ?: return false

        // Try to find targeted editable node
        var targetNode: AccessibilityNodeInfo? = null
        if (targetBounds != null && targetBounds.size == 4) {
            val rect = Rect(targetBounds[0], targetBounds[1], targetBounds[2], targetBounds[3])
            targetNode = findNodeByBounds(root, rect)
        }

        if (targetNode == null) {
            targetNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }

        if (targetNode != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        return false
    }

    private fun findNodeByBounds(node: AccessibilityNodeInfo, targetRect: Rect): AccessibilityNodeInfo? {
        val nodeRect = Rect()
        node.getBoundsInScreen(nodeRect)
        if (nodeRect == targetRect && node.isEditable) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByBounds(child, targetRect)
            if (found != null) return found
        }
        return null
    }

    private fun collectInteractiveNodes(): List<UiElementNode> {
        val result = mutableListOf<UiElementNode>()
        val root = rootInActiveWindow ?: return result
        var counter = 0

        fun traverse(node: AccessibilityNodeInfo) {
            val rect = Rect()
            node.getBoundsInScreen(rect)

            val text = node.text?.toString().orEmpty()
            val contentDesc = node.contentDescription?.toString().orEmpty()
            val isClickable = node.isClickable
            val isEditable = node.isEditable
            val isScrollable = node.isScrollable

            if (text.isNotBlank() || contentDesc.isNotBlank() || isClickable || isEditable || isScrollable) {
                result.add(
                    UiElementNode(
                        id = counter++,
                        text = text,
                        contentDescription = contentDesc,
                        className = node.className?.toString().orEmpty(),
                        viewId = node.viewIdResourceName.orEmpty(),
                        isClickable = isClickable,
                        isEditable = isEditable,
                        isScrollable = isScrollable,
                        bounds = rect
                    )
                )
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child)
            }
        }

        traverse(root)
        return result
    }

    private fun buildCompactNodeTreeJson(nodes: List<UiElementNode>): String {
        val array = JSONArray()
        for (node in nodes.take(40)) { // Limit to top 40 interactive elements to keep payload compact
            val item = JSONObject().apply {
                put("id", node.id)
                if (node.text.isNotBlank()) put("text", node.text)
                if (node.contentDescription.isNotBlank()) put("desc", node.contentDescription)
                put("clickable", node.isClickable)
                if (node.isEditable) put("editable", true)
                put("bounds", JSONArray(listOf(node.bounds.left, node.bounds.top, node.bounds.right, node.bounds.bottom)))
            }
            array.put(item)
        }
        return array.toString()
    }

    private suspend fun captureScreenshotJpeg(): ByteArray? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return suspendCancellableCoroutine { continuation ->
                try {
                    takeScreenshot(Display.DEFAULT_DISPLAY, screenshotExecutor, object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            val hardwareBuffer: HardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            hardwareBuffer.close()

                            if (bitmap != null) {
                                val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                bitmap.recycle()
                                val stream = ByteArrayOutputStream()
                                copy.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                                copy.recycle()
                                if (continuation.isActive) continuation.resume(stream.toByteArray())
                            } else {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(tag, "Screenshot capture failed with code: $errorCode")
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                } catch (e: Exception) {
                    Log.e(tag, "Exception during takeScreenshot", e)
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
        return null // Fallback to text-only UI hierarchy for older Android versions
    }
}
