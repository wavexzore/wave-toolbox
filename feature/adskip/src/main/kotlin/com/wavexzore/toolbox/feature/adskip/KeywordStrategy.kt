package com.wavexzore.toolbox.feature.adskip

import com.wavexzore.toolbox.core.selector.NodeView

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
    private const val WIDE_CONTAINER_RATIO = 0.6f
    private const val MAX_ANCESTOR_CHECK = 6

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
                if (matchedKeyword != null && excludes.none { text.contains(it) } && passesFilters(node, screenWidth, screenHeight)) {
                    matchedPick = Pick(node, matchedKeyword)
                }
            }
        }
        if (matchedPick == null) {
            val vid = node.viewId?.lowercase()
            val vidKeyword = vid?.let { v -> DEFAULT_VID_KEYWORDS.firstOrNull { v.contains(it) } }
            if (vidKeyword != null && passesFilters(node, screenWidth, screenHeight)) {
                matchedPick = Pick(node, "vid:$vidKeyword", source = "vid")
            }
        }
        matchedPick?.let { out += it }
        node.children.forEach { collect(it, keywords, excludes, screenWidth, screenHeight, out) }
    }

    /** D-104 过滤链（任一不过即弃）。 */
    private fun passesFilters(node: NodeView, screenWidth: Int, screenHeight: Int): Boolean {
        if (!node.isVisible) return false
        if (node.isEditable) return false
        if (node.isScrollable) return false
        if (node.bounds.width <= 0 || node.bounds.height <= 0) return false
        // 宽 > 60% 屏且无文本 → 容器（顶部整条），不可直接点
        val wide = screenWidth > 0 && node.bounds.width > screenWidth * WIDE_CONTAINER_RATIO
        if (wide && node.text.isNullOrBlank()) return false
        // 不可点击也保留：点击可点击祖先/坐标兜底由执行器完成（李跳跳同序）
        return true
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
