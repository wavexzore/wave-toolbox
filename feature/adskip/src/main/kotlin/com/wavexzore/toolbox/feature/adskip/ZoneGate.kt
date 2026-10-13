package com.wavexzore.toolbox.feature.adskip

/**
 * 区域门闸（T-208 自 AdSkipFeature.groupInZone 抽出，纯函数可测）：
 *
 * - 弹窗上下文：仅 `popupAware=true` 或 `zone=popup` 组可入（D-205）；
 * - 非弹窗：`zone=popup` 组排除；`zone=activity` 组要求活动已知且已过稳定期；
 * - `entry`/`any` 无附加约束（entry 的时间窗由令牌新鲜度统一把关）。
 */
internal object ZoneGate {

    fun allows(
        group: AdSkipGroup,
        popupContext: Boolean,
        activity: String?,
        activityAt: Long,
        now: Long,
    ): Boolean {
        if (popupContext) return group.popupAware == true || group.effectiveZone == AdSkipGroup.ZONE_POPUP
        return when (group.effectiveZone) {
            AdSkipGroup.ZONE_POPUP -> false
            AdSkipGroup.ZONE_ACTIVITY -> activity != null && now - activityAt >= group.settleMs
            else -> true
        }
    }
}
