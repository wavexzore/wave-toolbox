package com.wavexzore.toolbox.core.automation

import com.wavexzore.toolbox.core.selector.NodeView
import com.wavexzore.toolbox.core.selector.Rect
import com.wavexzore.toolbox.core.selector.snapshot.SnapshotNode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/** T-008 测试用假环境：脚本化点击结果 + 唤醒通道 + 动作记录（虚拟时间下运行）。 */
class FakeTaskEnv : TaskEnvironment {

    var tree: NodeView? = null
    var activity: String? = null

    val actions = mutableListOf<String>()
    val logs = mutableListOf<String>()

    /** 点击结果脚本（按序出队；空时取 [clickDefault]）。 */
    val clickScript = ArrayDeque<Boolean>()
    var clickDefault = true
    var backResult = true
    var scrollResult = true
    var scrollHook: ((Boolean) -> Unit)? = null
    var openAppHook: ((String) -> Unit)? = null

    private val wakeups = Channel<Unit>(Channel.CONFLATED)

    fun wake() {
        wakeups.trySend(Unit)
    }

    override suspend fun currentTree(): NodeView? = tree

    override fun currentActivity(): String? = activity

    override suspend fun awaitChange(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) { wakeups.receive() } != null

    override suspend fun click(node: NodeView): Boolean {
        actions += "click:${node.text ?: node.className}"
        return if (clickScript.isNotEmpty()) clickScript.removeFirst() else clickDefault
    }

    override suspend fun back(): Boolean {
        actions += "back"
        return backResult
    }

    override suspend fun scroll(forward: Boolean): Boolean {
        actions += if (forward) "scrollDown" else "scrollUp"
        scrollHook?.invoke(forward)
        return scrollResult
    }

    override suspend fun openApp(pkg: String) {
        actions += "open:$pkg"
        openAppHook?.invoke(pkg)
    }

    override fun log(message: String) {
        logs += message
    }
}

object TestTree {

    fun node(
        text: String? = null,
        desc: String? = null,
        cls: String? = null,
        bounds: List<Int> = listOf(0, 0, 100, 50),
        clickable: Boolean = false,
        children: List<NodeView> = emptyList(),
    ): SnapshotNode = SnapshotNode(
        text = text,
        contentDescription = desc,
        viewId = null,
        className = cls,
        bounds = Rect(bounds[0], bounds[1], bounds[2], bounds[3]),
        isClickable = clickable,
        isVisible = true,
        isEditable = false,
        isScrollable = false,
        depth = 0,
        drawingOrder = null,
        children = children,
    )

    fun root(vararg children: NodeView): SnapshotNode =
        node(cls = "android.widget.FrameLayout", bounds = listOf(0, 0, 1080, 2000), children = children.toList())
}
