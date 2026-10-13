package com.wavexzore.toolbox.core.accessibility.action

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import com.wavexzore.toolbox.core.accessibility.pipeline.A11yEvent
import com.wavexzore.toolbox.core.accessibility.pipeline.A11yEventType
import com.wavexzore.toolbox.core.accessibility.pipeline.EventPipeline
import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import com.wavexzore.toolbox.core.data.rules.RuleRepository
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import com.wavexzore.toolbox.core.rules.index.RuleIndex
import com.wavexzore.toolbox.core.rules.model.RawRule
import com.wavexzore.toolbox.core.selector.NodeView
import com.wavexzore.toolbox.core.selector.SelectorEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 规则引擎（T-007 / 04 §5.3）：被动执行层「界面上出现 X 就做 Y」。
 *
 * 链路：事件管线（已过滤+节流，事件单线程）→ 账本（页面实例/冷却/上限）→ 索引查询（pkg+activity+版本）
 * → 规则匹配（[RuleMatcher]）→ 动作执行（[ActionExecutor]）→ 200ms 校验重试（[ClickVerifier]）。
 *
 * - 每个事件最多执行一条规则（命中第一条 allowed 的规则即止；被冷却/超限的规则跳过继续下一条）；
 * - 索引 5s TTL + 内置规则首次自动导入；版本号按包缓存；
 * - 任何异常不影响管线（collect 内自捕获）。
 */
