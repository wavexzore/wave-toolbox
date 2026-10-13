package com.wavexzore.toolbox.core.accessibility.pipeline

fun a11yEvent(
    type: A11yEventType = A11yEventType.WINDOW_CONTENT_CHANGED,
    packageName: String? = "com.example.app",
    className: String? = "com.example.app.MainActivity",
    text: String? = null,
    eventTime: Long = 0L,
    windowId: Int = 1,
    displayId: Int = 0,
) = A11yEvent(
    type = type,
    packageName = packageName,
    className = className,
    text = text,
    eventTime = eventTime,
    windowId = windowId,
    displayId = displayId,
)
