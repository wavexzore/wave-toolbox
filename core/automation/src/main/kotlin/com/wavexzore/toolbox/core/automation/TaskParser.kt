package com.wavexzore.toolbox.core.automation

import com.wavexzore.toolbox.core.selector.SelectorParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import li.songe.json5.Json5

/**
 * 任务定义解析（JSON5，内部验证版）。
 *
 * 输入：单个任务对象或任务数组。字段：
 * ```json5
 * { id: "demo", name: "演示", pkg: "com.example", maxDurationMs: 30000,
 *   steps: [ { type: "openApp", pkg: "com.example" }, { type: "delay", ms: 500 } ] }
 * ```
 *
 * 安全上限由 [TaskSafety] 统一约束：超限/未知步骤/非法选择器一律登记错误并丢弃该任务。
 */
object TaskParser {

    data class TaskParseError(
        val source: String,
        val taskId: String?,
        val message: String,
    )

    data class Outcome(
        val tasks: List<TaskDef>,
        val errors: List<TaskParseError>,
    )

    private val packageRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+$")
    private val idRegex = Regex("^[A-Za-z0-9._-]{1,80}$")

    /** 解析一个文件（单任务/任务数组）。 */
    fun parse(text: String, source: String): Outcome {
        val element = try {
            Json5.parseToJsonElement(text)
        } catch (t: Throwable) {
            return Outcome(emptyList(), listOf(TaskParseError(source, null, "JSON5 语法错误: ${t.message}")))
        }
        return when (element) {
            is JsonArray -> {
                val tasks = ArrayList<TaskDef>()
                val errors = ArrayList<TaskParseError>()
                element.forEachIndexed { index, item ->
                    val (task, itemErrors) = parseTaskElement(item, "$source[$index]")
                    task?.let(tasks::add)
                    errors += itemErrors
                }
                Outcome(tasks, errors)
            }
            is JsonObject -> {
                val (task, errors) = parseTaskElement(element, source)
                Outcome(listOfNotNull(task), errors)
            }
            else -> Outcome(emptyList(), listOf(TaskParseError(source, null, "顶层必须是对象或数组")))
        }
    }

    /** 严格解析单个任务；任一致命错误抛 [IllegalArgumentException]。 */
    fun parseStrict(text: String, source: String = "task"): TaskDef {
        val outcome = parse(text, source)
        outcome.errors.firstOrNull()?.let { throw IllegalArgumentException("[${it.source}] ${it.message}") }
        require(outcome.tasks.size == 1) { "期望单个任务，实际 ${outcome.tasks.size} 个" }
        return outcome.tasks.single()
    }

    private fun parseTaskElement(element: JsonElement, source: String): Pair<TaskDef?, List<TaskParseError>> {
        val obj = element as? JsonObject
            ?: return null to listOf(TaskParseError(source, null, "任务必须是对象"))
        val errors = ArrayList<TaskParseError>()
        val id = obj.str("id").orEmpty().trim()
        if (!idRegex.matches(id)) errors += TaskParseError(source, id.ifBlank { null }, "任务 id 缺失或非法（允许字母/数字/._-）")

        val pkg = obj.str("pkg").orEmpty().trim()
        if (!packageRegex.matches(pkg)) errors += TaskParseError(source, id, "任务 pkg 非法: '$pkg'")

        val name = obj.str("name").orEmpty().trim()
        if (name.length > 100) errors += TaskParseError(source, id, "任务 name 过长（>100）")

        val maxDuration = obj.long("maxDurationMs") ?: TaskDef.DEFAULT_MAX_DURATION_MS
        if (maxDuration !in TaskDef.MIN_DURATION_MS..TaskDef.HARD_MAX_DURATION_MS) {
            errors += TaskParseError(
                source,
                id,
                "maxDurationMs 越界: $maxDuration（${TaskDef.MIN_DURATION_MS}..${TaskDef.HARD_MAX_DURATION_MS}）",
            )
        }

        val stepsElement = obj["steps"] as? JsonArray
        if (stepsElement == null) {
            errors += TaskParseError(source, id, "缺少 steps 数组")
        } else if (stepsElement.isEmpty()) {
            errors += TaskParseError(source, id, "steps 不能为空")
        } else if (stepsElement.size > TaskSafety.MAX_STEPS) {
            errors += TaskParseError(source, id, "步骤数超限: ${stepsElement.size} > ${TaskSafety.MAX_STEPS}")
        }

        val steps = ArrayList<Step>()
        stepsElement?.forEachIndexed { index, stepElement ->
            val (step, stepErrors) = parseStep(stepElement, source, index)
            step?.let(steps::add)
            errors += stepErrors
        }
        if (errors.isNotEmpty()) return null to errors
        return TaskDef(
            id = id,
            name = name,
            pkg = pkg,
            maxDurationMs = maxDuration,
            steps = steps,
        ) to emptyList()
    }

