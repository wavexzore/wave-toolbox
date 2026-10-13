package com.wavexzore.toolbox.core.selector

/**
 * 选择器引擎（纯 JVM，04 §5.2）：查找/匹配/排除与结果排序。
 *
 * - 候选 = 选择器最后一步；其前置步骤沿祖先链回验（`>` 严格父级、空格/`@` 任意层级祖先）；
 * - 命中排序：Y 最小 → depth 最小 → drawingOrder 最小 → X 最小；
 * - `exclude` 语义：候选自身或任一祖先命中排除选择器则丢弃（等价子树跳过）；
 * - 坏语法：解析失败返回空结果/`false`，不抛出（失败策略见 04 §5.2）。
 */
class SelectorEngine(
    private val maxVisitedNodes: Int = DEFAULT_MAX_VISITED,
) {

    data class MatchResult(val node: NodeView, val reason: String, val score: Int)

    /** 查找全部命中（已排序）。 */
    fun findAll(root: NodeView, selector: String, excludes: List<String> = emptyList()): List<MatchResult> {
        val compiled = parse(selector) ?: return emptyList()
        val excluded = excludes.mapNotNull(::parse)
        val results = ArrayList<MatchResult>()
        walk(root) { node, path ->
            if (matchesLastStep(node, compiled) &&
                !isExcluded(path, excluded) &&
                verifyChain(path, compiled)
            ) {
                results.add(MatchResult(node, selector, node.bounds.top))
            }
            true
        }
        return results.sortedWith(RESULT_COMPARATOR)
    }

    /** 命中优先（排序后取第一个）。 */
    fun findFirst(root: NodeView, selector: String, excludes: List<String> = emptyList()): NodeView? =
        findAll(root, selector, excludes).firstOrNull()?.node

    fun anyMatches(root: NodeView, selector: String): Boolean {
        val compiled = parse(selector) ?: return false
        var found = false
        walk(root) { node, path ->
            if (matchesLastStep(node, compiled) && verifyChain(path, compiled)) {
                found = true
                false
            } else {
                true
            }
        }
        return found
    }

    /** 指定节点是否命中（含祖先链验证）。 */
    fun matchNode(root: NodeView, selector: String, target: NodeView): Boolean {
        val compiled = parse(selector) ?: return false
        var matched = false
        walk(root) { node, path ->
            if (node === target) {
                matched = matchesLastStep(node, compiled) && verifyChain(path, compiled)
                false
            } else {
                true
            }
        }
        return matched
    }

    /** 从既有结果中过滤掉（自身或祖先）命中任一排除选择器的项。 */
    fun excludeMatches(
        root: NodeView,
        results: List<MatchResult>,
        excludes: List<String>,
    ): List<MatchResult> {
        val excluded = excludes.mapNotNull(::parse)
        if (excluded.isEmpty()) return results
        val paths = HashMap<NodeView, List<NodeView>>()
        walk(root) { node, path ->
            paths[node] = path.toList()
            true
        }
        return results.filter { result ->
            val path = paths[result.node] ?: return@filter true
            !isExcluded(path, excluded)
        }
    }

    /** 解析（带 LRU 缓存）；失败返回 null。 */
    fun parse(raw: String): Selector? =
        when (val result = SelectorCache.parse(raw)) {
            is SelectorParser.ParseResult.Ok -> result.selector
            is SelectorParser.ParseResult.Error -> null
        }

    // ---- 内部 ----

    private fun matchesLastStep(node: NodeView, selector: Selector): Boolean =
        selector.steps.last().conditions.all { ConditionMatcher.matches(node, it) }

    /** 沿候选的祖先链（path 不含候选自身以上部分已含）回验前置步骤。 */
    private fun verifyChain(path: List<NodeView>, selector: Selector): Boolean {
        val steps = selector.steps
        if (steps.size == 1) return true
        var cursor = path.size - 2
        for (i in steps.indices.reversed()) {
            if (i == 0) break
            val prevStep = steps[i - 1]
            when (steps[i].relation) {
                SelRelation.CHILD -> {
                    if (cursor < 0 || !matchesStep(path[cursor], prevStep)) return false
                    cursor--
                }
                SelRelation.DESCENDANT, SelRelation.ANCHOR -> {
                    var found = false
                    while (cursor >= 0) {
                        if (matchesStep(path[cursor], prevStep)) {
                            found = true
                            cursor--
                            break
                        }
                        cursor--
                    }
                    if (!found) return false
                }
                SelRelation.FIRST -> Unit
            }
        }
        return true
    }

    private fun matchesStep(node: NodeView, step: Selector.Step): Boolean =
        step.conditions.all { ConditionMatcher.matches(node, it) }

    private fun isExcluded(path: List<NodeView>, excludes: List<Selector>): Boolean {
        if (excludes.isEmpty()) return false
        for (index in path.indices) {
            for (exclude in excludes) {
                if (matchesLastStep(path[index], exclude) &&
                    verifyChain(path.subList(0, index + 1), exclude)
                ) {
                    return true
                }
            }
        }
        return false
    }

    /** 前序遍历；path 为 root..node 的祖先链。回调返回 false 停止遍历。 */
    private fun walk(root: NodeView, visit: (NodeView, List<NodeView>) -> Boolean) {
        val stack = ArrayDeque<WalkEntry>()
        stack.addLast(WalkEntry(root, 0))
        val path = ArrayList<NodeView>()
        var visited = 0
        while (stack.isNotEmpty()) {
            if (visited >= maxVisitedNodes) return
            val entry = stack.removeLast()
            while (path.size > entry.depth) path.removeAt(path.size - 1)
            path.add(entry.node)
            visited++
            if (!visit(entry.node, path)) return
            val children = entry.node.children
            for (i in children.indices.reversed()) {
                stack.addLast(WalkEntry(children[i], entry.depth + 1))
            }
        }
    }

    private class WalkEntry(val node: NodeView, val depth: Int)

    companion object {
        const val DEFAULT_MAX_VISITED = 20_000

        val RESULT_COMPARATOR: Comparator<MatchResult> = compareBy(
            { it.node.bounds.top },
            { it.node.depth },
            { it.node.drawingOrder ?: Int.MAX_VALUE },
            { it.node.bounds.left },
        )
    }
}
