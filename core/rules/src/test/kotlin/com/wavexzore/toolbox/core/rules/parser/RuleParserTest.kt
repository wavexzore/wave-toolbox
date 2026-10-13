package com.wavexzore.toolbox.core.rules.parser

import com.wavexzore.toolbox.core.rules.model.RuleAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuleParserTest {

    private val validJson5 = """
        {
          id: "com.demo",
          name: "demo",
          apps: [{ id: "com.demo" }],
          rules: [
            {
              id: "demo@A",
              matches: ['[text="跳过"]'],
              action: "click"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses valid group`() {
        val (group, errors) = RuleParser.parseGroup(validJson5, "test")
        assertNotNull(group)
        assertEquals("com.demo", group.id)
        assertEquals(1, group.rules.size)
        assertEquals(RuleAction.CLICK, group.rules[0].action)
        assertEquals(500, group.rules[0].actionCd)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `json5 comments and trailing commas allowed`() {
        val text = """
            {
              // 注释
              id: "com.c",
              apps: [{ id: "com.c" },],
              rules: [{ id: "r", matches: ['[text="a"]'], },],
            }
        """.trimIndent()
        val (group, errors) = RuleParser.parseGroup(text, "test")
        assertNotNull(group)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `bad json5 yields fatal error`() {
        val (group, errors) = RuleParser.parseGroup("{ id: ", "bad.json5")
        assertNull(group)
        assertEquals(1, errors.size)
        assertTrue(errors[0].fatal)
    }

    @Test
    fun `blank group id is fatal`() {
        val (group, errors) = RuleParser.parseGroup("""{ id: "", rules: [] }""", "t")
        assertNull(group)
        assertTrue(errors.any { it.fatal && it.message.contains("组 id") })
    }

    @Test
    fun `future schema version is fatal`() {
        val (group, errors) = RuleParser.parseGroup("""{ id: "g", schemaVersion: 99, rules: [] }""", "t")
        assertNull(group)
        assertTrue(errors.any { it.message.contains("schemaVersion") })
    }

    @Test
    fun `bad selector isolated with rule id`() {
        val text = """
            { id: "g", apps: [{id: "p"}], rules: [
                { id: "ok", matches: ['[text="x"]'] },
                { id: "bad", matches: ["[text=x"] }
            ] }
        """.trimIndent()
        val (group, errors) = RuleParser.parseGroup(text, "t")
        assertNotNull(group) // 组仍可加载
        assertEquals(1, errors.size)
        assertEquals("bad", errors[0].ruleId)
        assertTrue(errors[0].message.contains("选择器语法错误"))
        assertTrue(!errors[0].fatal)
    }

    @Test
    fun `empty matches and anyMatches is error`() {
        val (group, errors) = RuleParser.parseGroup("""{ id: "g", apps: [{id:"p"}], rules: [{ id: "r" }] }""", "t")
        assertNotNull(group)
        assertTrue(errors.any { it.ruleId == "r" && it.message.contains("均为空") })
    }

    @Test
    fun `version range object and array forms`() {
        val text = """
            { id: "g", apps: [{ id: "p", versionCode: { min: 10, exclude: [12] } }],
              rules: [{ id: "r", matches: ['[text="x"]'],
                        versionName: ["1.0", "9.9", ["5.0"], []],
                        versionCode: [3, 100, [], [50]] }] }
        """.trimIndent()
        val (group, errors) = RuleParser.parseGroup(text, "t")
        assertNotNull(group)
        assertTrue(errors.isEmpty())
        assertEquals(10, group.apps[0].versionCode?.min)
        assertEquals(listOf(12), group.apps[0].versionCode?.exclude)
        assertEquals("1.0", group.rules[0].versionName?.min)
        assertEquals(listOf("5.0"), group.rules[0].versionName?.include)
        assertEquals(3, group.rules[0].versionCode?.min)
        assertEquals(listOf(50), group.rules[0].versionCode?.exclude)
    }

    @Test
    fun `position px and percent forms`() {
        val text = """
            { id: "g", apps: [{id:"p"}], rules: [
                { id: "px", matches: ['[text="x"]'], position: { x: 900, y: 120 } },
                { id: "pct", matches: ['[text="y"]'], position: { x: "95%", y: "5%" } }
            ] }
        """.trimIndent()
        val (group, errors) = RuleParser.parseGroup(text, "t")
        assertNotNull(group)
        assertTrue(errors.isEmpty())
        assertEquals(900, group.rules[0].position?.x)
        assertEquals(120, group.rules[0].position?.y)
        assertEquals(95f, group.rules[1].position?.xPercent)
        assertEquals(5f, group.rules[1].position?.yPercent)
        assertNull(group.rules[1].position?.x)
    }

    @Test
    fun `parseMany handles array with isolation`() {
        val text = """
            [
              { id: "a", apps: [{id:"p"}], rules: [{ id: "r", matches: ['[text="x"]'] }] },
              { id: "b" },
              { id: "c", apps: [{id:"q"}], rules: [{ id: "r2", matches: ['[text="z"]'] }] }
            ]
        """.trimIndent()
        val outcome = RuleParser.parseMany(text, "many")
        assertEquals(3, outcome.groups.size)
        assertTrue(outcome.groups.any { it.id == "b" })
        assertTrue(outcome.hasError || outcome.groups.first { it.id == "b" }.rules.isEmpty())
    }

    @Test
    fun `action variants parse`() {
        val text = """
            { id: "g", apps: [{id:"p"}], rules: [
                { id: "l", matches: ['[text="x"]'], action: "longClick" },
                { id: "b", matches: ['[text="y"]'], action: "back" }
            ] }
        """.trimIndent()
        val (group, errors) = RuleParser.parseGroup(text, "t")
        assertNotNull(group)
        assertTrue(errors.isEmpty())
        assertEquals(RuleAction.LONG_CLICK, group.rules[0].action)
        assertEquals(RuleAction.BACK, group.rules[1].action)
    }
}
