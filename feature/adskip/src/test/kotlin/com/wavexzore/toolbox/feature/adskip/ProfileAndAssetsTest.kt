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
    fun builtinDemoRuleDisabled() {
        val text = File("../../core/rules/src/main/assets/rules/builtin/com.tencent.mm.json5").readText()
        assertTrue(text.contains("enabled: false"))
    }
}
