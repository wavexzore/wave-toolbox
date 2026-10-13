package com.wavexzore.toolbox.feature.adskip

import com.wavexzore.toolbox.core.selector.snapshot.SnapshotTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 快照回归（T-008）：20 个场景 fixture（结构取自真机常见开屏/弹窗布局），
 * 覆盖命中/排除/过滤/排序/兜底路径。fixture 以 SnapshotTree JSON 内联，便于回归与增补。
 */
class FixtureStrategyTest {

    private data class Fixture(
        val name: String,
        val json: String,
        val expectedTop: Int?,
        val extraKeywords: List<String> = emptyList(),
        val expectPath: String? = null,
    )

    private val fixtures: List<Fixture> = listOf(
        Fixture(
            "f01-右上角跳过",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"text":"跳过","class":"TextView","clickable":true,"bounds":[880,170,1030,230]}]}""",
            170,
        ),
        Fixture(
            "f02-倒计时跳过",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"text":"跳过 3s","class":"TextView","clickable":true,"bounds":[840,160,1040,240]}]}""",
            160,
        ),
        Fixture(
            "f03-跳过广告底部",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"text":"跳过广告","class":"TextView","clickable":true,"bounds":[880,2180,1020,2240]}]}""",
            2180,
        ),
        Fixture(
            "f04-排除跳过登录",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"text":"跳过登录","class":"TextView","clickable":true,"bounds":[860,180,1020,240]}]}""",
            null,
        ),
        Fixture(
            "f05-关闭按钮desc",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"desc":"关闭","class":"ImageButton","clickable":true,"bounds":[960,150,1040,230]}]}""",
            150,
        ),
        Fixture(
            "f06-我知道了弹窗",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"class":"Dialog","bounds":[100,900,980,1500],"children":[
                  {"text":"我知道了","class":"Button","clickable":true,"bounds":[400,1380,700,1450]}]}]}""",
            1380,
        ),
        Fixture(
            "f07-×符号",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"text":"×","class":"TextView","clickable":true,"bounds":[990,160,1050,220]}]}""",
            160,
        ),
        Fixture(
            "f08-两个跳过取顶部",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"text":"跳过","class":"TextView","clickable":true,"bounds":[880,1200,1020,1260]},
                {"text":"跳过","class":"TextView","clickable":true,"bounds":[880,200,1020,260]}]}""",
            200,
        ),
        Fixture(
            "f09-editable不点",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"text":"跳过","class":"EditText","editable":true,"clickable":true,"bounds":[100,1200,300,1260]}]}""",
            null,
        ),
        Fixture(
            "f10-长文本不点",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"text":"点击跳过广告立即进入","class":"TextView","clickable":true,"bounds":[100,200,500,260]}]}""",
            null,
        ),
        Fixture(
            "f11-追加关键词命中",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"text":"忽略","class":"TextView","clickable":true,"bounds":[900,180,1010,240]}]}""",
            180,
            extraKeywords = listOf("忽略"),
        ),
        Fixture(
            "f12-不可见不点",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"text":"跳过","class":"TextView","visible":false,"clickable":true,"bounds":[880,180,1020,240]}]}""",
            null,
        ),
        Fixture(
            "f13-点击祖先回退",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"class":"TextView","bounds":[860,160,1040,260],"children":[
                  {"text":"跳过","class":"View","bounds":[880,180,1020,240]}]}]}""",
            180,
        ),
        Fixture(
            "f14-顶部优先于更小Y之外",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"text":"关闭","class":"TextView","clickable":true,"bounds":[100,660,300,720]},
                {"text":"关闭","class":"TextView","clickable":true,"bounds":[100,540,300,600]}]}""",
            540,
        ),
        Fixture(
            "f15-同Y取右",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"text":"关闭","class":"TextView","clickable":true,"bounds":[100,200,300,260]},
                {"text":"关闭","class":"TextView","clickable":true,"bounds":[800,200,1000,260]}]}""",
            200,
        ),
        Fixture(
            "f16-弹窗关闭desc",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"class":"Dialog","bounds":[80,1200,1000,1800],"children":[
                  {"desc":"关闭","class":"ImageView","clickable":true,"bounds":[930,1230,990,1290]}]}]}""",
            1230,
        ),
        Fixture(
            "f17-空格跳过",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"text":" 跳过 ","class":"TextView","clickable":true,"bounds":[880,180,1020,240]}]}""",
            180,
        ),
        Fixture(
            "f18-排除关闭提醒",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"text":"关闭提醒","class":"TextView","clickable":true,"bounds":[880,190,1050,250]}]}""",
            null,
        ),
        Fixture(
            "f19-scrollable容器不点",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"text":"跳过","class":"ScrollView","scrollable":true,"clickable":true,"bounds":[0,0,1080,2400]}]}""",
            null,
        ),
        Fixture(
            "f20-右对齐Y相同取右（并列）",
            """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
                {"text":"跳过","class":"TextView","clickable":true,"bounds":[500,180,700,240]},
                {"text":"跳过","class":"TextView","clickable":true,"bounds":[880,180,1020,240]}]}""",
            180,
        ),
    )

    @Test
    fun allFixturesMatchExpectation() {
        assertEquals(20, fixtures.size)
        fixtures.forEach { fixture ->
            val root = SnapshotTree.fromJson(fixture.json)
            val pick = KeywordStrategy.pick(
                root = root,
                extraKeywords = fixture.extraKeywords,
                screenWidth = 1080,
                screenHeight = 2400,
            )
            if (fixture.expectedTop == null) {
                assertNull(pick, "fixture=${fixture.name} 期望无候选")
            } else {
                assertNotNull(pick, "fixture=${fixture.name} 期望命中")
                assertEquals(fixture.expectedTop, pick.node.bounds.top, "fixture=${fixture.name} 命中节点不符")
            }
        }
    }
}
