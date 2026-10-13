package com.wavexzore.toolbox.feature.wechat

import android.content.Context
import android.util.Log
import com.wavexzore.toolbox.core.automation.TaskEnvironment
import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import com.wavexzore.toolbox.core.selector.NodeView
import com.wavexzore.toolbox.core.selector.snapshot.SnapshotCollector
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

/**
 * 检测会话（T-003/T-004/T-005/T-008/T-010）。
 *
 * 状态机（事件驱动，非轮询）：打开微信 → 建群 → 渐进扫描选择批（≤30）→ 完成 →
 * 读群系统消息（滚 4 屏去重）→ 解析三类 → 退群 → 下一批……直到列表走空。
 * - 单步 25s / 会话 15min 看门狗（D-208）；超时落失败快照（files/wechat_fail/）；
 * - 暂停仅停推进（HUD 回调）；停止 = 取消协程；
 * - dry-run 不退群（T-007）。
 */
@Singleton
class WechatDetectionSession @Inject constructor(
    @ApplicationContext private val context: Context,
    env: TaskEnvironment,
    private val config: WechatConfig,
    private val store: WechatStore,
    private val hud: WechatHud,
    private val collector: SnapshotCollector,
    dispatchers: AppDispatchers,
) {

    private val nav = WechatNav(env)
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private var job: Job? = null

    private val _paused = MutableStateFlow(false)

    data class Progress(
        val running: Boolean = false,
        val paused: Boolean = false,
        val phase: String = "idle",
        val batch: Int = 0,
        val selected: Int = 0,
        val processed: Int = 0,
        val found: Int = 0,
        val message: String = "",
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    init {
        hud.onToggle = {
            if (_paused.value) resume() else pause()
        }
        hud.onStop = { stop() }
    }

    fun start() {
        if (_progress.value.running) return
        job = scope.launch { runSession() }
    }

    fun pause() {
        _paused.value = true
        _progress.value = _progress.value.copy(paused = true)
    }

    fun resume() {
        _paused.value = false
        _progress.value = _progress.value.copy(paused = false)
    }

    fun stop() {
        paused { _paused.value = false }
        job?.cancel()
    }

    private inline fun paused(block: () -> Unit) = block()

    private suspend fun awaitResume() {
        while (_paused.value && coroutineContext.isActive) delay(200)
    }

    private fun setPhase(phase: String, message: String = "", batch: Int = -1, selected: Int = -1, processed: Int = -1, found: Int = -1) {
        val current = _progress.value
        _progress.value = current.copy(
            running = true,
            phase = phase,
            message = message,
            batch = if (batch >= 0) batch else current.batch,
            selected = if (selected >= 0) selected else current.selected,
            processed = if (processed >= 0) processed else current.processed,
            found = if (found >= 0) found else current.found,
        )
        Log.i(TAG, "phase=$phase $message batch=$batch selected=$selected")
        hud.update("微信检测 ${current.copy(phase = phase, message = message).let { formatHud(it) }}")
    }

    private fun formatHud(p: Progress): String = when {
        p.message.isNotEmpty() -> "${p.phase} ${p.message}"
        else -> "批${p.batch} 处理${p.processed} 命中${p.found}"
    }

    private suspend fun runSession() {
        val batchSize = runCatching { config.batchSize.first() }.getOrDefault(WechatConfig.DEFAULT_BATCH)
        val dryRun = runCatching { config.dryRun.first() }.getOrDefault(false)
        val timeout = runCatching { config.sessionTimeoutMs.first() }.getOrDefault(WechatConfig.DEFAULT_TIMEOUT)
        val wechatVersion = runCatching {
            context.packageManager.getPackageInfo(WechatNav.WECHAT_PKG, 0).versionName
        }.getOrNull()
        val runId = store.startRun(wechatVersion)

        var status = "failed"
        var batches = 0
        var processed = 0
        var found = 0
        try {
            withTimeout(timeout) {
                setPhase("preflight", "预检")
                preflight()
                setPhase("opening", "打开微信")
                nav.openWechat()
                if (!ensureMainPage()) fail("未进入微信主界面（空树或锚点缺失）")
                if (envTreeNull()) fail("空树预检失败：服务未连接或微信未发布节点树（核对 D-011 伪装类名）")

                val pool = LinkedHashSet<String>()
                val processedSet = HashSet<String>()
                var batchNo = 0
                var listComplete = false

                hud.show("微信检测 批0 处理0 命中0")

                while (coroutineContext.isActive) {
                    awaitResume()
                    batchNo++

                    // 回主界面并打开"发起群聊"
                    setPhase("menu", "打开建群")
                    if (!openCreateGroup()) fail("打开建群失败")
                    // 该页即选择页（MvvmContactListUI）：顶部有"完成"，下为联系人列表
                    if (nav.waitForText(listOf(WechatNav.ANCHOR_COMPLETE), 8_000) == null) fail("未进入选择联系人页")
                    if (!waitRows(8_000)) fail("选择页未出现联系人行")

                    // 从顶部开始扫描并选择本批
                    scrollToTop()
                    val selectedNow = LinkedHashSet<String>()
                    var actual = nav.completeCount()
                    val strikes = HashMap<String, Int>()
                    var stable = 0
                    var lastHash = ""
                    while (coroutineContext.isActive && actual < batchSize) {
                        awaitResume()
                        val root = nav.tree()
                        if (root == null) {
                            nav.settle(400)
                            continue
                        }
                        val screenWidth = root.bounds.width
                        val rows = ContactSelect.scanRows(root, screenWidth, ContactSelect.PAGE_OPTION_TITLES)
                        if (rows.isEmpty()) {
                            // 转场未稳：不参与判尾，等待重扫
                            nav.settle(400)
                            continue
                        }
                        // 以行内 CheckBox 真值刷新已选集合（自愈：位移误点/hover 丢点会被纠正重试）
                        rows.forEach { row ->
                            if (row.checked) {
                                selectedNow += row.name
                                strikes[row.name] = 0
                            } else if (row.name in selectedNow) {
                                selectedNow -= row.name
                                strikes[row.name] = (strikes[row.name] ?: 0) + 1
                            }
                        }
                        actual = nav.completeCount()
                        pool += rows.map { it.name }
                        log("scan batch=$batchNo rows=${rows.size} checked=${rows.count { it.checked }} head=${rows.take(4).map { it.name }}")
                        setPhase("selecting", "选$actual", batch = batchNo, selected = actual)

                        var tapped = false
                        for (row in rows) {
                            if (actual >= batchSize) break
                            if (row.checked || row.name in processedSet) continue
                            if (row.name in selectedNow || (strikes[row.name] ?: 0) >= 3) continue
                            // 每次点击前按名字重解析坐标（列表可能因"已选"条出现而位移）
                            val fresh = resolveRow(row.name) ?: row.node
                            nav.tapCenter(fresh)
                            nav.settle(320)
                            val now = nav.completeCount()
                            if (now > actual) actual = now
                            selectedNow += row.name
                            tapped = true
                        }
                        if (tapped) {
                            nav.settle(250)
                            continue
                        }

                        val hash = ContactSelect.stableHash(rows)
                        if (hash == lastHash) {
                            stable++
                            if (stable >= 2) {
                                listComplete = true
                                break
                            }
                        } else {
                            stable = 0
                            lastHash = hash
                        }
                        if (!nav.scroll(forward = true)) {
                            log("scroll failed at batch=$batchNo selected=${selectedNow.size}")
                            listComplete = true
                            break
                        }
                        nav.settle(500)
                    }

                    // 按行内 CheckBox 真值做一次全量校准（含遗留待用选择场景）
                    if (actual > 0) {
                        val truth = collectCheckedNames()
                        if (truth.size != selectedNow.size) log("invited 校准 ${selectedNow.size} -> ${truth.size}")
                        if (truth.size != actual) log("警告：勾选真值(${truth.size}) 与按钮计数($actual) 不一致")
                        selectedNow.clear()
                        selectedNow += truth
                    }
                    log("batch#$batchNo 选择结束 actual=$actual invited=${selectedNow.size}")

                    if (actual == 0) {
                        status = "ok"
                        break
                    }

                    setPhase("creating", "完成", batch = batchNo, selected = selectedNow.size)
                    log("complete-btn before click=「${nav.textNow(WechatNav.ANCHOR_COMPLETE)}」 selected=${selectedNow.size}")
                    val completed = nav.clickText(listOf(WechatNav.ANCHOR_COMPLETE), 8_000) ||
                        nav.clickText(listOf(WechatNav.ANCHOR_COMPLETE), 4_000)
                    if (!completed) fail("未找到完成按钮")
                    nav.settle(1_200)
                    // 完成后：正常进入聊天页；若命中风控则停在安全验证页 → 结束流程，人工验证后重跑
                    var enteredChat = false
                    var security = false
                    val chatDeadline = System.currentTimeMillis() + 20_000
                    while (System.currentTimeMillis() < chatDeadline && coroutineContext.isActive) {
                        val act = nav.currentActivity()
                        val anchor = nav.textNow(WechatNav.ANCHOR_CHAT_INFO_DESC) != null
                        if (act != null && act.contains("ChattingUI") && anchor) {
                            log("已进入聊天页 act=$act")
                            enteredChat = true
                            break
                        }
                        if (isSecurityPage()) {
                            security = true
                            break
                        }
                        nav.settle(600)
                    }
                    if (!enteredChat && security) {
                        log("命中安全验证页，结束流程（人工完成后重跑）")
                        status = "verify"
                        setPhase("security", "需安全验证，请人工验证后重跑", batch = batchNo, selected = selectedNow.size)
                        break
                    }
                    if (!enteredChat) fail("建群后未进入聊天页")

                    setPhase("parsing", "读消息", batch = batchNo, selected = selectedNow.size)
                    val messages = collectMessages()
                    val outcome = SystemMessageParser.parse(messages, invited = selectedNow)
                    val entries = outcome.hits.flatMap { hit ->
                        hit.names.map { name ->
                            ResultEntry(
                                name = name,
                                category = hit.category,
                                raw = hit.raw.take(500),
                                at = System.currentTimeMillis(),
                                runId = runId,
                            )
                        }
                    }
                    store.addEntries(entries)
                    found += entries.size
                    log("batch#$batchNo selected=${selectedNow.size} found=${entries.size} joined=${outcome.joined.size} dryRun=$dryRun")
                    setPhase("batching", "命中${found}", batch = batchNo, selected = selectedNow.size, found = found)

                    // 解析期间若被风控安全验证页覆盖 → 结束流程，人工验证后重跑
                    if (isSecurityPage()) {
                        log("解析期间命中安全验证页，结束流程（人工完成后重跑）")
                        status = "verify"
                        setPhase("security", "需安全验证，请人工验证后重跑", batch = batchNo, selected = selectedNow.size)
                        break
                    }

                    if (dryRun) {
                        backToMain()
                    } else {
                        val exited = exitGroup()
                        if (!exited && isSecurityPage()) {
                            log("退群期间命中安全验证页，结束流程（人工完成后重跑）")
                            status = "verify"
                            setPhase("security", "需安全验证，请人工验证后重跑", batch = batchNo, selected = selectedNow.size)
                            break
                        }
                        if (!exited) fail("退群失败（会话中断，群可能残留）")
                    }

                    processedSet += selectedNow
                    processed += selectedNow.size
                    batches++
                    setPhase("batching", "处理${processed}", batch = batchNo, processed = processed, found = found)
                    store.finishRun(runId, "running", batches, processed, found)

                    if (listComplete && (pool - processedSet).isEmpty()) {
                        status = "ok"
                        break
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            snapshot("session-timeout")
            status = "failed"
            setPhase("failed", "会话超时")
        } catch (e: CancellationException) {
            status = "cancelled"
            setPhase("stopped", "已停止")
        } catch (e: Throwable) {
            snapshot("exception")
            Log.w(TAG, "session failed", e)
            status = "failed"
            setPhase("failed", e.message ?: "异常")
        } finally {
            store.finishRun(runId, status, batches, processed, found)
            hud.hide()
            _progress.value = _progress.value.copy(running = false, paused = false, phase = if (status == "ok") "finished" else _progress.value.phase)
        }
    }

    private suspend fun preflight() {
        val launchable = context.packageManager.getLaunchIntentForPackage(WechatNav.WECHAT_PKG) != null
        if (!launchable) fail("未安装微信")
    }

    private suspend fun envTreeNull(): Boolean = nav.tree() == null

    /** 风控安全验证页（webview + 文案锚点）。 */
    private suspend fun isSecurityPage(): Boolean =
        nav.textNow("为了保障你的账号安全") != null || nav.textNow("去验证") != null

    /** 打开"发起群聊"选择页（多 tab 容错 + 3 次重试）。 */
    private suspend fun openCreateGroup(): Boolean {
        repeat(3) { attempt ->
            awaitResume()
            if (!ensureMainPage(6_000)) {
                nav.back()
                nav.settle(400)
            }
            if (!nav.hasDescNow(WechatNav.ANCHOR_MORE_DESC)) {
                // 不在"微信/通讯录"tab：先切回微信 tab
                nav.clickText(listOf("微信"), exact = true)
                nav.settle(500)
            }
            val opened = nav.clickText(listOf(WechatNav.ANCHOR_MORE_DESC), 3_500, descToo = true) &&
                nav.clickText(listOf(WechatNav.ANCHOR_LAUNCH_GROUP), 4_000) &&
                nav.waitForText(listOf(WechatNav.ANCHOR_COMPLETE), 8_000) != null
            if (opened) return true
            log("openCreateGroup attempt#${attempt + 1} failed")
            nav.back()
            nav.settle(600)
        }
        return false
    }

    /** 按昵称即时重解析行节点（点击前取最新坐标）。 */
    private suspend fun resolveRow(name: String): NodeView? =
        nav.tree()?.let { root ->
            ContactSelect.scanRows(root, root.bounds.width, ContactSelect.PAGE_OPTION_TITLES)
                .firstOrNull { it.name == name }?.node
        }

    /** 等待选择页出现可点击联系人行（转场期间树不稳定）。 */
    private suspend fun waitRows(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && coroutineContext.isActive) {
            val root = nav.tree()
            if (root != null) {
                val rows = ContactSelect.scanRows(root, root.bounds.width, ContactSelect.PAGE_OPTION_TITLES)
                if (rows.isNotEmpty()) {
                    log("waitRows ok rows=${rows.size} head=${rows.take(3).map { it.name }}")
                    return true
                }
            }
            nav.settle(400)
        }
        log("waitRows TIMEOUT " + scanDebug())
        return false
    }

    /** 行扫描失败诊断（节点统计）。 */
    private suspend fun scanDebug(): String {
        val root = nav.tree() ?: return "no-tree"
        var total = 0
        var clickable = 0
        var clickEmpty = 0
        var passDim = 0
        var passNoName = 0
        fun walk(n: NodeView) {
            total++
            if (n.isClickable) {
                clickable++
                if (n.text.isNullOrBlank() && n.contentDescription.isNullOrBlank()) {
                    clickEmpty++
                    val h = n.bounds.height
                    val w = n.bounds.width
                    if (h in 60..420 && w >= root.bounds.width * 0.6) passDim++
                    if (passDim > 0 && ContactSelect.nicknameDebug(n) == null) passNoName++
                }
            }
            n.children.forEach(::walk)
        }
        walk(root)
        val rows = ContactSelect.scanRows(root, root.bounds.width, ContactSelect.PAGE_OPTION_TITLES)
        return "total=$total clickable=$clickable clickEmpty=$clickEmpty passDim=$passDim passNoName=$passNoName rows=${rows.size} rootW=${root.bounds.width} cls=${root.className}"
    }

    /** 可靠回到微信主界面：快速探测 → 连续返回兜底 → 重新拉起。 */
    private suspend fun ensureMainPage(timeoutMs: Long = 15_000): Boolean {
        if (nav.waitMainPage(minOf(timeoutMs, 4_000))) return true
        repeat(4) {
            nav.back()
            nav.settle(400)
            if (nav.waitMainPage(3_000)) return true
        }
        nav.openWechat()
        return nav.waitMainPage(15_000)
    }

    private suspend fun scrollToTop() {
        var stable = 0
        var last = ""
        repeat(10) {
            val root = nav.tree() ?: return
            val rows = ContactSelect.scanRows(root, root.bounds.width)
            val hash = ContactSelect.stableHash(rows)
            if (hash == last) {
                stable++
                if (stable >= 2) return
            } else {
                stable = 0
                last = hash
            }
            if (!nav.scroll(forward = false)) return
            nav.settle(350)
        }
    }

    /** 全量收集当前已勾选行昵称（自顶向下滚扫，返回勾选真值集合）。 */
    private suspend fun collectCheckedNames(): Set<String> {
        val names = LinkedHashSet<String>()
        scrollToTop()
        var rounds = 0
        while (rounds < 12 && coroutineContext.isActive) {
            rounds++
            val root = nav.tree() ?: break
            val rows = ContactSelect.scanRows(root, root.bounds.width, ContactSelect.PAGE_OPTION_TITLES)
            rows.forEach { if (it.checked) names += it.name }
            if (!nav.scroll(forward = true)) break
            nav.settle(420)
        }
        return names
    }

    private suspend fun collectMessages(): List<String> {
        // 先等消息渲染：直到业务文本 ≥2 条（新群系统消息通常 1~3 条）或超时
        var spare = 15
        while (spare-- > 0) {
            val root = nav.tree() ?: break
            val count = nav.dfs(root) { n -> (n.text?.trim()?.length ?: 0) >= 2 }.size
            if (count >= 2) break
            nav.settle(600)
        }
        val collected = LinkedHashSet<String>()
        var stable = 0
        var last = ""
        var rounds = 0
        while (rounds < 6) {
            rounds++
            val root = nav.tree() ?: break
            val visible = nav.dfs(root) { it.text?.isNotBlank() == true }
                .mapNotNull { it.text?.trim()?.takeIf(String::isNotBlank) }
            val before = collected.size
            collected += visible
            val hash = visible.size.toString() + "#" + collected.size
            if (hash == last && collected.size == before) {
                stable++
                if (stable >= 1) break
            } else {
                stable = 0
                last = hash
            }
            if (!nav.scroll(forward = false)) break
            nav.settle(450)
        }
        return collected.toList()
    }

    private suspend fun exitGroup(): Boolean {
        if (!nav.clickText(listOf(WechatNav.ANCHOR_CHAT_INFO_DESC))) return false
        var found = nav.waitForText(listOf(WechatNav.ANCHOR_EXIT_GROUP), 2_500) != null
        var rounds = 0
        while (!found && rounds < 6) {
            rounds++
            nav.scroll(forward = true)
            nav.settle(400)
            found = nav.waitForText(listOf(WechatNav.ANCHOR_EXIT_GROUP), 1_500) != null
        }
        if (!found) return false
        if (!nav.clickText(listOf(WechatNav.ANCHOR_EXIT_GROUP), 5_000)) return false
        nav.clickFirstVariants(WechatNav.EXIT_DIALOG_VARIANTS, 8_000)
        return nav.waitMainPage(20_000)
    }

    private suspend fun backToMain() {
        repeat(4) {
            if (nav.waitForText(listOf("通讯录", "发现"), 1_200) != null && nav.currentActivity()?.contains("LauncherUI") == true) {
                return
            }
            nav.back()
            nav.settle(400)
        }
        nav.waitMainPage(8_000)
    }

    private suspend fun <T> retryStep(name: String, block: suspend () -> T?): T? {
        repeat(2) { attempt ->
            val result = try {
                withTimeout(STEP_TIMEOUT_MS) { block() }
            } catch (e: TimeoutCancellationException) {
                snapshot("timeout-$name-$attempt")
                null
            }
            if (result != null) return result
            nav.settle(500)
        }
        return null
    }

    private fun fail(message: String): Nothing {
        snapshot("fail")
        throw IllegalStateException(message)
    }

    private fun snapshot(tag: String) {
        runCatching {
            val json = collector.collectJson() ?: return
            val dir = File(context.filesDir, "wechat_fail").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            File(dir, "$stamp-$tag.json").writeText(json)
        }
    }

    private fun log(message: String) = Log.i(TAG, message)

    companion object {
        const val TAG = "WaveWechat.Detect"
        const val STEP_TIMEOUT_MS = 25_000L
    }
}
