package com.wavexzore.toolbox.core.accessibility.pipeline

import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * 管线内部事件快照（从 AccessibilityEvent 提取的最小字段，事件对象此后可安全丢弃）。
 */
data class A11yEvent(
    val type: A11yEventType,
    val packageName: String?,
    val className: String?,
    val text: String?,
    val eventTime: Long,
    val windowId: Int,
    val displayId: Int,
)

enum class A11yEventType {
    WINDOW_STATE_CHANGED,
    WINDOW_CONTENT_CHANGED,
    WINDOWS_CHANGED,
    VIEW_SCROLLED,
    OTHER,
    ;

    companion object {
        fun fromEventType(eventType: Int): A11yEventType = when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> WINDOW_STATE_CHANGED
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> WINDOW_CONTENT_CHANGED
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> WINDOWS_CHANGED
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> VIEW_SCROLLED
            else -> OTHER
        }
    }
}

/** 事件 → 快照（保持廉价，只提取过滤/日志/触发所需字段）。 */
fun AccessibilityEvent.toA11yEvent(): A11yEvent = A11yEvent(
    type = A11yEventType.fromEventType(eventType),
    packageName = packageName?.toString(),
    className = className?.toString(),
    text = text.firstOrNull { it != null }?.toString(),
    eventTime = eventTime,
    windowId = windowId,
    displayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) displayId else 0,
)
