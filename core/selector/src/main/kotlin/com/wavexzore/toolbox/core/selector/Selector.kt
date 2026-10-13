package com.wavexzore.toolbox.core.selector

/** 条件属性（首期最小子集，04 §5.2）。 */
enum class SelAttr {
    VID,
    TEXT,
    DESC,
    CLASS,
    ;

    companion object {
        fun fromName(name: String): SelAttr? = when (name) {
            "vid" -> VID
            "text" -> TEXT
            "desc" -> DESC
            "class" -> CLASS
            else -> null
        }
    }
}

/** 属性运算符：`=` 相等、`^=` 前缀、`*=` 包含、`!=` 不等（取反 `=`）。 */
enum class SelOp(val symbol: String) {
    EQ("="),
    PREFIX("^="),
    CONTAINS("*="),
    NOT_EQ("!="),
}

/** 单条条件 `[attr op value]`。 */
data class SelCondition(
    val attr: SelAttr,
    val op: SelOp,
    val value: String,
)

/** 步骤与上一步的关系（首步为 FIRST）。 */
enum class SelRelation {
    FIRST,

    /** 空格：上一步是其任意层级祖先。 */
    DESCENDANT,

    /** `>`：上一步是其直接父节点。 */
    CHILD,

    /** `@`：祖先锚点（同 DESCENDANT 的匹配语义，语义标注为锚定）。 */
    ANCHOR,
}

/** 解析产物：候选步（最后一步）+ 祖先约束链。 */
data class Selector(
    val raw: String,
    val steps: List<Step>,
) {
    data class Step(val conditions: List<SelCondition>, val relation: SelRelation)
}
