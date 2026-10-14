package com.wavexzore.toolbox.feature.adskip.profile

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 微信（com.tencent.mm）识别器（B-206 续）。
 *
 * 准入理由（pack 不可表达——需按页选择性别抑制关键词）：小程序页（AppBrandUI*）右上角
 * 「关闭」胶囊是页面固有按钮而非广告；通用关键词「关闭」会在进入窗口期把它当跳过点掉
 * → 小程序被立即退出（实测 21:05：打开小程序 165ms 后 skip keyword:关闭 → 返回聊天）。
 *
 * 处置：仅在小程序页抑制该按钮；页面自身开屏广告的「跳过」不受影响
 * （跳过≠关闭，不落入抑制选择器）。
 */
@Singleton
class WeChatProfile @Inject constructor() : AppAdProfile {

    override val pkgs: Set<String> = setOf(PKG)

    /** 小程序页：抑制右上角「关闭」胶囊（text/desc 双形态覆盖）。 */
    override fun suppressOn(activity: String?): List<String> =
        if (activity?.contains(APP_BRAND_UI) == true) {
            listOf("""[text="关闭"]""", """[desc="关闭"]""")
        } else {
            emptyList()
        }

    companion object {
        const val PKG = "com.tencent.mm"

        /** 小程序容器 Activity（AppBrandUI00/01/10… 前缀匹配）。 */
        const val APP_BRAND_UI = "com.tencent.mm.plugin.appbrand.ui.AppBrandUI"
    }
}
