package com.wavexzore.toolbox.core.accessibility.action

import com.wavexzore.toolbox.core.rules.model.RawRule
import com.wavexzore.toolbox.core.selector.NodeView
import com.wavexzore.toolbox.core.selector.SelectorEngine

/**
 * 规则匹配评估（纯逻辑）：把 [RawRule] 的 `matches/anyMatches/excludeMatches` 语义落到具体目标节点。
 *
 * - `matches`：全部选择器必须命中**同一个节点**——以第一个选择器的排序结果逐候选回验；
 * - `anyMatches`：非空时其中至少一个选择器需命中（对 `matches` 候选为附加门禁）；
 * - `matches` 为空且 `anyMatches` 非空：以第一个能命中的 anyMatches 选择器的最高优先级节点为目标；
 * - `excludeMatches`：候选自身或祖先命中即排除（复用 SelectorEngine 语义）。
 */
object RuleMatcher {

    data class Outcome(
        val target: NodeView? = null,
        val viaSelector: String? = null,
        val reason: String = "",
    )

    /** 单规则候选上限（防大树上多候选 × 多次回验的放大）。 */
    const val MAX_CANDIDATES = 50

    fun evaluate(engine: SelectorEngine, root: NodeView, rule: RawRule): Outcome {
        if (rule.matches.isNotEmpty()) {
            val candidates = engine.findAll(root, rule.matches[0], rule.excludeMatches).take(MAX_CANDIDATES)
            if (candidates.isEmpty()) return Outcome(reason = "matches[0] 未命中")
            for (candidate in candidates) {
                var allHit = true
                for (index in 1 until rule.matches.size) {
                    if (!engine.matchNode(root, rule.matches[index], candidate.node)) {
                        allHit = false
                        break
                    }
                }
                if (!allHit) continue
                if (!anyGate(engine, root, rule)) return Outcome(reason = "anyMatches 未命中")
                return Outcome(candidate.node, viaSelector = rule.matches.joinToString(" + "))
            }
            return Outcome(reason = "候选未同时满足全部 matches")
        }

        if (rule.anyMatches.isNotEmpty()) {
            for (selector in rule.anyMatches) {
                val node = engine.findFirst(root, selector, rule.excludeMatches) ?: continue
                return Outcome(node, viaSelector = selector)
            }
            return Outcome(reason = "anyMatches 未命中")
        }

        return Outcome(reason = "无可用选择器")
    }

    private fun anyGate(engine: SelectorEngine, root: NodeView, rule: RawRule): Boolean =
        rule.anyMatches.isEmpty() || rule.anyMatches.any { engine.anyMatches(root, it) }
}
