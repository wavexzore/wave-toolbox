package com.wavexzore.toolbox.feature.adskip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wavexzore.toolbox.core.design.components.WaveDivider
import com.wavexzore.toolbox.core.design.components.WaveNote
import com.wavexzore.toolbox.core.design.components.WaveScaffold
import com.wavexzore.toolbox.core.design.components.WaveSearchField
import com.wavexzore.toolbox.core.design.components.WaveSwitch
import com.wavexzore.toolbox.core.design.components.WaveTextAction
import com.wavexzore.toolbox.core.design.components.WaveTopBar
import com.wavexzore.toolbox.core.design.theme.Wave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 按应用控制（原型 03）：搜索 + 逐 App 开关（关闭 = 写入禁用集合）。 */
@Composable
fun AdSkipAppsScreen(
    feature: AdSkipFeature,
    config: AdSkipConfig,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val disabled by config.disabledPackages.collectAsStateWithLifecycle(initialValue = emptySet())
    var apps by remember { mutableStateOf(emptyList<AdSkipFeature.AppEntry>()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { feature.launcherApps() }
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, ignoreCase = true) || it.pkg.contains(query, ignoreCase = true)
        }
    }

    WaveScaffold(
        topBar = {
            WaveTopBar(
                title = "按应用控制",
                onBack = onBack,
                actions = {
                    if (disabled.isNotEmpty()) {
                        WaveTextAction("全部开启", onClick = { scope.launch { config.clearDisabled() } })
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(6.dp))
            WaveSearchField(value = query, onValueChange = { query = it }, placeholder = "搜索应用或包名")
            WaveNote("默认全部开启；共 ${apps.size} 个 · 已关闭 ${disabled.size} 个。")

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.pkg }) { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Wave.Ink)
                            Text(
                                app.pkg,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Wave.Ink3,
                            )
                        }
                        WaveSwitch(
                            checked = app.pkg !in disabled,
                            onCheckedChange = { enabled ->
                                scope.launch { config.setPackageDisabled(app.pkg, !enabled) }
                            },
                        )
                    }
                    WaveDivider()
                }
            }
            WaveNote("关闭后该应用仍计入统计，但不再自动点击。")
            Spacer(Modifier.height(10.dp))
        }
    }
}
