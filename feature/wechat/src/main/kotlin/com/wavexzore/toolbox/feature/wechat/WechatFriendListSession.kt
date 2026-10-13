package com.wavexzore.toolbox.feature.wechat

import android.content.Context
import android.util.Log
import com.wavexzore.toolbox.core.automation.TaskEnvironment
import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import com.wavexzore.toolbox.core.selector.snapshot.SnapshotCollector
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 好友列表收集（v1.1 方案 Task-101）：通讯录页自顶向下滚扫全量昵称，去重后落库。
 * 供转账验证会话逐条消费；与验证解耦（先列表、后验证、可断点）。
 */
@Singleton
class WechatFriendListSession @Inject constructor(
    @ApplicationContext private val context: Context,
    env: TaskEnvironment,
    private val store: WechatStore,
    private val hud: WechatHud,
    private val collector: SnapshotCollector,
    dispatchers: AppDispatchers,
) {

    private val nav = WechatNav(env)
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private var job: Job? = null

    data class Progress(
        val running: Boolean = false,
        val phase: String = "idle",
        val collected: Int = 0,
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    fun start() {
        if (_progress.value.running) return
        job = scope.launch { runSession() }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun runSession() {
        var status = "failed"
        try {
            withTimeout(SESSION_TIMEOUT_MS) {
                setPhase("preflight", "预检")
                nav.openWechat()
                if (!ensureMainPage()) fail("未进入微信主界面")
                if (nav.tree() == null) fail("空树预检失败（服务未连接）")

                setPhase("collect", "收集")
                if (!nav.tapText(listOf("通讯录"), 6_000, exact = true)) fail("未找到通讯录 tab")
                if (nav.waitForText(listOf("新的朋友", "搜索联系人"), 8_000) == null) fail("未进入通讯录页")
                hud.show("微信好友收集 0")

                val names = collectAll()
                if (names.isEmpty()) fail("未收集到任何好友")
                store.saveFriends(names)
                log("collected=${names.size} unique=${names.toHashSet().size}")
                status = "ok"
                setPhase("finished", "收集完成 ${names.size}")
            }
        } catch (e: TimeoutCancellationException) {
            snapshot("list-timeout")
            status = "failed"
            setPhase("failed", "收集超时")
        } catch (e: CancellationException) {
            status = "cancelled"
            setPhase("stopped", "已停止")
        } catch (e: Throwable) {
            snapshot("list-exception")
            Log.w(TAG, "list session failed", e)
            status = "failed"
            setPhase("failed", e.message ?: "异常")
        } finally {
            hud.hide()
            _progress.value = _progress.value.copy(running = false, phase = if (status == "ok") "finished" else _progress.value.phase)
        }
    }

    /** 自顶向下滚扫全量好友昵称（去重保序；不足时按微信计数自动补扫一遍）。 */
    private suspend fun collectAll(): List<String> {
        val names = LinkedHashSet<String>()
        scrollToTop()
        scanPass(names)
        val expected = readFriendCount()
        if (expected != null && names.size < expected) {
            log("pass1=${names.size} expected=$expected，补扫")
            scanPass(names)
            scrollToTop()
        }
        log("collect done unique=${names.size} expected=$expected")
        return names.toList()
    }

    /** 单遍滚扫（当前位置 → 底部）。 */
    private suspend fun scanPass(names: LinkedHashSet<String>) {
        var lastHash = ""
        var stable = 0
        var rounds = 0
        while (coroutineContext.isActive && rounds < 200) {
            rounds++
            val root = nav.tree() ?: break
            val rows = ContactSelect.scanRows(root, root.bounds.width, ContactSelect.SPECIAL_ROWS)
            rows.forEach { r -> if (!isJunkName(r.name)) names += r.name }
            _progress.value = _progress.value.copy(collected = names.size)
            if (rounds % 3 == 0 || rows.isNotEmpty()) {
                setPhase("collect", "收集 ${names.size}")
                hud.update("微信好友收集 ${names.size}")
            }
            val hash = rows.map { it.name }.joinToString("|")
            if (hash == lastHash) {
                stable++
                if (stable >= 3) break
            } else {
                stable = 0
                lastHash = hash
            }
            if (!nav.swipeStep(forward = true)) break
            nav.settle(430)
        }
    }

    /** 底部「N个朋友」计数（微信自报好友数，作为补扫阈值）。 */
    private suspend fun readFriendCount(): Int? {
        val root = nav.tree() ?: return null
        val t = nav.dfs(root) { node -> node.text?.let { COUNT_RE.matches(it) } == true }
            .mapNotNull { it.text }.firstOrNull() ?: return null
        return COUNT_RE.find(t)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** 非好友行：功能入口、页标题、节头、底部计数。 */
    private fun isJunkName(name: String): Boolean =
        name in ContactSelect.SPECIAL_ROWS || name == "通讯录" || name == "#" || COUNT_RE.matches(name)

    private suspend fun scrollToTop() {
        var stable = 0
        var last = ""
        repeat(14) {
            val root = nav.tree() ?: return
            val rows = ContactSelect.scanRows(root, root.bounds.width, ContactSelect.SPECIAL_ROWS)
            val hash = ContactSelect.stableHash(rows)
            if (hash == last) {
                stable++
                if (stable >= 2) return
            } else {
                stable = 0
                last = hash
            }
            if (!nav.swipeStep(forward = false)) return
            nav.settle(400)
        }
    }

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

    private suspend fun setPhase(phase: String, message: String = "") {
        _progress.value = _progress.value.copy(phase = phase)
        log("phase=$phase $message")
        hud.update("微信好友收集 ${_progress.value.collected}${if (message.isBlank()) "" else " · $message"}")
    }

    private fun fail(message: String): Nothing {
        snapshot("list-fail")
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
        const val TAG = "WaveWechat.Friends"
        private const val SESSION_TIMEOUT_MS = 10 * 60_000L

        /** 底部「N个朋友 / N位联系人」计数行。 */
        private val COUNT_RE = Regex("^\\s*(\\d+)\\s*[个位]\\s*(朋友|联系人)\\s*$")
    }
}
