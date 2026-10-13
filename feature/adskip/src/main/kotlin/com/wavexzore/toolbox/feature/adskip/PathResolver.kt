package com.wavexzore.toolbox.feature.adskip

import com.wavexzore.toolbox.core.selector.NodeView

/**
 * 树路径兜底（T-005；李跳跳 `LttService.b` 语义移植）。
 * `path` 形如 `0/2/1`：逐层按 (drawingOrder 升序, childCount 升序) 排序后的子索引。
 * 版本漂移时只要层级前缀不破即可命中；全路径失败由调用方退回 position。
 */
object PathResolver {

    const val MAX_DEPTH = 16

    fun resolve(root: NodeView, path: String): NodeView? {
        val steps = path.split('/').mapNotNull { it.trim().toIntOrNull() }
        if (steps.isEmpty()) return null
        var cursor: NodeView = root
        for (step in steps) {
            val children = cursor.children
                .sortedWith(compareBy({ it.drawingOrder ?: Int.MAX_VALUE }, { it.children.size }))
            cursor = children.getOrNull(step) ?: return null
        }
        return cursor
    }
}
