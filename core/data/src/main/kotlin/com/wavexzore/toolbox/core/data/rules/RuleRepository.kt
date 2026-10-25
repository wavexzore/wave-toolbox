package com.wavexzore.toolbox.core.data.rules

import android.content.Context
import com.wavexzore.toolbox.core.rules.index.RuleIndex
import com.wavexzore.toolbox.core.rules.model.RawRuleGroup
import com.wavexzore.toolbox.core.rules.parser.RuleParser
import com.wavexzore.toolbox.core.rules.source.BuiltinRuleLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * 规则仓库（T-006）：assets 内置导入 → Room 持久化（pkg 索引）→ 供引擎构建 [RuleIndex]。
 * 坏规则通过 [RuleParser.Outcome.errors] 隔离并落 `rule_errors` 表。
 */
@Singleton
class RuleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ToolboxDatabase,
) {

    private val loader = BuiltinRuleLoader(context.assets)

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** 导入内置规则（幂等：整源替换）。 */
    suspend fun importBuiltin(): RuleParser.Outcome {
        val outcome = loader.loadAll()
        persist(SOURCE_BUILTIN, outcome)
        return outcome
    }

    /** 导入用户规则文本（单文件/数组）。 */
    suspend fun importUser(text: String, source: String = SOURCE_USER): RuleParser.Outcome {
        val outcome = RuleParser.parseMany(text, source)
        persist(source, outcome)
        return outcome
    }

    suspend fun currentGroups(): List<RawRuleGroup> =
        db.ruleDao().enabledGroups().mapNotNull { runCatching { decode(it.json) }.getOrNull() }

    /** 内置规则源是否已导入（全部禁用也算已导入，不能再用 enabled 列表为空判断）。 */
    suspend fun hasBuiltinSource(): Boolean = db.ruleDao().countBySource(SOURCE_BUILTIN) > 0

    suspend fun buildIndex(): RuleIndex = RuleIndex.build(currentGroups())

    fun observePackages(): Flow<Set<String>> =
        db.ruleDao().observePackages().map { it.toSet() }

    fun observeGroupCount(): Flow<Int> =
        db.ruleDao().observeEnabledGroups().map { it.size }

    /**
     * 规则表变更信号（每次 enabled 组数据变化触发一次）。
     * 引擎据此丢弃旧 [RuleIndex]，下一次事件再重建；无需周期全表扫描。
     */
    fun observeIndexRevision(): Flow<Unit> =
        db.ruleDao().observeEnabledGroups().map { Unit }

    suspend fun recentErrors(): List<RuleErrorEntity> = db.ruleDao().recentErrors()

    private suspend fun persist(source: String, outcome: RuleParser.Outcome) {
        val now = System.currentTimeMillis()
        val groups = outcome.groups.map { group ->
            RuleGroupEntity(
                id = group.id,
                name = group.name,
                order = group.order,
                enabled = group.enabled,
                schemaVersion = group.schemaVersion,
                source = source,
                json = json.encodeToString(RawRuleGroup.serializer(), group),
                updatedAt = now,
            )
        }
        val apps = outcome.groups.flatMap { group ->
            group.apps.map { app -> RuleAppEntity(group.id, app.id) }
        }
        val errors = outcome.errors.map { error ->
            RuleErrorEntity(
                source = error.source,
                groupId = error.groupId,
                ruleId = error.ruleId,
                message = error.message,
                fatal = error.fatal,
                createdAt = now,
            )
        }
        db.ruleDao().replaceSource(source, groups, apps)
        if (errors.isNotEmpty()) db.ruleDao().insertErrors(errors)
    }

    private fun decode(groupJson: String): RawRuleGroup =
        json.decodeFromString(RawRuleGroup.serializer(), groupJson)

    companion object {
        const val SOURCE_BUILTIN = "builtin"
        const val SOURCE_USER = "user"
    }
}
