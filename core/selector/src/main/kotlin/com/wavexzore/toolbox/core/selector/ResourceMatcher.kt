package com.wavexzore.toolbox.core.selector

/**
 * 资源名匹配原语（R-209 公共化：归一 KeywordStrategy vid 片段匹配 与 ConditionMatcher vid 全等/后缀语义）。
 *
 * 两种语义并存但实现唯一：
 * - [vidContains]：小写包含（adskip 关键词候选兜底 `id:skip` 收紧版）；
 * - [vidEquals]：全等或资源后缀（选择器 `@[vid="close"]`）。
 */
object ResourceMatcher {

    fun vidContains(viewId: String?, fragment: String): Boolean {
        if (viewId.isNullOrEmpty() || fragment.isEmpty()) return false
        return viewId.lowercase().contains(fragment.lowercase())
    }

    fun vidEquals(viewId: String?, value: String): Boolean =
        viewId == value || viewId?.substringAfterLast('/') == value
}
