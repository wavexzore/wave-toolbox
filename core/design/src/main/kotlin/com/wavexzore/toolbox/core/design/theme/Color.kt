package com.wavexzore.toolbox.core.design.theme

import androidx.compose.ui.graphics.Color

/**
 * wave 设计系统 · 米白直角方案。
 *
 * 令牌值与 wavexzore-ledger（src/index.css `@theme`）对齐，保持家族一致：
 * 纸白底 + 深绿主色 + 苔绿次级文字；颜色只做语义（运行/风险/注意/信息）；
 * 本应用全程 0 圆角（家族其他端为圆角，数值冻结、圆角策略不同）。
 */
object Wave {
    // ---- 基础（纸白单色，取自 ledger） ----
    val Bg = Color(0xFFF2F5EE)         // 次级底（深一档纸白）
    val Screen = Color(0xFFF7F8F4)     // 页面底 = ledger paper
    val Card = Color(0xFFFFFFFF)       // 卡片 = ledger white
    val Well = Color(0xFFEDF2E9)       // 浅井/软底 = ledger soft
    val Line = Color(0xFFE7EBE3)       // 描边 / 分割 = ledger line
    val LineStrong = Color(0xFFDCE2D7) // 强分割（列表头/边框）
    val Ink = Color(0xFF263830)        // 主文字 = ledger ink
    val InkStrong = Color(0xFF234F42)  // 深绿主色（主按钮/选中态）= ledger green
    val Deep = Color(0xFF193E33)       // 深墨绿（toast/品牌）
    val Ink2 = Color(0xFF657267)       // 次级文字 = ledger muted
    val Ink3 = Color(0xFF94A08C)       // 弱化文字
    val Sage = Color(0xFF78916B)       // 中性图标（苔绿）
    val SwitchOff = Color(0xFFD7DDD1)  // 开关关态轨道
    val SegBg = Color(0xFFE9EEE3)      // 分段容器底

    // ---- 语义色（浅底 / 深字 成对，取自 ledger 家族色） ----
    val GreenBg = Color(0xFFE6EDE0)
    val GreenTx = Color(0xFF557A48)
    val RedBg = Color(0xFFF3E4DF)
    val RedTx = Color(0xFFB86349)
    val YellowBg = Color(0xFFF8E9DD)
    val YellowTx = Color(0xFFA26542)
    val BlueBg = Color(0xFFE4EAF0)
    val BlueTx = Color(0xFF6B8297)

    // ---- 其他 ----
    val Scrim = Color(0x4D19342A)      // ledger overlay
    val Lime = Color(0xFFD9EDAE)       // 深底上的柠檬点缀
}
