package com.wavexzore.toolbox.feature.adskip

import com.wavexzore.toolbox.core.selector.Rect
import com.wavexzore.toolbox.core.selector.snapshot.SnapshotTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 关键词策略用例（T-002）：正/反/过滤/排序/边界。 */
class KeywordStrategyTest {

    private fun tree(json: String) = SnapshotTree.fromJson(json)

    @Test
    fun picksTopRightSkipOverBottom() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1200,2600], "children":[
              { "text":"跳过", "class":"TextView", "clickable":true, "bounds":[900,180,1100,260] },
              { "text":"跳过", "class":"TextView", "clickable":true, "bounds":[900,2200,1100,2280] }
            ]}
            """.trimIndent(),
        )
        val pick = KeywordStrategy.pick(root, screenWidth = 1200, screenHeight = 2600)
        assertNotNull(pick)
        assertEquals(180, pick.node.bounds.top)
    }

    @Test
    fun topAreaBonusBeatsSmallerYOutsideTopArea() {
        // 2400(不顶部) vs 700(顶部区) —— 顶部优先按 Y<25% 判定（650 阈值）
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "text":"关闭", "class":"TextView", "clickable":true, "bounds":[100,700,300,780] },
              { "text":"关闭", "class":"TextView", "clickable":true, "bounds":[100,300,300,380] }
            ]}
            """.trimIndent(),
        )
        val pick = KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600)
        assertEquals(300, pick?.node?.bounds?.top)
    }

    @Test
    fun excludesSkipLogin() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "text":"跳过登录", "class":"TextView", "clickable":true, "bounds":[800,200,950,260] }
            ]}
            """.trimIndent(),
        )
        assertNull(KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600))
    }

    @Test
    fun excludesEditableNode() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "text":"跳过", "class":"EditText", "editable":true, "clickable":true, "bounds":[100,200,300,260] }
            ]}
            """.trimIndent(),
        )
        assertNull(KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600))
    }

    @Test
    fun excludesScrollableContainer() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "text":"跳过", "class":"ScrollView", "scrollable":true, "clickable":true, "bounds":[0,0,1000,2600] }
            ]}
            """.trimIndent(),
        )
        assertNull(KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600))
    }

    @Test
    fun excludesInvisibleAndZeroArea() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "text":"跳过", "class":"TextView", "visible":false, "clickable":true, "bounds":[100,200,300,260] },
              { "text":"关闭", "class":"TextView", "clickable":true, "bounds":[100,300,100,300] }
            ]}
            """.trimIndent(),
        )
        assertNull(KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600))
    }

    @Test
    fun rejectsLongTextOverSixChars() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "text":"点击跳过广告立即进入", "class":"TextView", "clickable":true, "bounds":[100,200,400,260] }
            ]}
            """.trimIndent(),
        )
        assertNull(KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600))
    }

    @Test
    fun matchesByContentDescription() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "desc":"关闭", "class":"ImageButton", "clickable":true, "bounds":[900,150,980,230] }
            ]}
            """.trimIndent(),
        )
        assertNotNull(KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600))
    }

    @Test
    fun matchesByViewIdSkip() {
        // 无文本但有 skip viewId（李跳跳 id:skip 语义）→ 命中
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "vid":"com.example:id/btn_skip", "class":"ImageView", "clickable":true, "bounds":[900,160,980,240] }
            ]}
            """.trimIndent(),
        )
        val pick = KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600)
        assertNotNull(pick)
        assertEquals("vid:skip", pick.matched)
    }

    @Test
    fun textMatchPreferredOverVidMatch() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "vid":"close", "class":"ImageView", "clickable":true, "bounds":[900,180,980,260] },
              { "text":"跳过", "class":"TextView", "clickable":true, "bounds":[100,150,300,210] }
            ]}
            """.trimIndent(),
        )
        val pick = KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600)
        assertNotNull(pick)
        // 文本命中节点 Y 更小（150 < 180）→ 排序胜出
        assertEquals("跳过", pick.matched)
    }

    @Test
    fun extraKeywordsExtendDefaults() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "text":"忽略", "class":"TextView", "clickable":true, "bounds":[800,200,900,260] }
            ]}
            """.trimIndent(),
        )
        assertNull(KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600))
        assertNotNull(
            KeywordStrategy.pick(root, extraKeywords = listOf("忽略"), screenWidth = 1000, screenHeight = 2600),
        )
    }

    @Test
    fun wideTextlessContainerDroppedButAncestorClickableKept() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "desc":"跳过", "class":"View", "bounds":[0,80,1000,300], "children":[
                { "text":"跳过", "class":"TextView", "clickable":true, "bounds":[850,150,950,220] }
              ]}
            ]}
            """.trimIndent(),
        )
        val pick = KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600)
        assertNotNull(pick)
        assertEquals(150, pick.node.bounds.top)
    }

    // ---- 误操作修复（2026-10-14）：易误点关键词的约束 ----

    @Test
    fun ambiguousCloseMidScreenUnclickableRejected() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "text":"关闭", "class":"TextView", "bounds":[100,1300,300,1360] }
            ]}
            """.trimIndent(),
        )
        assertNull(KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600))
    }

    @Test
    fun ambiguousCloseMidScreenClickableAccepted() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "text":"关闭", "class":"TextView", "clickable":true, "bounds":[100,1300,300,1360] }
            ]}
            """.trimIndent(),
        )
        assertNotNull(KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600))
    }

    @Test
    fun ambiguousCloseTopAreaUnclickableAccepted() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "text":"关闭", "class":"TextView", "bounds":[100,200,300,260] }
            ]}
            """.trimIndent(),
        )
        assertNotNull(KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600))
    }

    @Test
    fun cornerXMidScreenUnclickableRejected() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "text":"×", "class":"TextView", "bounds":[300,1300,360,1360] }
            ]}
            """.trimIndent(),
        )
        assertNull(KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600))
    }

    @Test
    fun cornerXTopRightUnclickableAccepted() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "text":"×", "class":"TextView", "bounds":[900,200,960,260] }
            ]}
            """.trimIndent(),
        )
        assertNotNull(KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600))
    }

    @Test
    fun cornerXTopLeftUnclickableRejected() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "text":"×", "class":"TextView", "bounds":[50,200,110,260] }
            ]}
            """.trimIndent(),
        )
        assertNull(KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600))
    }

    @Test
    fun plainSkipMidScreenUnclickableStillAccepted() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1000,2600], "children":[
              { "text":"跳过", "class":"TextView", "bounds":[100,1300,300,1360] }
            ]}
            """.trimIndent(),
        )
        assertNotNull(KeywordStrategy.pick(root, screenWidth = 1000, screenHeight = 2600))
    }
}
