package com.wavexzore.toolbox.core.design.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wavexzore.toolbox.core.design.theme.Wave
import kotlin.math.roundToInt

/**
 * wave 组件库（米白直角方案，令牌对齐 ledger）：卡片 / 行 / 标签 / 开关 / 分段 / Tab / 搜索 / 统计。
 * 全部 0 圆角；层级用 1px 描边与底色，不用阴影；主色=深绿，链接=深绿。
 */

/** 语义色调（仅状态使用）。 */
enum class WaveTone(val bg: Color, val fg: Color) {
    Neutral(Wave.Well, Wave.InkStrong),
    Green(Wave.GreenBg, Wave.GreenTx),
    Red(Wave.RedBg, Wave.RedTx),
    Yellow(Wave.YellowBg, Wave.YellowTx),
    Blue(Wave.BlueBg, Wave.BlueTx),
}

// ---------------------------------------------------------------- 基础容器

@Composable
fun WaveScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = Wave.Screen,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = topBar,
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
fun WaveCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = Wave.Card,
        border = BorderStroke(1.dp, Wave.Line),
        shape = RoundedCornerShape(0),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun WaveSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Wave.Ink,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 3.dp, top = 24.dp, bottom = 10.dp),
    )
}

@Composable
fun WaveDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = Wave.Line)
}

@Composable
fun WaveNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Wave.Ink2,
        fontSize = 11.5.sp,
        lineHeight = 17.sp,
        modifier = modifier.padding(horizontal = 3.dp, vertical = 8.dp),
    )
}

// ---------------------------------------------------------------- 顶栏 / 按钮

@Composable
fun WaveIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = Wave.Ink,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun WaveTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            WaveIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
        }
        Text(
            text = title,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Wave.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack != null) 2.dp else 12.dp),
        )
        actions()
    }
}

@Composable
fun WaveTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Wave.InkStrong) {
    Text(
        text = text,
        color = color,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@Composable
fun WavePrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .background(Wave.InkStrong)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun WaveGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Wave.Ink2, fontSize = 13.5.sp)
    }
}

// ---------------------------------------------------------------- 行 / 标签

@Composable
fun WaveRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    minHeight: Dp = 58.dp,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = minHeight)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Wave.Ink)
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.5.sp, color = Wave.Ink2, modifier = Modifier.padding(top = 2.dp))
            }
        }
        trailing?.let {
            Row(verticalAlignment = Alignment.CenterVertically) { it() }
        }
    }
}

@Composable
fun WaveChevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = Wave.Ink3,
        modifier = Modifier.size(19.dp),
    )
}

@Composable
fun WaveIconBox(icon: ImageVector, tone: WaveTone = WaveTone.Neutral, size: Dp = 36.dp, iconSize: Dp = 18.dp) {
    Box(
        modifier = Modifier.size(size).background(tone.bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (tone == WaveTone.Neutral) Wave.Sage else tone.fg,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun WavePill(text: String, tone: WaveTone = WaveTone.Neutral, mono: Boolean = false) {
    Box(
        modifier = Modifier
            .background(tone.bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = tone.fg,
            fontSize = if (mono) 10.sp else 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

@Composable
fun WaveChip(text: String, onClick: (() -> Unit)? = null, onRemove: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .background(Wave.Card)
            .border(1.dp, Wave.Line)
            .height(30.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 12.dp, end = if (onRemove != null) 6.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, fontSize = 12.5.sp, color = Wave.Ink)
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "移除", tint = Wave.Ink3, modifier = Modifier.size(13.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- 控件

@Composable
fun WaveSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val knobOffset by animateDpAsState(if (checked) 21.dp else 3.dp, label = "switch")
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .background(if (checked) Wave.InkStrong else Wave.SwitchOff)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = knobOffset)
                .size(20.dp)
                .background(Color.White)
                .border(1.dp, Color(0x141F1E1B)),
        )
    }
}

@Composable
fun WaveSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    WaveRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        trailing = { WaveSwitch(checked, onCheckedChange) },
    )
}

/** 分段选择（对齐 ledger segmented）。返回选中下标。 */
@Composable
fun WaveSeg(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Wave.SegBg)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .then(if (active) Modifier.background(Wave.Card).border(1.dp, Wave.Line) else Modifier)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 12.5.sp,
                    color = if (active) Wave.InkStrong else Wave.Ink2,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 页内 Tab（下划线指示，选中深绿）。 */
@Composable
fun WaveTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        titles.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(45.dp)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    color = if (active) Wave.InkStrong else Wave.Ink2,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (active) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Wave.InkStrong),
                    )
                }
            }
        }
    }
    WaveDivider()
}

@Composable
fun WaveSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Wave.Card)
            .border(1.dp, Wave.Line)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Wave.Ink3, modifier = Modifier.size(17.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, color = Wave.Ink3, fontSize = 13.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = Wave.Ink),
                cursorBrush = SolidColor(Wave.InkStrong),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 筛选 chip（选中为深绿底白字）。 */
@Composable
fun WaveFilterChip(text: String, active: Boolean, onClick: () -> Unit) {    Box(
        modifier = Modifier
            .background(if (active) Wave.InkStrong else Wave.Card)
            .border(1.dp, if (active) Wave.InkStrong else Wave.Line)
            .height(30.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (active) Color.White else Wave.Ink2, fontSize = 12.sp)
    }
}

/** 直角滑杆：细轨道 + 竖条滑块；点按/拖动即更新（按 step 吸附）。 */
@Composable
fun WaveSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    step: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp),
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val trackWidth = maxWidth
        val span = (valueRange.last - valueRange.first).coerceAtLeast(1)
        val fraction = ((value - valueRange.first).toFloat() / span).coerceIn(0f, 1f)

        fun emit(x: Float) {
            if (widthPx <= 0f) return
            val f = (x / widthPx).coerceIn(0f, 1f)
            val snapped = ((valueRange.first + f * span) / step).roundToInt() * step
            onValueChange(snapped.coerceIn(valueRange.first, valueRange.last))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(valueRange, step, widthPx) {
                    detectTapGestures { emit(it.x) }
                }
                .pointerInput(valueRange, step, widthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { emit(it.x) },
                        onHorizontalDrag = { change, _ -> emit(change.position.x) },
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Wave.Line),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(Wave.InkStrong),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (trackWidth - 10.dp) * fraction)
                    .size(width = 10.dp, height = 22.dp)
                    .background(Wave.InkStrong),
            )
        }
    }
}

// ---------------------------------------------------------------- 统计

@Composable
fun WaveStatCell(value: String, unit: String?, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.4).sp,
                    fontFeatureSettings = "tnum",
                ),
                color = Wave.Ink,
            )
            if (unit != null) {
                Text(unit, color = Wave.Ink3, fontSize = 10.5.sp, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            }
        }
        Text(label, color = Wave.Ink3, fontSize = 10.5.sp)
    }
}

@Composable
fun WaveStatsRow(stats: List<Triple<String, String?, String>>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { (value, unit, label) ->
            WaveStatCell(value = value, unit = unit, label = label, modifier = Modifier.weight(1f))
        }
    }
}
