package com.wavexzore.toolbox.core.rules.parser

import com.wavexzore.toolbox.core.rules.model.RawRuleGroup
import com.wavexzore.toolbox.core.rules.model.RuleAction
import com.wavexzore.toolbox.core.selector.SelectorParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import li.songe.json5.Json5

/**
 * 规则解析（JSON5）：坏规则隔离并登记错误（不中断整批加载）。
 *
 * - 输入：单个组对象，或组对象数组；
 * - 校验：id 非空、schemaVersion 可解析、选择器语法可解析、actionCd/actionMaximum 合法；
 * - [RuleError.fatal] 标记组级致命错误（该组不可加载），其余为可放行警告；
 * - 输出 [Outcome]：可加载组 + 错误列表（含来源/组/规则定位）。
 */
object RuleParser {

    data class RuleError(
        val source: String,
        val groupId: String?,
        val ruleId: String?,
        val message: String,
        val fatal: Boolean = false,
    )

    data class Outcome(
        val groups: List<RawRuleGroup>,
        val errors: List<RuleError>,
    ) {
        val hasError: Boolean get() = errors.isNotEmpty()
    }

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    /** 解析单个组；组级致命错误时返回 (null, errors)。 */
    fun parseGroup(text: String, source: String): Pair<RawRuleGroup?, List<RuleError>> {
        val element = try {
            Json5.parseToJsonElement(text)
        } catch (t: Throwable) {
            return null to listOf(RuleError(source, null, null, "JSON5 语法错误: ${t.message}", fatal = true))
        }
        return parseElement(element, source)
    }

    /** 解析一批（数组或单对象）。 */
    fun parseMany(text: String, source: String): Outcome {
        val element = try {
            Json5.parseToJsonElement(text)
        } catch (t: Throwable) {
            return Outcome(emptyList(), listOf(RuleError(source, null, null, "JSON5 语法错误: ${t.message}", fatal = true)))
        }
        val groups = ArrayList<RawRuleGroup>()
        val errors = ArrayList<RuleError>()
        when (element) {
            is JsonArray -> element.forEachIndexed { index, item ->
                val (group, groupErrors) = parseElement(item, "$source[$index]")
                group?.let(groups::add)
                errors += groupErrors
            }
            is JsonObject -> {
                val (group, groupErrors) = parseElement(element, source)
                group?.let(groups::add)
                errors += groupErrors
            }
            else -> errors += RuleError(source, null, null, "顶层必须是对象或数组", fatal = true)
        }
        return Outcome(groups, errors)
    }

    private fun parseElement(element: JsonElement, source: String): Pair<RawRuleGroup?, List<RuleError>> {
        if (element !is JsonObject) {
            return null to listOf(RuleError(source, null, null, "顶层必须是对象", fatal = true))
        }
        val group = try {
            lenientJson.decodeFromJsonElement(RawRuleGroup.serializer(), element)
        } catch (t: Throwable) {
            return null to listOf(RuleError(source, null, null, "字段解码失败: ${t.message}", fatal = true))
        }
        val errors = validate(source, group)
        return if (errors.none { it.fatal }) group to errors else null to errors
    }

    private fun validate(source: String, group: RawRuleGroup): List<RuleError> {
        val errors = ArrayList<RuleError>()
        if (group.id.isBlank()) {
            errors += RuleError(source, group.id, null, "组 id 为空", fatal = true)
        }
        if (group.schemaVersion > RawRuleGroup.CURRENT_SCHEMA_VERSION) {
            errors += RuleError(
                source,
                group.id,
                null,
                "schemaVersion=${group.schemaVersion} 高于支持值 ${RawRuleGroup.CURRENT_SCHEMA_VERSION}",
                fatal = true,
            )
        }
        group.apps.forEach { app ->
            if (app.id.isBlank()) errors += RuleError(source, group.id, null, "apps 存在空 pkg")
        }
        group.rules.forEach { rule ->
            if (rule.id.isBlank()) errors += RuleError(source, group.id, rule.id, "规则 id 为空")
            if (rule.matches.isEmpty() && rule.anyMatches.isEmpty()) {
                errors += RuleError(source, group.id, rule.id, "matches 与 anyMatches 均为空")
            }
            (rule.matches + rule.anyMatches + rule.excludeMatches).distinct().forEach { selector ->
                val result = SelectorParser.parse(selector)
                if (result is SelectorParser.ParseResult.Error) {
                    errors += RuleError(
                        source,
                        group.id,
                        rule.id,
                        "选择器语法错误 '$selector' @${result.position}: ${result.message}",
                    )
                }
            }
            if (rule.actionCd < 0) errors += RuleError(source, group.id, rule.id, "actionCd 为负")
            if (rule.actionMaximum != null && rule.actionMaximum <= 0) {
                errors += RuleError(source, group.id, rule.id, "actionMaximum 必须为正")
            }
            if (rule.action == RuleAction.POSITION && rule.position == null) {
                errors += RuleError(source, group.id, rule.id, "action=position 但未提供 position")
            }
        }
        return errors
    }
}
