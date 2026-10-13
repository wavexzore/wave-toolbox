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
 * 转账验证会话（v1.1 主方案 Task-102/103/104）：逐好友「搜索直达 → 资料页 → 聊天 → 转账页」，
 * 以转账页「转账给 昵称(*实名)」实名信号判活；无实名时点「转账」以弹窗/确认页复核；异常好友自动打标签。
 *
 * 真机校准（WeChat 8.0.79 / M154FF）：
 * - 通讯录搜索直达（ContactsSearchUI）为可靠跳转（weixin:// scheme 本机不可靠，暂缓）；
 * - 资料页 desc「更多信息」→ 朋友设置 →「设置朋友资料」→ 标签行 → 选择标签（chip desc 含"已选中"）；
 * - 转账页确认页 desc「关闭」；返回链建议用左上角「返回」（keyevent BACK 会被面板吞）。
 */
@Singleton
class WechatTransferVerifySession @Inject constructor(
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
        val index: Int = 0,
        val total: Int = 0,
        val zomibes: Int = 0,
        val tagged: Int = 0,
        val current: String = "",
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    fun start(retryUnknown: Boolean = false) {
        if (_progress.value.running) return
        hud.onToggle = { if (_paused.value) resume() else pause() }
        hud.onStop = { stop() }
        job = scope.launch { runSession(retryUnknown) }
    }

    fun stop() {
        job?.cancel()
    }

    fun pause() {
        _paused.value = true
        _progress.value = _progress.value.copy(paused = true)
    }

    fun resume() {
        _paused.value = false
        _progress.value = _progress.value.copy(paused = false)
    }

    private suspend fun awaitResume() {
        while (_paused.value && coroutineContext.isActive) {
            awaitResumeTick()
        }
    }

    private suspend fun awaitResumeTick() = delay(250)

    private suspend fun runSession(retryUnknown: Boolean) {
        var status = "failed"
        var done = 0
        try {
            withTimeout(SESSION_TIMEOUT_MS) {
                setPhase("preflight", "预检")
                nav.openWechat()
                if (!ensureMainPage()) fail("未进入微信主界面")
                if (nav.tree() == null) fail("空树预检失败（服务未连接）")

                val tagName = config.tagName.first()
                val friends = store.data.first().friends
                if (friends.isEmpty()) fail("好友列表为空（先执行「收集好友」）")

                setPhase("opening", "进搜索")
                if (!openContactSearch()) fail("未进入通讯录搜索页")

                var index = 0
                for ((i, friend) in friends.withIndex()) {
                    awaitResume()
                    val need = friend.status == FriendStatus.PENDING || (retryUnknown && friend.status == FriendStatus.UNKNOWN)
                    if (!need) continue
                    index++
                    _progress.value = _progress.value.copy(index = index, total = friends.size, current = friend.name)
                    hud.update("微信验证 $index 异常${_progress.value.zomibes} · ${friend.name}")
                    val result = verifyOne(i, friend, tagName)
                    done++
                    if (result.first == FriendStatus.ZOMBIE) {
                        _progress.value = _progress.value.copy(zomibes = _progress.value.zomibes + 1)
                    }
                    if (result.second) {
                        _progress.value = _progress.value.copy(tagged = _progress.value.tagged + 1)
                    }
                    setPhase("verified", "第${index}条 ${friend.name} → ${result.first.label}")
                }
                status = "ok"
                setPhase("finished", "完成 ${done} 条")
            }
        } catch (e: TimeoutCancellationException) {
            snapshot("verify-timeout")
            status = "failed"
            setPhase("failed", "验证超时")
        } catch (e: CancellationException) {
            status = "cancelled"
            setPhase("stopped", "已停止")
        } catch (e: Throwable) {
            snapshot("verify-exception")
            Log.w(TAG, "verify session failed", e)
            status = "failed"
            setPhase("failed", e.message ?: "异常")
        } finally {
            store.markVerifyRun()
            hud.hide()
            _progress.value = _progress.value.copy(running = false, paused = false)
        }
    }

    /** 单好友验证；返回 (状态, 是否已打标签)。内部自兜底，不抛出。 */
    private suspend fun verifyOne(storeIndex: Int, friend: FriendEntry, tagName: String): Pair<FriendStatus, Boolean> {
        var tagged = false
        return try {
            withTimeout(FRIEND_TIMEOUT_MS) {
                val r = doVerify(friend)
                if (r.first == FriendStatus.ZOMBIE) {
                    tagged = runCatching { tagOnProfile(friend.name, tagName) }.getOrDefault(false)
                }
                store.updateFriendAt(storeIndex) {
                    it.copy(status = r.first, wxid = it.wxid ?: r.third, region = it.region ?: r.fourth, signal = r.second, verifiedAt = System.currentTimeMillis(), tagged = tagged)
                }
                backToSearch()
                r.first to tagged
            }
        } catch (e: TimeoutCancellationException) {
            snapshot("verify-friend-timeout-${friend.name}")
            runCatching { backToSearch() }
            store.updateFriendAt(storeIndex) { it.copy(status = FriendStatus.UNKNOWN, signal = "timeout", verifiedAt = System.currentTimeMillis()) }
            FriendStatus.UNKNOWN to false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            snapshot("verify-friend-exception-${friend.name}")
            runCatching { backToSearch() }
            store.updateFriendAt(storeIndex) { it.copy(status = FriendStatus.UNKNOWN, signal = e.message, verifiedAt = System.currentTimeMillis()) }
            FriendStatus.UNKNOWN to false
        }
    }

    /** 核心验证：返回 (状态, 信号, wxid?, region?)。 */
    private suspend fun doVerify(friend: FriendEntry): Quad {
        // 1) 搜索直达 → 资料页
        if (!openContactSearch()) fail("搜索页缺失")
        if (!nav.setSearchText(friend.name)) fail("搜索框写入失败")
        nav.settle(600)
        val hit = waitResultRow(friend.name, 6_000) ?: fail("搜索结果未出现:${friend.name}")
        nav.tapCenter(hit)
        if (!waitActivity("ContactInfoUI", 8_000)) fail("资料页未打开")
        nav.settle(400)
        val profileRoot = nav.tree()
        val wxid = extractAfter(profileRoot, "微信号")
        val region = extractAfter(profileRoot, "地区")

        // 2) 资料页 → 发消息 → 聊天页
        if (!nav.tapText(listOf("发消息"), 6_000, exact = true)) fail("未找到发消息")
        if (!waitActivity("ChattingUI", 8_000)) fail("聊天页未打开")
        nav.settle(500)

        // 3) 聊天页 → + → 转账 → 转账页
        if (!nav.tapText(listOf("更多功能按钮"), 6_000)) fail("未找到聊天+")
        nav.settle(700)
        if (!nav.tapText(listOf("转账"), 6_000, exact = true, minTop = 1_400)) fail("未找到转账项")
        if (nav.waitForText(listOf("转账给"), 12_000) == null) fail("转账页未打开")

        // 4) 读实名信号
        val header = nav.textNow("转账给") ?: ""
        val real = header.contains("(*")
        if (real) return Quad(FriendStatus.OK, "实名:${header.take(24)}", wxid, region)

        // 5) 无实名 → 点转账复核（僵尸弹窗 / 未实名提示 / 确认页实名）
        nav.tapText(listOf("转账"), 5_000, exact = true, minTop = 1_900)
        nav.settle(1_800)
        val texts = allTexts()
        val zombieSign = ZOMBIE_SIGNS.firstOrNull { sign -> texts.any { it.contains(sign) } }
        val sheetReal = texts.any { it.startsWith("向") && it.contains("转账") && it.contains("(*") }
        val weakReal = texts.any { it.contains("未实名") }
        closeSheetOrPopup()
        when {
            zombieSign != null -> return Quad(FriendStatus.ZOMBIE, "弹窗:$zombieSign", wxid, region)
            sheetReal -> return Quad(FriendStatus.OK, "确认页实名", wxid, region)
            weakReal -> return Quad(FriendStatus.OK, "未实名提示", wxid, region)
            else -> {
                // 再试一次（首次点击可能丢）
                nav.tapText(listOf("转账"), 4_000, exact = true, minTop = 1_900)
                nav.settle(1_800)
                val texts2 = allTexts()
                val z2 = ZOMBIE_SIGNS.firstOrNull { sign -> texts2.any { it.contains(sign) } }
                val s2 = texts2.any { it.startsWith("向") && it.contains("转账") && it.contains("(*") }
                closeSheetOrPopup()
                return when {
                    z2 != null -> Quad(FriendStatus.ZOMBIE, "弹窗:$z2", wxid, region)
                    s2 -> Quad(FriendStatus.OK, "确认页实名", wxid, region)
                    else -> Quad(FriendStatus.UNKNOWN, "无信号:${texts2.take(6).joinToString("|").take(80)}", wxid, region)
                }
            }
        }
    }

    data class Quad(val first: FriendStatus, val second: String?, val third: String?, val fourth: String?)

    /** 异常好友打标签（在资料页执行；返回是否成功）。 */
    private suspend fun tagOnProfile(name: String, tagName: String): Boolean {
        awaitResume()
        backToProfile() || fail("回资料页失败")
        if (!nav.tapText(listOf("更多信息"), 5_000, descToo = true)) fail("未找到资料页更多")
        if (nav.waitForText(listOf("设置朋友资料"), 6_000) == null) fail("朋友设置页未打开")
        if (!nav.tapText(listOf("设置朋友资料"), 5_000)) fail("未找到设置朋友资料")
        if (nav.waitForText(listOf("设置备注"), 6_000) == null) fail("备注标签表单未打开")
        if (!openTagRow()) fail("未找到标签行")
        if (nav.waitForText(listOf("选择标签"), 6_000) == null) fail("选择标签弹层未打开")
        nav.settle(400)
        var chip = findCheckableChip(tagName)
        if (chip == null) {
            if (!createTag(tagName)) fail("标签不存在且创建失败: $tagName")
            chip = findCheckableChip(tagName) ?: fail("标签chip未出现")
        }
        if (chip.contentDescription?.contains("已选中") != true) nav.tapCenter(chip)
        nav.settle(300)
        if (!nav.tapText(listOf("完成"), 5_000, exact = true)) fail("选择标签完成失败")
        nav.settle(400)
        if (nav.waitForText(listOf("设置备注"), 5_000) == null) fail("回表单失败")
        if (!nav.tapText(listOf("完成"), 5_000, exact = true)) fail("保存标签失败")
        if (nav.waitForText(listOf("朋友设置"), 6_000) == null) fail("保存后未回设置页")
        log("tagged $name -> $tagName")
        return true
    }

    /** 新建标签（"我的标签"右侧 + 号）。 */
    private suspend fun createTag(tagName: String): Boolean {
        val root = nav.tree() ?: return false
        val header = nav.findByText(root, listOf("我的标签"), exact = true) ?: return false
        val plus = nav.dfs(root) { n ->
            n.isClickable && ((n.bounds.top + n.bounds.bottom) / 2) in (header.bounds.top - 80)..(header.bounds.bottom + 80) &&
                n.bounds.left > root.bounds.width / 2 && n.bounds.width in 40..300
        }.minByOrNull { kotlin.math.abs((it.bounds.top + it.bounds.bottom) / 2 - (header.bounds.top + header.bounds.bottom) / 2) } ?: return false
        nav.tapCenter(plus)
        if (nav.waitForText(listOf("新建标签"), 5_000) == null) return false
        if (!nav.setSearchText(tagName)) return false
        nav.settle(300)
        nav.tapText(listOf("完成", "确定"), 5_000, exact = false)
        nav.settle(400)
        return nav.waitForText(listOf("选择标签"), 5_000) != null
    }

    /** 选择标签弹层中的标签 chip（desc 含"标签已选中/未选中"）。 */
    private suspend fun findCheckableChip(tagName: String): NodeView? {
        val root = nav.tree() ?: return null
        return nav.findByText(root, listOf(tagName), exact = true)
            ?.takeIf { it.contentDescription?.contains("标签") == true }
    }

    /** 备注表单中「标签」行（标签 label 下方最近的可点击整行）。 */
    private suspend fun openTagRow(): Boolean {
        val root = nav.tree() ?: return false
        val label = nav.findByText(root, listOf("标签"), exact = true) ?: return false
        val row = nav.dfs(root) { n ->
            n.isClickable && n.bounds.top >= label.bounds.top + 30 &&
                n.bounds.top <= label.bounds.bottom + 260 && n.bounds.width > 600
        }.minByOrNull { it.bounds.top } ?: return false
        return nav.tapCenter(row)
    }

    // ---- 导航辅助 ----

    private suspend fun openContactSearch(): Boolean {
        val act = nav.currentActivity()
        if (act != null && act.contains("Search")) return true
        if (!ensureMainPage(6_000)) nav.back()
        nav.tapText(listOf("通讯录"), 5_000, exact = true)
        if (nav.waitForText(listOf("搜索联系人"), 6_000) == null) return false
        if (!nav.tapText(listOf("搜索联系人"), 5_000)) return false
        return waitActivity("Search", 6_000)
    }

    private suspend fun waitResultRow(name: String, timeoutMs: Long): NodeView? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val root = nav.tree()
            if (root != null) {
                nav.findByText(root, listOf(name), exact = true, minTop = 500)?.let { return it }
                // 模糊兜底：第一个结果行
                val any = nav.findByText(root, listOf(name), exact = false, minTop = 900)
                if (any != null && !any.isEditable) return any
            }
            nav.settle(350)
        }
        return null
    }

    private suspend fun waitActivity(suffix: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val act = nav.currentActivity()
            if (act != null && act.contains(suffix)) return true
            nav.settle(300)
        }
        return false
    }

    private suspend fun allTexts(): List<String> {
        val root = nav.tree() ?: return emptyList()
        return nav.dfs(root) { it.text?.isNotBlank() == true }.mapNotNull { it.text?.trim() }
    }

    private suspend fun extractAfter(root: NodeView?, prefix: String): String? {
        if (root == null) return null
        val t = nav.dfs(root) { it.text?.startsWith(prefix) == true }.firstOrNull()?.text ?: return null
        return t.substringAfter(":").trim().ifBlank { null }
    }

    private suspend fun closeSheetOrPopup() {
        // 确认页：desc 关闭；弹窗：确定/知道了
        if (nav.tapText(listOf("关闭"), 1_200, descToo = true)) {
            nav.settle(500)
            return
        }
        nav.tapText(listOf("确定", "知道了", "继续转账"), 1_200, descToo = false)
        nav.settle(300)
    }

    /** 回资料页（聊天/转账页 → 资料页）。 */
    private suspend fun backToProfile(): Boolean {
        repeat(6) {
            val act = nav.currentActivity()
            if (act != null && act.contains("ContactInfoUI")) return true
            if (act != null && act.contains("ProfileSettingUI")) return true
            nav.tapTopLeftBack()
            nav.settle(600)
        }
        return false
    }

    /** 任意状态 → 通讯录搜索页（验证循环的稳定锚点）。 */
    private suspend fun backToSearch() {
        repeat(10) {
            val act = nav.currentActivity()
            if (act != null && act.contains("Search")) return
            nav.tapTopLeftBack()
            nav.settle(500)
            if (nav.waitForText(listOf("搜索联系人"), 1_200) != null) return
        }
        // 兜底：回主界面再进
        runCatching {
            ensureMainPage(5_000)
            openContactSearch()
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
        _progress.value = _progress.value.copy(phase = phase, running = true)
        log("phase=$phase $message")
    }

    private fun fail(message: String): Nothing = throw IllegalStateException(message)

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
        const val TAG = "WaveWechat.Verify"
        private const val SESSION_TIMEOUT_MS = 90 * 60_000L
        private const val FRIEND_TIMEOUT_MS = 120_000L

        /** 僵尸弹窗特征文案（真机校准后可扩）。 */
        private val ZOMBIE_SIGNS = listOf(
            "你不是收款方好友",
            "不是收款方好友",
            "不是你好友",
            "不是你的好友",
            "把你删",
            "已删除你",
            "无法收款",
            "先发送朋友验证",
        )
    }
}
