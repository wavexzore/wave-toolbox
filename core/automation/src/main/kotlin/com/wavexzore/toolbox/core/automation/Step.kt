package com.wavexzore.toolbox.core.automation

/**
 * 步骤失败策略：`ABORT` 终止任务；`SKIP`/`CONTINUE` 记录后继续（CONTINUE 用于"失败可接受但需留痕"）。
 */
enum class FailPolicy { ABORT, SKIP, CONTINUE }

enum class ScrollDir { DOWN, UP }

/** 滚动终止条件。 */
sealed interface Until {
    /** 滚动到内容稳定（连续两次树签名一致或达上限）。 */
    data object Stable : Until

    /** 固定次数。 */
    data class Times(val times: Int) : Until

    /** 滚动直至选择器命中（或达最大次数）。 */
    data class Selector(val selector: String, val maxScrolls: Int = 10) : Until
}

/**
 * Step DSL（T-008，04 §5.4，RS-004/RS-005）：与业务解耦的最小步骤集。
 *
 * 等待类步骤由事件管线唤醒（非轮询 sleep），统一受 [timeoutMs] 兜底；失败按 [failPolicy] 处理。
 */
sealed interface Step {

    val timeoutMs: Long
    val failPolicy: FailPolicy

    /** 等待选择器命中。 */
    data class WaitFor(
        val selector: String,
        override val timeoutMs: Long = 5_000,
        override val failPolicy: FailPolicy = FailPolicy.ABORT,
    ) : Step

    /** 点击选择器命中节点（不存在则等待；[retry] 为首次失败后的重试次数）。 */
    data class Click(
        val selector: String,
        val retry: Int = 2,
        val optional: Boolean = false,
        override val timeoutMs: Long = 5_000,
        override val failPolicy: FailPolicy = FailPolicy.ABORT,
    ) : Step

    /** 等待 Activity 后缀出现（全等或 `.`/裸后缀匹配）。 */
    data class WaitActivity(
        val classSuffix: String,
        override val timeoutMs: Long = 10_000,
        override val failPolicy: FailPolicy = FailPolicy.ABORT,
    ) : Step

    /** 返回（GLOBAL_ACTION_BACK）。 */
    data class Back(
        val times: Int = 1,
        override val timeoutMs: Long = 3_000,
        override val failPolicy: FailPolicy = FailPolicy.SKIP,
    ) : Step

    /** 滚动（方向 + 终止条件）。 */
    data class Scroll(
        val dir: ScrollDir,
        val until: Until = Until.Times(1),
        override val timeoutMs: Long = 8_000,
        override val failPolicy: FailPolicy = FailPolicy.SKIP,
    ) : Step

    /** 读取选择器命中节点的文本到变量表（`into`）。 */
    data class ReadText(
        val selector: String,
        val into: String,
        val optional: Boolean = false,
        override val timeoutMs: Long = 3_000,
        override val failPolicy: FailPolicy = FailPolicy.ABORT,
    ) : Step

    /** 断言文本存在；`${name}` 形式取变量表值。 */
    data class AssertText(
        val text: String,
        override val timeoutMs: Long = 3_000,
        override val failPolicy: FailPolicy = FailPolicy.ABORT,
    ) : Step

    /** 延时。 */
    data class Delay(
        val ms: Long,
        override val failPolicy: FailPolicy = FailPolicy.ABORT,
    ) : Step {
        override val timeoutMs: Long get() = ms
    }

    /** 打开应用（包名）。 */
    data class OpenApp(
        val pkg: String,
        override val timeoutMs: Long = 5_000,
        override val failPolicy: FailPolicy = FailPolicy.ABORT,
    ) : Step
}

/** 任务定义（具体任务内容由特性模块提供）。 */
data class TaskDef(
    val id: String,
    val name: String = "",
    val pkg: String,
    val steps: List<Step>,
)
