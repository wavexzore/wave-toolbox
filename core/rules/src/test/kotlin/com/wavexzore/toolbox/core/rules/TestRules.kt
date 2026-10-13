package com.wavexzore.toolbox.core.rules

import com.wavexzore.toolbox.core.rules.index.RuleIndex
import com.wavexzore.toolbox.core.rules.model.RawApp
import com.wavexzore.toolbox.core.rules.model.RawRule
import com.wavexzore.toolbox.core.rules.model.RawRuleGroup

object TestRules {

    fun rule(
        id: String = "rule",
        order: Int = 0,
        enabled: Boolean = true,
        activityIds: List<String> = emptyList(),
        excludeActivityIds: List<String> = emptyList(),
        matches: List<String> = listOf("[text=x]"),
        anyMatches: List<String> = emptyList(),
    ) = RawRule(
        id = id,
        order = order,
        enabled = enabled,
        activityIds = activityIds,
        excludeActivityIds = excludeActivityIds,
        matches = matches,
        anyMatches = anyMatches,
    )

    fun group(
        id: String = "group",
        order: Int = 0,
        enabled: Boolean = true,
        apps: List<RawApp> = listOf(RawApp(id = "com.test.app")),
        rules: List<RawRule> = listOf(rule()),
    ) = RawRuleGroup(id = id, order = order, enabled = enabled, apps = apps, rules = rules)

    fun index(vararg groups: RawRuleGroup): RuleIndex = RuleIndex.build(groups.toList())
}
