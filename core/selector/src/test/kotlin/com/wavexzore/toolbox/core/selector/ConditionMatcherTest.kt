package com.wavexzore.toolbox.core.selector

import com.wavexzore.toolbox.core.selector.snapshot.SnapshotTree
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConditionMatcherTest {

    private fun node(
        text: String? = null,
        desc: String? = null,
        vid: String? = null,
        cls: String? = null,
    ) = SnapshotTree.fromDto(TestTrees.dto(text = text, desc = desc, vid = vid, cls = cls))

    private fun cond(attr: SelAttr, op: SelOp, value: String) = SelCondition(attr, op, value)

    @Test
    fun `eq text hit and miss`() {
        val n = node(text = "跳过")
        assertTrue(ConditionMatcher.matches(n, cond(SelAttr.TEXT, SelOp.EQ, "跳过")))
        assertFalse(ConditionMatcher.matches(n, cond(SelAttr.TEXT, SelOp.EQ, "关闭")))
    }

    @Test
    fun `prefix hit`() {
        assertTrue(ConditionMatcher.matches(node(text = "跳过 5s"), cond(SelAttr.TEXT, SelOp.PREFIX, "跳过")))
    }

    @Test
    fun `contains hit`() {
        assertTrue(ConditionMatcher.matches(node(text = "点击查看广告详情"), cond(SelAttr.TEXT, SelOp.CONTAINS, "广告")))
    }

    @Test
    fun `not eq hit and miss`() {
        assertTrue(ConditionMatcher.matches(node(text = "取消"), cond(SelAttr.TEXT, SelOp.NOT_EQ, "确定")))
        assertFalse(ConditionMatcher.matches(node(text = "确定"), cond(SelAttr.TEXT, SelOp.NOT_EQ, "确定")))
    }

    @Test
    fun `desc and class direct`() {
        assertTrue(ConditionMatcher.matches(node(desc = "关闭"), cond(SelAttr.DESC, SelOp.EQ, "关闭")))
        assertTrue(ConditionMatcher.matches(node(cls = "android.widget.TextView"), cond(SelAttr.CLASS, SelOp.EQ, "android.widget.TextView")))
    }

    @Test
    fun `vid suffix semantics`() {
        val n = node(vid = "com.demo.app:id/btn_skip")
        assertTrue(ConditionMatcher.matches(n, cond(SelAttr.VID, SelOp.EQ, "btn_skip")))
        assertTrue(ConditionMatcher.matches(n, cond(SelAttr.VID, SelOp.EQ, "com.demo.app:id/btn_skip")))
        assertFalse(ConditionMatcher.matches(n, cond(SelAttr.VID, SelOp.EQ, "btn")))
    }

    @Test
    fun `missing attr only matches not eq`() {
        val n = node()
        assertTrue(ConditionMatcher.matches(n, cond(SelAttr.TEXT, SelOp.NOT_EQ, "x")))
        assertFalse(ConditionMatcher.matches(n, cond(SelAttr.TEXT, SelOp.EQ, "x")))
    }

    @Test
    fun `unicode text`() {
        assertTrue(ConditionMatcher.matches(node(text = "小蜜蜂🐝"), cond(SelAttr.TEXT, SelOp.CONTAINS, "🐝")))
        assertTrue(ConditionMatcher.matches(node(text = "群聊（3）"), cond(SelAttr.TEXT, SelOp.PREFIX, "群聊")))
    }
}
