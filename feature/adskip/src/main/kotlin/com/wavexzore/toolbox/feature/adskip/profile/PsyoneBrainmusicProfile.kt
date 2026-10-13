package com.wavexzore.toolbox.feature.adskip.profile

import com.wavexzore.toolbox.feature.adskip.AdSkipGroup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 小睡眠（com.psyone.brainmusic）识别器（T-205；Q-205/F4）。
 *
 * 准入理由（pack 不可表达）：`itv_jump` 组件同时出现在主页与开屏组件中（F4 实测），
 * 通用 keyword/vid 回退会在主页误把它当"跳过"点击 → 通过 [suppressOn] 在主页（MainActivity）
 * 抑制通用来源；开屏阶段（Splash/SplashHotAdActivity）不受影响。
 */
@Singleton
class PsyoneBrainmusicProfile @Inject constructor() : AppAdProfile {

    override val pkgs: Set<String> = setOf(PKG)

    override fun suppressOn(activity: String?): List<String> =
        if (activity?.contains("MainActivity") == true) listOf(ITV_JUMP_SELECTOR) else emptyList()

    override fun keywordExtra(): List<String> = emptyList()

    override fun groups(): List<AdSkipGroup> = emptyList()

    companion object {
        const val PKG = "com.psyone.brainmusic"

        /** F4：主页同名组件（vid 全等/资源后缀语义）。 */
        const val ITV_JUMP_SELECTOR = """@[vid="itv_jump"]"""
    }
}
