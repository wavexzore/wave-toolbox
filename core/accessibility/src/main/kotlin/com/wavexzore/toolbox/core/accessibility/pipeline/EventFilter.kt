package com.wavexzore.toolbox.core.accessibility.pipeline

/** 丢弃原因（统计与日志用）。 */
enum class DropReason {
    ENGINE_DISABLED,
    SELF_PACKAGE,
    SECONDARY_DISPLAY,
    SCROLLED_NOISE,
    NOT_ALLOWED,
}

/**
 * 事件过滤（纯逻辑，JVM 可测）：自身包名 / 副屏 / 滚动噪声 / 引擎开关 / 应用白名单。
 *
 * 自身包例外（B-205）：**WSC 放行**——AdSkipFeature 需要该边界信号维护「跨包令牌」
 * （自身包前台若不记录，返回任一 App 会被误判为同包重复 → 复用陈旧实例 → 开屏窗口
 * 已过期 → 该次进入零尝试）；其余自包事件（CONTENT 等）仍丢弃，避免自 UI 事件入管路。
 *
 * @return 命中则返回丢弃原因；null 表示放行。
 */
class EventFilter {

    fun shouldAccept(
        event: A11yEvent,
        selfPackageName: String,
        engineEnabled: Boolean,
        allowPackages: Set<String>,
    ): DropReason? {
        if (!engineEnabled) return DropReason.ENGINE_DISABLED
        if (event.displayId != DEFAULT_DISPLAY_ID) return DropReason.SECONDARY_DISPLAY
        if (event.packageName == selfPackageName && event.type != A11yEventType.WINDOW_STATE_CHANGED) {
            return DropReason.SELF_PACKAGE
        }
        if (event.type == A11yEventType.VIEW_SCROLLED) return DropReason.SCROLLED_NOISE
        if (allowPackages.isNotEmpty() && event.packageName != null && event.packageName !in allowPackages) {
            return DropReason.NOT_ALLOWED
        }
        return null
    }

    companion object {
        const val DEFAULT_DISPLAY_ID = 0
    }
}
