package com.wavexzore.toolbox.core.accessibility.action

/**
 * 动作账本（T-007）：冷却（`actionCd`）与次数上限（`actionMaximum`）的进程内账本。
 *
 * 实例语义：`(pkg, activity)` 为「页面实例」，实例切换（含 A→B→A 返回）清空全部计数与冷却。
 * 事件类名区分为两类（[onWindow] 的 `activityLike`）：
 * - activity 事件（真实 Activity 类名）：切换实例并重置；
 * - 非 activity 事件（`android.widget.FrameLayout`、弹窗、键盘等）：仅用于跨应用占位，
 *   不重置同应用计数；同应用占位随后升级为真实 activity 时也不重置（避免同页二次点击）。
 *
 * 仅事件单线程访问（无锁，见 D-006）。
 */
class ActionLedger {

    data class Decision(
        val allowed: Boolean,
        val reason: DenyReason? = null,
        val instanceSeq: Long = 0,
        val count: Int = 0,
    )

    enum class DenyReason { COOLDOWN, MAX_EXCEEDED }

    private var currentKey: String? = null
    private var instanceSeq = 0L
    private var lastSwitchAt = 0L
    private val counts = HashMap<String, Int>()
    private val lastActionAt = HashMap<String, Long>()

    /** 当前页面实例序号（自增，供日志定位）。 */
    val instance: Long get() = instanceSeq

    var packageName: String? = null
        private set

    var activity: String? = null
        private set

    /** 窗口变化时调用；返回 true 表示进入了新的页面实例（计数/冷却已重置）。 */
    fun onWindow(pkg: String?, cls: String?, activityLike: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        if (pkg.isNullOrBlank()) return false
        if (activityLike) {
            val key = "$pkg/$cls"
            if (key == currentKey) return false
            if (currentKey == "$pkg/*" && packageName == pkg && activity == null) {
                // 同应用占位升级为真实 activity：不重置
                currentKey = key
                activity = cls
                return false
            }
            switchTo(pkg, cls, key, now)
            return true
        }
        if (pkg != packageName || currentKey == null) {
            switchTo(pkg, null, "$pkg/*", now)
            return true
        }
        return false
    }

    /** 是否允许执行（不记账）。 */
    fun decision(ruleId: String, cooldownMs: Long, actionMaximum: Int?, now: Long): Decision {
        val last = lastActionAt[ruleId]
        if (last != null && cooldownMs > 0 && now - last < cooldownMs) {
            return Decision(false, DenyReason.COOLDOWN, instanceSeq, counts[ruleId] ?: 0)
        }
        val count = counts[ruleId] ?: 0
        if (actionMaximum != null && count >= actionMaximum) {
            return Decision(false, DenyReason.MAX_EXCEEDED, instanceSeq, count)
        }
        return Decision(true, instanceSeq = instanceSeq, count = count)
    }

    /** 执行后记账（含校验重试）。 */
    fun markExecuted(ruleId: String, now: Long) {
        counts[ruleId] = (counts[ruleId] ?: 0) + 1
        lastActionAt[ruleId] = now
    }

    /** 测试/诊断用：当前规则已执行次数。 */
    internal fun countOf(ruleId: String): Int = counts[ruleId] ?: 0

    /** 测试用：清空计数与冷却（生产路径由页面实例切换自动清理）。 */
    internal fun resetAll() {
        counts.clear()
        lastActionAt.clear()
    }

    private fun switchTo(pkg: String, cls: String?, key: String, now: Long) {
        currentKey = key
        packageName = pkg
        activity = cls
        instanceSeq++
        // 快速连续切换（≤ [CARRYOVER_MS]）视为同一页面的"占位/已知化"链，保留计数与冷却；
        // 典型场景：内容事件先于 WINDOW_STATE_CHANGED 到达，动作已发生而实例才被识别。
        if (now - lastSwitchAt > CARRYOVER_MS) {
            counts.clear()
            lastActionAt.clear()
        }
        lastSwitchAt = now
    }

    companion object {
        /** 连续切换的计数保留窗口。 */
        const val CARRYOVER_MS = 1_000L
    }
}
