package com.wavexzore.toolbox.core.accessibility.automation

import android.content.Context
import android.content.Intent
import android.util.Log
import com.wavexzore.toolbox.core.accessibility.action.ActionExecutor
import com.wavexzore.toolbox.core.accessibility.action.AndroidNodeView
import com.wavexzore.toolbox.core.accessibility.action.WindowTreeProvider
import com.wavexzore.toolbox.core.accessibility.pipeline.A11yEventType
import com.wavexzore.toolbox.core.accessibility.pipeline.EventPipeline
import com.wavexzore.toolbox.core.automation.TaskEnvironment
import com.wavexzore.toolbox.core.rules.model.RawRule
import com.wavexzore.toolbox.core.rules.model.RuleAction
import com.wavexzore.toolbox.core.selector.NodeView
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 生产 [TaskEnvironment] 适配（T-010 接线）：把任务编排器（T-008）接到真实运行时
 * （事件管线/窗口树/动作执行器/全局动作）。
 */
@Singleton
class AndroidTaskEnvironment @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pipeline: EventPipeline,
    private val treeProvider: WindowTreeProvider,
    private val executor: ActionExecutor,
) : TaskEnvironment {

    override suspend fun currentTree(): NodeView? = treeProvider.currentTree()?.tree?.root

    @Volatile
    private var lastActivity: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            pipeline.events.collect { event ->
                val cls = event.className
                if (event.type == A11yEventType.WINDOW_STATE_CHANGED && cls != null && isActivityLike(cls)) {
                    lastActivity = cls
                }
            }
        }
    }

    override fun currentActivity(): String? = lastActivity

    override suspend fun awaitChange(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) { pipeline.events.first() } != null

    override suspend fun click(node: NodeView): Boolean {
        val picked = treeProvider.currentTree() ?: return false
        val result = executor.execute(
            RawRule(id = ENV_RULE_PREFIX + "click", action = RuleAction.CLICK),
            node as? AndroidNodeView,
            picked.tree.screenWidth,
            picked.tree.screenHeight,
        )
        return result.ok
    }

    override suspend fun back(): Boolean {
        val picked = treeProvider.currentTree()
        val result = executor.execute(
            RawRule(id = ENV_RULE_PREFIX + "back", action = RuleAction.BACK),
            null,
            picked?.tree?.screenWidth ?: 0,
            picked?.tree?.screenHeight ?: 0,
        )
        return result.ok
    }

    override suspend fun scroll(forward: Boolean): Boolean {
        val picked = treeProvider.currentTree() ?: return false
        val target = firstScrollable(picked.tree.root) as? AndroidNodeView ?: picked.tree.root as? AndroidNodeView
        val result = executor.execute(
            RawRule(
                id = ENV_RULE_PREFIX + "scroll",
                action = if (forward) RuleAction.SCROLL_FORWARD else RuleAction.SCROLL_BACKWARD,
            ),
            target,
            picked.tree.screenWidth,
            picked.tree.screenHeight,
        )
        return result.ok
    }

    override suspend fun openApp(pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "openApp failed: $pkg", it) }
    }

    override fun log(message: String) {
        Log.d(TAG, message)
    }

    private fun firstScrollable(node: NodeView): NodeView? {
        if (node.isScrollable && node.isVisible) return node
        for (child in node.children) {
            firstScrollable(child)?.let { return it }
        }
        return null
    }

    /** 真实 Activity 类名（排除框架组件类；与 RuleEngine 同口径）。 */
    private fun isActivityLike(cls: String): Boolean {
        if (cls.isBlank()) return false
        if (cls.endsWith("Window") || cls.endsWith("WindowView") || cls.endsWith("PopupWindow")) return false
        return !cls.startsWith("android.") &&
            !cls.startsWith("androidx.") &&
            !cls.startsWith("android.support.") &&
            !cls.startsWith("com.android.internal.") &&
            !cls.startsWith("com.google.android.material.") &&
            !cls.startsWith("com.android.systemui.")
    }

    companion object {
        const val TAG = "WaveA11y.TaskEnv"
        private const val ENV_RULE_PREFIX = "env.task@"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TaskEnvironmentModule {

    @Binds
    abstract fun bindTaskEnvironment(environment: AndroidTaskEnvironment): TaskEnvironment
}
