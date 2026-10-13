package com.wavexzore.toolbox.core.automation

import com.wavexzore.toolbox.core.selector.NodeView

/**
 * 任务执行环境端口（T-008）：将编排器与无障碍运行时解耦，测试用假实现驱动全部分支。
 * 生产适配（接 ActionExecutor/EventPipeline/窗口树）由上层模块提供。
 */
interface TaskEnvironment {

    /** 当前活动窗口树；无窗口时 null。 */
    suspend fun currentTree(): NodeView?

    /** 当前 Activity 全名（含包）；未知时 null。 */
    fun currentActivity(): String?

    /**
     * 等待一次"界面可能变化"的信号（事件管线驱动）；[timeoutMs] 内无信号返回 false。
     * 仅作唤醒，条件判断由调用方在唤醒后自行复查（非轮询语义）。
     */
    suspend fun awaitChange(timeoutMs: Long): Boolean

    /** 点击节点；返回是否执行成功。 */
    suspend fun click(node: NodeView): Boolean

    /** 坐标手势点击（绕过 performAction，规避 RecyclerView 节点复用失效）。 */
    suspend fun tapAt(x: Int, y: Int): Boolean = false

    /** 向可编辑节点写入文本（ACTION_SET_TEXT）；返回是否执行成功。 */
    suspend fun setText(node: NodeView, text: String): Boolean = false

    /** 全局返回。 */
    suspend fun back(): Boolean

    /** 滚动（方向：true=向后/下）。 */
    suspend fun scroll(forward: Boolean): Boolean

    /** 手势滑动（起终点屏幕坐标）；不支持的环境返回 false。 */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300): Boolean = false

    /** 打开应用。 */
    suspend fun openApp(pkg: String)

    /** 日志（默认空实现）。 */
    fun log(message: String) {}
}
