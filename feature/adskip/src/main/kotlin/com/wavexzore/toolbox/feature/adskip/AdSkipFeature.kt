package com.wavexzore.toolbox.feature.adskip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wavexzore.toolbox.core.feature.api.FeatureEvent
import com.wavexzore.toolbox.core.feature.api.ToolFeature
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 开屏广告跳过特性（T-010 空壳）：只注册 SPI 与事件计数，真实规则/任务由特性目录任务接入。
 * 注册路径 = 本模块 Hilt `@IntoSet` + app 依赖本模块，零改引擎。
 */
@Singleton
class AdSkipFeature @Inject constructor() : ToolFeature {

    override val id: String = "adskip"
    override val name: String = "开屏广告自动跳过"
    override val description: String = "识别应用启动页广告并自动点击跳过"
    override val order: Int = 10

    private val _eventCount = MutableStateFlow(0L)
    val eventCount: StateFlow<Long> = _eventCount

    override fun onEvent(event: FeatureEvent) {
        _eventCount.value = _eventCount.value + 1
    }

    @Composable
    override fun SettingsPane(scope: ColumnScope, modifier: Modifier) {
        val count by eventCount.collectAsState()
        Column(modifier = modifier.padding(vertical = 8.dp)) {
            Text(
                text = "空壳已接入引擎：累计派发事件 $count",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "规则与自动化编排随「开屏广告自动跳过」特性任务接入（依赖基座 T-001~T-012）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AdSkipModule {

    @Binds
    @IntoSet
    abstract fun bindAdSkipFeature(feature: AdSkipFeature): ToolFeature
}
