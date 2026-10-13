package com.wavexzore.toolbox.core.accessibility.action

import com.wavexzore.toolbox.core.selector.NodeView
import com.wavexzore.toolbox.core.selector.Rect
import com.wavexzore.toolbox.core.selector.snapshot.SnapshotNode

/** T-007 纯逻辑测试的轻量树构造。 */
object A11yTestTrees {

    fun node(
        text: String? = null,
        desc: String? = null,
        vid: String? = null,
        cls: String? = null,
        left: Int = 0,
        top: Int = 0,
        right: Int = 100,
        bottom: Int = 50,
        clickable: Boolean = false,
        visible: Boolean = true,
        editable: Boolean = false,
        scrollable: Boolean = false,
        depth: Int = 0,
        children: List<NodeView> = emptyList(),
    ): SnapshotNode = SnapshotNode(
        text = text,
        contentDescription = desc,
        viewId = vid,
        className = cls,
        bounds = Rect(left, top, right, bottom),
        isClickable = clickable,
        isVisible = visible,
        isEditable = editable,
        isScrollable = scrollable,
        depth = depth,
        drawingOrder = null,
        children = children,
    )

    fun root(vararg children: NodeView): SnapshotNode =
        node(cls = "android.widget.FrameLayout", right = 1080, bottom = 2000, children = children.toList())
}
