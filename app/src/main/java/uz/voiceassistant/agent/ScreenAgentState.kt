package uz.voiceassistant.agent

import android.graphics.Rect

data class UiElementNode(
    val id: Int,
    val text: String,
    val contentDescription: String,
    val className: String,
    val viewId: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val bounds: Rect
)

data class AgentStepAction(
    val action: String, // "tap", "type", "scroll", "back", "home", "done"
    val targetBounds: List<Int>?, // [x1, y1, x2, y2]
    val text: String?,
    val reasoningForUser: String,
    val isSensitive: Boolean = false
)

enum class AgentStatus {
    IDLE,
    CAPTURING_SCREEN,
    THINKING,
    AWAITING_CONFIRMATION,
    EXECUTING_ACTION,
    COMPLETED,
    FAILED
}
