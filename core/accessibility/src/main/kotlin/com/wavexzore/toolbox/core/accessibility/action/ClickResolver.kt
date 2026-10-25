package com.wavexzore.toolbox.core.accessibility.action

import com.wavexzore.toolbox.core.selector.NodeFilters
import com.wavexzore.toolbox.core.selector.NodeView
import com.wavexzore.toolbox.core.selector.Rect

/**
 * 点击目标解析（纯逻辑，李跳跳 `c()` 语义移植，04 §5.5 防误触）：
 *
 * 1. 命中节点自身及其祖先中，找最近的「可点击且非过滤类」节点 → [Resolution.Direct]（走 performAction）；
 * 2. 找不到 → [Resolution.CenterTap] 点命中节点中心（坐标兜底）；
 * 3. 命中节点零面积且无候选 → [Resolution.NoTarget]。
 *
 * 过滤类（作为 performAction 目标易误触/无效）：editable、scrollable、checkable（勾选类组件）、
 * 宽 > 60% 屏宽且无文本/描述的大容器；不可见节点同样跳过。
 */
object ClickResolver {

    sealed interface Resolution {
        /** 走节点 performAction（最近合格可点击节点）。 */
        data class Direct(val view: NodeView) : Resolution

        /** 坐标兜底：点命中节点中心 (x, y)。 */
        data class CenterTap(val x: Int, val y: Int) : Resolution

        data object NoTarget : Resolution
    }

    const val WIDE_RATIO = NodeFilters.WIDE_RATIO

    /**
     * @param target 命中节点
     * @param ancestorsNearestFirst 祖先链（由近到远，不含 target 自身）
     * @param screenWidth 屏幕宽（px，用于宽容器判定；<=0 时跳过该判定）
     */
    fun resolve(target: NodeView, ancestorsNearestFirst: List<NodeView>, screenWidth: Int): Resolution {
        for (node in sequenceOf(target).plus(ancestorsNearestFirst.asSequence())) {
            if (!node.isClickable || !node.isVisible) continue
            if (isFilteredTarget(node, screenWidth)) continue
            return Resolution.Direct(node)
        }
        val center = centerOf(target.bounds)
        return if (center != null) Resolution.CenterTap(center.first, center.second) else Resolution.NoTarget
    }

    /** 过滤类判定（可点击但不宜作为 performAction 直接目标；R-209 起由 [NodeFilters] 唯一实现）。 */
    internal fun isFilteredTarget(node: NodeView, screenWidth: Int): Boolean =
        NodeFilters.isFilteredPerformTarget(node, screenWidth)

    fun isCheckableClass(className: String?): Boolean = NodeFilters.isCheckableClass(className)

    fun centerOf(rect: Rect): Pair<Int, Int>? {
        // B-205：退化放行——零高/零宽但非全退化（如 Compose height=0 按钮）的中心仍是
        // 屏内有效点；仅全退化 [0,0,0,0] 返回 null。
        if (rect.width <= 0 && rect.height <= 0) return null
        return rect.left + rect.width / 2 to rect.top + rect.height / 2
    }
}
