package com.wavexzore.toolbox.feature.adskip.profile

import com.wavexzore.toolbox.feature.adskip.AdSkipGroup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MIUI 系统广告服务（com.miui.systemAdSolution）识别器——MSA 开屏（B-206 续）。
 *
 * 准入理由（pack 不可表达——跨进程窗口 + 点击派发机制 + 补点节拍）：MSA 开屏为独立进程
 * 全屏窗口（cls=android.widget.FrameLayout，自带「跳过广告 N」胶囊）。实测（21:09 小米应用商店）：
 *  - 窗口入场期点击被吞：连续 performAction 均 ok=true 但页面无反应，倒计时照走自然结束；
 *  - 有效补点窗口只有大约「入场 ~0.5s 后」到「跳过节点从无障碍树消失 ~4.5s」之间 1.5~2s。
 *
 * 处置：① [forceGesture] 真实触摸派发（等价用户点击）；② 空白匹配补点组把实例预算
 * 提到 5 次，配合引擎 verify 回退后的短冷却补点，直到跳过按钮消失。
 */
@Singleton
class SystemAdSolutionProfile @Inject constructor() : AppAdProfile {

    override val pkgs: Set<String> = setOf(PKG)

    /** MSA 跳过按钮：performAction 派发不生效（实测）→ 统一手势坐标点击。 */
    override fun forceGesture(): Boolean = true

    /** 跳过补点组：空白匹配（沿用通用 keyword/vid 候选），仅提升预算并允许弹窗窗口参与。 */
    override fun groups(): List<AdSkipGroup> = listOf(
        AdSkipGroup(
            id = SKIP_RETRY_GROUP,
            name = "MSA开屏跳过补点",
            actionCd = 350,
            actionMaximum = 5,
            popupAware = true,
        ),
    )

    companion object {
        const val PKG = "com.miui.systemAdSolution"

        const val SKIP_RETRY_GROUP = "com.miui.systemAdSolution#msa-skip-retry"
    }
}
