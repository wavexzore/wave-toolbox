package com.wavexzore.toolbox.core.accessibility.action

import com.wavexzore.toolbox.core.rules.model.RawRule
import com.wavexzore.toolbox.core.selector.SelectorEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuleMatcherTest {

    private val engine = SelectorEngine()

    private fun rule(
        matches: List<String> = emptyList(),
        anyMatches: List<String> = emptyList(),
        excludeMatches: List<String> = emptyList(),
    ) = RawRule(id = "r", matches = matches, anyMatches = anyMatches, excludeMatches = excludeMatches)

    @Test
    fun `all matches must hit the same node`() {
        val tree = A11yTestTrees.root(
            A11yTestTrees.node(text = "登录", vid = "com.a:id/btn"),
            A11yTestTrees.node(text = "登录", vid = "com.a:id/other"),
        )
        val outcome = RuleMatcher.evaluate(engine, tree, rule(matches = listOf("[text=登录]", "[vid=btn]")))
        assertNotNull(outcome.target)
        assertEquals("com.a:id/btn", outcome.target.viewId)
    }

    @Test
    fun `selector hitting different nodes fails same-node requirement`() {
        val tree = A11yTestTrees.root(
            A11yTestTrees.node(text = "登录"),
            A11yTestTrees.node(vid = "x"),
        )
        val outcome = RuleMatcher.evaluate(engine, tree, rule(matches = listOf("[text=登录]", "[vid=x]")))
        assertNull(outcome.target)
        assertTrue(outcome.reason.contains("未同时满足"))
    }

    @Test
    fun `exclude matches via ancestor removes candidate`() {
        val excludedContainer = A11yTestTrees.node(cls = "android.widget.LinearLayout", children = listOf(
            A11yTestTrees.node(text = "跳过"),
        ))
        val tree = A11yTestTrees.root(excludedContainer)
        val outcome = RuleMatcher.evaluate(
            engine,
            tree,
            rule(matches = listOf("[text=跳过]"), excludeMatches = listOf("[class=android.widget.LinearLayout]")),
        )
        assertNull(outcome.target)
    }

    @Test
    fun `anyMatches gates matches candidates`() {
        val tree = A11yTestTrees.root(A11yTestTrees.node(text = "跳过"))
        val outcome = RuleMatcher.evaluate(
            engine,
            tree,
            rule(matches = listOf("[text=跳过]"), anyMatches = listOf("[text=关闭]")),
        )
        assertNull(outcome.target)
        assertTrue(outcome.reason.contains("anyMatches"))
    }

    @Test
    fun `anyMatches only uses first hitting selector`() {
        val tree = A11yTestTrees.root(A11yTestTrees.node(text = "关闭"))
        val outcome = RuleMatcher.evaluate(engine, tree, rule(anyMatches = listOf("[text=跳过]", "[text=关闭]")))
        assertNotNull(outcome.target)
        assertEquals("关闭", outcome.target.text)
        assertEquals("[text=关闭]", outcome.viaSelector)
    }

    @Test
    fun `no selectors yields reason`() {
        val outcome = RuleMatcher.evaluate(engine, A11yTestTrees.root(), rule())
        assertNull(outcome.target)
        assertEquals("无可用选择器", outcome.reason)
    }

    @Test
    fun `empty tree yields first selector miss`() {
        val outcome = RuleMatcher.evaluate(engine, A11yTestTrees.root(), rule(matches = listOf("[text=甲]")))
        assertNull(outcome.target)
        assertTrue(outcome.reason.contains("matches[0]"))
    }

    @Test
    fun `anyMatches second selector can gate candidates`() {
        val tree = A11yTestTrees.root(
            A11yTestTrees.node(text = "跳过"),
            A11yTestTrees.node(text = "关闭"),
        )
        val outcome = RuleMatcher.evaluate(
            engine,
            tree,
            rule(matches = listOf("[text=跳过]"), anyMatches = listOf("[text=关闭]", "[text=不在]")),
        )
        assertNotNull(outcome.target)
        assertEquals("跳过", outcome.target.text)
    }
}
