package com.wavexzore.toolbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wavexzore.toolbox.core.design.components.WaveDivider
import com.wavexzore.toolbox.core.design.theme.Wave

/** 底部导航（原型）：功能 / 设置，纸白底 + 1px 描边分隔；选中深绿（对齐 ledger nav）。 */
@Composable
fun WaveBottomBar(current: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Wave.Card)) {
        WaveDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(60.dp),
        ) {
            BottomItem(0, "功能", Icons.Filled.Bolt, current, onSelect, Modifier.weight(1f))
            BottomItem(1, "设置", Icons.Outlined.Settings, current, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BottomItem(
    index: Int,
    label: String,
    icon: ImageVector,
    current: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = index == current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelect(index) }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) Wave.InkStrong else Wave.Ink2,
            modifier = Modifier.size(22.dp),
        )
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) Wave.InkStrong else Wave.Ink2,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
