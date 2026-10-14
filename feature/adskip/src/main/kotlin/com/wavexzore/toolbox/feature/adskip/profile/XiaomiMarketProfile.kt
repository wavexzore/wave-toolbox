package com.wavexzore.toolbox.feature.adskip.profile

import com.wavexzore.toolbox.feature.adskip.AdSkipGroup
import com.wavexzore.toolbox.feature.adskip.AdSkipPosition
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
 * 退出策略（同次实测）：该页两个「关闭」按钮为 **height=0 幽灵节点**，performAction 返回 true
 * 但不关页（仅关任务小弹窗）；真正退页入口是左上角**无文本/无 desc 的返回 Button**
 * （bounds [78,129][183,231]，通用关键词够不到）→ 声明 activityIds 限定的 position 组盲点该处，
 * 并抑制幽灵「关闭」防止通用关键词抢先消耗单实例预算。
 */
@Singleton
class XiaomiMarketProfile @Inject constructor() : AppAdProfile {

    override val pkgs: Set<String> = setOf(PKG)

    override fun inAppWindowRefresh(): Boolean = true

    /** 种果树页：抑制两个幽灵「关闭」（text/desc 各一），让位给 position 退出。 */
    override fun suppressOn(activity: String?): List<String> =
        if (activity?.contains(ORCHARD_ACTIVITY) == true) {
            listOf("""[text="关闭"]""", """[desc="关闭"]""")
        } else {
            emptyList()
        }

    /** 种果树页退出组：仅该 Activity 生效；position 盲点左上返回箭头（10.8% / 6.9%）。
     *  actionMaximum=3：页面初始化前 1~2s 会吞点击（实测），靠心跳按 1.2s 冷却节拍补点；
     *  实例更替（退页成功）后旧心跳自停，不会误点首页。 */
    override fun groups(): List<AdSkipGroup> = listOf(
        AdSkipGroup(
            id = ORCHARD_EXIT_GROUP,
            name = "种果树开屏退出",
            activityIds = listOf(ORCHARD_ACTIVITY),
            position = AdSkipPosition(xPercent = 10.8f, yPercent = 6.9f),
            actionMaximum = 3,
        ),
    )

    companion object {
        const val PKG = "com.xiaomi.market"

        /** 互动开屏（种果树）Activity（activityIds 后缀匹配）。 */
        const val ORCHARD_ACTIVITY = "com.xiaomi.orchard.sdk.ui.OrchardActivity"

        const val ORCHARD_EXIT_GROUP = "com.xiaomi.market#orchard-exit"
    }
}
