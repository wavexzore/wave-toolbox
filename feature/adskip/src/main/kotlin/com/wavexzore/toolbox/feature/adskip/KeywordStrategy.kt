package com.wavexzore.toolbox.feature.adskip

import com.wavexzore.toolbox.core.accessibility.action.AndroidNodeView
import com.wavexzore.toolbox.core.selector.NodeFilters
import com.wavexzore.toolbox.core.selector.NodeView
import com.wavexzore.toolbox.core.selector.ResourceMatcher

/**
 * 关键词跳过策略（T-002；李跳跳 `LttService.e`/`c` 语义移植，纯 JVM 可测）。
 *
 * 管线：文本/desc 包含关键词 → 长度/排除词/可点击性/可见性过滤 → 顶部优先 + Y-min 排序 → 取最优。
 *
 * 语义备忘（对照 `third_party/litiaotiao_analysis` 行级注释）：
 * - 默认关键词 {跳过, 关闭, ×, ✕, 跳过广告, 关闭广告, 我知道了}；
 * - D-102：包含匹配且「命中节点文本长度 ≤ [MAX_TEXT_LEN]」，排除词优先（"跳过登录"等不点）；
 * - D-104 过滤：editable / checkable / scrollable 节点不点；不可见不点；零面积不点；宽 > 60% 屏且无文本的容器不点；
 * - D-103 排序：先顶部区域（Y < 25% 屏高），再 Y 最小，再 X 靠右优先（右上角常见）。
 */
object KeywordStrategy {

    const val MAX_TEXT_LEN = 6
    private const val TOP_AREA_RATIO = 0.25f
    private const val MAX_ANCESTOR_CHECK = 6

    /** 误操作修复（2026-10-14）：易误点关键词，需"顶部 35% 或 自/祖先可点击"约束。 */
    private val AMBIGUOUS_KEYWORDS = setOf("关闭")

    /** 角落类关键词（×/✕）：需"右上角 或 可点击"约束。 */
    private val CORNER_KEYWORDS = setOf("×", "✕")
    private const val CONSTRAINT_TOP_RATIO = 0.35f
    private const val CONSTRAINT_RIGHT_RATIO = 0.45f

    val DEFAULT_KEYWORDS = listOf("跳过", "关闭", "×", "✕", "跳过广告", "关闭广告", "我知道了")
    val DEFAULT_EXCLUDES = listOf("跳过登录", "跳过引导", "跳过介绍", "跳过更新", "跳过设置", "关闭会员", "关闭提醒")

    /** viewId 片段匹配（李跳跳 `id:skip` 语义的收紧版；小写包含）。 */
    val DEFAULT_VID_KEYWORDS = listOf("skip", "jump", "close")

    data class Pick(
        val node: NodeView,
        val matched: String,
        val source: String = "keyword",
    )

    /**
     * 在树中选最优点击候选；无候选返回 null。
     * [extraKeywords] 用户/规则包追加；[excludes] 覆盖默认排除词（空用默认）。
     */
    fun pick(
        root: NodeView,
        extraKeywords: List<String> = emptyList(),
        excludes: List<String> = DEFAULT_EXCLUDES,
        screenWidth: Int,
        screenHeight: Int,
    ): Pick? {
        val keywords = DEFAULT_KEYWORDS + extraKeywords
        val candidates = ArrayList<Pick>(8)
        collect(root, keywords, excludes, screenWidth, screenHeight, candidates)
        return bestOf(candidates, screenHeight)
    }

    /** 候选择优（供 `matches` 选择器结果与关键词结果共用的排序口径）。 */
    fun bestOf(candidates: List<Pick>, screenHeight: Int): Pick? =
        candidates.sortedWith(comparator(screenHeight)).firstOrNull()

    private fun collect(
        node: NodeView,
        keywords: List<String>,
        excludes: List<String>,
        screenWidth: Int,
        screenHeight: Int,
        out: MutableList<Pick>,
    ) {
        var matchedPick: Pick? = null
        val raw = node.text ?: node.contentDescription
        if (raw != null) {
            val text = raw.trim()
            if (text.isNotEmpty() && text.length <= MAX_TEXT_LEN) {
                val matchedKeyword = keywords.firstOrNull { text.contains(it) }
                if (matchedKeyword != null && excludes.none { text.contains(it) } && passesFilters(node, screenWidth, screenHeight) &&
                    passesConstraint(node, matchedKeyword, screenWidth, screenHeight)
                ) {
                    matchedPick = Pick(node, matchedKeyword)
                }
            }
        }
        if (matchedPick == null) {
            val vidKeyword = DEFAULT_VID_KEYWORDS.firstOrNull { ResourceMatcher.vidContains(node.viewId, it) }
            if (vidKeyword != null && passesFilters(node, screenWidth, screenHeight)) {
                matchedPick = Pick(node, "vid:$vidKeyword", source = "vid")
            }
        }
        matchedPick?.let { out += it }
        node.children.forEach { collect(it, keywords, excludes, screenWidth, screenHeight, out) }
    }

    /** D-104 过滤链（任一不过即弃；R-209 起由 [NodeFilters] 唯一实现）。 */
    private fun passesFilters(node: NodeView, screenWidth: Int, screenHeight: Int): Boolean =
        NodeFilters.passesKeywordCandidate(node, screenWidth)

    /** 误操作修复：易误点关键词的空间/可点击性约束。 */
    private fun passesConstraint(node: NodeView, keyword: String, screenWidth: Int, screenHeight: Int): Boolean {
        val ambiguous = keyword in AMBIGUOUS_KEYWORDS
        val corner = keyword in CORNER_KEYWORDS
        if (!ambiguous && !corner) return true
        if (clickableSelfOrAncestor(node)) return true
        val topArea = screenHeight > 0 && node.bounds.top < screenHeight * CONSTRAINT_TOP_RATIO
        if (corner) {
            val rightHalf = screenWidth > 0 && node.bounds.left > screenWidth * CONSTRAINT_RIGHT_RATIO
            return topArea && rightHalf
        }
        return topArea
    }

    /** 自/祖先 4 级内可点击（Android 实际树）；快照树退化为仅自身。 */
    private fun clickableSelfOrAncestor(node: NodeView): Boolean {
        if (node.isClickable) return true
        val android = node as? AndroidNodeView ?: return false
        var cur = runCatching { android.source.parent }.getOrNull()
        var depth = 0
        while (cur != null && depth < 4) {
            val c = cur
            if (runCatching { c.isClickable }.getOrDefault(false)) return true
            cur = runCatching { c.parent }.getOrNull()
            depth++
        }
        return false
    }

    /** D-103 排序：顶部区域 → Y 最小 → X 靠右。 */
    private fun comparator(screenHeight: Int) = Comparator<Pick> { a, b ->
        val topA = if (screenHeight > 0 && a.node.bounds.top < screenHeight * TOP_AREA_RATIO) 0 else 1
        val topB = if (screenHeight > 0 && b.node.bounds.top < screenHeight * TOP_AREA_RATIO) 0 else 1
        if (topA != topB) return@Comparator topA - topB
        val byY = a.node.bounds.top - b.node.bounds.top
        if (byY != 0) return@Comparator byY
        b.node.bounds.left - a.node.bounds.left
    }
}
