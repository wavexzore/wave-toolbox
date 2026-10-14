package com.wavexzore.toolbox.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wavexzore.toolbox.core.design.theme.Wave

/**
 * 品牌锁定标（字标 V1 横排）：[品牌块] wave工具箱 (+ 可选尾注)。
 *
 * 图形 = 应用图标迷你块（方波 W、0 圆角方形）；文字 = 系统无衬线 semibold；
 * 与图标同令牌（forest 底 / lime 笔触）。浅底场景使用（深底规范见 docs/design/app-icon）。
 */
@Composable
internal fun WaveBrandLockup(
    modifier: Modifier = Modifier,
    chipSize: Dp = 22.dp,
    titleSize: TextUnit = 16.5.sp,
    titleColor: Color = Wave.Ink,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_brand_chip),
            contentDescription = null,
            modifier = Modifier.size(chipSize),
        )
        Spacer(Modifier.width(if (chipSize > 18.dp) 9.dp else 7.dp))
        Text(
            "wave工具箱",
            fontSize = titleSize,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
            color = titleColor,
        )
        trailing?.invoke()
    }
}
