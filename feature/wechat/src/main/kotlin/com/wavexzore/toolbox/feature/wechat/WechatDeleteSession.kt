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
import kotlin.coroutines.coroutineContext
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

/**
 * 删除会话（T-012；D-209/D-210/D-211）。
 * 逐条：通讯录 → 搜索显示名 → 首条结果 → 资料页名称二次校验 → 更多 → 删除 →
 * （可选）勾"同时删除聊天记录" → 确认 → 标记已清理。幂等：已清理跳过；"添加到通讯录"= 已非好友。
 */
@Singleton
class WechatDeleteSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val env: TaskEnvironment,
    private val store: WechatStore,
    private val hud: WechatHud,
    private val config: WechatConfig,
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
        val total: Int = 0,
        val done: Int = 0,
        val failed: Int = 0,
        val currentName: String = "",
        val message: String = "",
        val finished: Boolean = false,
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    init {
        hud.onToggle = { if (_paused.value) resume() else pause() }
        hud.onStop = { stop() }
    }

    fun start(names: List<String>, deleteChatRecord: Boolean) {
        if (_progress.value.running) return
        job = scope.launch { runDelete(names, deleteChatRecord) }
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
        _paused.value = false
        job?.cancel()
    }

    private suspend fun awaitResume() {
        while (_paused.value && coroutineContext.isActive) delay(200)
    }

    private fun setProgress(block: (Progress) -> Progress) {
        _progress.value = block(_progress.value).copy(running = true)
        val p = _progress.value
        hud.update("微信删除 ${p.done}/${p.total} 失败${p.failed} ${p.currentName}")
    }

    private suspend fun runDelete(names: List<String>, deleteChatRecord: Boolean) {
        val timeout = runCatching { config.sessionTimeoutMs.first() }.getOrDefault(WechatConfig.DEFAULT_TIMEOUT)
        val targets = names.distinct()
        var done = 0
        var failed = 0
        _progress.value = Progress(running = true, total = targets.size)
        hud.show("微信删除 0/${targets.size}")
        try {
            withTimeout(timeout) {
                nav.openWechat()
                if (!nav.waitMainPage(20_000)) fail("未进入微信主界面")
                if (nav.tree() == null) fail("空树预检失败")

                for (name in targets) {
                    awaitResume()
                    if (!coroutineContext.isActive) break
                    setProgress { it.copy(currentName = name) }
                    val ok = runCatching { deleteOne(name, deleteChatRecord) }
                        .onFailure { t -> Log.w(TAG, "delete failed: $name", t) }
                        .getOrDefault(false)
                    if (ok) {
                        store.markCleaned(listOf(name))
                        done++
                    } else {
                        snapshot("delete-fail-$name")
                        failed++
                    }
                    setProgress { it.copy(done = done, failed = failed) }
                }
            }
        } catch (e: TimeoutCancellationException) {
            snapshot("session-timeout")
            _progress.value = _progress.value.copy(message = "会话超时")
        } catch (e: CancellationException) {
            _progress.value = _progress.value.copy(message = "已停止")
        } catch (e: Throwable) {
            snapshot("exception")
            _progress.value = _progress.value.copy(message = e.message ?: "异常")
        } finally {
            hud.hide()
            _progress.value = _progress.value.copy(running = false, paused = false, finished = true, currentName = "")
        }
    }

    private suspend fun deleteOne(name: String, deleteChatRecord: Boolean): Boolean {
        // 回主界面
        if (!nav.waitMainPage(5_000)) {
            nav.back(); nav.settle(400)
            if (!nav.waitMainPage(5_000)) return false
        }
        // 通讯录 → 搜索
        nav.clickText(listOf(WechatNav.ANCHOR_CONTACTS_TAB), exact = true)
        val searchField = nav.waitForText(listOf(WechatNav.ANCHOR_SEARCH_DESC), 6_000, descToo = true)
            ?: return false
        if (!nav.clickNode(searchField)) return false
        nav.settle(300)
        val input = findEditor() ?: return false
        env.setText(input, name)
        nav.settle(900)

        // 搜索结果：非编辑框、文本含名字的首行
        val result = findResultRow(name) ?: run {
            nav.settle(600)
            findResultRow(name)
        } ?: return false
        if (!nav.clickNode(result)) return false

        // 资料页校验：更多按钮 + 名字出现
        val profile = nav.waitForText(listOf("更多"), 6_000, descToo = true) != null &&
            nav.waitForText(listOf(name), 4_000) != null
        if (!profile) {
            nav.back(); nav.settle(500)
            return false
        }
        // 已非好友（"添加到通讯录"）→ 标记清理完成
        val addable = nav.waitForText(listOf(WechatNav.ANCHOR_ADD_TO_CONTACTS), 1_200, exact = true) != null
        if (addable) {
            store.markCleaned(listOf(name), alreadyFriendless = true)
            nav.back(); nav.settle(500)
            return true
        }

        // 更多 → 删除
        if (!nav.clickText(listOf("更多"), descToo = true)) return false
        if (!nav.clickText(listOf("删除"), 4_000, exact = true) &&
            !nav.clickText(listOf("删除"), 2_000)
        ) {
            return false
        }
        // 确认弹窗（可选删聊天记录）
        if (nav.waitForText(listOf("将联系人", "删除"), 4_000) == null) return false
        if (deleteChatRecord) {
            // 勾选"同时删除…"（点标签即切换；失败不阻塞）
            runCatching {
                val label = nav.tree()?.let { root -> nav.findByText(root, listOf("同时删除")) }
                if (label != null) {
                    delay(150)
                    env.click(label)
                }
            }
        }
        // 确认按钮：精确"删除" → 变体"确定"
        val confirmed = nav.clickText(listOf("删除"), 4_000, exact = true) ||
            nav.clickFirstVariants(WechatNav.DELETE_DIALOG_CONFIRM, 3_000)
        if (!confirmed) return false

        // 回到搜索页或通讯录
        nav.waitGoneText(listOf("更多"), 6_000)
        nav.settle(500)
        nav.back()
        nav.settle(400)
        return true
    }

    private suspend fun findEditor(): NodeView? {
        repeat(6) {
            val root = nav.tree()
            if (root != null) {
                val editor = nav.dfs(root) { node ->
                    node.isEditable || node.className?.contains("EditText") == true
                }.firstOrNull()
                if (editor != null) return editor
            }
            nav.settle(300)
        }
        return null
    }

    private suspend fun findResultRow(name: String): NodeView? {
        repeat(8) {
            val root = nav.tree()
            if (root != null) {
                val row = nav.dfs(root) { node ->
                    node.isVisible && !node.isEditable &&
                        node.className?.contains("EditText") != true &&
                        node.text?.trim()?.contains(name) == true
                }.firstOrNull()
                if (row != null) return row
            }
            nav.settle(300)
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

    companion object {
        const val TAG = "WaveWechat.Delete"
    }
}
