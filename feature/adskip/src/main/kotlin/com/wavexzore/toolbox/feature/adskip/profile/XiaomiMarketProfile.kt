package com.wavexzore.toolbox.feature.adskip.profile

import com.wavexzore.toolbox.feature.adskip.AdSkipGroup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 小米应用商店（com.xiaomi.market）识别器（B-205）。
 *
 * 准入理由（pack 不可表达）：其「开屏」类广告以应用内页面形态出现且时机漂移——
 * 互动开屏（`com.xiaomi.orchard.sdk.ui.OrchardActivity`）/广告落地页常在进入前台
 * 5s 之后或经应用内跳转打开；通用 keyword/vid 本可命中其「关闭」按钮，缺的只是窗口期
 * → 启用 [AppAdProfile.inAppWindowRefresh]（新的真实 Activity 重置开屏窗口期）。
 *
 * 实测（a077bc70 / market 4.126.10，2026-10-14）：OrchardActivity 于进入前台 87s 后出现时
 * 引擎零尝试；页面内 `关闭` 为 Button(text=关闭, clickable=true)，通用兜底可直接命中。
 *
 * 调账（2026-10-14 二次，用户反馈「种果树每次进入都会被自动退出」）：该页两个「关闭」为
 * **height=0 幽灵节点**（performAction 返回 true 但不关页，仅关任务小弹窗）→ 抑制之防误点；
 * 且该机版本种果树**从不自动打开**（恒为手动进入）→ 摘除原 position 退出组，
 * 进入后停留（想玩即玩，需手动返回）。
 */
@Singleton
class XiaomiMarketProfile @Inject constructor() : AppAdProfile {

    override val pkgs: Set<String> = setOf(PKG)

    override fun inAppWindowRefresh(): Boolean = true

    /** 种果树页：抑制两个幽灵「关闭」（text/desc 各一），防误点消耗单实例预算。 */
    override fun suppressOn(activity: String?): List<String> =
        if (activity?.contains(ORCHARD_ACTIVITY) == true) {
            listOf("""[text="关闭"]""", """[desc="关闭"]""")
        } else {
            emptyList()
        }

    /**
     * 2026-10-14 调账：不再声明种果树退出组（见类注释）。
     * 如需恢复自动退出：加入 activityIds=[ORCHARD_ACTIVITY] 的 position 组（10.8% / 6.9%，
     * actionMaximum=3 冷却补点）即可，组 id 沿用 [ORCHARD_EXIT_GROUP]。
     */
    override fun groups(): List<AdSkipGroup> = emptyList()

    companion object {
        const val PKG = "com.xiaomi.market"

        /** 互动开屏（种果树）Activity（activityIds 后缀匹配）。 */
        const val ORCHARD_ACTIVITY = "com.xiaomi.orchard.sdk.ui.OrchardActivity"

        const val ORCHARD_EXIT_GROUP = "com.xiaomi.market#orchard-exit"
    }
}