    private fun parseStep(element: JsonElement, source: String, index: Int): Pair<Step?, List<TaskParseError>> {
        val obj = element as? JsonObject
            ?: return null to listOf(TaskParseError(source, null, "step#$index 必须是对象"))
        val errors = ArrayList<TaskParseError>()
        val where = "step#$index"
        val type = obj.str("type").orEmpty().lowercase()
        val step: Step? = when (type) {
            "waitfor" -> {
                val selector = obj.requireSelector(errors, source, where, "selector")
                val policy = obj.policy(errors, source, where, FailPolicy.ABORT)
                val timeout = obj.timeout(errors, source, where, 5_000)
                selector?.let { Step.WaitFor(it, timeout, policy) }
            }

            "click" -> {
                val selector = obj.requireSelector(errors, source, where, "selector")
                val retry = obj.int("retry") ?: 2
                if (retry !in 0..TaskSafety.MAX_CLICK_RETRY) {
                    errors += TaskParseError(source, null, "$where retry 越界: $retry（0..${TaskSafety.MAX_CLICK_RETRY}）")
                }
                val policy = obj.policy(errors, source, where, FailPolicy.ABORT)
                val timeout = obj.timeout(errors, source, where, 5_000)
                val optional = obj.bool("optional") ?: false
                selector?.let { Step.Click(it, retry, optional, timeout, policy) }
            }

            "waitactivity" -> {
                val suffix = obj.str("classSuffix").orEmpty().trim()
                if (suffix.isBlank()) errors += TaskParseError(source, null, "$where classSuffix 为空")
                val policy = obj.policy(errors, source, where, FailPolicy.ABORT)
                val timeout = obj.timeout(errors, source, where, 10_000)
                if (suffix.isNotBlank()) Step.WaitActivity(suffix, timeout, policy) else null
            }

            "back" -> {
                val times = obj.int("times") ?: 1
                if (times !in 1..TaskSafety.MAX_BACK_TIMES) {
                    errors += TaskParseError(source, null, "$where times 越界: $times（1..${TaskSafety.MAX_BACK_TIMES}）")
                }
                val policy = obj.policy(errors, source, where, FailPolicy.SKIP)
                val timeout = obj.timeout(errors, source, where, 3_000)
                Step.Back(times, timeout, policy)
            }

            "scroll" -> {
                val dirText = obj.str("dir").orEmpty().lowercase()
                val dir = when (dirText) {
                    "", "down" -> ScrollDir.DOWN
                    "up" -> ScrollDir.UP
                    else -> {
                        errors += TaskParseError(source, null, "$where dir 非法: '$dirText'")
                        ScrollDir.DOWN
                    }
                }
                val until = obj.parseUntil(errors, source, where)
                val policy = obj.policy(errors, source, where, FailPolicy.SKIP)
                val timeout = obj.timeout(errors, source, where, 8_000)
                Step.Scroll(dir, until, timeout, policy)
            }

            "readtext" -> {
                val selector = obj.requireSelector(errors, source, where, "selector")
                val into = obj.str("into").orEmpty().trim()
                if (into.isBlank()) errors += TaskParseError(source, null, "$where into 为空")
                val policy = obj.policy(errors, source, where, FailPolicy.ABORT)
                val timeout = obj.timeout(errors, source, where, 3_000)
                val optional = obj.bool("optional") ?: false
                if (selector != null && into.isNotBlank()) {
                    Step.ReadText(selector, into, optional, timeout, policy)
                } else {
                    null
                }
            }

            "asserttext" -> {
                val text = obj.str("text").orEmpty()
                if (text.isBlank()) errors += TaskParseError(source, null, "$where text 为空")
                val policy = obj.policy(errors, source, where, FailPolicy.ABORT)
                val timeout = obj.timeout(errors, source, where, 3_000)
                if (text.isNotBlank()) Step.AssertText(text, timeout, policy) else null
            }

            "delay" -> {
                val ms = obj.long("ms") ?: 1_000L
                if (ms !in 0..TaskSafety.MAX_DELAY_MS) {
                    errors += TaskParseError(source, null, "$where ms 越界: $ms（0..${TaskSafety.MAX_DELAY_MS}）")
                }
                val policy = obj.policy(errors, source, where, FailPolicy.ABORT)
                Step.Delay(ms, policy)
            }

            "openapp" -> {
                val target = obj.str("pkg").orEmpty().trim()
                if (!packageRegex.matches(target)) {
                    errors += TaskParseError(source, null, "$where pkg 非法: '$target'")
                }
                val policy = obj.policy(errors, source, where, FailPolicy.ABORT)
                val timeout = obj.timeout(errors, source, where, 5_000)
                if (packageRegex.matches(target)) Step.OpenApp(target, timeout, policy) else null
            }

            "" -> {
                errors += TaskParseError(source, null, "$where 缺少 type")
                null
            }

            else -> {
                errors += TaskParseError(source, null, "$where 未知 type: '$type'")
                null
            }
        }
        return step to errors
    }

