package com.wavexzore.toolbox.core.automation

import com.wavexzore.toolbox.core.selector.NodeView
import com.wavexzore.toolbox.core.selector.SelectorEngine
import com.wavexzore.toolbox.core.selector.snapshot.SnapshotTree
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 任务编排器（T-008）：Step 顺序状态机 + 每步超时/重试/失败策略 + 全局取消 + 进度流。
 *
 * - 等待类步骤由 [TaskEnvironment.awaitChange]（事件管线）唤醒，条件在唤醒后复查，[Step.timeoutMs] 兜底；
 * - 取消（[cancel]）立即打断等待：不强拉 Activity，当前动作完成后回到用户可控状态；
 * - 同包互斥：同一 `pkg` 同时仅一个任务执行，后到者在 [run] 流内挂起等待；
 * - `ReadText` 变量表仅存活于单次运行；`AssertText` 支持 `${name}` 取变量。
 */
class TaskExecutor(
    private val env: TaskEnvironment,
    private val selectorEngine: SelectorEngine = SelectorEngine(),
) {

    private val mutexes = HashMap<String, Mutex>()
    private val cancelSignal = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var cancelled = false

    /** 全局取消（对当前/后续已开始的运行生效；不强制导航）。 */
    fun cancel() {
        cancelled = true
        cancelSignal.trySend(Unit)
    }

    fun run(task: TaskDef): Flow<TaskProgress> = flow {
        cancelled = false
        val mutex = mutexes.getOrPut(task.pkg) { Mutex() }
        mutex.withLock {
            val vars = HashMap<String, String>()
            emit(TaskProgress.Started(task.id, task.steps.size))
            env.log("task[${task.id}] start pkg=${task.pkg} steps=${task.steps.size}")
            for ((index, step) in task.steps.withIndex()) {
                if (cancelled) {
                    emit(TaskProgress.Finished(TaskProgress.Status.CANCELLED))
                    env.log("task[${task.id}] cancelled at step#$index")
                    return@withLock
                }
                emit(TaskProgress.StepStarted(index, step))
                val result = executeStep(step, vars)
                emit(
                    TaskProgress.StepFinished(
                        index,
                        result.ok,
                        skipped = result.skipped || (!result.ok && step.failPolicy != FailPolicy.ABORT),
                        reason = result.reason,
                    ),
                )
                if (!result.ok) {
                    env.log("task[${task.id}] step#$index ${step.kind()} failed: ${result.reason}")
                    if (cancelled) {
                        emit(TaskProgress.Finished(TaskProgress.Status.CANCELLED))
                        return@withLock
                    }
                    if (step.failPolicy == FailPolicy.ABORT) {
                        emit(TaskProgress.Finished(TaskProgress.Status.FAILED, result.reason ?: "step#$index failed"))
                        return@withLock
                    }
                }
            }
            if (cancelled) {
                emit(TaskProgress.Finished(TaskProgress.Status.CANCELLED))
            } else {
                emit(TaskProgress.Finished(TaskProgress.Status.SUCCESS))
                env.log("task[${task.id}] success")
            }
        }
    }

    // ---- 步骤执行 ----

    private class StepResult(val ok: Boolean, val skipped: Boolean = false, val reason: String? = null)

    private suspend fun executeStep(step: Step, vars: MutableMap<String, String>): StepResult {
        if (step is Step.Delay) return runStep(step, vars)
        val timeout = step.timeoutMs
        if (timeout <= 0) return runStep(step, vars)
        val result = withTimeoutOrNull(timeout) { runStep(step, vars) }
        return result ?: StepResult(false, reason = "timeout(${timeout}ms)")
    }

    private suspend fun runStep(step: Step, vars: MutableMap<String, String>): StepResult = when (step) {
        is Step.WaitFor -> {
            if (waitFor { findNode(step.selector) != null }) StepResult(true)
            else StepResult(false, reason = "等待超时: ${step.selector}")
        }

        is Step.Click -> clickStep(step)

        is Step.WaitActivity -> {
            if (waitFor { activityMatches(step.classSuffix) }) StepResult(true)
            else StepResult(false, reason = "等待 Activity 超时: ${step.classSuffix}")
        }

        is Step.Back -> {
            var ok = true
            repeat(step.times.coerceAtLeast(1)) {
                if (cancelled) return@repeat
                ok = env.back() && ok
                delay(BACK_SETTLE_MS)
            }
            StepResult(ok, reason = if (ok) null else "back 失败")
        }

        is Step.Scroll -> scrollStep(step)

        is Step.ReadText -> {
            if (!waitFor { findNode(step.selector) != null }) {
                if (step.optional) StepResult(true, skipped = true, reason = "未找到（optional）")
                else StepResult(false, reason = "等待超时: ${step.selector}")
            } else {
                val node = findNode(step.selector)
                vars[step.into] = node?.text.orEmpty()
                StepResult(true)
            }
        }

        is Step.AssertText -> {
            val expected = resolveVar(step.text, vars)
            if (waitFor { treeHasText(expected) }) StepResult(true)
            else StepResult(false, reason = "断言失败: $expected")
        }

        is Step.Delay -> {
            delay(step.ms)
            StepResult(true)
        }

        is Step.OpenApp -> {
            env.openApp(step.pkg)
            delay(OPEN_SETTLE_MS)
            StepResult(true)
        }
    }

    private suspend fun clickStep(step: Step.Click): StepResult {
        var attempts = 0
        val maxAttempts = step.retry.coerceAtLeast(0) + 1
        while (attempts < maxAttempts) {
            attempts++
            var node = findNode(step.selector)
            if (node == null) {
                if (step.optional) return StepResult(true, skipped = true, reason = "未找到（optional）")
                if (!waitFor { findNode(step.selector) != null }) {
                    return StepResult(false, reason = "等待节点超时: ${step.selector}")
                }
                node = findNode(step.selector) ?: return StepResult(false, reason = "节点消失: ${step.selector}")
            }
            if (cancelled) return StepResult(true, reason = "cancelled")
            if (env.click(node)) return StepResult(true)
            env.log("click 未成功（attempt=$attempts/${maxAttempts}）: ${step.selector}")
            if (attempts < maxAttempts) {
                delay(CLICK_RETRY_DELAY_MS)
                env.awaitChange(CLICK_RETRY_DELAY_MS)
            }
        }
        return StepResult(false, reason = "点击重试耗尽（$maxAttempts 次）: ${step.selector}")
    }

    private suspend fun scrollStep(step: Step.Scroll): StepResult {
        val forward = step.dir == ScrollDir.DOWN
        when (val until = step.until) {
            is Until.Times -> {
                repeat(until.times.coerceAtLeast(1)) {
                    if (cancelled) return@repeat
                    env.scroll(forward)
                    awaitSlice()
                }
                return StepResult(true)
            }

            is Until.Selector -> {
                var scrolls = 0
                while (scrolls < until.maxScrolls) {
                    if (findNode(until.selector) != null) return StepResult(true)
                    if (cancelled) return StepResult(true, reason = "cancelled")
                    env.scroll(forward)
                    scrolls++
                    awaitSlice()
                }
                return if (findNode(until.selector) != null) StepResult(true)
                else StepResult(false, reason = "滚动 ${until.maxScrolls} 次仍未命中: ${until.selector}")
            }

            Until.Stable -> {
                var lastSignature: String? = null
                var stableRounds = 0
                var rounds = 0
                while (rounds < STABLE_MAX_ROUNDS && stableRounds < 2) {
                    if (cancelled) return StepResult(true, reason = "cancelled")
                    val before = treeSignature()
                    env.scroll(forward)
                    rounds++
                    awaitSlice()
                    val after = treeSignature()
                    if (after != null && after == before) stableRounds++ else stableRounds = 0
                    lastSignature = after
                }
                return StepResult(lastSignature != null, reason = if (lastSignature == null) "无窗口" else null)
            }
        }
    }

    // ---- 等待/工具 ----

    /** 条件为真返回 true；否则在事件唤醒/短切片后复查，直至（由外层 withTimeout 兜底）取消或条件满足。 */
    private suspend fun waitFor(condition: suspend () -> Boolean): Boolean {
        if (condition()) return true
        while (!cancelled) {
            awaitSignal()
            if (cancelled) return false
            if (condition()) return true
        }
        return false
    }

    /** 等待「环境变化信号」或「取消信号」；两者竞争，先到者胜。 */
    private suspend fun awaitSignal(): Boolean = coroutineScope {
        val envDeferred = async { env.awaitChange(AWAIT_SLICE_MS) }
        val cancelDeferred = async { cancelSignal.receive() }
        select {
            envDeferred.onAwait { woke ->
                cancelDeferred.cancel()
                woke
            }
            cancelDeferred.onAwait {
                envDeferred.cancel()
                true
            }
        }
    }

    private suspend fun awaitSlice() {
        awaitSignal()
    }

    private suspend fun findNode(selector: String): NodeView? =
        env.currentTree()?.let { selectorEngine.findFirst(it, selector) }

    private fun activityMatches(suffix: String): Boolean {
        val activity = env.currentActivity() ?: return false
        return activity == suffix || activity.endsWith(".$suffix") || activity.endsWith(suffix)
    }

    private suspend fun treeHasText(text: String): Boolean =
        env.currentTree()?.let { treeHasText(it, text) } ?: false

    private fun treeHasText(root: NodeView, text: String): Boolean {
        val stack = ArrayDeque<NodeView>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node.text == text) return true
            node.children.forEach(stack::addLast)
        }
        return false
    }

    private fun resolveVar(text: String, vars: Map<String, String>): String =
        if (text.startsWith("\${") && text.endsWith("}")) {
            vars[text.substring(2, text.length - 1)] ?: text
        } else {
            text
        }

    private suspend fun treeSignature(): String? = env.currentTree()?.let { SnapshotTree.toJson(it) }

    private fun Step.kind(): String = this::class.simpleName ?: "Step"

    companion object {
        /** 等待切片：唤醒条件复查的最大间隔（安全兜底，非轮询主路径）。 */
        const val AWAIT_SLICE_MS = 500L

        private const val CLICK_RETRY_DELAY_MS = 200L
        private const val BACK_SETTLE_MS = 150L
        private const val OPEN_SETTLE_MS = 400L
        private const val STABLE_MAX_ROUNDS = 10
    }
}
