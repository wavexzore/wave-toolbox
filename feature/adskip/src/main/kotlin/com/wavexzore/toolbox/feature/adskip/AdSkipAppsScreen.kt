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

/** 按应用控制（原型 03；T-206 三态）：搜索 + 逐 App 开关 + 单独模式（跟随/仅开屏/全屏蔽）。 */
@Composable
fun AdSkipAppsScreen(
    feature: AdSkipFeature,
    config: AdSkipConfig,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val disabled by config.disabledPackages.collectAsStateWithLifecycle(initialValue = emptySet())
    val perApp by config.perAppMode.collectAsStateWithLifecycle(initialValue = emptyMap())
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

    fun modeOf(pkg: String): String =
        perApp[pkg] ?: if (pkg in disabled) AdSkipConfig.MODE_OFF else AdSkipConfig.MODE_FOLLOW

    fun modeLabel(mode: String): String = when (mode) {
        AdSkipConfig.MODE_OFF -> "关闭"
        AdSkipGroup.MODE_ANY -> "全屏蔽"
        AdSkipGroup.MODE_SPLASH -> "仅开屏"
        else -> "跟随"
    }

    fun nextMode(mode: String): String = when (mode) {
        AdSkipConfig.MODE_FOLLOW -> AdSkipGroup.MODE_SPLASH
        AdSkipGroup.MODE_SPLASH -> AdSkipGroup.MODE_ANY
        else -> AdSkipConfig.MODE_FOLLOW
    }

    val offCount = apps.count { modeOf(it.pkg) == AdSkipConfig.MODE_OFF }
    val customCount = apps.count { perApp[it.pkg] != null }

    WaveScaffold(
        topBar = {
            WaveTopBar(
                title = "按应用控制",
                onBack = onBack,
                actions = {
                    if (offCount > 0 || customCount > 0) {
                        WaveTextAction("全部跟随", onClick = { scope.launch { config.clearDisabled() } })
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(6.dp))
            WaveSearchField(value = query, onValueChange = { query = it }, placeholder = "搜索应用或包名")
            WaveNote("默认跟随总开关；点右侧文字可单独设为「仅开屏 / 全屏蔽」。共 ${apps.size} 个 · 已关闭 $offCount 个 · 单独模式 $customCount 个。")

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.pkg }) { app ->
                    val mode = modeOf(app.pkg)
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
                        if (mode != AdSkipConfig.MODE_OFF) {
                            WaveTextAction(
                                text = modeLabel(mode),
                                onClick = { scope.launch { config.setPackageMode(app.pkg, nextMode(mode)) } },
                            )
                        }
                        WaveSwitch(
                            checked = mode != AdSkipConfig.MODE_OFF,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    config.setPackageMode(
                                        app.pkg,
                                        if (enabled) AdSkipConfig.MODE_FOLLOW else AdSkipConfig.MODE_OFF,
                                    )
                                }
                            },
                        )
                    }
                    WaveDivider()
                }
            }
            WaveNote("关闭/单独模式仅影响自动点击；统计不受影响。三态条目为兼容旧版本保留迁移。")
            Spacer(Modifier.height(10.dp))
        }
    }
}
