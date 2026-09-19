package uz.voiceassistant.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class ScreenAgentHud(
    private val context: Context,
    private val onCancel: () -> Unit
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hudView: View? = null
    private var statusTextView: TextView? = null
    private var indicatorView: View? = null
    private var isShowing = false

    fun show(initialText: String) {
        mainHandler.post {
            if (isShowing) {
                updateText(initialText)
                return@post
            }

            try {
                val layout = createHudLayout(initialText)
                val params = WindowManager.LayoutParams().apply {
                    type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    format = PixelFormat.TRANSLUCENT
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = dpToPx(55)
                    width = WindowManager.LayoutParams.WRAP_CONTENT
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                }

                windowManager.addView(layout, params)
                hudView = layout
                isShowing = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateText(text: String, isDone: Boolean = false, isError: Boolean = false) {
        mainHandler.post {
            if (!isShowing) {
                show(text)
                return@post
            }

            statusTextView?.text = text

            val indicatorBg = indicatorView?.background as? GradientDrawable
            when {
                isError -> indicatorBg?.setColor(Color.parseColor("#FF5252"))
                isDone -> indicatorBg?.setColor(Color.parseColor("#4CAF50"))
                else -> indicatorBg?.setColor(Color.parseColor("#00E5FF"))
            }
        }
    }

    fun dismiss() {
        mainHandler.post {
            if (isShowing && hudView != null) {
                try {
                    windowManager.removeView(hudView)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                hudView = null
                statusTextView = null
                indicatorView = null
                isShowing = false
            }
        }
    }

    private fun createHudLayout(initialText: String): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(10), dpToPx(12), dpToPx(10))

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1E1E24"))
                cornerRadius = dpToPx(24).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#40FFFFFF"))
            }
            elevation = dpToPx(8).toFloat()
        }

        val dot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(10), dpToPx(10)).apply {
                marginEnd = dpToPx(10)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E5FF"))
            }
        }
        indicatorView = dot
        root.addView(dot)

        val tv = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(14)
            }
            text = initialText
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            maxLines = 2
            maxWidth = dpToPx(250)
        }
        statusTextView = tv
        root.addView(tv)

        val closeBtn = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(20), dpToPx(20))
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#B0BEC5"))
            setOnClickListener {
                onCancel()
                dismiss()
            }
        }
        root.addView(closeBtn)

        return root
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}