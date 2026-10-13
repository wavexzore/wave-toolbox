package com.wavexzore.toolbox.feature.wechat

import com.wavexzore.toolbox.core.automation.TaskEnvironment
import com.wavexzore.toolbox.core.selector.NodeView
import kotlinx.coroutines.delay

/**
 * 微信页面导航原语（T-002）：文本/desc 锚点查找 + 事件唤醒等待 + 点击（走基座执行器三级回退）。
 * 全部接口超时兜底，禁止裸 sleep 轮询（D-201）。
 */
class WechatNav(private val env: TaskEnvironment) {

    /** 已知可安全关闭的弹层按钮（等待间隙顺手清障）。 */
    private val safeDismiss = listOf("以后再说", "我知道了", "下次再说", "知道了", "暂不")

    suspend fun tree(): NodeView? = env.currentTree()

    fun dfs(root: NodeView, predicate: (NodeView) -> Boolean): List<NodeView> {
        val out = ArrayList<NodeView>()
        fun walk(node: NodeView) {
            if (predicate(node)) out += node
            node.children.forEach(::walk)
        }
        walk(root)
        return out
    }

    /** 当前树中匹配文本的首个节点文本（诊断/校验用）。 */
    suspend fun textNow(needle: String): String? =
        tree()?.let { findByText(it, listOf(needle))?.text }

    /** 解析"完成(N)"按钮当前选择数；无选择（"完成"）时为 0。 */
    suspend fun completeCount(): Int {
        val text = textNow(ANCHOR_COMPLETE) ?: return 0
        val m = Regex("""\((\d+)\)""").find(text) ?: return 0
        return m.groupValues[1].toIntOrNull() ?: 0
    }

    fun findByText(
        root: NodeView,
        needles: List<String>,
        exact: Boolean = false,
        descToo: Boolean = true,
        minTop: Int = 0,
    ): NodeView? = dfs(root) { node ->
        if (node.bounds.top < minTop) return@dfs false
        val haystacks = ArrayList<String>(2)
        node.text?.let { haystacks += it }
        if (descToo) node.contentDescription?.let { haystacks += it }
        haystacks.any { hay ->
            needles.any { needle -> if (exact) hay.trim() == needle else hay.contains(needle) }
        }
    }.firstOrNull()