    private fun JsonObject.parseUntil(
        errors: MutableList<TaskParseError>,
        source: String,
        where: String,
    ): Until {
        val element = this["until"]
        if (element == null) return Until.Times(1)
        val obj = element as? JsonObject
            ?: run {
                errors += TaskParseError(source, null, "$where until 必须是对象")
                return Until.Times(1)
            }
        return when (val type = obj.str("type").orEmpty().lowercase()) {
            "", "times" -> {
                val times = obj.int("times") ?: 1
                if (times !in 1..TaskSafety.MAX_SCROLL_TIMES) {
                    errors += TaskParseError(source, null, "$where until.times 越界: $times（1..${TaskSafety.MAX_SCROLL_TIMES}）")
                }
                Until.Times(times)
            }

            "selector" -> {
                val selector = obj.requireSelector(errors, source, where, "selector")
                val maxScrolls = obj.int("maxScrolls") ?: 10
                if (maxScrolls !in 1..TaskSafety.MAX_SCROLL_TIMES) {
                    errors += TaskParseError(source, null, "$where until.maxScrolls 越界: $maxScrolls（1..${TaskSafety.MAX_SCROLL_TIMES}）")
                }
                if (selector != null) Until.Selector(selector, maxScrolls) else Until.Stable
            }

            "stable" -> Until.Stable

            else -> {
                errors += TaskParseError(source, null, "$where until.type 未知: '$type'")
                Until.Stable
            }
        }
    }

    private fun JsonObject.requireSelector(
        errors: MutableList<TaskParseError>,
        source: String,
        where: String,
        key: String,
    ): String? {
        val selector = str(key).orEmpty().trim()
        if (selector.isBlank()) {
            errors += TaskParseError(source, null, "$where $key 为空")
            return null
        }
        val result = SelectorParser.parse(selector)
        if (result is SelectorParser.ParseResult.Error) {
            errors += TaskParseError(source, null, "$where 选择器语法错误 '$selector' @${result.position}: ${result.message}")
            return null
        }
        return selector
    }

    private fun JsonObject.policy(
        errors: MutableList<TaskParseError>,
        source: String,
        where: String,
        default: FailPolicy,
    ): FailPolicy = when (val text = str("failPolicy").orEmpty().lowercase()) {
        "" -> default
        "abort" -> FailPolicy.ABORT
        "skip" -> FailPolicy.SKIP
        "continue" -> FailPolicy.CONTINUE
        else -> {
            errors += TaskParseError(source, null, "$where failPolicy 未知: '$text'")
            default
        }
    }

    private fun JsonObject.timeout(
        errors: MutableList<TaskParseError>,
        source: String,
        where: String,
        default: Long,
    ): Long {
        val timeout = long("timeoutMs") ?: default
        if (timeout !in TaskSafety.MIN_STEP_TIMEOUT_MS..TaskSafety.MAX_STEP_TIMEOUT_MS) {
            errors += TaskParseError(
                source,
                null,
                "$where timeoutMs 越界: $timeout（${TaskSafety.MIN_STEP_TIMEOUT_MS}..${TaskSafety.MAX_STEP_TIMEOUT_MS}）",
            )
        }
        return timeout
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
}
