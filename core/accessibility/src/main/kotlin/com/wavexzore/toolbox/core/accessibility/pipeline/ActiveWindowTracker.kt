package com.wavexzore.toolbox.core.accessibility.pipeline

/**
 * 当前活动窗口缓存（纯逻辑，JVM 可测）：由 WINDOW_STATE_CHANGED 更新，
 * 供规则/任务在无根节点查询时低成本获取"现在该作用于哪个窗口"（窗口 ID + 时间戳容差）。
 */
class ActiveWindowTracker {

    data class ActiveWindow(
        val packageName: String?,
        val className: String?,
        val windowId: Int,
        val timestamp: Long,
    )

    private var last: ActiveWindow? = null

    fun onEvent(event: A11yEvent, now: Long) {
        if (event.type == A11yEventType.WINDOW_STATE_CHANGED) {
            last = ActiveWindow(event.packageName, event.className, event.windowId, now)
        }
    }

    fun current(toleranceMs: Long, now: Long): ActiveWindow? =
        last?.takeIf { now - it.timestamp <= toleranceMs }

    companion object {
        const val DEFAULT_TOLERANCE_MS = 5_000L
    }
}
