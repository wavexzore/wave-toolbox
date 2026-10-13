package com.wavexzore.toolbox.core.selector

import com.wavexzore.toolbox.core.selector.snapshot.SnapshotNodeDto
import com.wavexzore.toolbox.core.selector.snapshot.SnapshotTree

/** 测试用节点/树构造（fromDto 自动计算 depth）。 */
object TestTrees {

    fun dto(
        text: String? = null,
        desc: String? = null,
        vid: String? = null,
        cls: String? = null,
        top: Int = 0,
        left: Int = 0,
        clickable: Boolean = false,
        visible: Boolean = true,
        drawingOrder: Int? = null,
        children: List<SnapshotNodeDto> = emptyList(),
    ) = SnapshotNodeDto(
        text = text,
        desc = desc,
        vid = vid,
        className = cls,
        bounds = listOf(left, top, left + 100, top + 60),
        clickable = clickable,
        visible = visible,
        drawingOrder = drawingOrder,
        children = children,
    )

    fun tree(root: SnapshotNodeDto = dto(cls = "android.widget.FrameLayout")) =
        SnapshotTree.fromDto(root)

    fun fixture(name: String): String =
        checkNotNull(TestTrees::class.java.getResource("/fixtures/$name")) {
            "fixture /fixtures/$name not found"
        }.readText()
}
