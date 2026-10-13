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
        if (event.packageName == selfPackageName) return DropReason.SELF_PACKAGE
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
