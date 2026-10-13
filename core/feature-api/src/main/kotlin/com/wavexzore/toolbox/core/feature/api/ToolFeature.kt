package com.wavexzore.toolbox.core.feature.api

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 特性 SPI（T-010，D-009）：新功能只依赖本模块 + 在自身 Hilt 模块注册，零改引擎。
 *
 * - [order] 决定首页展示与事件分发顺序；
 * - [onEvent] 在事件单线程被调用（须快速返回，重活自行投递）；
 * - [SettingsPane] 为可选的设置面板（首页/设置页路由到特性详情时渲染）。
 */
interface ToolFeature {

    /** 稳定 ID（设置键 `feature.<id>.enabled`）。 */
    val id: String

    val name: String

    val description: String

    val order: Int get() = 0

    val defaultEnabled: Boolean get() = true

    /** 事件分发（已按 [id] 过滤特性开关后调用）。 */
    fun onEvent(event: FeatureEvent) {}

    /** 特性设置面板（有独立配置时覆写）。 */
    @Composable
    fun SettingsPane(scope: ColumnScope, modifier: Modifier) {
        Unit
    }
}
