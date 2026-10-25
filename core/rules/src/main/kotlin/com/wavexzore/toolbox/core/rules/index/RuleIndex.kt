package com.wavexzore.toolbox.core.rules.index

import com.wavexzore.toolbox.core.rules.matcher.ActivityMatcher
import com.wavexzore.toolbox.core.rules.matcher.VersionMatcher
import com.wavexzore.toolbox.core.rules.model.RawApp
import com.wavexzore.toolbox.core.rules.model.RawRule
import com.wavexzore.toolbox.core.rules.model.RawRuleGroup

/**
 * 规则索引（纯逻辑）：按 pkg 预分组，查询时做 enable/版本/activity 过滤并按 order 排序。
 * apps 为空的组视为全局组，对任意包生效。
 */
class RuleIndex private constructor(
    private val byPackage: Map<String, List<Entry>>,
    private val wildcard: List<Entry>,
    private val missingVersionPolicy: VersionMatcher.MissingVersionPolicy,
) {

    data class Entry(val group: RawRuleGroup, val app: RawApp?, val rule: RawRule)

    data class MatchedRule(val group: RawRuleGroup, val rule: RawRule)

    fun query(
        packageName: String,
        activity: String? = null,
        versionCode: Int? = null,
        versionName: String? = null,
    ): List<MatchedRule> {
        val candidates = ArrayList<Entry>(byPackage[packageName].orEmpty().size + wildcard.size)
        candidates += byPackage[packageName].orEmpty()
        candidates += wildcard
        return candidates
            .filter { entry -> allows(entry, activity, versionCode, versionName) }
            .sortedWith(COMPARATOR)
            .map { MatchedRule(it.group, it.rule) }
    }

    fun packages(): Set<String> = byPackage.keys

    private fun allows(entry: Entry, activity: String?, versionCode: Int?, versionName: String?): Boolean {
        val group = entry.group
        val rule = entry.rule
        val app = entry.app
        if (!group.enabled || !rule.enabled) return false
        if (app != null && !app.enabled) return false
        // 目标应用版本约束（组级 + 应用级）；版本未知默认不匹配。
        if (!VersionMatcher.allows(group.versionCode, versionCode, missingVersionPolicy)) return false
        if (!VersionMatcher.allowsVersionName(group.versionName, versionName, missingVersionPolicy)) return false
        if (app != null) {
            if (!VersionMatcher.allows(app.versionCode, versionCode, missingVersionPolicy)) return false
            if (!VersionMatcher.allowsVersionName(app.versionName, versionName, missingVersionPolicy)) return false
        }
        // 规则级约束
        if (!VersionMatcher.allows(rule.versionCode, versionCode, missingVersionPolicy)) return false
        if (!VersionMatcher.allowsVersionName(rule.versionName, versionName, missingVersionPolicy)) return false
        return ActivityMatcher.allows(rule.activityIds, rule.excludeActivityIds, activity)
    }

    companion object {

        private val COMPARATOR: Comparator<Entry> = compareBy(
            { it.group.order },
            { it.rule.order },
            { it.rule.id },
        )

        fun build(
            groups: List<RawRuleGroup>,
            missingVersionPolicy: VersionMatcher.MissingVersionPolicy = VersionMatcher.MissingVersionPolicy.DENY,
        ): RuleIndex {
            val byPackage = HashMap<String, MutableList<Entry>>()
            val wildcard = ArrayList<Entry>()
            groups.forEach { group ->
                group.rules.forEach { rule ->
                    if (group.apps.isEmpty()) {
                        wildcard.add(Entry(group, null, rule))
                    } else {
                        group.apps.forEach { app -> byPackage.getOrPut(app.id) { ArrayList() }.add(Entry(group, app, rule)) }
                    }
                }
            }
            return RuleIndex(byPackage, wildcard, missingVersionPolicy)
        }
    }
}