    suspend fun waitForText(
        needles: List<String>,
        timeoutMs: Long = 8_000,
        exact: Boolean = false,
        descToo: Boolean = true,
        minTop: Int = 0,
    ): NodeView? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            tree()?.let { root ->
                findByText(root, needles, exact, descToo, minTop)?.let { return it }
                dismissSafePopup(root)
            }
            val remain = deadline - System.currentTimeMillis()
            if (remain <= 0) break
            env.awaitChange(minOf(500, remain))
        }
        return null
    }

    suspend fun waitGoneText(needles: List<String>, timeoutMs: Long = 6_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val root = tree() ?: return true
            if (findByText(root, needles) == null) return true
            env.awaitChange(400)
        }
        return false
    }

    suspend fun clickNode(node: NodeView): Boolean = env.click(node)

    /** 手势坐标点击节点中心（规避 RecyclerView 复用导致的 performAction 失效）。 */
    suspend fun tapCenter(node: NodeView): Boolean =
        env.tapAt((node.bounds.left + node.bounds.right) / 2, (node.bounds.top + node.bounds.bottom) / 2)

    /** 等待并坐标点击首个匹配文本/desc（文本节点不可点也可用：点其坐标等效点行）。 */
    suspend fun tapText(
        needles: List<String>,
        timeoutMs: Long = 6_000,
        exact: Boolean = false,
        descToo: Boolean = true,
        minTop: Int = 0,
    ): Boolean {
        val node = waitForText(needles, timeoutMs, exact, descToo, minTop) ?: return false
        return tapCenter(node)
    }

    /** 当前可编辑输入框写入文本（ACTION_SET_TEXT；反复取新节点，替换旧值）。 */
    suspend fun setSearchText(value: String, timeoutMs: Long = 4_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val node = tree()?.let { root ->
                dfs(root) { it.isEditable || it.className?.contains("EditText") == true }.firstOrNull()
            }
            if (node != null) {
                runCatching { env.click(node) }
                settle(180)
                val fresh = tree()?.let { root ->
                    dfs(root) { it.isEditable || it.className?.contains("EditText") == true }.firstOrNull()
                } ?: node
                if (env.setText(fresh, value)) return true
            }
            settle(300)
        }
        return false
    }

    /** 点击左上角返回（部分页面 keyevent BACK 会被面板吞掉，坐标更可靠）。 */
    suspend fun tapTopLeftBack(): Boolean {
        val ok = tapText(listOf("返回"), 2_500, descToo = true)
        if (!ok) {
            env.tapAt(80, 216)
            settle(300)
        }
        return true
    }

    suspend fun clickText(
        needles: List<String>,
        timeoutMs: Long = 6_000,
        exact: Boolean = false,
        optional: Boolean = false,
        descToo: Boolean = true,
        minTop: Int = 0,
    ): Boolean {
        val node = waitForText(needles, timeoutMs, exact, descToo, minTop) ?: return optional
        return clickNode(node)
    }

    /** 点击所有命中（按序）中的第一个可点击者；适配多文案变体（离开群聊/确定/删除并退出）。 */
    suspend fun clickFirstVariants(variants: List<String>, timeoutMs: Long = 6_000, optional: Boolean = false): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val root = tree()
            if (root != null) {
                for (variant in variants) {
                    val node = findByText(root, listOf(variant))
                    if (node != null) return clickNode(node)
                }
                dismissSafePopup(root)
            }
            env.awaitChange(400)
        }
        return optional
    }

    suspend fun back(): Boolean = env.back()

    suspend fun scroll(forward: Boolean = true): Boolean = env.scroll(forward)

    /** 小步手势滑动（默认视口 45%，制造重叠减少边界漏收）。 */
    suspend fun swipeStep(forward: Boolean, fraction: Float = 0.45f): Boolean {
        val root = tree() ?: return false
        val h = root.bounds.height
        if (h <= 0) return false
        val x = root.bounds.width / 2
        val fromY = if (forward) (h * 0.75f).toInt() else (h * 0.28f).toInt()
        val delta = (h * fraction).toInt()
        val toY = if (forward) fromY - delta else fromY + delta
        return env.swipe(x, fromY, x, toY, 320L)
    }

    suspend fun openWechat() = env.openApp(WECHAT_PKG)

    suspend fun currentActivity(): String? = env.currentActivity()

    suspend fun waitActivityContains(suffix: String, timeoutMs: Long = 8_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val activity = currentActivity()
            if (activity != null && activity.contains(suffix)) return true
            env.awaitChange(300)
        }
        return false
    }

    /** 当前树中是否已存在某 desc（快速探测，不等）。 */
    suspend fun hasDescNow(needle: String): Boolean =
        tree()?.let { findByText(it, listOf(needle), descToo = true) } != null

    /** 微信主界面：LauncherUI 活动 + 底部 tab 锚点（避免误命中 MIUI 桌面上的"微信"图标）。 */
    suspend fun waitMainPage(timeoutMs: Long = 15_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val activity = currentActivity()
            val root = tree()
            if (activity != null && activity.contains("LauncherUI") && root != null &&
                findByText(root, listOf("通讯录"), exact = true) != null
            ) {
                return true
            }
            env.awaitChange(400)
        }
        return false
    }

    fun isMainPage(root: NodeView): Boolean =
        findByText(root, listOf("通讯录"), exact = false) != null &&
            findByText(root, listOf("发现", "通讯录", "我"), descToo = true) != null

    /** 等待间隙点击安全弹层按钮（若有）。 */
    suspend fun dismissSafePopup(root: NodeView) {
        for (text in safeDismiss) {
            val node = findByText(root, listOf(text), exact = true) ?: continue
            env.click(node)
            return
        }
    }

    suspend fun settle(ms: Long = 400) = delay(ms)

    companion object {
        const val WECHAT_PKG = "com.tencent.mm"

        const val ANCHOR_MORE_DESC = "更多功能"
        const val ANCHOR_LAUNCH_GROUP = "发起群聊"
        const val ANCHOR_SELECT_TITLE = "选择联系人"
        const val ANCHOR_CHAT_INFO_DESC = "更多信息"
        const val ANCHOR_EXIT_GROUP = "删除并退出"
        val EXIT_DIALOG_VARIANTS = listOf("离开群聊", "确定", "退出并删除", "删除并退出")
        const val ANCHOR_CONTACTS_TAB = "通讯录"
        const val ANCHOR_SEARCH_DESC = "搜索"
        const val ANCHOR_MORE_PROFILE_DESC = "更多"
        const val ANCHOR_DELETE_FRIEND = "删除"
        val DELETE_DIALOG_CONFIRM = listOf("删除", "确定")
        const val ANCHOR_ADD_TO_CONTACTS = "添加到通讯录"
        const val ANCHOR_COMPLETE = "完成"
    }
}
