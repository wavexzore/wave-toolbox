package com.wavexzore.toolbox.feature.adskip

/**
 * 系统界面豁免（D-226；修复「相册-发送等被误点秒关」，2026-10-14；
 * 2026-10-19 扩展系统设置，防止把无障碍/特性开关点关）：
 *
 * Android 系统选择器 `com.android.intentresolver`（分享面板 / 「打开方式」）是独立包、
 * 窗口全屏、类名不命中弹窗判别（PopupClassifier），会被管线当作「新包进入前台 = 新开屏
 * 窗口」，再由通用兜底（GroupFallback）以关键词「关闭」点到面板的 × 而秒关（真机实测
 * 误点间隔约 0.6s；四个判别跳各自都没有错，组合结果错）。
 *
 * `com.android.settings` 同理：无障碍服务开关就在系统设置内，页面上的「关闭」/开关
 * 会被关键词链当作广告关闭点掉，表现为「自动跳过功能自己关了」。系统设置没有开屏
 * 广告，整体豁免无召回损失。
 *
 * 选择器不是广告：命中名单的包整体不参与自动点击；与运行模式无关（splash/any 一致豁免，
 * Q-212）；与 [PkgMode] 无关；系统设置的内置规则包保留但不再可达。
 * 2026-10-22（D-238）：补 `com.android.systemui` / `miui.systemui.plugin`——通知栏、
 * 锁屏、系统插件不是广告，真机日志显示它们仍会被通用兜底扫描（无误点但浪费预算）。
 * 名单扩展需真机实测证据（如文件选择器 `com.android.documentsui` 暂未收）。
 */
internal object SystemPicker {

    /** 系统界面包名（分享/「打开方式」选择器 + 系统设置 + 系统 UI 宿主）。 */
    val PKGS: Set<String> = setOf(
        "com.android.intentresolver",
        "com.android.settings",
        "com.android.systemui",
        "miui.systemui.plugin",
    )

    /** 息屏显示（AOD）包：桌面/息屏不是广告，对齐李跳跳白名单。 */
    const val AOD_PKG = "com.miui.aod"

    /**
     * 该包是否豁免自动点击。
     *
     * [homePackages] 为当前设备的 HOME/桌面包集合（由调用方查询后传入，纯函数便于测试）。
     * 桌面、负一屏、AOD 都不是广告，自动点击只会误触。
     */
    fun exempt(pkg: String, homePackages: Set<String> = emptySet()): Boolean =
        pkg in PKGS || pkg == AOD_PKG || pkg in homePackages
}
