package com.wavexzore.toolbox.feature.adskip

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wavexzore.toolbox.core.accessibility.action.ActionExecutor
import com.wavexzore.toolbox.core.accessibility.action.AndroidNodeView
import com.wavexzore.toolbox.core.accessibility.action.WindowTreeProvider
import com.wavexzore.toolbox.core.data.stats.StatsRepository
import com.wavexzore.toolbox.core.feature.api.FeatureEvent
import com.wavexzore.toolbox.core.feature.api.ToolFeature
import com.wavexzore.toolbox.core.rules.model.RawRule
import com.wavexzore.toolbox.core.rules.model.RuleAction
import com.wavexzore.toolbox.core.rules.model.RulePosition
import com.wavexzore.toolbox.core.selector.NodeView
import com.wavexzore.toolbox.core.selector.SelectorEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 开屏广告自动跳过特性（T-002/T-004/T-005/T-006/T-007）。
 *
 * 链路：窗口/内容事件 → 作用域检查（总开关由注册表过滤、单 App 名单、mode 窗口期）→
 * 关键词策略（Y 最小 + 顶部优先）→ 规则包 `matches` → `path` → `position` 兜底 →
 * [ActionExecutor] 点击（performAction→祖先→坐标）→ 冷却/限次/统计/日志。
 *
 * 预算：单次事件最多 3 次尝试、间隔 200ms；年轻窗口首屏 CONTENT 事件延迟 150ms。
 */
