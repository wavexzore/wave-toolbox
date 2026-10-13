package com.wavexzore.toolbox.core.accessibility.pipeline

/**
 * CONTENT_CHANGED 节流（纯逻辑，JVM 可测；D-006：默认 100ms，来自设置 `engine.contentThrottleMs`）。
 *
 * 仅节流 `WINDOW_CONTENT_CHANGED`；`WINDOW_STATE_CHANGED`/`WINDOWS_CHANGED` 永不丢弃。
 * 节流粒度 = 包名 + 窗口 ID（不同窗口互不影响）。
 */
class ContentThrottler {

    private data class Key(val packageName: String?, val windowId: Int)

    private val lastPassAt = HashMap<Key, Long>()
    private var lastSweepAt = 0L

    /** @return true=放行；false=窗口期内丢弃。 */
    fun shouldDispatch(event: A11yEvent, throttleMs: Long, now: Long): Boolean {
        val key = Key(event.packageName, event.windowId)
        if (event.type != A11yEventType.WINDOW_CONTENT_CHANGED) {
            lastPassAt[key] = now
            return true
        }
        sweepIfNeeded(now, throttleMs)
        val last = lastPassAt[key]
        if (last != null && now - last < throttleMs) return false
        lastPassAt[key] = now
        return true
    }

    /** 防 key 无限增长：过期项按窗口期清理。 */
    private fun sweepIfNeeded(now: Long, throttleMs: Long) {
        if (now - lastSweepAt < SWEEP_INTERVAL_MS) return
        lastSweepAt = now
        lastPassAt.entries.removeAll { now - it.value > throttleMs * 10 }
    }

    companion object {
        private const val SWEEP_INTERVAL_MS = 60_000L
    }
}
