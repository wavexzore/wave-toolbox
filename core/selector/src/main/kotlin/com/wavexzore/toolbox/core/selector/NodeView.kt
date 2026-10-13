package com.wavexzore.toolbox.core.selector

/** 纯 JVM 矩形（与 android.graphics.Rect 解耦）。 */
data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Long get() = width.toLong() * height.toLong()
}

/**
 * 节点视图抽象（04 §5.2）：纯数据、无 Android 依赖；
 * 设备侧由 T-007 的 AccessibilityNodeInfo 适配器实现，测试侧由 [SnapshotTree] 构造。
 */
interface NodeView {
    val text: String?
    val contentDescription: String?
    val viewId: String?
    val className: String?
    val bounds: Rect
    val isClickable: Boolean
    val isVisible: Boolean
    val isEditable: Boolean
    val isScrollable: Boolean
    val depth: Int
    val drawingOrder: Int?
    val children: List<NodeView>
}
