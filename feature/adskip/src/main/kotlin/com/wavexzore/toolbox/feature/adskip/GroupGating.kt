package com.wavexzore.toolbox.feature.adskip

/**
 * 组准入（T-204；自 AdSkipFeature.groupActive 等价迁移 + `activityIds` 接线）：
 *
 * - splash 模式只放行 splash 组（不变）；
 * - `excludeActivityIds`：命中即排除（不变）；
 * - `activityIds`（v1 死字段 → v2 接线）：非空且当前活动已知时，必须命中其一（前缀/后缀/全等）。
 */
internal object GroupGating {

    fun active(group: AdSkipGroup, runMode: String, activity: String?): Boolean {
        if (runMode == AdSkipGroup.MODE_SPLASH && group.mode != AdSkipGroup.MODE_SPLASH) return false
        if (activity != null && group.excludeActivityIds.any { activityMatches(it, activity) }) return false
        if (activity != null && group.activityIds.isNotEmpty() && group.activityIds.none { activityMatches(it, activity) }) {
            return false
        }
        return true
    }

    fun activityMatches(pattern: String, activity: String): Boolean =
        activity == pattern || activity.endsWith(pattern) || activity.startsWith(pattern)
}
