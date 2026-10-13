package com.wavexzore.toolbox.feature.adskip

import com.wavexzore.toolbox.core.selector.snapshot.SnapshotTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 树路径兜底用例（T-005）。 */
class PathResolverTest {

    private val tree = """
        { "class":"Root", "bounds":[0,0,100,100], "children":[
          { "class":"A", "drawingOrder":1, "bounds":[0,0,50,50], "children":[
            { "class":"A1", "drawingOrder":0, "bounds":[0,0,1,1] },
            { "class":"A2", "desc":"目标", "clickable":true, "bounds":[0,0,2,2] }
          ]},
          { "class":"B", "drawingOrder":0, "bounds":[50,0,100,50] }
        ]}
    """.trimIndent()

    @Test
    fun resolvesByDrawingOrderSortedChildIndex() {
        val root = SnapshotTree.fromJson(tree)
        // 第 0 层：drawingOrder 排序后 index0=B，index1=A
        val a = PathResolver.resolve(root, "1")
        assertEquals("A", a?.className)
        // A 的子层：index1=A2
        val a2 = PathResolver.resolve(root, "1/1")
        assertEquals("A2", a2?.className)
    }

    @Test
    fun outOfRangeReturnsNull() {
        val root = SnapshotTree.fromJson(tree)
        assertNull(PathResolver.resolve(root, "9"))
        assertNull(PathResolver.resolve(root, "1/5"))
    }
}
