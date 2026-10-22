package com.wavexzore.toolbox.feature.adskip

import com.wavexzore.toolbox.core.accessibility.action.AndroidNodeView
import com.wavexzore.toolbox.core.selector.NodeFilters
import com.wavexzore.toolbox.core.selector.NodeView
import com.wavexzore.toolbox.core.selector.ResourceMatcher
import kotlin.math.abs

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

    /**
     * 页内浮层判定（D-236；2026-10-22 浮窗误点修复）：树根的直接子节点若只占屏幕一小块、
     * 且不是全宽贴顶横幅，则视为应用内悬浮容器；其内部的通用 keyword/vid 不予点击
     * （规则包显式追加的关键词/选择器不受影响）。
     */
    private const val FLOAT_PANEL_MAX_AREA = 0.35f
    private const val FLOAT_PANEL_MAX_WIDTH = 0.95f
    private const val FLOAT_PANEL_MIN_TOP = 0.08f
    private const val FLOAT_PANEL_CENTER_TOLERANCE = 0.08f

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
    ): Pick? = pickAll(root, extraKeywords, excludes, screenWidth, screenHeight).firstOrNull()

    /**
     * 按同口径返回**全部**候选（B-210）：供 Provider 在被抑制项上跳过而非整链放弃
     * （授权页「关闭(取消)」被抑制后应继续取「允许」；小程序胶囊被抑制后仍可点页面「跳过」）。
     */
    fun pickAll(
        root: NodeView,
        extraKeywords: List<String> = emptyList(),
        excludes: List<String> = DEFAULT_EXCLUDES,
        screenWidth: Int,
        screenHeight: Int,
    ): List<Pick> {
        val keywords = DEFAULT_KEYWORDS + extraKeywords
        val candidates = ArrayList<Pick>(8)
        collect(root, null, keywords, extraKeywords, excludes, screenWidth, screenHeight, candidates)
        return candidates.sortedWith(comparator(screenHeight))
    }

    /** 候选择优（供 `matches` 选择器结果与关键词结果共用的排序口径）。 */
    fun bestOf(candidates: List<Pick>, screenHeight: Int): Pick? =
        candidates.sortedWith(comparator(screenHeight)).firstOrNull()

    private fun collect(
        node: NodeView,
        container: NodeView?,
        keywords: List<String>,
        extraKeywords: List<String>,
        excludes: List<String>,
        screenWidth: Int,
        screenHeight: Int,
        out: MutableList<Pick>,
    ) {
        // 顶层容器（root 的直接子节点）判浮层；candidate 自身是容器时不据此拦截。
        val inFloatingPanel = container != null && container !== node &&
            isFloatingPanel(container, screenWidth, screenHeight)
        var matchedPick: Pick? = null
        val raw = node.text ?: node.contentDescription
        if (raw != null) {
            val text = raw.trim()
            if (text.isNotEmpty() && text.length <= MAX_TEXT_LEN) {
                val matchedKeyword = keywords.firstOrNull { text.contains(it) }
                val explicit = matchedKeyword != null && matchedKeyword in extraKeywords
                val floatBlocked = matchedKeyword != null && matchedKeyword in DEFAULT_KEYWORDS && !explicit
                if (matchedKeyword != null && excludes.none { text.contains(it) } && passesFilters(node, screenWidth, screenHeight) &&
                    passesConstraint(node, matchedKeyword, screenWidth, screenHeight) &&
                    !(inFloatingPanel && floatBlocked)
                ) {
                    matchedPick = Pick(node, matchedKeyword)
                }
            }
        }
        if (matchedPick == null) {
            val vidKeyword = DEFAULT_VID_KEYWORDS.firstOrNull { ResourceMatcher.vidContains(node.viewId, it) }
            if (vidKeyword != null && passesFilters(node, screenWidth, screenHeight) &&
                passesVidConstraint(node, vidKeyword, screenWidth, screenHeight) &&
                !(inFloatingPanel && vidKeyword == "close")
            ) {
                matchedPick = Pick(node, "vid:$vidKeyword", source = "vid")
            }
        }
        matchedPick?.let { out += it }
        node.children.forEach { child -> collect(child, container ?: child, keywords, extraKeywords, excludes, screenWidth, screenHeight, out) }
    }

    /**
     * 页内浮层容器：面积 < 35% 屏、非全宽（不是顶部/底部横幅）、顶部下沉 > 8%，
     * 且**非水平居中**（模态 Dialog 通常左右对称，需保持既可关闭）。
     * 直接叶子（children 为空）不判浮层——保持既有「按钮即候选」语义。
     */
    internal fun isFloatingPanel(container: NodeView, screenWidth: Int, screenHeight: Int): Boolean {
        if (screenWidth <= 0 || screenHeight <= 0) return false
        if (container.children.isEmpty()) return false
        val b = container.bounds
        if (b.width <= 0 || b.height <= 0) return false
        val areaRatio = (b.width.toLong() * b.height.toLong()).toDouble() / (screenWidth.toLong() * screenHeight.toLong())
        if (areaRatio >= FLOAT_PANEL_MAX_AREA) return false
        if (b.width >= screenWidth * FLOAT_PANEL_MAX_WIDTH) return false
        if (b.top <= screenHeight * FLOAT_PANEL_MIN_TOP) return false
        // 居中弹窗豁免：悬浮球/小窗通常贴边，Dialog 左右留白对称。
        val centered = abs(b.left - (screenWidth - b.right)) <= screenWidth * FLOAT_PANEL_CENTER_TOLERANCE
        if (centered) return false
        return true
    }

    /** vid 关键词空间约束（D-236）：`close` 只认顶部/右上角或底部右侧，避免浮窗中部 × 被当广告关闭。 */
    private fun passesVidConstraint(node: NodeView, keyword: String, screenWidth: Int, screenHeight: Int): Boolean {
        if (keyword != "close") return true
        if (screenWidth <= 0 || screenHeight <= 0) return true
        val topArea = node.bounds.top < screenHeight * CONSTRAINT_TOP_RATIO
        val rightHalf = node.bounds.left > screenWidth * CONSTRAINT_RIGHT_RATIO
        if (topArea && rightHalf) return true
        // 底部右侧（部分开屏把关闭放在右下角）。
        return node.bounds.top > screenHeight * 0.6f && node.bounds.left > screenWidth * 0.55f
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
