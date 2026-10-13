package com.wavexzore.toolbox.feature.adskip.infra

import java.util.concurrent.ConcurrentHashMap

/**
 * 尝试调度与预算（T-201 自 AdSkipFeature 等价迁移）：
 * 冷却 / 间隔 / 并发闸门 / 每实例成功上限与失败兜底 / 日志去重。
 *
 * 预算语义（不变式）：`tree=null / 无候选` 不消耗预算；仅真实点击后计数。
 */
class AttemptScheduler(
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val perInstanceCount = ConcurrentHashMap<String, Int>()
    private val perInstanceAttempts = ConcurrentHashMap<String, Int>()
    private val lastAttemptAt = ConcurrentHashMap<String, Long>()
    private val lastClickAt = ConcurrentHashMap<String, Long>()
    private val firstContentSeen = ConcurrentHashMap<String, Boolean>()
    private val inFlight = ConcurrentHashMap<String, Boolean>()
    private val missLogged = ConcurrentHashMap.newKeySet<String>()
    private val popupLogged = ConcurrentHashMap.newKeySet<String>()
    private val lastGroupActionAt = ConcurrentHashMap<String, Long>()
    private val identityClicks = ConcurrentHashMap<String, Int>()

    fun lastClickAt(pkg: String): Long = lastClickAt[pkg] ?: 0L

    /** 冷却 + 尝试间隔 + 并发闸门；通过则登记 inFlight 与 lastAttemptAt。 */
    fun tryEnter(pkg: String, key: String, clickCooldownMs: Long, attemptGapMs: Long): Boolean {
        val now = clock()
        if (now - lastClickAt(pkg) < clickCooldownMs) return false
        if (now - (lastAttemptAt[key] ?: 0L) < attemptGapMs) return false
        if (inFlight.putIfAbsent(key, true) != null) return false
        lastAttemptAt[key] = now
        return true
    }

    fun leave(key: String) {
        inFlight.remove(key)
    }

    /** 成功上限内且失败兜底预算未耗尽。 */
    fun hasBudget(key: String, maxPerInstance: Int, overhead: Int): Boolean =
        (perInstanceCount[key] ?: 0) < maxPerInstance &&
            (perInstanceAttempts[key] ?: 0) < maxPerInstance + overhead

    /** 一次真实点击后的记账（ok=true 计入成功数）。 */
    fun onOutcome(key: String, ok: Boolean) {
        perInstanceAttempts.merge(key, 1, Int::plus)
        if (ok) perInstanceCount.merge(key, 1, Int::plus)
    }

    fun recordClick(pkg: String) {
        lastClickAt[pkg] = clock()
    }

    fun successCount(key: String): Int = perInstanceCount[key] ?: 0

    /** 首屏 CONTENT 判定（同实例仅首次为真）。 */
    fun isFirstContent(key: String): Boolean = firstContentSeen.putIfAbsent(key, true) == null

    fun onceMiss(key: String): Boolean = missLogged.add(key)

    fun oncePopup(key: String): Boolean = popupLogged.add(key)

    /** 组级最小间隔（v2 死字段接线；作用域=应用+实例，实例更替时随 retainOnly 清理）。 */
    fun groupEligible(pkg: String, groupId: String, cdMs: Long): Boolean {
        val last = lastGroupActionAt["$pkg#$groupId"] ?: return true
        return clock() - last >= cdMs
    }

    fun recordGroupAction(pkg: String, groupId: String) {
        lastGroupActionAt["$pkg#$groupId"] = clock()
    }

    /** 同实例同控件（身份键，去 text）已执行次数（D-215 上限 2）。 */
    fun identityCount(instanceKey: String, identity: String): Int =
        identityClicks["$instanceKey|$identity"] ?: 0

    fun recordIdentity(instanceKey: String, identity: String) {
        identityClicks.merge("$instanceKey|$identity", 1, Int::plus)
    }

    fun clearIdentity(instanceKey: String, identity: String) {
        identityClicks.remove("$instanceKey|$identity")
    }

    /** 复核失败（点击后节点仍在且未变）→ 回退一次成功记账，允许重试；同时放开控件次数。 */
    fun onVerifyFailed(instanceKey: String, identity: String) {
        perInstanceCount.compute(instanceKey) { _, v -> ((v ?: 1) - 1).coerceAtLeast(0) }
        clearIdentity(instanceKey, identity)
    }

    /** 保留同包当前实例键，清理旧实例（防 map 长跑膨胀）。 */
    fun retainOnly(pkg: String, keepKey: String) {
        val prefix = "$pkg#"
        perInstanceCount.keys.removeIf { it.startsWith(prefix) && it != keepKey }
        perInstanceAttempts.keys.removeIf { it.startsWith(prefix) && it != keepKey }
        lastAttemptAt.keys.removeIf { it.startsWith(prefix) && it != keepKey }
        firstContentSeen.keys.removeIf { it.startsWith(prefix) && it != keepKey }
        inFlight.keys.removeIf { it.startsWith(prefix) && it != keepKey }
        missLogged.removeIf { it.startsWith(prefix) && it != keepKey }
        popupLogged.removeIf { it.startsWith(prefix) && it != keepKey }
        lastGroupActionAt.keys.removeIf { it.startsWith(prefix) && it != keepKey }
        identityClicks.keys.removeIf { it.startsWith(prefix) && !it.startsWith("$keepKey|") }
    }
}
