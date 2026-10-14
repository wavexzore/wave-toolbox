package com.wavexzore.toolbox.feature.adskip

import com.wavexzore.toolbox.feature.adskip.profile.PsyoneBrainmusicProfile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** T-205：小睡眠 profile（F4 抑制）+ mm 内置停用迁移（Q-209）资产回归。 */
class ProfileAndAssetsTest {

    @Test
    fun psyoneSuppressionOnlyOnMainActivity() {
        val profile = PsyoneBrainmusicProfile()
        assertEquals(setOf("com.psyone.brainmusic"), profile.pkgs)
        assertEquals(
            listOf(PsyoneBrainmusicProfile.ITV_JUMP_SELECTOR),
            profile.suppressOn("com.psyone.brainmusic.ui.activity.MainActivity"),
        )
        assertTrue(profile.suppressOn("com.psyone.brainmusic.ui.activity.SplashHotAdActivity").isEmpty())
        assertTrue(profile.suppressOn(null).isEmpty())
    }

    @Test
    fun mmPackCarriesMigratedBuiltinRule() {
        val text = File("src/main/assets/rules/adskip/com.tencent.mm.json5").readText()
        val pack = AdSkipPackCodec.parse(text)
        assertEquals(2, pack.schemaVersion)
        val entry = pack.groups.first { it.id == "com.tencent.mm#splash-entry" }
        assertEquals(listOf("com.tencent.mm.ui.LauncherUI"), entry.activityIds)
        assertTrue(entry.matches.contains("[text=\"点击进入微信\"]"))
        // activityIds 接线（T-204）后与基座规则约束等效
        assertTrue(GroupGating.active(entry, AdSkipGroup.MODE_SPLASH, "com.tencent.mm.ui.LauncherUI"))
        assertFalse(GroupGating.active(entry, AdSkipGroup.MODE_SPLASH, "com.tencent.mm.chat.ui.ChattingUI"))
    }

    @Test
    fun xiaomiMarketProfileOptsIntoInAppWindow() {
        // B-205：市场启用应用内窗口刷新；未适配应用保持默认（false）。
        val profile = com.wavexzore.toolbox.feature.adskip.profile.XiaomiMarketProfile()
        assertEquals(setOf("com.xiaomi.market"), profile.pkgs)
        assertTrue(profile.inAppWindowRefresh())
        assertFalse(PsyoneBrainmusicProfile().inAppWindowRefresh())
    }

    @Test
    fun xiaomiMarketOrchardExitGroupIsScopedAndSuppressesGhostClose() {
        // B-205：种果树页退出组仅在该 Activity 生效；幽灵「关闭」只在该页抑制。
        val profile = com.wavexzore.toolbox.feature.adskip.profile.XiaomiMarketProfile()
        val group = profile.groups().first { it.id == "com.xiaomi.market#orchard-exit" }
        assertEquals(listOf("com.xiaomi.orchard.sdk.ui.OrchardActivity"), group.activityIds)
        assertEquals(10.8f, group.position?.xPercent)
        assertEquals(6.9f, group.position?.yPercent)
        assertEquals(3, group.actionMaximum)
        assertTrue(GroupGating.active(group, AdSkipGroup.MODE_SPLASH, "com.xiaomi.orchard.sdk.ui.OrchardActivity"))
        assertFalse(GroupGating.active(group, AdSkipGroup.MODE_SPLASH, "com.xiaomi.market.business_ui.main.MarketTabActivity"))
        assertEquals(
            listOf("""[text="关闭"]""", """[desc="关闭"]"""),
            profile.suppressOn("com.xiaomi.orchard.sdk.ui.OrchardActivity"),
        )
        assertTrue(profile.suppressOn("com.xiaomi.market.business_ui.main.MarketTabActivity").isEmpty())
        assertTrue(profile.suppressOn(null).isEmpty())
    }

    @Test
    fun builtinDemoRuleDisabled() {
        val text = File("../../core/rules/src/main/assets/rules/builtin/com.tencent.mm.json5").readText()
        assertTrue(text.contains("enabled: false"))
    }
}
