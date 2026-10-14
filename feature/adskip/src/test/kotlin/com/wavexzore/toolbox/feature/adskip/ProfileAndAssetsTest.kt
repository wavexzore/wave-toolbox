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
    fun xiaomiMarketOrchardStaysAndSuppressesGhostClose() {
        // B-206 调账（2026-10-14 二次）：种果树不再自动退出（该机恒手动进入，用户反馈）——
        // 退出组摘除；幽灵「关闭」仍抑制；应用内窗口刷新保持启用。
        val profile = com.wavexzore.toolbox.feature.adskip.profile.XiaomiMarketProfile()
        assertTrue(profile.groups().isEmpty())
        assertTrue(profile.inAppWindowRefresh())
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

    @Test
    fun miniProgramProfileSuppressesCapsuleOnContainerPagesOnly() {
        // B-207/B-209：小程序容器页与微信授权页抑制「关闭」胶囊；普通页不抑制。
        val profile = com.wavexzore.toolbox.feature.adskip.profile.MiniProgramCapsuleProfile()
        assertEquals(
            setOf("com.tencent.mm", "com.eg.android.AlipayGphone", "com.tencent.mobileqq"),
            profile.pkgs,
        )
        val capsule = listOf("""[text*="关闭"]""", """[desc*="关闭"]""")
        assertEquals(capsule, profile.suppressOn("com.tencent.mm.plugin.appbrand.ui.AppBrandUI00"))
        assertEquals(capsule, profile.suppressOn("com.tencent.mm.plugin.appbrand.launching.AppBrandLaunchProxyUI"))
        assertEquals(capsule, profile.suppressOn("com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI3"))
        assertEquals(
            capsule,
            profile.suppressOn("com.tencent.luggage.wxa.standalone_open_runtime.container.WxaContainerMultiTaskActivity0"),
        )
        assertEquals(capsule, profile.suppressOn("com.tencent.widget.loading.QUILoadingDialog"))
        assertEquals(capsule, profile.suppressOn("com.alipay.mobile.nebulax.xriver.activity.XRiverActivity\$App01"))
        assertEquals(capsule, profile.suppressOn("com.alipay.mobile.nebulax.xriver.activity.XRiverLoadingActivity"))
        assertEquals(capsule, profile.suppressOn("com.tencent.mm.plugin.webview.ui.tools.SDKOAuthUI"))
        assertTrue(profile.suppressOn("com.tencent.mm.ui.LauncherUI").isEmpty())
        assertTrue(profile.suppressOn(null).isEmpty())
        assertEquals(listOf("允许", "同意"), profile.keywordExtra())
        assertFalse(profile.forceGesture())
    }

    @Test
    fun msaProfileRetriesWithGestureBudget() {
        // B-206 续：MSA 开屏——强制手势（performAction 不生效）+ 补点预算（入场吞点击修正）。
        val profile = com.wavexzore.toolbox.feature.adskip.profile.SystemAdSolutionProfile()
        assertEquals(setOf("com.miui.systemAdSolution"), profile.pkgs)
        assertTrue(profile.forceGesture())
        val group = profile.groups().single()
        assertEquals(5, group.actionMaximum)
        assertEquals(350L, group.actionCd)
        assertEquals(true, group.popupAware)
        assertTrue(GroupGating.active(group, AdSkipGroup.MODE_SPLASH, "android.widget.FrameLayout"))
        assertTrue(profile.suppressOn(null).isEmpty())
    }
}
