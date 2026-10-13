package com.wavexzore.toolbox.core.selector

/**
 * 节点过滤原语（R-209 公共化：归一 KeywordStrategy.passesFilters 与 ClickResolver.isFilteredTarget）。
 *
 * 两条链语义保持各自等价，仅共享原语（宽容器判定统一为 `>= toInt()` 边界；keyword 链在
 * 宽度恰等于阈值的边界上由「>」收紧为「>=」，属可接受边界差，测试锁定）。
 */
object NodeFilters {

    const val WIDE_RATIO = 0.6f

    /** 宽容器（宽度 ≥ 60% 屏宽；screenWidth<=0 时不判定）。 */
    fun isWideContainer(node: NodeView, screenWidth: Int): Boolean =
        screenWidth > 0 && node.bounds.width >= (screenWidth * WIDE_RATIO).toInt()

    /**
     * keyword/vid 候选链过滤（与 KeywordStrategy 现状等价）：
     * 可见、非编辑、非滚动、非零面积；宽容器且无文本剔除。
     */
    fun passesKeywordCandidate(node: NodeView, screenWidth: Int): Boolean {
        if (!node.isVisible || node.isEditable || node.isScrollable) return false
        if (node.bounds.width <= 0 || node.bounds.height <= 0) return false
        if (isWideContainer(node, screenWidth) && node.text.isNullOrBlank()) return false
        return true
    }

    /**
     * performAction 目标过滤（与 ClickResolver 现状等价）：
     * 编辑/滚动/勾选类组件、宽且无文本/描述的大容器不宜作为直接目标。
     */
    fun isFilteredPerformTarget(node: NodeView, screenWidth: Int): Boolean {
        if (node.isEditable || node.isScrollable) return true
        if (isCheckableClass(node.className)) return true
        if (isWideContainer(node, screenWidth) && node.text.isNullOrBlank() && node.contentDescription.isNullOrBlank()) return true
        return false
    }

    fun isCheckableClass(className: String?): Boolean =
        className?.substringAfterLast('.') in CHECKABLE_CLASSES

    private val CHECKABLE_CLASSES = setOf("CheckBox", "RadioButton", "Switch", "ToggleButton", "CompoundButton")
}
