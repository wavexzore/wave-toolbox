package com.wavexzore.toolbox.core.selector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SelectorParserTest {

    private fun ok(raw: String): Selector {
        val result = SelectorParser.parse(raw)
        assertIs<SelectorParser.ParseResult.Ok>(result, "expect ok for: $raw")
        return result.selector
    }

    private fun error(raw: String): SelectorParser.ParseResult.Error {
        val result = SelectorParser.parse(raw)
        assertIs<SelectorParser.ParseResult.Error>(result, "expect error for: $raw")
        return result
    }

    @Test
    fun `empty selector is error`() {
        val e = error("")
        assertEquals(0, e.position)
    }

    @Test
    fun `blank selector is error`() {
        error("   ")
    }

    @Test
    fun `simple equality`() {
        val selector = ok("[text=确定]")
        assertEquals(1, selector.steps.size)
        assertEquals(SelRelation.FIRST, selector.steps[0].relation)
        val c = selector.steps[0].conditions.single()
        assertEquals(SelAttr.TEXT, c.attr)
        assertEquals(SelOp.EQ, c.op)
        assertEquals("确定", c.value)
    }

    @Test
    fun `quoted value keeps spaces`() {
        val c = ok("""[text="确定 删除"]""").steps[0].conditions.single()
        assertEquals("确定 删除", c.value)
    }

    @Test
    fun `prefix operator`() {
        assertEquals(SelOp.PREFIX, ok("[text^=跳过]").steps[0].conditions.single().op)
    }

    @Test
    fun `contains operator`() {
        assertEquals(SelOp.CONTAINS, ok("[text*=广告]").steps[0].conditions.single().op)
    }

    @Test
    fun `not eq operator`() {
        assertEquals(SelOp.NOT_EQ, ok("[text!=取消]").steps[0].conditions.single().op)
    }

    @Test
    fun `vid desc class attributes`() {
        assertEquals(SelAttr.VID, ok("[vid=btn_skip]").steps[0].conditions.single().attr)
        assertEquals(SelAttr.DESC, ok("[desc=关闭]").steps[0].conditions.single().attr)
        assertEquals(SelAttr.CLASS, ok("[class=android.widget.TextView]").steps[0].conditions.single().attr)
    }

    @Test
    fun `missing bracket is error with position`() {
        val e = error("[text=确定")
        assertTrue(e.position >= 0)
    }

    @Test
    fun `unknown attribute is error`() {
        val e = error("[foo=1]")
        assertTrue(e.message.contains("foo"))
    }

    @Test
    fun `missing operator is error`() {
        error("[text确定]").let { }
    }

    @Test
    fun `empty value allowed`() {
        assertEquals("", ok("[text=]").steps[0].conditions.single().value)
    }

    @Test
    fun `space is descendant relation`() {
        val selector = ok("[vid=a] [text=b]")
        assertEquals(2, selector.steps.size)
        assertEquals(SelRelation.DESCENDANT, selector.steps[1].relation)
    }

    @Test
    fun `gt is child relation`() {
        assertEquals(SelRelation.CHILD, ok("[vid=a] > [text=b]").steps[1].relation)
    }

    @Test
    fun `at is anchor relation`() {
        assertEquals(SelRelation.ANCHOR, ok("@[vid=a] [text=b]").steps[1].relation)
        assertEquals(SelRelation.ANCHOR, ok("[vid=a] @ [text=b]").steps[1].relation)
    }

    @Test
    fun `escaped bracket in raw value`() {
        assertEquals("a]b", ok("""[text=a\]b]""").steps[0].conditions.single().value)
    }

    @Test
    fun `leading combinator on first step`() {
        val selector = ok("> [text=a]")
        assertEquals(SelRelation.FIRST, selector.steps[0].relation)
    }

    @Test
    fun `cache returns same instance`() {
        val a = SelectorCache.parse("[text=cache_probe]")
        val b = SelectorCache.parse("[text=cache_probe]")
        assertSame(a, b)
        val e1 = SelectorCache.parse("[broken=]")
        val e2 = SelectorCache.parse("[broken=]")
        assertSame(e1, e2)
    }
}