@Singleton
class AdSkipFeature @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: AdSkipConfig,
    private val loader: AdSkipPackLoader,
    private val treeProvider: WindowTreeProvider,
    private val executor: ActionExecutor,
    private val statsRepository: StatsRepository,
) : ToolFeature {

    override val id: String = "adskip"
    override val name: String = "开屏广告自动跳过"
    override val description: String = "识别开屏/弹窗中的跳过与关闭按钮并自动点击"
    override val order: Int = 10

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val selectorEngine = SelectorEngine()

    @Volatile
    private var mode: String = AdSkipConfig.DEFAULT_MODE

    @Volatile
    private var extraKeywords: List<String> = emptyList()

    @Volatile
    private var disabledPackages: Set<String> = emptySet()

    private data class WindowMark(val activity: String?, val at: Long, val instance: Long)

    private val windows = ConcurrentHashMap<String, WindowMark>()
    private val instanceSeq = AtomicLong()
    private val perInstanceCount = ConcurrentHashMap<String, Int>()
    private val lastClickAt = ConcurrentHashMap<String, Long>()
    private val firstContentSeen = ConcurrentHashMap<String, Boolean>()
    private val inFlight = ConcurrentHashMap<String, Boolean>()
    private val missLogged = ConcurrentHashMap.newKeySet<String>()

    private val _eventCount = MutableStateFlow(0L)

    /** 累计派发事件（设置页展示）。 */
    val eventCount: StateFlow<Long> = _eventCount

    private val _skipCount = MutableStateFlow(0L)

    /** 本次运行成功跳过次数。 */
    val skipCount: StateFlow<Long> = _skipCount

    private var lastSkipSummary: String? = null

    /** 最近一次跳过摘要（诊断）。 */
    fun lastSkip(): String? = lastSkipSummary

    init {
        scope.launch { config.mode.collect { mode = it } }
        scope.launch { config.extraKeywords.collect { extraKeywords = it.toList() } }
        scope.launch { config.disabledPackages.collect { disabledPackages = it } }
    }

    override fun onEvent(event: FeatureEvent) {
        _eventCount.value = _eventCount.value + 1
        val pkg = event.packageName ?: return
        if (pkg == context.packageName) return
        val now = System.currentTimeMillis()

        if (event.type == TYPE_WINDOW_STATE_CHANGED) {
            val activity = event.className
            val prev = windows[pkg]
            if (activity != null && activity != prev?.activity) {
                windows[pkg] = WindowMark(activity, now, instanceSeq.incrementAndGet())
            } else if (prev != null) {
                windows[pkg] = prev.copy(at = now)
            }
        }
        val mark = windows[pkg] ?: WindowMark(null, now, instanceSeq.incrementAndGet()).also { windows[pkg] = it }
        val instanceKey = "$pkg#${mark.instance}"

        if (pkg in disabledPackages) return

        // mode=splash：仅在窗口切换后的 splash 窗口期内动作
        val splashActive = mode == AdSkipGroup.MODE_ANY ||
            (mark.activity != null && now - mark.at <= SPLASH_WINDOW_MS)
        if (!splashActive) return

        val lastClick = lastClickAt[pkg] ?: 0L
        if (now - lastClick < CLICK_COOLDOWN_MS) return
        if (inFlight.putIfAbsent(instanceKey, true) != null) return

        val isFirstContent = event.type == TYPE_CONTENT_CHANGED && firstContentSeen.putIfAbsent(instanceKey, true) == null
        val delayMs = if (isFirstContent) YOUNG_DELAY_MS else 0L

        scope.launch {
            try {
                if (delayMs > 0) delay(delayMs)
                attempt(pkg, instanceKey, mark)
            } finally {
                inFlight.remove(instanceKey)
            }
        }
    }

    private suspend fun attempt(pkg: String, instanceKey: String, mark: WindowMark) {
        val groups = loader.groupsFor(pkg).filter { groupActive(it, mark.activity) }
        val maxPerInstance = groups.maxOfOrNull { it.actionMaximum } ?: if (mode == AdSkipGroup.MODE_ANY) 2 else 1
        repeat(MAX_ATTEMPTS) { index ->
            if ((perInstanceCount[instanceKey] ?: 0) >= maxPerInstance) return
            val outcome = tryOnce(pkg, groups, mark)
            if (outcome != null) {
                if (outcome.ok) {
                    perInstanceCount.merge(instanceKey, 1, Int::plus)
                    lastClickAt[pkg] = System.currentTimeMillis()
                    _skipCount.value = _skipCount.value + 1
                    lastSkipSummary = "${System.currentTimeMillis()} pkg=$pkg src=${outcome.source} mech=${outcome.mechanism} cls=${mark.activity?.substringAfterLast('.')}"
                }
                return
            }
            if (index < MAX_ATTEMPTS - 1) delay(ATTEMPT_INTERVAL_MS)
        }
        if (missLogged.add(instanceKey)) {
            val probe = treeProvider.currentTree(pkg = pkg)
            Log.d(
                TAG,
                "no-candidate pkg=$pkg act=${mark.activity?.substringAfterLast('.')} " +
                    "nodes=${probe?.tree?.nodeCount} root=[${probe?.tree?.root?.let { "${it.className} ${it.bounds}" }}]",
            )
        }
    }

    private data class Outcome(val ok: Boolean, val mechanism: String?, val source: String)

    private suspend fun tryOnce(pkg: String, groups: List<AdSkipGroup>, mark: WindowMark): Outcome? {
        val picked = treeProvider.currentTree(pkg = pkg) ?: return null
        val tree = picked.tree
        val now = System.currentTimeMillis()

        // 1) 关键词（默认 + 用户 + 规则包追加）
        val groupKeywords = groups.flatMap { it.keywords }
        val keywordPick = KeywordStrategy.pick(
            root = tree.root,
            extraKeywords = extraKeywords + groupKeywords,
            screenWidth = tree.screenWidth,
            screenHeight = tree.screenHeight,
        )
        if (keywordPick != null) {
            val result = click(keywordPick.node, tree.screenWidth, tree.screenHeight)
            record(pkg, "keyword:${keywordPick.matched}", result, now)
            return Outcome(result.ok, result.mechanism, "keyword:${keywordPick.matched}")
        }

        // 2) 规则包 matches（选择器）
        val matchCandidates = ArrayList<KeywordStrategy.Pick>(4)
        for (group in groups) {
            for (selector in group.matches) {
                tree.root.let { root ->
                    selectorEngine.findAll(root, selector).forEach { match ->
                        matchCandidates += KeywordStrategy.Pick(match.node, selector, "match")
                    }
                }
            }
        }
        KeywordStrategy.bestOf(matchCandidates, tree.screenHeight)?.let { pick ->
            val result = click(pick.node, tree.screenWidth, tree.screenHeight)
            record(pkg, "match:${pick.matched}", result, now)
            return Outcome(result.ok, result.mechanism, "match")
        }

        // 3) path 兜底
        for (group in groups) {
            val path = group.path ?: continue
            val node = PathResolver.resolve(tree.root, path) ?: continue
            val result = click(node, tree.screenWidth, tree.screenHeight)
            record(pkg, "path:${group.id}", result, now)
            return Outcome(result.ok, result.mechanism, "path")
        }

        // 4) position 兜底
        for (group in groups) {
            val position = group.position ?: continue
            val rule = RawRule(
                id = "adskip@position:${group.id}",
                action = RuleAction.POSITION,
                position = RulePosition(
                    x = position.x,
                    y = position.y,
                    xPercent = position.xPercent,
                    yPercent = position.yPercent,
                ),
            )
            val result = executor.execute(rule, null, tree.screenWidth, tree.screenHeight)
            record(pkg, "position:${group.id}", result, now)
            return Outcome(result.ok, result.mechanism, "position")
        }
        return null
    }

    private suspend fun click(node: NodeView, screenWidth: Int, screenHeight: Int): ActionExecutor.Result =
        executor.execute(
            RawRule(id = "adskip@click", action = RuleAction.CLICK),
            node as? AndroidNodeView,
            screenWidth,
            screenHeight,
        )

    private fun record(pkg: String, source: String, result: ActionExecutor.Result, now: Long) {
        statsRepository.record(
            featureId = id,
            pkg = pkg,
            ruleId = source,
            kind = "SKIP",
            ok = result.ok,
            mechanism = result.mechanism,
            durationMs = System.currentTimeMillis() - now,
        )
        Log.i(TAG, "skip pkg=$pkg src=$source ok=${result.ok} mech=${result.mechanism}")
    }

    private fun groupActive(group: AdSkipGroup, activity: String?): Boolean {
        if (mode == AdSkipGroup.MODE_SPLASH && group.mode != AdSkipGroup.MODE_SPLASH) return false
        if (activity != null && group.excludeActivityIds.any { activityMatches(it, activity) }) return false
        return true
    }

    private fun activityMatches(pattern: String, activity: String): Boolean =
        activity == pattern || activity.endsWith(pattern) || activity.startsWith(pattern)

    /** 设置面板（SPI）。 */
    @Composable
    override fun SettingsPane(scope: ColumnScope, modifier: Modifier) {
        AdSkipSettingsPane(feature = this, config = config, loaderState = loader.state, modifier = modifier)
    }

    // ---- 设置页辅助（导入导出/应用列表） ----

    fun importUserPack(text: String, name: String = "imported.json5") {
        runCatching { File(loader.userDir(), name).writeText(text) }
            .onSuccess { loader.reload(); Log.i(TAG, "user pack imported: $name") }
            .onFailure { Log.w(TAG, "import failed", it) }
    }

    fun exportPacksJson(): String {
        val json = kotlinx.serialization.json.Json { prettyPrint = true }
        val files = loader.state.value.packs.values.map {
            AdSkipPackFile(pkg = it.pkg, packVersion = it.packVersion, groups = it.groups)
        }
        return json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(AdSkipPackFile.serializer()),
            files,
        )
    }

    fun userPackDir(): File = loader.userDir()

    data class AppEntry(val pkg: String, val label: String)

    fun launcherApps(): List<AppEntry> = runCatching {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                pkg to info.loadLabel(pm).toString()
            }
            .distinctBy { it.first }
            .sortedBy { it.second }
            .map { AppEntry(it.first, it.second) }
    }.getOrDefault(emptyList())

    companion object {
        const val TAG = "WaveAdSkip"

        const val TYPE_WINDOW_STATE_CHANGED = "WINDOW_STATE_CHANGED"
        const val TYPE_CONTENT_CHANGED = "WINDOW_CONTENT_CHANGED"

        /** 开屏窗口期（窗口切换后多久内视为开屏）。 */
        const val SPLASH_WINDOW_MS = 10_000L

        /** 同 App 连续点击最小间隔。 */
        const val CLICK_COOLDOWN_MS = 1_200L

        /** 单事件尝试次数与间隔。 */
        const val MAX_ATTEMPTS = 3
        const val ATTEMPT_INTERVAL_MS = 200L

        /** 年轻窗口首屏延迟（D-107）。 */
        const val YOUNG_DELAY_MS = 150L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AdSkipModule {

    @Binds
    @IntoSet
    abstract fun bindAdSkipFeature(feature: AdSkipFeature): ToolFeature
}
