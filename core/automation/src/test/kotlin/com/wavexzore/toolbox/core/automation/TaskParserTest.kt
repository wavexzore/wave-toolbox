package com.wavexzore.toolbox.core.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 任务 JSON5 解析与安全上限（内部验证版）。 */
class TaskParserTest {

    @Test
    fun `parses full task with common steps`() {
        val task = TaskParser.parseStrict(
            """
            {
              id: "demo.splash",
              name: "开屏验证",
              pkg: "com.example.splashdemo",
              maxDurationMs: 30000,
              steps: [
                { type: "openApp", pkg: "com.example.splashdemo" },
                { type: "waitFor", selector: '[text*="跳过"]', timeoutMs: 8000 },
                { type: "click", selector: '[text*="跳过"]', retry: 1 },
                { type: "waitActivity", classSuffix: "ContentActivity", timeoutMs: 5000 },
                { type: "readText", selector: '[text*="内容"]', into: "content" },
                { type: "assertText", text: "内容页（广告已跳过）" },
                { type: "scroll", dir: "up", until: { type: "selector", selector: '[text="顶部"]', maxScrolls: 5 } },
                { type: "back", times: 2 },
                { type: "delay", ms: 300 },
              ],
            }
            """.trimIndent(),
            "demo.json5",
        )
        assertEquals("demo.splash", task.id)
        assertEquals("com.example.splashdemo", task.pkg)
        assertEquals(30_000, task.maxDurationMs)
        assertEquals(TaskDef.HARD_MAX_STEPS, TaskSafety.MAX_STEPS)
        assertEquals(9, task.steps.size)
        assertTrue(task.steps[1] is Step.WaitFor)
        assertTrue(task.steps[6] is Step.Scroll)
        assertEquals(ScrollDir.UP, (task.steps[6] as Step.Scroll).dir)
        assertTrue((task.steps[6] as Step.Scroll).until is Until.Selector)
    }

    @Test
    fun `rejects too many steps`() {
        val steps = (1..TaskSafety.MAX_STEPS + 1).joinToString(",") { """{ type: "delay", ms: 1 }""" }
        val outcome = TaskParser.parse(
            """{ id: "t", pkg: "com.x", steps: [$steps] }""",
            "too-many.json5",
        )
        assertTrue(outcome.tasks.isEmpty())
        assertTrue(outcome.errors.any { it.message.contains("步骤数超限") })
    }

    @Test
    fun `rejects invalid selector and package`() {
        val outcome = TaskParser.parse(
            """
            { id: "t", pkg: "not a package", steps: [
              { type: "click", selector: '[text="x"' },
            ] }
            """.trimIndent(),
            "bad.json5",
        )
        assertTrue(outcome.tasks.isEmpty())
        assertTrue(outcome.errors.any { it.message.contains("pkg 非法") })
        assertTrue(outcome.errors.any { it.message.contains("选择器语法错误") })
    }

    @Test
    fun `rejects unsafe ranges`() {
        val outcome = TaskParser.parse(
            """
            { id: "t", pkg: "com.x", maxDurationMs: 999999, steps: [
              { type: "delay", ms: 99999 },
              { type: "click", selector: '[text="x"]', retry: 9, timeoutMs: 99999 },
              { type: "scroll", until: { type: "times", times: 999 } },
            ] }
            """.trimIndent(),
            "unsafe.json5",
        )
        assertTrue(outcome.tasks.isEmpty())
        assertTrue(outcome.errors.any { it.message.contains("maxDurationMs 越界") })
        assertTrue(outcome.errors.any { it.message.contains("ms 越界") })
        assertTrue(outcome.errors.any { it.message.contains("retry 越界") })
        assertTrue(outcome.errors.any { it.message.contains("timeoutMs 越界") })
        assertTrue(outcome.errors.any { it.message.contains("until.times 越界") })
    }

    @Test
    fun `rejects unknown step type`() {
        val outcome = TaskParser.parse(
            """{ id: "t", pkg: "com.x", steps: [ { type: "teleport" } ] }""",
            "unknown.json5",
        )
        assertTrue(outcome.tasks.isEmpty())
        assertTrue(outcome.errors.any { it.message.contains("未知 type") })
    }

    @Test
    fun `strict parse throws on errors`() {
        assertFailsWith<IllegalArgumentException> {
            TaskParser.parseStrict("""{ id: "t", pkg: "com.x", steps: [] }""", "empty.json5")
        }
    }

    @Test
    fun `parses task array and isolates bad item`() {
        val outcome = TaskParser.parse(
            """
            [
              { id: "ok", pkg: "com.x", steps: [ { type: "delay", ms: 1 } ] },
              { id: "bad", pkg: "com.x", steps: [ { type: "nope" } ] },
            ]
            """.trimIndent(),
            "array.json5",
        )
        assertEquals(listOf("ok"), outcome.tasks.map { it.id })
        assertEquals(1, outcome.errors.size)
    }
}
