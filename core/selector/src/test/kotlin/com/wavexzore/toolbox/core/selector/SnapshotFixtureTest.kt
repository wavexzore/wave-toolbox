package com.wavexzore.toolbox.core.selector

import com.wavexzore.toolbox.core.selector.snapshot.SnapshotTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SnapshotFixtureTest {

    private val engine = SelectorEngine()

    @Test
    fun `ad splash finds topmost skip`() {
        val root = SnapshotTree.fromJson(TestTrees.fixture("ad_splash.json"))
        val skip = engine.findFirst(root, "[text^=跳过]")
        assertNotNull(skip)
        assertEquals(130, skip.bounds.top)
    }

    @Test
    fun `ad splash anchor selects top-right button only`() {
        val root = SnapshotTree.fromJson(TestTrees.fixture("ad_splash.json"))
        val hit = engine.findFirst(root, "@[vid=skip_container] [text=跳过]")
        assertNotNull(hit)
        assertEquals(130, hit.bounds.top)
        // 无锚点时两个 跳过 都在
        assertEquals(2, engine.findAll(root, "[text=跳过]").size)
    }

    @Test
    fun `wechat list unicode and vid`() {
        val root = SnapshotTree.fromJson(TestTrees.fixture("wechat_list.json"))
        assertEquals("周涵", engine.findFirst(root, "[text=周涵]")?.text)
        assertEquals("小蜜蜂🐝", engine.findFirst(root, "[text*=蜜蜂]")?.text)
        assertNotNull(engine.findFirst(root, "[vid=avatar]"))
    }

    @Test
    fun `dialog chain finds confirm button`() {
        val root = SnapshotTree.fromJson(TestTrees.fixture("dialog.json"))
        val confirm = engine.findFirst(root, "@[class=android.app.Dialog] [class=android.widget.LinearLayout] [text=确定]")
        assertNotNull(confirm)
        assertTrue(confirm.isClickable)
        assertEquals("确定", confirm.text)
    }

    @Test
    fun `dialog exclude cancels`() {
        val root = SnapshotTree.fromJson(TestTrees.fixture("dialog.json"))
        val both = engine.findAll(root, "[class=android.widget.Button]")
        assertEquals(2, both.size)
        val filtered = engine.findAll(root, "[class=android.widget.Button]", excludes = listOf("[text=取消]"))
        assertEquals(1, filtered.size)
        assertEquals("确定", filtered[0].node.text)
    }

    @Test
    fun `json roundtrip preserves structure`() {
        val root = SnapshotTree.fromJson(TestTrees.fixture("dialog.json"))
        val jsonAgain = SnapshotTree.toJson(root)
        val root2 = SnapshotTree.fromJson(jsonAgain)
        assertEquals(root.text, root2.text)
        assertEquals(root.children.size, root2.children.size)
        assertEquals(
            engine.findAll(root, "[text=确定]").first().node.bounds,
            engine.findAll(root2, "[text=确定]").first().node.bounds,
        )
    }
}
