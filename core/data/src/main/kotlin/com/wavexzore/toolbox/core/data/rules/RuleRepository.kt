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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * 规则仓库（T-006）：assets 内置导入 → Room 持久化（pkg 索引）→ 供引擎构建 [RuleIndex]。
 *
 * 统一存储：通用规则与 adskip 规则包都解析为 [RawRuleGroup]，共用 `rule_groups` / `rule_apps`。
 * 坏规则通过 [RuleParser.Outcome.errors] 隔离并落 `rule_errors` 表。
 */
@Singleton
class RuleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ToolboxDatabase,
) {

    private val loader = BuiltinRuleLoader(context.assets)
    private val writeMutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** 最近一次导入的错误摘要（UI/诊断；避免每次变更都查表）。 */
    @Volatile
    var lastErrors: List<String> = emptyList()
        private set

    /** 导入内置规则（幂等：整源替换；通用 + adskip 一次导入）。 */
    suspend fun importBuiltin(): RuleParser.Outcome = writeMutex.withLock {
        val outcome = loader.loadAll()
        persistLocked(SOURCE_BUILTIN, outcome)
        outcome
    }

    /** 内置源缺失或资产有变更时补导入；无需变更则返回 null（供 adskip 等旁路消费者兜底）。 */
    suspend fun ensureBuiltinImported(): RuleParser.Outcome? = writeMutex.withLock {
        val outcome = loader.loadAll()
        val expected = outcome.groups.map { it.id }.toSet()
        val existing = db.ruleDao().groupIdsBySource(SOURCE_BUILTIN).toSet()
        if (expected.isNotEmpty() && existing.containsAll(expected)) return@withLock null
        persistLocked(SOURCE_BUILTIN, outcome)
        outcome
    }

    /** 导入用户规则文本（单文件/数组/规则包）。 */
    suspend fun importUser(text: String, source: String = SOURCE_USER): RuleParser.Outcome = writeMutex.withLock {
        val outcome = RuleParser.parseMany(text, source)
        persistLocked(source, outcome)
        outcome
    }

    suspend fun currentGroups(): List<RawRuleGroup> =
        db.ruleDao().enabledGroups().mapNotNull { row ->
            runCatching { decode(row.json).copy(source = row.source) }.getOrNull()
        }

    /** 某包启用组（Room 直查；adskip 适配器用它兜底）。 */
    suspend fun groupsForPackage(pkg: String): List<RawRuleGroup> =
        db.ruleDao().groupsForPackage(pkg).mapNotNull { row ->
            runCatching { decode(row.json).copy(source = row.source) }.getOrNull()
        }

    /** 规则表变更流（已解码；供 adskip 适配器等旁路消费者）。 */
    fun observeGroups(): Flow<List<RawRuleGroup>> =
        db.ruleDao().observeEnabledGroups().map { rows ->
            rows.mapNotNull { row ->
                runCatching { decode(row.json).copy(source = row.source) }.getOrNull()
            }
        }

    /** 全部组（含禁用）；用于用户包整包覆盖判定（禁用也算覆盖）。 */
    suspend fun allGroups(): List<RawRuleGroup> =
        db.ruleDao().allGroups().mapNotNull { row ->
            runCatching { decode(row.json).copy(source = row.source) }.getOrNull()
        }

    /** 全部组变更流（含禁用）。 */
    fun observeAllGroups(): Flow<List<RawRuleGroup>> =
        db.ruleDao().observeAllGroups().map { rows ->
            rows.mapNotNull { row ->
                runCatching { decode(row.json).copy(source = row.source) }.getOrNull()
            }
        }

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

    private suspend fun persistLocked(source: String, outcome: RuleParser.Outcome) {
        lastErrors = outcome.errors.map { error -> "[${error.source}] ${error.message}" }
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
            group.apps.map { app -> RuleAppEntity(groupId = group.id, source = source, pkg = app.id) }
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
