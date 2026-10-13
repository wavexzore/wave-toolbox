package com.wavexzore.toolbox.feature.adskip

import com.wavexzore.toolbox.core.selector.snapshot.SnapshotTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * T-208 负样本 fixtures（误点回归）：真实误报模式必须不被点击；F4 用例锁定需 profile 抑制的既有行为。
 */
class NegativeFixturesTest {

    private fun pick(json: String, extra: List<String> = emptyList()) =
        KeywordStrategy.pick(SnapshotTree.fromJson(json), extraKeywords = extra, screenWidth = 1080, screenHeight = 2400)

    @Test
    fun loginSkipLikeButtonIsNotPicked() {
        val json = """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
            {"text":"跳过登录","class":"TextView","clickable":true,"bounds":[880,170,1030,230]}]}"""
        assertNull(pick(json))
    }

    @Test
    fun guideAndUpdateSkipAreNotPicked() {
        assertNull(pick("""{ "text":"跳过引导","class":"TextView","clickable":true,"bounds":[880,170,1030,230]}"""))
        assertNull(pick("""{ "text":"跳过更新","class":"TextView","clickable":true,"bounds":[880,170,1030,230]}"""))
    }

    @Test
    fun launcherIconLabelAndBottomNavAreNotPicked() {
        // 回到桌面：图标窗口（FrameLayout + 应用名 ≤6 字）
        assertNull(pick("""{ "class":"android.widget.FrameLayout","text":"微信","bounds":[100,900,300,1100]}"""))
        // 应用内底部导航
        assertNull(pick("""{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
            {"text":"我的","class":"TextView","clickable":true,"bounds":[900,2300,1000,2360]}]}"""))
    }

    @Test
    fun wideBannerContainersAreNotPickedButChildIs() {
        // 底部大横幅（无文本）含"跳过"子按钮 → 应选中子按钮而非容器
        val json = """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
            {"class":"FrameLayout","bounds":[0,1700,1080,2400],"children":[
              {"text":"跳过","class":"TextView","clickable":true,"bounds":[880,1750,1030,1810]}]}]}"""
        val picked = pick(json)
        assertNotNull(picked)
        assertEquals(1750, picked.node.bounds.top)
    }

    @Test
    fun f4ItvJumpIsPickedByVidWithoutSuppression() {
        // F4：小睡眠主页 itv_jump —— 通用 vid 档会命中（因此需要 profile suppressOn 在主页抑制）；
        // 注意：节点需非"宽容器"（宽 500 < 60% 屏宽）才通过关键词档过滤
        val json = """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
            {"vid":"com.psyone.brainmusic:id/itv_jump","class":"android.widget.FrameLayout","clickable":true,"bounds":[100,300,600,380]}]}"""
        val picked = pick(json)
        assertNotNull(picked)
        assertEquals("vid:jump", picked.matched)
    }

    @Test
    fun countdownTextStillPickedByPrefixKeyword() {
        assertNotNull(pick("""{ "text":"跳过 5s","class":"TextView","clickable":true,"bounds":[880,170,1030,230]}"""))
    }
}