@Singleton
class RuleEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pipeline: EventPipeline,
    private val ruleRepository: RuleRepository,
    private val treeProvider: WindowTreeProvider,
    private val executor: ActionExecutor,
    private val settingsRepository: SettingsRepository,
    dispatchers: AppDispatchers,
) {

    private val selfPackage = context.packageName
    private val selectorEngine = SelectorEngine()
    private val ledger = ActionLedger()
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.event)
    private val versions = HashMap<String, Pair<Int?, String?>>()
    private val lastDenyLogAt = HashMap<String, Long>()

    @Volatile
    private var index: RuleIndex? = null

    @Volatile
    private var indexLoadedAt = 0L

    @Volatile
    private var verifyMs = SettingsRepository.DEFAULT_CLICK_VERIFY_MS.toLong()

    @Volatile
    private var running = false

    private var collectorsStarted = false
    private var actionSeq = 0L

    /** 已执行动作数（诊断/证据）。 */
    val actionCount = AtomicLong()

    private val _recentActions = MutableStateFlow<List<ActionRecord>>(emptyList())

    /** 最近动作流（UI 事件日志页「动作」页签；400ms 合并发布）。 */
    val recentActions: StateFlow<List<ActionRecord>> = _recentActions

    private val actionBuffer = ArrayDeque<ActionRecord>(ACTION_LOG_LIMIT + 1)
    private var actionJournalSeq = 0L
    private var lastActionFlushAt = 0L

    fun clearActions() {
        actionBuffer.clear()
        _recentActions.value = emptyList()
    }

    private fun appendAction(
        kind: ActionRecord.Kind,
        rule: RawRule,
        pkg: String?,
        mechanism: String? = null,
        ok: Boolean,
        detail: String? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        actionBuffer.addFirst(
            ActionRecord(++actionJournalSeq, now, kind, rule.id, pkg, mechanism, ok, detail),
        )
        while (actionBuffer.size > ACTION_LOG_LIMIT) actionBuffer.removeLast()
        if (now - lastActionFlushAt >= ACTION_FLUSH_INTERVAL_MS) {
            lastActionFlushAt = now
            _recentActions.value = actionBuffer.toList()
        }
    }

    fun start() {
        running = true
        if (collectorsStarted) return
        collectorsStarted = true
        scope.launch { settingsRepository.clickVerifyMs.collect { verifyMs = it.toLong() } }
        scope.launch { runCatching { loadIndex(importBuiltin = true) } }
        scope.launch {
            pipeline.events.collect { event ->
                if (!running) return@collect
                runCatching { onEvent(event) }.onFailure { Log.e(TAG, "onEvent failed", it) }
            }
        }
        Log.i(TAG, "started (instance=${ledger.instance})")
    }

    fun stop() {
        running = false
        Log.i(TAG, "stopped: actions=${actionCount.get()}")
    }

    suspend fun onEvent(event: A11yEvent) {
        val pkg = event.packageName ?: return
        val now = System.currentTimeMillis()
        // 账本先行：本次事件所属包/页面实例（内容事件先于窗口事件到达时先占位，窗口事件到达只升级不重置）
        if (event.type == A11yEventType.WINDOW_STATE_CHANGED) {
            val activityLike = isActivityLike(event.className)
            if (ledger.onWindow(pkg, event.className?.takeIf { activityLike }, activityLike, now)) {
                Log.d(
                    TAG,
                    "window pkg=$pkg cls=${event.className?.substringAfterLast('.')} " +
                        "instance=${ledger.instance} disp=${event.displayId}",
                )
            }
        } else {
            ledger.onWindow(pkg, null, activityLike = false, now = now)
        }
        if (pkg == selfPackage) return

        val rules = ensureIndex()?.query(pkg, activityFor(pkg), versionCodeOf(pkg), versionNameOf(pkg)) ?: return
        Log.d(TAG, "eval pkg=$pkg rules=${rules.size} act=${activityFor(pkg)?.substringAfterLast('.')}")
        if (rules.isEmpty()) return

        val picked = treeProvider.currentTree(pkg = pkg, displayId = event.displayId)
        if (picked == null) {
            logThrottled("no-tree", "no-tree pkg=$pkg")
            return
        }
        logThrottled("tree", "tree pkg=$pkg nodes=${picked.tree.nodeCount} truncated=${picked.tree.truncated} disp=${picked.displayId} root=[${picked.tree.root.describe()}]")
        val tree = picked.tree
        for (matched in rules) {
            val rule = matched.rule
            val decision = ledger.decision(rule.id, rule.actionCd, rule.actionMaximum, now)
            if (!decision.allowed) {
                logDeny(rule, decision.reason, pkg, now)
                continue
            }
            val outcome = RuleMatcher.evaluate(selectorEngine, tree.root, rule)
            val target = outcome.target
            if (target == null) {
                logMiss(rule, outcome.reason, pkg, now)
                continue
            }
            ledger.markExecuted(rule.id, now)
            val result = runCatching {
                executor.execute(rule, target as? AndroidNodeView, tree.screenWidth, tree.screenHeight)
            }.getOrElse { t -> ActionExecutor.Result(false, "exception", reason = t.message) }
            actionSeq++
            actionCount.incrementAndGet()
            appendAction(ActionRecord.Kind.ACTION, rule, pkg, result.mechanism, result.ok, result.reason)
            Log.d(
                TAG,
                "action#$actionSeq rule=${rule.id} pkg=$pkg act=${rule.action} mech=${result.mechanism} " +
                    "ok=${result.ok} node=${result.node ?: target.toStringOrNull()} reason=${result.reason}",
            )
            if (result.ok) {
                scheduleVerify(rule, target, ledger.instance)
                break
            }
        }
    }

    /** 动作后复核：原节点仍在且未离开该页面实例 → 重试一次（重试独立于冷却/上限，仅记账）。 */
    private fun scheduleVerify(rule: RawRule, target: NodeView, instanceAtAction: Long) {
        val signature = ClickVerifier.signature(target)
        scope.launch {
            delay(verifyMs)
            if (!running) return@launch
            if (ledger.instance != instanceAtAction) return@launch
            val picked = treeProvider.currentTree(ledger.packageName, displayId = null) ?: return@launch
            val freshTarget = RuleMatcher.evaluate(selectorEngine, picked.tree.root, rule).target ?: return@launch
            val stillPresent = ClickVerifier.signature(freshTarget) == signature
            if (!ClickVerifier.shouldRetry(attemptsUsed = 1, maxAttempts = MAX_ATTEMPTS, nodeStillPresent = stillPresent)) return@launch
            ledger.markExecuted(rule.id, System.currentTimeMillis())
            val result = executor.execute(rule, freshTarget as? AndroidNodeView, picked.tree.screenWidth, picked.tree.screenHeight)
            actionSeq++
            actionCount.incrementAndGet()
            appendAction(ActionRecord.Kind.VERIFY_RETRY, rule, ledger.packageName, result.mechanism, result.ok, result.reason)
            Log.d(TAG, "verify-retry#$actionSeq rule=${rule.id} mech=${result.mechanism} ok=${result.ok}")
        }
    }

    /** 真实 Activity 类名（非 `android.*`/`androidx.*` 等框架组件类）。 */
    private fun isActivityLike(cls: String?): Boolean {
        if (cls.isNullOrBlank()) return false
        if (cls.endsWith("Window") || cls.endsWith("WindowView") || cls.endsWith("PopupWindow")) return false
        return !cls.startsWith("android.") &&
            !cls.startsWith("androidx.") &&
            !cls.startsWith("android.support.") &&
            !cls.startsWith("com.android.internal.") &&
            !cls.startsWith("com.google.android.material.") &&
            !cls.startsWith("com.android.systemui.")
    }

    private fun activityFor(pkg: String): String? =
        ledger.activity?.takeIf { ledger.packageName == pkg }

    private suspend fun ensureIndex(): RuleIndex? {
        val current = index
        if (current != null && System.currentTimeMillis() - indexLoadedAt < INDEX_TTL_MS) return current
        return loadIndex(importBuiltin = false)
    }

    private suspend fun loadIndex(importBuiltin: Boolean = false): RuleIndex? = try {
        if (importBuiltin || ruleRepository.currentGroups().isEmpty()) {
            val outcome = ruleRepository.importBuiltin()
            Log.i(TAG, "builtin imported: groups=${outcome.groups.size} errors=${outcome.errors.size}")
        }
        ruleRepository.buildIndex().also {
            index = it
            indexLoadedAt = System.currentTimeMillis()
            Log.d(TAG, "index loaded: packages=${it.packages().size}")
        }
    } catch (t: Throwable) {
        Log.e(TAG, "loadIndex failed", t)
        index
    }

    private fun versionCodeOf(pkg: String): Int? = versions.getOrPut(pkg) { queryVersion(pkg) }.first

    private fun versionNameOf(pkg: String): String? = versions.getOrPut(pkg) { queryVersion(pkg) }.second

    private fun queryVersion(pkg: String): Pair<Int?, String?> = try {
        val info = context.packageManager.getPackageInfo(pkg, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
        code to info.versionName
    } catch (t: Throwable) {
        null to null
    }

    private fun logDeny(rule: RawRule, reason: ActionLedger.DenyReason?, pkg: String, now: Long) {
        val last = lastDenyLogAt[rule.id] ?: 0L
        if (now - last < DENY_LOG_INTERVAL_MS) return
        lastDenyLogAt[rule.id] = now
        Log.d(TAG, "deny($reason) rule=${rule.id} pkg=$pkg")
        appendAction(ActionRecord.Kind.DENY, rule, pkg, ok = false, detail = reason?.name, now = now)
    }

    private fun logMiss(rule: RawRule, reason: String, pkg: String, now: Long) {
        val key = "miss:${rule.id}"
        val last = lastDenyLogAt[key] ?: 0L
        if (now - last < MISS_LOG_INTERVAL_MS) return
        lastDenyLogAt[key] = now
        Log.d(TAG, "miss rule=${rule.id} pkg=$pkg reason=$reason")
        appendAction(ActionRecord.Kind.MISS, rule, pkg, ok = false, detail = reason, now = now)
    }

    private fun logThrottled(key: String, message: String) {
        val now = System.currentTimeMillis()
        val last = lastDenyLogAt[key] ?: 0L
        if (now - last < MISS_LOG_INTERVAL_MS) return
        lastDenyLogAt[key] = now
        Log.d(TAG, message)
    }

    private fun NodeView.toStringOrNull(): String = (this as? AndroidNodeView)?.describe() ?: bounds.toString()

    companion object {
        const val TAG = "WaveA11y.Rule"

        /** 索引缓存有效期。 */
        private const val INDEX_TTL_MS = 5_000L

        /** 校验重试上限（总执行次数，含首次）。 */
        private const val MAX_ATTEMPTS = 2

        /** 同规则拒绝日志最小间隔。 */
        private const val DENY_LOG_INTERVAL_MS = 1_000L

        /** 同规则 miss / 树状态日志最小间隔。 */
        private const val MISS_LOG_INTERVAL_MS = 2_000L

        /** 动作流环容量与 UI 合并发布间隔。 */
        const val ACTION_LOG_LIMIT = 100
        const val ACTION_FLUSH_INTERVAL_MS = 400L
    }
}
