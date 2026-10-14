package com.wavexzore.toolbox.feature.adskip

/**
 * 系统选择器豁免（D-226；修复「相册-发送等被误点秒关」，2026-10-14）：
 *
 * Android 系统选择器 `com.android.intentresolver`（分享面板 / 「打开方式」）是独立包、
 * 窗口全屏、类名不命中弹窗判别（PopupClassifier），会被管线当作「新包进入前台 = 新开屏
 * 窗口」，再由通用兜底（GroupFallback）以关键词「关闭」点到面板的 × 而秒关（真机实测
 * 误点间隔约 0.6s；四个判别跳各自都没有错，组合结果错）。
 *
 * 选择器不是广告：命中名单的包整体不参与自动点击；与运行模式无关（splash/any 一致豁免，
 * Q-212）；与 [PkgMode]/内置规则包无关（该包不在「按应用」列表、无内置包）。
 * 名单扩展需真机实测证据（如文件选择器 `com.android.documentsui` 暂未收）。
 */
internal object SystemPicker {

    /** 系统选择器包名（初始仅分享/「打开方式」选择器）。 */
    val PKGS: Set<String> = setOf("com.android.intentresolver")

    /** 该包是否豁免自动点击。 */
    fun exempt(pkg: String): Boolean = pkg in PKGS
}
