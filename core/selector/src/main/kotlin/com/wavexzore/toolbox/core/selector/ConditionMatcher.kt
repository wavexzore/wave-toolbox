package com.wavexzore.toolbox.core.selector

/** 单条件求值（纯逻辑）。属性缺失时：`!=` 命中，其余不命中。 */
object ConditionMatcher {

    fun matches(node: NodeView, condition: SelCondition): Boolean {
        val actual = when (condition.attr) {
            SelAttr.VID -> node.viewId
            SelAttr.TEXT -> node.text
            SelAttr.DESC -> node.contentDescription
            SelAttr.CLASS -> node.className
        } ?: return condition.op == SelOp.NOT_EQ

        return when (condition.op) {
            SelOp.EQ -> equalsValue(condition.attr, actual, condition.value)
            SelOp.PREFIX -> actual.startsWith(condition.value)
            SelOp.CONTAINS -> actual.contains(condition.value)
            SelOp.NOT_EQ -> !equalsValue(condition.attr, actual, condition.value)
        }
    }

    /** vid 采用"全等或资源后缀"语义（04 §5.2：仅后缀匹配语义）。 */
    private fun equalsValue(attr: SelAttr, actual: String, value: String): Boolean =
        actual == value || (attr == SelAttr.VID && actual.substringAfterLast('/') == value)
}
