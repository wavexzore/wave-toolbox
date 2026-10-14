package com.wavexzore.toolbox.feature.adskip

/**
 * 无可用组时的通用兜底（D-223；修复 v1→重构回归）：
 *
 * v1 中关键词/vid 步骤无条件参与（不依赖任何组）；重构后管线先以「组过滤为空」短路，
 * 导致未适配应用（无 pack、无 profile 组）再无候选。兜底组语义与 v1 等价：
 * - `zone=entry`（mode=splash 推导）——弹窗窗口仍由 SkipGate 拦截；
 * - 无 `matches`/`path`/`position`——仅恢复 keyword/vid 来源；
 * - 不声明 `actionMaximum`——实例预算沿用运行模式默认（splash=1、any=2，见 `groupMax`）。
 */
internal object GroupFallback {

    /** 兜底组 id（进统计 `provider/zone` 记录与日志）。 */
    const val GENERIC_ID = "generic"

    /** 兜底组（不可变单例）。 */
    val generic: AdSkipGroup = AdSkipGroup(id = GENERIC_ID, name = "通用兜底", order = 0)

    /** 过滤后的组为空 → 单元素兜底；否则原样返回。 */
    fun resolve(activeGroups: List<AdSkipGroup>): List<AdSkipGroup> =
        if (activeGroups.isEmpty()) listOf(generic) else activeGroups
}
