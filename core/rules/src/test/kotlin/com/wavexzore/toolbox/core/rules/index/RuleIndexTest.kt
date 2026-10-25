package com.wavexzore.toolbox.core.rules.index

import com.wavexzore.toolbox.core.rules.TestRules
import com.wavexzore.toolbox.core.rules.matcher.VersionMatcher
import com.wavexzore.toolbox.core.rules.model.RawApp
import com.wavexzore.toolbox.core.rules.model.RawRule
import com.wavexzore.toolbox.core.rules.model.RawRuleGroup
import com.wavexzore.toolbox.core.rules.model.VersionRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleIndexTest {

    @Test
    fun `query only returns rules for package`() {
        val index = TestRules.index(
            TestRules.group(id = "g1", apps = listOf(RawApp("com.a")), rules = listOf(TestRules.rule(id = "ra"))),
            TestRules.group(id = "g2", apps = listOf(RawApp("com.b")), rules = listOf(TestRules.rule(id = "rb"))),
        )
        assertEquals(listOf("ra"), index.query("com.a").map { it.rule.id })
        assertEquals(listOf("rb"), index.query("com.b").map { it.rule.id })
        assertTrue(index.query("com.c").isEmpty())
    }

    @Test
    fun `wildcard group applies to all packages`() {
        val index = TestRules.index(
            TestRules.group(id = "global", apps = emptyList(), rules = listOf(TestRules.rule(id = "globalRule"))),
        )
        assertEquals(listOf("globalRule"), index.query("any.pkg").map { it.rule.id })
    }

    @Test
    fun `disabled group or rule filtered`() {
        val index = TestRules.index(
            TestRules.group(id = "off", enabled = false, apps = listOf(RawApp("com.a"))),
            TestRules.group(id = "on", apps = listOf(RawApp("com.a")), rules = listOf(TestRules.rule(id = "offRule", enabled = false))),
        )
        assertTrue(index.query("com.a").isEmpty())
    }

    @Test
    fun `ordering by group order then rule order`() {
        val index = TestRules.index(
            TestRules.group(id = "g1", order = 5, apps = listOf(RawApp("com.a")), rules = listOf(TestRules.rule(id = "r5"))),
            TestRules.group(id = "g2", order = 1, apps = listOf(RawApp("com.a")), rules = listOf(TestRules.rule(id = "r1a"), TestRules.rule(id = "r1b", order = 2))),
        )
        assertEquals(listOf("r1a", "r1b", "r5"), index.query("com.a").map { it.rule.id })
    }

    @Test
    fun `activity filter`() {
        val index = TestRules.index(
            TestRules.group(
                apps = listOf(RawApp("com.a")),
                rules = listOf(
                    TestRules.rule(id = "splash", activityIds = listOf("Splash")),
                    TestRules.rule(id = "any"),
                ),
            ),
        )
        assertEquals(listOf("any", "splash"), index.query("com.a", activity = "com.a.Splash").map { it.rule.id })
        assertEquals(listOf("any"), index.query("com.a", activity = "com.a.Main").map { it.rule.id })
        assertEquals(listOf("any"), index.query("com.a", activity = null).map { it.rule.id })
    }

    @Test
    fun `rule version filter`() {
        val index = TestRules.index(
            TestRules.group(
                apps = listOf(RawApp("com.a")),
                rules = listOf(
                    TestRules.rule(id = "newish").copy(versionCode = VersionRange(min = 100)),
                    TestRules.rule(id = "always"),
                ),
            ),
        )
        assertEquals(listOf("always"), index.query("com.a", versionCode = 50).map { it.rule.id })
        assertEquals(listOf("always", "newish"), index.query("com.a", versionCode = 150).map { it.rule.id })
    }

    @Test
    fun `app level version filter`() {
        val index = TestRules.index(
            TestRules.group(
                apps = listOf(RawApp("com.a", versionName = VersionRange(min = "2.0"))),
                rules = listOf(TestRules.rule(id = "r")),
            ),
        )
        assertTrue(index.query("com.a", versionName = "1.5").isEmpty())
        assertEquals(1, index.query("com.a", versionName = "2.1").size)
    }

    @Test
    fun `group level version filter`() {
        val group = TestRules.group(apps = listOf(RawApp("com.a"))).copy(versionCode = VersionRange(max = 10))
        val index = TestRules.index(group)
        assertEquals(1, index.query("com.a", versionCode = 5).size)
        assertTrue(index.query("com.a", versionCode = 20).isEmpty())
    }

    @Test
    fun `packages exposes indexed packages`() {
        val index = TestRules.index(
            TestRules.group(id = "g", apps = listOf(RawApp("com.a"), RawApp("com.b"))),
        )
        assertEquals(setOf("com.a", "com.b"), index.packages())
    }

    @Test
    fun `missing version denied for versioned rule by default`() {
        val group = TestRules.group(
            apps = listOf(RawApp("com.a")),
            rules = listOf(TestRules.rule(id = "versioned").copy(versionCode = VersionRange(min = 100))),
        )
        val strict = RuleIndex.build(listOf(group))
        assertTrue(strict.query("com.a", versionCode = null).isEmpty())

        val lenient = RuleIndex.build(listOf(group), VersionMatcher.MissingVersionPolicy.ALLOW)
        assertEquals(listOf("versioned"), lenient.query("com.a", versionCode = null).map { it.rule.id })
    }

    @Test
    fun `duplicate groups keep stable sort by rule id`() {
        val index = TestRules.index(
            RawRuleGroup(id = "g", apps = listOf(RawApp("com.a")), rules = listOf(
                RawRule(id = "zz"),
                RawRule(id = "aa"),
            )),
        )
        assertEquals(listOf("aa", "zz"), index.query("com.a").map { it.rule.id })
    }
}
