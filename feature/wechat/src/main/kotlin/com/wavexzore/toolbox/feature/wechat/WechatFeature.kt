package com.wavexzore.toolbox.feature.wechat

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
 * 微信僵尸好友清理特性（T-010 空壳）：只注册 SPI 与事件计数；
 * 任务编排（批检 30、删除对话流）随「微信僵尸好友检测与清理」特性任务接入。
 */
@Singleton
class WechatFeature @Inject constructor() : ToolFeature {

    override val id: String = "wechat"
    override val name: String = "微信僵尸好友清理"
    override val description: String = "批量检测单向删除的僵尸好友并清理（保留聊天记录）"
    override val order: Int = 20

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
                text = "流程：识别 → 列表 → 弹窗选择 → 点击删除 → 删除选中用户；批 30、保留最近 5 轮。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WechatModule {

    @Binds
    @IntoSet
    abstract fun bindWechatFeature(feature: WechatFeature): ToolFeature
}
