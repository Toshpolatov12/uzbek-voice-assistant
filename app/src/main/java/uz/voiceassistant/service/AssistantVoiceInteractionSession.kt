package uz.voiceassistant.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import uz.voiceassistant.R
import uz.voiceassistant.VoiceAssistantApp
import uz.voiceassistant.command.CommandParser
import uz.voiceassistant.command.NativeActionExecutor
import uz.voiceassistant.command.NativeCommand
import uz.voiceassistant.speech.SpeechManager
import uz.voiceassistant.speech.TtsManager
import uz.voiceassistant.ui.AssistantSessionActivity

class AssistantVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechManager: SpeechManager? = null
    private var ttsManager: TtsManager? = null
    private lateinit var actionExecutor: NativeActionExecutor

    private var statusTextView: TextView? = null
    private var recognizedTextView: TextView? = null
    private var listeningProgressBar: ProgressBar? = null

    override fun onCreate() {
        super.onCreate()
        val app = context.applicationContext as VoiceAssistantApp
        ttsManager = TtsManager(context)
        ttsManager?.setFallbackLanguage(app.settingsManager.fallbackLanguage)
        actionExecutor = NativeActionExecutor(context)
    }

    override fun onCreateContentView(): View {
        val rootLayout = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Bottom non-blocking card
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val density = context.resources.displayMetrics.density
            val pad = (20 * density).toInt()
            setPadding(pad, pad, pad, pad)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#FFFFFF"))
                cornerRadius = 28 * density
                setStroke((1 * density).toInt(), Color.parseColor("#E0E0E0"))
            }
            background = bg
            elevation = 16 * density

            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                val margin = (16 * density).toInt()
                setMargins(margin, margin, margin, (24 * density).toInt())
            }
            layoutParams = lp
        }

        // Header with mic icon and app name
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val micIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_mic)
            val size = (32 * context.resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
        }

        val titleView = TextView(context).apply {
            text = "O'zbek Ovozli Yordamchi"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#006C50"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val density = context.resources.displayMetrics.density
            setPadding((12 * density).toInt(), 0, 0, 0)
        }

        headerLayout.addView(micIcon)
        headerLayout.addView(titleView)

        // Status text
        statusTextView = TextView(context).apply {
            text = "Eshitmoqdaman... Gapiring"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#666666"))
            val density = context.resources.displayMetrics.density
            setPadding(0, (12 * density).toInt(), 0, 0)
        }

        // Recognized query
        recognizedTextView = TextView(context).apply {
            text = ""
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#212121"))
            val density = context.resources.displayMetrics.density
            setPadding(0, (8 * density).toInt(), 0, 0)
        }

        // Progress bar
        listeningProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            val density = context.resources.displayMetrics.density
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (4 * density).toInt()
            ).apply {
                topMargin = (12 * density).toInt()
            }
            layoutParams = lp
        }

        cardLayout.addView(headerLayout)
        cardLayout.addView(statusTextView)
        cardLayout.addView(recognizedTextView)
        cardLayout.addView(listeningProgressBar)

        rootLayout.addView(cardLayout)
        return rootLayout
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        try {
            val intent = Intent(context, AssistantSessionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(AssistantSessionActivity.EXTRA_AUTO_START_LISTENING, true)
            }
            startAssistantActivity(intent)
            hide()
        } catch (e: Exception) {
            statusTextView?.text = "Eshitmoqdaman... Gapiring"
            recognizedTextView?.text = ""
            listeningProgressBar?.visibility = View.VISIBLE
            startListening()
        }
    }

    private fun startListening() {
        speechManager?.destroy()
        val app = context.applicationContext as VoiceAssistantApp
        speechManager = SpeechManager(context, app.settingsManager.fallbackLanguage).apply {
            onPartialResults = { partialText ->
                mainHandler.post {
                    recognizedTextView?.text = partialText
                }
            }

            onResults = { finalText ->
                mainHandler.post {
                    handleUserVoiceCommand(finalText)
                }
            }

            onError = { _, errorMsg ->
                mainHandler.post {
                    statusTextView?.text = errorMsg
                    listeningProgressBar?.visibility = View.GONE
                    mainHandler.postDelayed({ hide() }, 2500)
                }
            }

            onFallbackWarning = { warning ->
                mainHandler.post {
                    statusTextView?.text = warning
                }
            }
        }
        speechManager?.startListening()
    }

    private fun handleUserVoiceCommand(commandText: String) {
        recognizedTextView?.text = "\"$commandText\""
        listeningProgressBar?.visibility = View.GONE

        val nativeCmd = CommandParser.parse(commandText)

        if (nativeCmd !is NativeCommand.Unknown) {
            statusTextView?.text = "Buyruq bajarilmoqda..."
            val feedback = actionExecutor.execute(nativeCmd)
            if (feedback.isNotBlank()) {
                statusTextView?.text = feedback
                ttsManager?.speak(feedback) {
                    mainHandler.postDelayed({ hide() }, 1500)
                }
            } else {
                mainHandler.postDelayed({ hide() }, 1000)
            }
        } else {
            // Universal Fallback Agent
            if (ScreenAgentService.isEnabled()) {
                statusTextView?.text = "Ekran yordamchisi vazifani bajarmoqda..."
                ttsManager?.speak("Tushundim, ekranni boshqarishni boshlayapman.") {
                    // Hide session overlay so ScreenAgentService has clear screen view
                    mainHandler.post {
                        hide()
                        ScreenAgentService.executeTask(context, commandText)
                    }
                }
            } else {
                val errorMsg = "Maxsus imkoniyatlar (Accessibility) yoqilmagan. Ilova sozlamalaridan yoqing."
                statusTextView?.text = errorMsg
                ttsManager?.speak(errorMsg) {
                    mainHandler.postDelayed({ hide() }, 2500)
                }
            }
        }
    }

    override fun onHide() {
        super.onHide()
        speechManager?.stopListening()
        speechManager?.destroy()
        speechManager = null
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager?.shutdown()
    }
}
