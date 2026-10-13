package com.wavexzore.toolbox.core.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * wave 主题（米白直角方案，令牌对齐 wavexzore-ledger）：强制浅色（家族即浅色系统），
 * 全组件 0 圆角。字阶与 ledger 一致：正文 14、次级 11~12、标题 600/650 字重负字距。
 */
private val WaveColorScheme = lightColorScheme(
    primary = Wave.InkStrong,
    onPrimary = Color.White,
    primaryContainer = Wave.Well,
    onPrimaryContainer = Wave.InkStrong,
    secondary = Wave.Ink2,
    onSecondary = Color.White,
    secondaryContainer = Wave.Well,
    onSecondaryContainer = Wave.Ink,
    tertiary = Wave.GreenTx,
    onTertiary = Color.White,
    background = Wave.Screen,
    onBackground = Wave.Ink,
    surface = Wave.Card,
    onSurface = Wave.Ink,
    surfaceVariant = Wave.Well,
    onSurfaceVariant = Wave.Ink2,
    outline = Wave.Line,
    outlineVariant = Wave.LineStrong,
    error = Wave.RedTx,
    onError = Color.White,
    errorContainer = Wave.RedBg,
    onErrorContainer = Wave.RedTx,
    scrim = Wave.Scrim,
)

/** 全直角：所有 Material 组件 0 圆角。 */
private val WaveShapes = Shapes(
    extraSmall = RoundedCornerShape(0),
    small = RoundedCornerShape(0),
    medium = RoundedCornerShape(0),
    large = RoundedCornerShape(0),
    extraLarge = RoundedCornerShape(0),
)

/** 字阶（对齐 ledger：14 基准 / 码值 10~11 / 标题负字距 600~650）。 */
private val WaveTypography = Typography(
    headlineLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight(650), letterSpacing = (-0.8).sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight(650), letterSpacing = (-0.6).sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight(650), letterSpacing = (-0.6).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight(600), letterSpacing = (-0.3).sp),
    titleSmall = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight(600)),
    bodyLarge = TextStyle(fontSize = 14.5.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 11.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight(550)),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight(500)),
    labelSmall = TextStyle(fontSize = 10.5.sp),
)

@Composable
fun WaveToolboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WaveColorScheme,
        shapes = WaveShapes,
        typography = WaveTypography,
        content = content,
    )
}
