package com.wavexzore.toolbox.feature.adskip

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wavexzore.toolbox.core.accessibility.action.ActionExecutor
import com.wavexzore.toolbox.core.accessibility.action.AndroidNodeView
import com.wavexzore.toolbox.core.accessibility.action.ClickVerifier
import com.wavexzore.toolbox.core.accessibility.action.NodeAdapter
import com.wavexzore.toolbox.core.accessibility.action.WindowTreeProvider
import com.wavexzore.toolbox.core.common.text.ClassNames
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import com.wavexzore.toolbox.core.data.stats.StatsRepository
import com.wavexzore.toolbox.core.feature.api.FeatureEvent
import com.wavexzore.toolbox.core.feature.api.ToolFeature
import com.wavexzore.toolbox.core.rules.model.RawRule
import com.wavexzore.toolbox.core.rules.model.RuleAction
import com.wavexzore.toolbox.core.rules.model.RulePosition
import com.wavexzore.toolbox.core.selector.NodeView
import com.wavexzore.toolbox.core.selector.SelectorEngine
import com.wavexzore.toolbox.feature.adskip.infra.AttemptScheduler
import com.wavexzore.toolbox.feature.adskip.infra.Heartbeat
import com.wavexzore.toolbox.feature.adskip.infra.OemQuirks
import com.wavexzore.toolbox.feature.adskip.infra.PopupClassifier
import com.wavexzore.toolbox.feature.adskip.infra.SkipGate
import com.wavexzore.toolbox.feature.adskip.infra.WindowBounds
import com.wavexzore.toolbox.feature.adskip.infra.WindowTracker
import com.wavexzore.toolbox.feature.adskip.profile.AdProfileFactory
import com.wavexzore.toolbox.feature.adskip.profile.AppAdProfile
import com.wavexzore.toolbox.feature.adskip.profile.PsyoneBrainmusicProfile
import com.wavexzore.toolbox.feature.adskip.profile.SystemAdSolutionProfile
import com.wavexzore.toolbox.feature.adskip.profile.WeChatProfile
import com.wavexzore.toolbox.feature.adskip.profile.XiaomiMarketProfile
import com.wavexzore.toolbox.feature.adskip.strategy.Candidate
import com.wavexzore.toolbox.feature.adskip.strategy.CandidateProvider
import com.wavexzore.toolbox.feature.adskip.strategy.KeywordProvider
import com.wavexzore.toolbox.feature.adskip.strategy.MatchProvider
import com.wavexzore.toolbox.feature.adskip.strategy.NodeIdentity
import com.wavexzore.toolbox.feature.adskip.strategy.PathProvider
import com.wavexzore.toolbox.feature.adskip.strategy.PositionProvider
import com.wavexzore.toolbox.feature.adskip.strategy.ProviderContext
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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
 * 开屏广告自动跳过特性（T-002/T-004/T-005/T-006/T-007；T-201 基础设施外置；T-203 Provider 链）。
 *
 * 链路：窗口/内容事件 → 作用域检查（总开关由注册表过滤、单 App 权限、mode 窗口期）→ [SkipGate] 区域守卫 →
 * Provider 链（keyword → match → path → position；profile 可重排/抑制/禁用）→ [ActionExecutor] 点击
 * （performAction→祖先→坐标）→ 冷却/去重/限次/复核/统计/日志。
 *
 * 基础设施（唯一事实源，`infra/`）：[WindowTracker] 窗口令牌、[PopupClassifier] 弹窗判别、
 * [AttemptScheduler] 预算/冷却/去重键、[SkipGate] 守卫、[OemQuirks] 厂商横切。
 * 策略层（`strategy/`）：[CandidateProvider] 链与候选身份（去 text）。
 *
 * 预算不变式：单次事件最多 3 次尝试、间隔 200ms；`tree=null`/无候选不消耗预算（仅真实点击后计数）；
 * 心跳（T-203）：入口 5s 窗口内自 +500ms 起每 750ms 单次尝试一次，成功即止/实例更替即取消。
 */
