package com.wavexzore.toolbox.core.selector

import com.wavexzore.toolbox.core.selector.snapshot.SnapshotTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectorEngineTest {

    private val engine = SelectorEngine()

    private fun tree(vararg children: com.wavexzore.toolbox.core.selector.snapshot.SnapshotNodeDto) =
        SnapshotTree.fromDto(TestTrees.dto(cls = "android.widget.FrameLayout", children = children.toList()))

    // ---- 基础命中 ----

    @Test
    fun `single match`() {
        val t = tree(TestTrees.dto(text = "跳过", clickable = true, top = 100))
        assertEquals("跳过", engine.findFirst(t, "[text=跳过]")?.text)
    }

    @Test
    fun `no hit returns null and empty`() {
        val t = tree(TestTrees.dto(text = "跳过"))
        assertNull(engine.findFirst(t, "[text=确定]"))
        assertTrue(engine.findAll(t, "[text=确定]").isEmpty())
    }

    @Test
    fun `empty tree no hit`() {
        val t = tree()
        assertNull(engine.findFirst(t, "[text=任意]"))
    }

    @Test
    fun `bad selector returns empty without throwing`() {
        val t = tree(TestTrees.dto(text = "跳过"))
        assertTrue(engine.findAll(t, "[text=跳过").isEmpty())
        assertTrue(engine.findAll(t, "[]").isEmpty())
        assertFalse(engine.anyMatches(t, "[foo=bar]"))
    }

    // ---- 排序：Y 最小 → depth 最小 → drawingOrder → X ----

    @Test
    fun `results sorted by top ascending`() {
        val t = tree(
            TestTrees.dto(text = "跳过", top = 200),
            TestTrees.dto(text = "跳过", top = 50),
        )
        val results = engine.findAll(t, "[text=跳过]")
        assertEquals(listOf(50, 200), results.map { it.node.bounds.top })
    }

    @Test
    fun `same top prefers smaller depth`() {
        val t = tree(
            TestTrees.dto(top = 0, children = listOf(TestTrees.dto(text = "跳过", top = 100))),
            TestTrees.dto(text = "跳过", top = 100),
        )
        val results = engine.findAll(t, "[text=跳过]")
        assertEquals(2, results.size)
        assertEquals(1, results.first().node.depth)
        assertEquals(2, results.last().node.depth)
    }

    @Test
    fun `same top and depth prefers smaller drawingOrder`() {
        val t = tree(
            TestTrees.dto(text = "x", top = 100, drawingOrder = 5),
            TestTrees.dto(text = "x", top = 100, drawingOrder = 1),
        )
        assertEquals(1, engine.findAll(t, "[text=x]").first().node.drawingOrder)
    }

    // ---- 祖先链 ----

    @Test
    fun `descendant chain hit and miss`() {
        val t = tree(
            TestTrees.dto(vid = "container", children = listOf(TestTrees.dto(text = "目标"))),
            TestTrees.dto(vid = "other", children = listOf(TestTrees.dto(text = "噪音"))),
        )
        assertEquals("目标", engine.findFirst(t, "[vid=container] [text=目标]")?.text)
        assertNull(engine.findFirst(t, "[vid=container] [text=噪音]"))
    }

    @Test
    fun `child relation requires direct parent`() {
        val t = tree(
            TestTrees.dto(vid = "parent", children = listOf(TestTrees.dto(text = "目标"))),
        )
        assertEquals("目标", engine.findFirst(t, "[vid=parent] > [text=目标]")?.text)
        // [vid=parent] > [root 层级不满足] —— 用更深树验证 strict
        val deep = tree(
            TestTrees.dto(vid = "grand", children = listOf(
                TestTrees.dto(vid = "parent", children = listOf(TestTrees.dto(text = "目标"))),
            )),
        )
        assertNull(engine.findFirst(deep, "[vid=grand] > [text=目标]"))
        assertEquals("目标", engine.findFirst(deep, "[vid=grand] [text=目标]")?.text)
    }

    @Test
    fun `anchor relation hit`() {
        val t = tree(
            TestTrees.dto(vid = "dialog", children = listOf(
                TestTrees.dto(text = "确认删除?", children = listOf(TestTrees.dto(text = "确定", clickable = true))),
            )),
        )
        assertEquals("确定", engine.findFirst(t, "@[text=确认删除?] [text=确定]")?.text)
    }

    @Test
    fun `three step chain`() {
        val t = tree(
            TestTrees.dto(vid = "a", children = listOf(
                TestTrees.dto(vid = "b", children = listOf(
                    TestTrees.dto(text = "c", top = 10),
                    TestTrees.dto(text = "c", top = 99),
                )),
            )),
        )
        val hit = engine.findFirst(t, "[vid=a] > [vid=b] [text=c]")
        assertEquals(10, hit?.bounds?.top)
    }

    // ---- anyMatches / matchNode ----

    @Test
    fun `anyMatches true and false`() {
        val t = tree(TestTrees.dto(text = "abc"))
        assertTrue(engine.anyMatches(t, "[text*=b]"))
        assertFalse(engine.anyMatches(t, "[text=a] > [text=abc]"))
    }

    @Test
    fun `matchNode specific node`() {
        val target = TestTrees.dto(text = "target")
        val t = tree(target, TestTrees.dto(text = "other"))
        val targetNode = t.children[0]
        assertTrue(engine.matchNode(t, "[text=target]", targetNode))
        assertFalse(engine.matchNode(t, "[text=target]", t.children[1]))
    }

    @Test
    fun `matchNode with chain`() {
        val t = tree(
            TestTrees.dto(vid = "p", children = listOf(TestTrees.dto(text = "child"))),
        )
        val child = t.children[0].children[0]
        assertTrue(engine.matchNode(t, "[vid=p] > [text=child]", child))
        assertFalse(engine.matchNode(t, "[vid=nope] [text=child]", child))
    }

    // ---- exclude ----

    @Test
    fun `exclude skips subtree`() {
        val t = tree(
            TestTrees.dto(vid = "hidden", children = listOf(TestTrees.dto(text = "x"))),
            TestTrees.dto(text = "x", top = 500),
        )
        val results = engine.findAll(t, "[text=x]", excludes = listOf("[vid=hidden]"))
        assertEquals(1, results.size)
        assertEquals(500, results[0].node.bounds.top)
    }

    @Test
    fun `exclude candidate itself`() {
        val t = tree(TestTrees.dto(text = "x", vid = "hidden"))
        assertTrue(engine.findAll(t, "[text=x]", excludes = listOf("[vid=hidden]")).isEmpty())
    }

    @Test
    fun `excludeMatches filters list`() {
        val t = tree(
            TestTrees.dto(vid = "hidden", children = listOf(TestTrees.dto(text = "x"))),
            TestTrees.dto(text = "x", top = 500),
        )
        val all = engine.findAll(t, "[text=x]")
        assertEquals(2, all.size)
        val filtered = engine.excludeMatches(t, all, listOf("[vid=hidden]"))
        assertEquals(1, filtered.size)
    }

    // ---- 边界 ----

    @Test
    fun `maxVisited truncates traversal`() {
        val children = (1..30).map { i -> TestTrees.dto(text = "n", top = i * 10) }
        val t = tree(*children.toTypedArray())
        val limited = SelectorEngine(maxVisitedNodes = 5)
        val results = limited.findAll(t, "[text=n]")
        assertTrue(results.size in 1..5)
        assertEquals(10, results.first().node.bounds.top)
    }

    @Test
    fun `unicode find`() {
        val t = tree(TestTrees.dto(text = "周涵"), TestTrees.dto(text = "小蜜蜂🐝"))
        assertEquals("小蜜蜂🐝", engine.findFirst(t, "[text*=🐝]")?.text)
    }

    @Test
    fun `vid suffix find`() {
        val t = tree(TestTrees.dto(vid = "com.tencent.mm:id/avatar"))
        assertEquals("avatar", engine.findFirst(t, "[vid=avatar]")?.viewId?.substringAfterLast('/'))
    }
}
