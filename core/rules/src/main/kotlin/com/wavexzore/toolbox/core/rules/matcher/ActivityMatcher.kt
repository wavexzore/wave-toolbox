package com.wavexzore.toolbox.core.rules.matcher

/**
 * activity 匹配（纯逻辑）：全等或 `.$entry` 后缀（兼容简写类名）；`excludeActivityIds` 优先。
 * `activityIds` 为空 = 组内该应用全部 activity。
 */
object ActivityMatcher {

    fun allows(
        activityIds: List<String>,
        excludeActivityIds: List<String> = emptyList(),
        activity: String?,
    ): Boolean {
        if (activity == null) return activityIds.isEmpty()
        if (excludeActivityIds.any { matchesEntry(it, activity) }) return false
        if (activityIds.isEmpty()) return true
        return activityIds.any { matchesEntry(it, activity) }
    }

    fun matchesEntry(entryRaw: String, activity: String): Boolean {
        val entry = entryRaw.trim().removePrefix(".")
        if (entry.isEmpty()) return true
        return activity == entry || activity.endsWith(".$entry")
    }
}
