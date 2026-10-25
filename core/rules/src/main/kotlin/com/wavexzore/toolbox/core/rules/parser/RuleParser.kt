package com.wavexzore.toolbox.core.rules.parser

import com.wavexzore.toolbox.core.rules.model.RawApp
import com.wavexzore.toolbox.core.rules.model.RawRuleGroup
import com.wavexzore.toolbox.core.rules.model.RawRulePack
import com.wavexzore.toolbox.core.rules.model.RuleAction
import com.wavexzore.toolbox.core.selector.SelectorParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import li.songe.json5.Json5

/**
 * 统一规则解析（JSON5）：通用规则组 + adskip 规则包共用 [RawRuleGroup]。
 *
 * 输入形态：
 * - 通用组：`{ id, apps, rules, ... }` 或组数组；
 * - 规则包：`{ schemaVersion, pkg, packVersion, groups: [...] }`。
 *
 * 校验：id 非空、schemaVersion 可解析、选择器语法可解析、actionCd/actionMaximum 合法；
 * adskip 组额外校验 mode/zone/settleMs/path。坏组隔离并登记错误（不中断整批）。
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

    /** 解析单个通用组；组级致命错误时返回 (null, errors)。规则包请用 [parseDocument]。 */
    fun parseGroup(text: String, source: String): Pair<RawRuleGroup?, List<RuleError>> {
        val outcome = parseDocument(text, source)
        return outcome.groups.firstOrNull() to outcome.errors
    }

    /** 解析一批（数组 / 单组 / 单包）。 */
    fun parseMany(text: String, source: String): Outcome = parseDocument(text, source)

    /** 统一入口：按字段自动识别通用组或规则包。 */
    fun parseDocument(text: String, source: String): Outcome {
        val element = try {
            Json5.parseToJsonElement(text)
        } catch (t: Throwable) {
            return Outcome(emptyList(), listOf(RuleError(source, null, null, "JSON5 语法错误: ${t.message}", fatal = true)))
        }
        return when (element) {
            is JsonArray -> {
                val groups = ArrayList<RawRuleGroup>()
                val errors = ArrayList<RuleError>()
                element.forEachIndexed { index, item ->
                    val outcome = parseObject(item, "$source[$index]")
                    groups += outcome.groups
                    errors += outcome.errors
                }
                Outcome(groups, errors)
            }
            is JsonObject -> parseObject(element, source)
            else -> Outcome(emptyList(), listOf(RuleError(source, null, null, "顶层必须是对象或数组", fatal = true)))
        }
    }

    /**
     * 严格解析规则包（导入/导出/单测用）；任一致命错误抛 [IllegalArgumentException]。
     * 返回的 groups 已补齐 `kind=adskip`、`apps=[pkg]`、`packVersion`。
     */
    fun parsePackStrict(text: String, source: String = "pack"): RawRulePack {
        val element = try {
            Json5.parseToJsonElement(text)
        } catch (t: Throwable) {
            throw IllegalArgumentException("JSON5 语法错误: ${t.message}", t)
        }
        require(element is JsonObject) { "规则包顶层必须是对象" }
        val (pack, errors) = parsePackObject(element, source)
        errors.firstOrNull { it.fatal }?.let { throw IllegalArgumentException(it.message) }
        return pack ?: throw IllegalArgumentException("规则包解析失败")
    }

    private fun parseObject(element: JsonElement, source: String): Outcome {
        if (element !is JsonObject) {
            return Outcome(emptyList(), listOf(RuleError(source, null, null, "顶层必须是对象", fatal = true)))
        }
        // 规则包：pkg + groups；兼容只写 groups 的旧文件（pkg 由组内 apps 推断）。
        if (element.containsKey("groups") || (element.containsKey("pkg") && !element.containsKey("rules"))) {
            val (pack, errors) = parsePackObject(element, source)
            return Outcome(pack?.groups.orEmpty(), errors)
        }
        return parseGenericGroup(element, source)
    }

    private fun parseGenericGroup(element: JsonObject, source: String): Outcome {
        val group = try {
            lenientJson.decodeFromJsonElement(RawRuleGroup.serializer(), element)
        } catch (t: Throwable) {
            return Outcome(emptyList(), listOf(RuleError(source, null, null, "字段解码失败: ${t.message}", fatal = true)))
        }
        val errors = validate(source, group)
        return outcomeOf(listOf(group), errors)
    }

    private fun parsePackObject(element: JsonObject, source: String): Pair<RawRulePack?, List<RuleError>> {
        val pack = try {
            lenientJson.decodeFromJsonElement(RawRulePack.serializer(), element)
        } catch (t: Throwable) {
            return null to listOf(RuleError(source, null, null, "规则包字段解码失败: ${t.message}", fatal = true))
        }
        val errors = ArrayList<RuleError>()
        if (pack.pkg.isBlank()) {
            errors += RuleError(source, null, null, "规则包 pkg 为空", fatal = true)
        }
        if (pack.schemaVersion > RawRulePack.CURRENT_SCHEMA_VERSION) {
            errors += RuleError(
                source,
                null,
                null,
                "规则包 schemaVersion=${pack.schemaVersion} 高于支持值 ${RawRulePack.CURRENT_SCHEMA_VERSION}",
                fatal = true,
            )
        }
        if (errors.any { it.fatal }) return null to errors

        val mapped = pack.groups.map { group ->
            group.copy(
                kind = RawRuleGroup.KIND_ADSKIP,
                apps = listOf(RawApp(id = pack.pkg)),
                packVersion = pack.packVersion,
                schemaVersion = pack.schemaVersion,
            )
        }
        mapped.forEach { group -> errors += validate(source, group) }
        val kept = filterFatal(mapped, errors)
        return pack.copy(groups = kept) to errors
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
        if (group.kind == RawRuleGroup.KIND_ADSKIP) validateAdSkip(source, group, errors)
        validateGroupMatchers(source, group, errors)
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

    private fun validateAdSkip(source: String, group: RawRuleGroup, errors: MutableList<RuleError>) {
        if (group.mode != RawRuleGroup.MODE_SPLASH && group.mode != RawRuleGroup.MODE_ANY) {
            errors += RuleError(source, group.id, null, "未知 mode=${group.mode}", fatal = true)
        }
        if (group.zone != null && group.zone !in RawRuleGroup.VALID_ZONES) {
            errors += RuleError(source, group.id, null, "未知 zone=${group.zone}", fatal = true)
        }
        if (group.settleMs !in 0..2000) {
            errors += RuleError(source, group.id, null, "settleMs 越界=${group.settleMs}", fatal = true)
        }
        if (group.actionCd < 0) errors += RuleError(source, group.id, null, "actionCd 为负")
        if (group.actionMaximum <= 0) errors += RuleError(source, group.id, null, "actionMaximum 必须为正")
        if (group.delayMs < 0) errors += RuleError(source, group.id, null, "delayMs 为负")
        group.path?.let { path ->
            val valid = path.isNotBlank() && path.split('/').all { part -> part.isNotEmpty() && part.toIntOrNull() != null }
            if (!valid) errors += RuleError(source, group.id, null, "path 必须是 0/2/1 形式")
        }
    }

    private fun validateGroupMatchers(source: String, group: RawRuleGroup, errors: MutableList<RuleError>) {
        // keywords 是文本片段，不走选择器语法；只有组级 matches 需要校验。
        group.matches.distinct().forEach { selector ->
            val result = SelectorParser.parse(selector)
            if (result is SelectorParser.ParseResult.Error) {
                errors += RuleError(
                    source,
                    group.id,
                    null,
                    "组选择器语法错误 '$selector' @${result.position}: ${result.message}",
                )
            }
        }
    }

    private fun outcomeOf(groups: List<RawRuleGroup>, errors: List<RuleError>): Outcome =
        Outcome(filterFatal(groups, errors), errors)

    private fun filterFatal(groups: List<RawRuleGroup>, errors: List<RuleError>): List<RawRuleGroup> {
        if (errors.any { it.fatal && it.groupId == null }) return emptyList()
        val fatalIds = errors.filter { it.fatal }.mapNotNull { it.groupId }.toSet()
        return groups.filter { it.id !in fatalIds }
    }
}
