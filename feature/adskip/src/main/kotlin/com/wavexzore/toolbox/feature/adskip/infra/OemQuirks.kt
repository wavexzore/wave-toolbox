package com.wavexzore.toolbox.feature.adskip.infra

import android.os.Build

/**
 * OEM 厂商差异（横切维度，D-218；R-210）：把不随 pkg 变的厂商怪癖集中一处，未知厂商回落现状默认。
 *
 * - 置信来源：`_recon/00 F2/F3`（MIUI 动画矩阵窗口、视图级 WSC 类名）、`_recon/01 §7`（HyperOS 冻结）；
 * - 消费方：PopupClassifier（动画容忍）、WindowTracker（噪声类名）、设置页（保活提示）；
 * - 测试：构造注入 manufacturer/brand（伪品牌），未知厂商必须与现行行为完全一致。
 */
class OemQuirks(
    val manufacturer: String? = runCatching { Build.MANUFACTURER }.getOrNull(),
    val brand: String? = runCatching { Build.BRAND }.getOrNull(),
) {

    val isMiui: Boolean = listOfNotNull(manufacturer, brand)
        .any { it.contains("xiaomi", ignoreCase = true) || it.contains("redmi", ignoreCase = true) }

    /** 应用开关动画的等比缩放下限：两轴比例均低于该值且接近 → 视为动画窗口（按全屏对待）。 */
    val animationScaleFloor: Float = 0.8f

    /** WSC 类名噪声（视图级类名，不应据此推进窗口类别）。 */
    val noiseClassPatterns: List<String> =
        if (isMiui) MIUI_NOISE_CLASSES else emptyList()

    /** 保活/自启动引导（设置页展示；null=无特定提示）。 */
    val keepAliveHint: String? =
        if (isMiui) "MIUI / HyperOS：请在「设置 → 应用 → 省电策略」允许本应用后台活动，并在最近任务中锁定卡片。" else null

    /** 噪声类名判定（尾缀匹配）。 */
    fun matchesNoiseClass(cls: String?): Boolean =
        cls != null && noiseClassPatterns.any { cls.endsWith(it) }

    companion object {
        /** MIUI/HyperOS 实测噪声（F3）：抽屉/翻页等视图级 WSC 类名。 */
        val MIUI_NOISE_CLASSES = listOf("ViewPager", "ViewPager2", "DrawerLayout")

        /** 测试/显式构造。 */
        val DEFAULT = OemQuirks(manufacturer = null, brand = null)
        val MIUI = OemQuirks(manufacturer = "Xiaomi", brand = "Redmi")
    }
}
