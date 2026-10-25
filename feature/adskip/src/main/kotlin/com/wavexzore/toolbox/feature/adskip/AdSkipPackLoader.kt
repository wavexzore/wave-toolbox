package com.wavexzore.toolbox.feature.adskip

import android.util.Log
import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import com.wavexzore.toolbox.core.data.rules.RuleRepository
import com.wavexzore.toolbox.core.rules.model.RawRuleGroup
import com.wavexzore.toolbox.core.rules.parser.RuleParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * adskip 规则视图（统一存储后的薄适配器）。
 *
 * - 唯一事实源：`RuleRepository`（Room，`kind=adskip` 的 [RawRuleGroup]）；
 * - 本类只做「按 pkg 分组 + 排序 + 状态镜像」，不再解析 assets/用户目录；
 * - 内置导入由 `RuleEngine` 或本类启动时兜底触发；
 * - 导入用户规则写入 Room（source=`user:<name>`），Room 失效通知自动刷新。
 */
@Singleton
class AdSkipPackLoader @Inject constructor(
    private val ruleRepository: RuleRepository,
    dispatchers: AppDispatchers,
) : PackGroupsSource {

    data class Pack(
        val pkg: String,
        val groups: List<AdSkipGroup>,
        val source: String,
        val packVersion: Int,
    )

    data class State(
        val packs: Map<String, Pack> = emptyMap(),
        val errors: List<String> = emptyList(),
        val loadedVersion: Int = 0,
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val _state = MutableStateFlow(State())

    /** 装载结果（UI 展示用）。 */
    val state: StateFlow<State> = _state

    init {
        scope.launch {
            ruleRepository.observeAllGroups().collect { groups -> rebuild(groups) }
        }
        // 旁路兜底：RuleEngine 尚未导入时，避免 adskip 首次进入前台无规则可用。
        scope.launch {
            runCatching { ruleRepository.ensureBuiltinImported() }
                .onSuccess { outcome ->
                    if (outcome != null) {
                        Log.i(TAG, "builtin ensured: groups=${outcome.groups.size} errors=${outcome.errors.size}")
                    }
                }
                .onFailure { Log.w(TAG, "ensure builtin failed", it) }
        }
    }

    /** 兼容旧调用：Room 变更会自动推送；这里补一次主动刷新。 */
    fun reload() {
        scope.launch {
            runCatching { ruleRepository.allGroups() }
                .onSuccess(::rebuild)
                .onFailure { Log.w(TAG, "reload failed", it) }
        }
    }

    override fun groupsFor(pkg: String): List<AdSkipGroup> =
        _state.value.packs[pkg]?.groups?.filter { it.enabled }?.sortedBy { it.order } ?: emptyList()

    /** 导入用户规则包（Room source=`user:<name>`；同名覆盖）。 */
    suspend fun importUser(text: String, name: String): RuleParser.Outcome =
        ruleRepository.importUser(text, source = "$SOURCE_USER_PREFIX$name")

    private fun rebuild(groups: List<RawRuleGroup>) {
        val byPkg = LinkedHashMap<String, MutableList<RawRuleGroup>>()
        groups.asSequence()
            .filter { it.kind == RawRuleGroup.KIND_ADSKIP }
            .forEach { group ->
                val pkg = group.apps.firstOrNull()?.id ?: return@forEach
                byPkg.getOrPut(pkg) { ArrayList() }.add(group)
            }
        val packs = LinkedHashMap<String, Pack>()
        byPkg.forEach { (pkg, pkgGroups) ->
            val effective = effectivePackGroups(pkgGroups)
            // 覆盖判定包含禁用组；真正提供给运行时/UI 的只保留启用组。
            val sorted = effective.filter { it.enabled }.sortedWith(compareBy({ it.order }, { it.id }))
            packs[pkg] = Pack(
                pkg = pkg,
                groups = sorted,
                source = effective.map { it.source }.distinct().sorted().joinToString(","),
                packVersion = effective.maxOfOrNull { it.packVersion } ?: 1,
            )
        }
        val next = State(
            packs = packs,
            errors = ruleRepository.lastErrors,
            loadedVersion = _state.value.loadedVersion + 1,
        )
        _state.value = next
        Log.i(TAG, "packs loaded: ${next.packs.size} apps, errors=${next.errors.size}, version=${next.loadedVersion}")
    }

    companion object {
        const val TAG = "WaveAdSkip.Pack"
        private const val SOURCE_USER_PREFIX = RawRuleGroup.SOURCE_USER_PREFIX
    }
}

/**
 * 同 pkg 用户包整包覆盖内置：只要存在 `source=user:<name>` 的组，就不再叠加该 pkg 的内置组。
 * 多份用户包同时存在时全部保留（同名包由 Room 覆盖）。
 */
internal fun effectivePackGroups(groups: List<RawRuleGroup>): List<RawRuleGroup> {
    val userGroups = groups.filter { it.source.startsWith(RawRuleGroup.SOURCE_USER_PREFIX) }
    return if (userGroups.isNotEmpty()) userGroups else groups
}