@Singleton
class AdSkipFeature @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: AdSkipConfig,
    private val loader: AdSkipPackLoader,
    private val profileFactory: AdProfileFactory,
    private val treeProvider: WindowTreeProvider,
    private val executor: ActionExecutor,
    private val statsRepository: StatsRepository,
    private val settings: SettingsRepository,
) : ToolFeature {

    override val id: String = "adskip"
    override val name: String = "开屏广告自动跳过"
    override val description: String = "识别开屏/弹窗中的跳过与关闭按钮并自动点击"
    override val order: Int = 10

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val selectorEngine = SelectorEngine()

    // ---- 基础设施（T-201；行为等价迁移） ----

    internal val quirks = OemQuirks()

    internal val classifier = PopupClassifier(
        quirks = quirks,
        windowBounds = { pkg ->
            treeProvider.focusedWindowBounds(pkg)?.let { WindowBounds(it.left, it.top, it.right, it.bottom) }
        },
        screenSize = {
            val dm = context.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        },
    )

    internal val tracker = WindowTracker(classifier, quirks)

    internal val scheduler = AttemptScheduler()

    internal val gate = SkipGate(classifier)

    private val heartbeat = Heartbeat(HEARTBEAT_FIRST_MS, HEARTBEAT_INTERVAL_MS)

    // ---- 策略链（T-203；默认链序=关键词(vid 并入) → matches → path → position） ----

    private val defaultProviders: List<CandidateProvider> =
        listOf(KeywordProvider(), MatchProvider(), PathProvider(), PositionProvider())

    private val providerById: Map<String, CandidateProvider> = defaultProviders.associateBy { it.id }

    /** 已见实例号（触发旧实例态清理/心跳）。 */
    private val seenInstances = ConcurrentHashMap<String, Long>()

    @Volatile
    private var mode: String = AdSkipConfig.DEFAULT_MODE

    @Volatile
    private var extraKeywords: List<String> = emptyList()

    @Volatile
    private var disabledPackages: Set<String> = emptySet()

    @Volatile
    private var perAppModes: Map<String, String> = emptyMap()

    /** 入口窗口毫秒（T-206 起可配；当前=常量）。 */
    @Volatile
    private var entryWindowMs: Long = SPLASH_WINDOW_MS

    /** 点击复核延迟（与基座 RuleEngine 同源设置）。 */
    @Volatile
    private var clickVerifyMs: Long = SettingsRepository.DEFAULT_CLICK_VERIFY_MS.toLong()

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
        scope.launch { config.perAppMode.collect { perAppModes = it } }
        scope.launch { config.entryWindowMs.collect { entryWindowMs = it } }
        scope.launch { settings.clickVerifyMs.collect { clickVerifyMs = it.toLong() } }
    }

    private fun pkgMode(pkg: String): String = PkgMode.effective(mode, perAppModes, disabledPackages, pkg)

    override fun onEvent(event: FeatureEvent) {
        _eventCount.value = _eventCount.value + 1
        val pkg = event.packageName ?: return

        // 令牌卫生（B-205）：自身包/三态 OFF 包/系统选择器都在下方早退，但它们进入前台
        // 同样是跨包边界——不记录则从这些中间态返回任一 App 时，onForeground 会误判
        // 「同包幂等」而复用陈旧实例 → 开屏窗口早已过期 → 该次进入零尝试（实测缺陷）。
        val foreground = treeProvider.isAppForeground(pkg)
        if (pkg == context.packageName) {
            if (foreground) tracker.noteForeground(pkg)
            return
        }
        val runMode = pkgMode(pkg)
        if (runMode == PkgMode.OFF) {
            if (foreground) tracker.noteForeground(pkg)
            return
        }

        // 系统选择器豁免（D-226）：分享/「打开方式」面板不是广告——原会被当作
        // 新开屏窗口，由通用兜底以关键词「关闭」点到面板 × 而秒关（实测 0.6s）。
        if (SystemPicker.exempt(pkg)) {
            Log.d(TAG, "picker-exempt pkg=$pkg type=${event.type}")
            if (foreground) tracker.noteForeground(pkg)
            return
        }

        // 误操作修复（2026-10-14）：包进入前台 = 新开屏窗口；非前台不点
        if (event.type == TYPE_WINDOW_STATE_CHANGED && foreground) {
            val wb = treeProvider.focusedWindowBounds(pkg)
            Log.d(
                TAG,
                "wsc pkg=$pkg cls=${event.className} text=${event.text} " +
                    "winPopup=${classifier.isPopupWindow(pkg)} clsPopup=${classifier.isPopupWindowClass(event.className)} " +
                    "noise=${quirks.matchesNoiseClass(event.className)} win=${wb?.let { "${it.left},${it.top},${it.right},${it.bottom}" }}",
            )
        }

        var mark = tracker.current(pkg)
        if (foreground) {
            mark = tracker.onForeground(pkg, event.className ?: mark?.activity)
        }
        if (event.type == TYPE_WINDOW_STATE_CHANGED) {
            mark = tracker.onWindowStateChanged(
                pkg, event.className, runMode, foreground,
                profileFactory.profile(pkg).inAppWindowRefresh(),
            )
        }
        mark = mark ?: tracker.ensure(pkg, null)
        val instanceKey = tracker.instanceKey(pkg, mark)

        val lastSeen = seenInstances.put(pkg, mark.instance)
        if (lastSeen == null || lastSeen != mark.instance) {
            scheduler.retainOnly(pkg, instanceKey)
            if (foreground) launchHeartbeat(pkg, mark, instanceKey)
        }

        if (!foreground) return

        // mode=splash：仅在开屏窗口新鲜期（进入前台后 N 秒；页面切换不再顺延）内动作
        if (!tracker.isFresh(pkg, runMode, entryWindowMs)) return

        if (!scheduler.tryEnter(pkg, instanceKey, CLICK_COOLDOWN_MS, ATTEMPT_GAP_MS)) return

        val isFirstContent = event.type == TYPE_CONTENT_CHANGED && scheduler.isFirstContent(instanceKey)
        // 组级 delayMs 接线（v2；缺省组=150 与旧值一致）；取命中组的最小值
        val delayMs = if (isFirstContent) {
            profileFactory.mergedGroups(pkg)
                .filter { GroupGating.active(it, runMode, mark.activity) }
                .minOfOrNull { it.delayMs } ?: YOUNG_DELAY_MS
        } else {
            0L
        }

        Log.d(TAG, "attempt-sched pkg=$pkg key=$instanceKey delay=$delayMs type=${event.type}")
        scope.launch {
            try {
                if (delayMs > 0) delay(delayMs)
                attempt(pkg, instanceKey, mark, runMode)
            } finally {
                scheduler.leave(instanceKey)
            }
        }
    }

    /**
     * 心跳（T-203；Q-211 参数）：入口窗口内固定节拍单次尝试，打穿"事件静默期"；
     * 成功达上限/预算耗尽/实例更替/窗口过期即止；与事件尝试共用 inFlight 闸门互斥。
     */
    private fun launchHeartbeat(pkg: String, mark: WindowTracker.Mark, instanceKey: String) {
        scope.launch {
            var tick = 0
            while (true) {
                tick++
                val due = heartbeat.dueAt(mark.at, tick)
                val wait = due - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                val current = tracker.current(pkg) ?: return@launch
                if (current.instance != mark.instance) return@launch
                if (!heartbeat.withinWindow(current.at, System.currentTimeMillis(), entryWindowMs)) return@launch
                val runMode = pkgMode(pkg)
                if (runMode == PkgMode.OFF) return@launch
                if (!scheduler.hasBudget(instanceKey, groupMax(pkg, current, runMode), ATTEMPT_OVERHEAD)) return@launch
                if (!scheduler.tryEnter(pkg, instanceKey, CLICK_COOLDOWN_MS, ATTEMPT_GAP_MS)) continue
                Log.d(TAG, "heartbeat-tick pkg=$pkg key=$instanceKey tick=$tick")
                try {
                    attempt(pkg, instanceKey, current, runMode, maxAttempts = 1)
                } finally {
                    scheduler.leave(instanceKey)
                }
            }
        }
    }

    private fun groupMax(pkg: String, mark: WindowTracker.Mark, runMode: String): Int {
        // 未适配应用（组为空→GroupFallback 兜底）不走 maxOf：预算沿用模式默认（v1 等价），见 GroupFallback 注释
        val groups = profileFactory.mergedGroups(pkg).filter { GroupGating.active(it, runMode, mark.activity) }
        return groups.maxOfOrNull { it.actionMaximum } ?: if (runMode == AdSkipGroup.MODE_ANY) 2 else 1
    }

    private suspend fun attempt(
        pkg: String,
        instanceKey: String,
        mark: WindowTracker.Mark,
        runMode: String,
        maxAttempts: Int = MAX_ATTEMPTS,
    ) {
        Log.d(TAG, "attempt-enter key=$instanceKey act=${mark.activity}")
        val maxPerInstance = groupMax(pkg, mark, runMode)
        // 已达成功上限或失败预算（成功上限+额外 2 次点击）→ 不再动作；
        // 注意：tree=null / 无候选不消耗预算（只在真实点击后计数），避免启动早期烧掉窗口期
        if (!scheduler.hasBudget(instanceKey, maxPerInstance, ATTEMPT_OVERHEAD)) return
        repeat(maxAttempts) { index ->
            val outcome = tryOnce(pkg, mark, runMode)
            if (outcome != null) {
                scheduler.onOutcome(instanceKey, outcome.ok)
                if (outcome.ok) {
                    scheduler.recordClick(pkg)
                    _skipCount.value = _skipCount.value + 1
                    lastSkipSummary = "${System.currentTimeMillis()} pkg=$pkg src=${outcome.source} mech=${outcome.mechanism} cls=${mark.activity?.substringAfterLast('.')}"
                }
                return
            }
            if (index < maxAttempts - 1) delay(ATTEMPT_INTERVAL_MS)
        }
        if (scheduler.onceMiss(instanceKey)) {
            val probe = treeProvider.currentTree(pkg = pkg)
            Log.d(
                TAG,
                "no-candidate pkg=$pkg act=${mark.activity} " +
                    "nodes=${probe?.tree?.nodeCount}",
            )
        }
    }

    private data class Outcome(val ok: Boolean, val mechanism: String?, val source: String)

    private suspend fun tryOnce(pkg: String, mark: WindowTracker.Mark, runMode: String): Outcome? {
        val instanceKey = tracker.instanceKey(pkg, mark)
        val mergedActive = profileFactory.mergedGroups(pkg).filter { GroupGating.active(it, runMode, mark.activity) }
        if (mergedActive.isEmpty() && scheduler.oncePopup("$pkg#${mark.instance}:fallback")) {
            Log.d(TAG, "fallback-enter pkg=$pkg act=${mark.activity}")
        }
        val active = GroupFallback.resolve(mergedActive)

        // 保守模式（仅开屏）：弹窗窗口默认不碰（F1）——仅显式 popupAware/zone=popup 组参与（D-205）
        val popupReason = gate.blockReason(pkg, mark.activity, runMode)
        val popupContext = popupReason != null
        if (popupContext && active.none { it.popupAware == true || it.effectiveZone == AdSkipGroup.ZONE_POPUP }) {
            if (scheduler.oncePopup("$pkg#${mark.instance}")) {
                val wb = treeProvider.focusedWindowBounds(pkg)
                val dm = context.resources.displayMetrics
                Log.d(TAG, "popup-skip pkg=$pkg cls=${mark.activity} ($popupReason) win=${wb?.let { "${it.left},${it.top},${it.right},${it.bottom}" }} screen=${dm.widthPixels}x${dm.heightPixels}")
            }
            return null
        }
        if (popupContext && scheduler.oncePopup("$pkg#${mark.instance}")) {
            Log.d(TAG, "popup-enter pkg=$pkg cls=${mark.activity} ($popupReason)")
        }

        val picked = treeProvider.currentTree(pkg = pkg) ?: return null
        val tree = picked.tree
        val now = System.currentTimeMillis()

        val zoneGroups = active.filter { ZoneGate.allows(it, popupContext, mark.activity, mark.activityAt, now) }
        if (zoneGroups.isEmpty()) return null

        val profile = profileFactory.profile(pkg)
        val suppressed = profile.suppressOn(mark.activity)
            .flatMap { selector -> selectorEngine.findAll(tree.root, selector) }
            .map { NodeIdentity.of(it.node) }
            .toSet()
        val ctx = ProviderContext(
            pkg = pkg,
            root = tree.root,
            screenWidth = tree.screenWidth,
            screenHeight = tree.screenHeight,
            groups = zoneGroups,
            profile = profile,
            activity = mark.activity,
            extraKeywords = extraKeywords + profile.keywordExtra() + zoneGroups.flatMap { it.keywords },
            suppressedIdentities = suppressed,
            skipKeyword = popupContext || zoneGroups.any { it.suppressGeneric },
            selectorEngine = selectorEngine,
            scheduler = scheduler,
        )

        for (provider in resolveProviders(profile.providerOrder())) {
            val candidate = provider.provide(ctx) ?: continue
            val identity = candidate.node?.let { NodeIdentity.of(it) }
            if (identity != null && scheduler.identityCount(instanceKey, identity) >= IDENTITY_CLICK_LIMIT) continue
            val result = execute(candidate, tree, profile.forceGesture())
            candidate.groupId?.let { scheduler.recordGroupAction(pkg, it) }
            if (identity != null) {
                scheduler.recordIdentity(instanceKey, identity)
                if (result.ok && candidate.node != null) {
                    scheduleVerify(pkg, mark, instanceKey, identity, candidate.node, runMode)
                }
            }
            record(pkg, candidate.source, result, now, mark.activity, zone = zoneOf(popupContext, runMode))
            return Outcome(result.ok, result.mechanism, candidate.source)
        }
        return null
    }

    /** 区域准入（v2 `zone`）：弹窗上下文=仅 popupAware/zone=popup；activity 区域需页面稳定期。 */
    private fun groupInZone(
        group: AdSkipGroup,
        popupContext: Boolean,
        mark: WindowTracker.Mark,
        now: Long,
    ): Boolean = ZoneGate.allows(group, popupContext, mark.activity, mark.activityAt, now)

    private fun resolveProviders(order: List<String>?): List<CandidateProvider> =
        (order ?: DEFAULT_PROVIDER_ORDER).mapNotNull { providerById[it] }

    private suspend fun execute(candidate: Candidate, tree: NodeAdapter.Tree, forceGesture: Boolean = false): ActionExecutor.Result {
        val position = candidate.position
        if (position != null) {
            val rule = RawRule(
                id = "adskip@${candidate.source}",
                action = RuleAction.POSITION,
                position = RulePosition(
                    x = position.x,
                    y = position.y,
                    xPercent = position.xPercent,
                    yPercent = position.yPercent,
                ),
            )
            return executor.execute(rule, null, tree.screenWidth, tree.screenHeight)
        }
        val node = candidate.node ?: return ActionExecutor.Result(ok = false, mechanism = "none")
        if (forceGesture) {
            // 强制手势（B-206 续）：跳过 performAction，直接坐标触摸（等价用户点击）
            return executor.execute(
                RawRule(id = "adskip@click", action = RuleAction.CLICK_CENTER),
                node as? AndroidNodeView,
                tree.screenWidth,
                tree.screenHeight,
            )
        }
        return click(node, tree.screenWidth, tree.screenHeight)
    }

    /** 点击复核（T-203；复用 [ClickVerifier]）：节点仍在且未变 → 回退成功记账允许重试。 */
    private fun scheduleVerify(
        pkg: String,
        mark: WindowTracker.Mark,
        instanceKey: String,
        identity: String,
        node: NodeView,
        runMode: String,
    ) {
        val signature = ClickVerifier.signature(node)
        scope.launch {
            delay(clickVerifyMs)
            val current = tracker.current(pkg) ?: return@launch
            if (current.instance != mark.instance) return@launch
            val picked = treeProvider.currentTree(pkg = pkg) ?: return@launch
            val found = NodeIdentity.find(picked.tree.root, identity) ?: return@launch
            val stillPresent = ClickVerifier.signature(found) == signature
            if (!stillPresent) return@launch
            if (ClickVerifier.shouldRetry(scheduler.successCount(instanceKey), groupMax(pkg, current, runMode) + 1, nodeStillPresent = true)) {
                scheduler.onVerifyFailed(pkg, instanceKey, identity, CLICK_COOLDOWN_MS, VERIFY_RETRY_AFTER_MS)
                Log.d(TAG, "verify-retry pkg=$pkg key=$instanceKey identity=$identity")
            } else {
                Log.d(TAG, "verify-still-present pkg=$pkg key=$instanceKey identity=$identity")
            }
        }
    }

    private suspend fun click(node: NodeView, screenWidth: Int, screenHeight: Int): ActionExecutor.Result =
        executor.execute(
            RawRule(id = "adskip@click", action = RuleAction.CLICK),
            node as? AndroidNodeView,
            screenWidth,
            screenHeight,
        )

    private fun zoneOf(popupContext: Boolean, runMode: String): String = when {
        popupContext -> AdSkipGroup.ZONE_POPUP
        runMode == AdSkipGroup.MODE_ANY -> AdSkipGroup.ZONE_ANY
        else -> AdSkipGroup.ZONE_ENTRY
    }

    private fun record(
        pkg: String,
        source: String,
        result: ActionExecutor.Result,
        now: Long,
        act: String? = null,
        zone: String? = null,
    ) {
        statsRepository.record(
            featureId = id,
            pkg = pkg,
            ruleId = source,
            kind = "SKIP",
            ok = result.ok,
            mechanism = result.mechanism,
            durationMs = System.currentTimeMillis() - now,
            zone = zone,
            provider = source.substringBefore(':'),
        )
        Log.i(TAG, "skip pkg=$pkg act=$act src=$source ok=${result.ok} mech=${result.mechanism}")
    }

    /** 设置面板（SPI）。 */
    @Composable
    override fun SettingsPane(scope: ColumnScope, modifier: Modifier, onOpenApps: () -> Unit) {
        AdSkipSettingsPane(
            feature = this,
            config = config,
            loaderState = loader.state,
            onOpenApps = onOpenApps,
            modifier = modifier,
        )
    }

    /** 按应用控制整屏（SPI）。 */
    @Composable
    override fun AppsScreen(onBack: () -> Unit) {
        AdSkipAppsScreen(feature = this, config = config, onBack = onBack)
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

        /** 开屏窗口期（进入前台后多久内视为开屏；误操作修复由 10s 收紧为 5s，且不再随页面切换顺延）。 */
        const val SPLASH_WINDOW_MS = 5_000L

        /** 同 App 连续点击最小间隔。 */
        const val CLICK_COOLDOWN_MS = 1_200L

        /** 复核失败后的补点等待（B-206 续）：无效点击不烧满冷却，短延时后允许补点。 */
        const val VERIFY_RETRY_AFTER_MS = 350L

        /** 同实例两次尝试的最小间隔（防事件风暴连点）。 */
        const val ATTEMPT_GAP_MS = 500L

        /** 单事件尝试次数与间隔。 */
        const val MAX_ATTEMPTS = 3
        const val ATTEMPT_INTERVAL_MS = 200L

        /** 每实例额外失败尝试预算（成功上限之外的兜底次数）。 */
        const val ATTEMPT_OVERHEAD = 2

        /** 年轻窗口首屏延迟（D-107）。 */
        const val YOUNG_DELAY_MS = 150L

        /** 心跳首发延迟与节拍（T-203；Q-211）。 */
        const val HEARTBEAT_FIRST_MS = 500L
        const val HEARTBEAT_INTERVAL_MS = 750L

        /** 同实例同控件最大执行次数（D-215）。 */
        const val IDENTITY_CLICK_LIMIT = 2

        /** 默认 Provider 链序（vid 并入 keyword 档）。 */
        val DEFAULT_PROVIDER_ORDER = listOf("keyword", "match", "path", "position")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AdSkipModule {

    @Binds
    @IntoSet
    abstract fun bindAdSkipFeature(feature: AdSkipFeature): ToolFeature

    @Binds
    abstract fun bindPackGroupsSource(loader: AdSkipPackLoader): PackGroupsSource

    /** 特定应用识别器集合（无绑定时空集，工厂回落通用）。 */
    @Multibinds
    abstract fun bindAppAdProfiles(): Set<AppAdProfile>

    @Binds
    @IntoSet
    abstract fun bindPsyoneProfile(profile: PsyoneBrainmusicProfile): AppAdProfile

    /** 小米应用商店（B-205：应用内开屏页窗口刷新）。 */
    @Binds
    @IntoSet
    abstract fun bindXiaomiMarketProfile(profile: XiaomiMarketProfile): AppAdProfile

    /** 微信（B-206 续：小程序页抑制「关闭」胶囊，防被当跳过点掉）。 */
    @Binds
    @IntoSet
    abstract fun bindWeChatProfile(profile: WeChatProfile): AppAdProfile

    /** MSA 开屏（B-206 续：跳过补点 + 强制手势，修入场吞点击）。 */
    @Binds
    @IntoSet
    abstract fun bindSystemAdSolutionProfile(profile: SystemAdSolutionProfile): AppAdProfile
}

/**
 * 真实 Activity 判定（R-209 归一：唯一实现在 [ClassNames]）：
 * 抽屉 DrawerLayout、弹窗 FrameLayout、裸类名 Main 等框架窗口返回 false。
 */
internal fun looksLikeActivity(cls: String?): Boolean = ClassNames.isActivityLike(cls)

/** 两轴比例接近且都明显小于屏幕 → 视为应用开关动画中的等比缩放窗口（按全屏对待）。 */
internal fun isUniformlyScaled(widthRatio: Float, heightRatio: Float): Boolean =
    com.wavexzore.toolbox.feature.adskip.infra.isUniformlyScaled(widthRatio, heightRatio)

/** 弹窗式窗口类别：框架 Dialog / PopupWindow（WSC 事件类别可能混入视图类，故只认这两种）。 */
internal fun isPopupWindowClass(cls: String?): Boolean =
    com.wavexzore.toolbox.feature.adskip.infra.isPopupWindowClassOf(cls)
