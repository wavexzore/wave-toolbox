package com.wavexzore.toolbox.feature.adskip.infra

import com.wavexzore.toolbox.core.common.text.ClassNames
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 窗口令牌状态机（T-201 自 AdSkipFeature 等价迁移；D-201/D-218）：
 *
 * - 包进入前台（跨包切换）= 唯一 ENTRY 重置点（误操作修复不变式）；
 * - `splash` 模式：应用内页面切换只更新类别、不刷新窗口期；
 * - `any` 模式：真实 Activity 且非弹窗窗口出现 → 重置实例（保留原激进行为）；
 * - 噪声类名（OEM 视图级 WSC，F3）不参与 Activity 判定（由 [ClassNames] 严格点号语义兜底）。
 */
class WindowTracker(
    private val classifier: PopupClassifier,
    private val quirks: OemQuirks,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    data class Mark(val activity: String?, val at: Long, val instance: Long, val activityAt: Long)

    private val marks = ConcurrentHashMap<String, Mark>()
    private val instanceSeq = AtomicLong()

    @Volatile
    private var lastForegroundPkg: String? = null

    fun current(pkg: String): Mark? = marks[pkg]

    /**
     * 记录前台包（仅更新跨包边界令牌，不建立/不更替实例；B-205 令牌卫生命）。
     * 供「自身包 / 三态 OFF 包 / 系统选择器」等早退路径使用——这些包不参与尝试，
     * 但它们的出现同样是跨包边界，必须打断后续 onForeground 的「同包幂等」判定。
     */
    fun noteForeground(pkg: String) {
        lastForegroundPkg = pkg
    }

    /** 包进入前台：跨包切换时建立新实例（同包重复调用幂等）。 */
    fun onForeground(pkg: String, cls: String?): Mark {
        val existing = marks[pkg]
        if (pkg == lastForegroundPkg && existing != null) return existing
        lastForegroundPkg = pkg
        return newInstance(pkg, cls)
    }

    /**
     * WSC 推进（等价迁移）：splash=只更新类别；any=真实活动窗口重置。
     *
     * [refreshOnActivity]（B-205 应用内增强，profile 显式开启）：splash 模式下亦对
     * 「新的真实 Activity」重置实例（弹窗窗口与噪声类名仍不重置）——为进入前台 5s
     * 之后才出现、或经应用内跳转产生的广告页（如应用商店互动开屏）开启窗口期。
     */
    fun onWindowStateChanged(
        pkg: String,
        cls: String?,
        mode: String,
        foreground: Boolean,
        refreshOnActivity: Boolean = false,
    ): Mark {
        val prev = marks[pkg] ?: return newInstance(pkg, cls)
        if (cls == null || cls == prev.activity) return prev
        val shouldReset = (mode == MODE_ANY || refreshOnActivity) && foreground &&
            ClassNames.isActivityLike(cls) && !classifier.isPopupWindow(pkg)
        return if (shouldReset) {
            newInstance(pkg, cls)
        } else {
            prev.copy(activity = cls, activityAt = clock()).also { marks[pkg] = it }
        }
    }

    /** 取（或懒建）当前实例。 */
    fun ensure(pkg: String, cls: String?): Mark = marks[pkg] ?: newInstance(pkg, cls)

    /** splash 窗口期新鲜度（any 恒真）。 */
    fun isFresh(pkg: String, mode: String, windowMs: Long): Boolean {
        val m = marks[pkg] ?: return false
        return mode == MODE_ANY || (m.activity != null && clock() - m.at <= windowMs)
    }

    fun instanceKey(pkg: String, mark: Mark): String = "$pkg#${mark.instance}"

    /** 记录当前前台包（诊断用）。 */
    fun lastForeground(): String? = lastForegroundPkg

    private fun newInstance(pkg: String, cls: String?): Mark {
        val now = clock()
        val mark = Mark(cls, now, instanceSeq.incrementAndGet(), now)
        marks[pkg] = mark
        return mark
    }

    private companion object {
        const val MODE_ANY = "any"
    }
}
