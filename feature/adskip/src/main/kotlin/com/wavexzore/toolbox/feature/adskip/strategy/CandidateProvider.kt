package com.wavexzore.toolbox.feature.adskip.strategy

import com.wavexzore.toolbox.core.selector.NodeView
import com.wavexzore.toolbox.core.selector.SelectorEngine
import com.wavexzore.toolbox.feature.adskip.AdSkipGroup
import com.wavexzore.toolbox.feature.adskip.AdSkipPosition
import com.wavexzore.toolbox.feature.adskip.infra.AttemptScheduler
import com.wavexzore.toolbox.feature.adskip.profile.AppAdProfile

/**
 * 候选提供者（T-203；D-217）：五级策略对象化，链序默认不变、可被 profile 重排/禁用。
 *
 * 约定：命中即返回（保持「一次尝试最多一击」语义）；`null`=本档无候选 → 交下一档；
 * 失败预算只在真实点击后消耗（既有不变式）。
 */
interface CandidateProvider {
    val id: String
    suspend fun provide(ctx: ProviderContext): Candidate?
}

/** 候选（节点或坐标动作；`groupId` 用于组级冷却记账）。 */
data class Candidate(
    val node: NodeView?,
    val position: AdSkipPosition?,
    val source: String,
    val groupId: String? = null,
)

/** 链上下文（已按弹窗/区域/组准入预过滤）。 */
class ProviderContext(
    val pkg: String,
    /** 目标树根（纯 [NodeView]；测试可注入快照树）。 */
    val root: NodeView,
    val screenWidth: Int,
    val screenHeight: Int,
    val groups: List<AdSkipGroup>,
    val profile: AppAdProfile,
    val activity: String?,
    val extraKeywords: List<String>,
    /** 被 profile.suppressOn 命中的节点身份集合（F4：默认压制通用 keyword/vid 来源）。 */
    val suppressedIdentities: Set<String>,
    /** 弹窗上下文或 suppressGeneric 生效 → 跳过通用 keyword 来源。 */
    val skipKeyword: Boolean,
    val selectorEngine: SelectorEngine,
    val scheduler: AttemptScheduler,
)

/** 节点身份（去 text，防倒计时误判——D-215；同时用于复核定位）。 */
object NodeIdentity {

    fun of(node: NodeView): String {
        val b = node.bounds
        return "${node.viewId.orEmpty()}|${node.className.orEmpty()}|${b.left},${b.top},${b.right},${b.bottom}"
    }

    fun find(root: NodeView, identity: String): NodeView? {
        if (of(root) == identity) return root
        for (child in root.children) {
            find(child, identity)?.let { return it }
        }
        return null
    }
}
