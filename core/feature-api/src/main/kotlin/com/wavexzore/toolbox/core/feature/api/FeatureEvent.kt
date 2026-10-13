package com.wavexzore.toolbox.core.feature.api

/** 派发给特性的最小事件（与管线 A11yEvent 解耦，避免特性依赖无障碍实现细节）。 */
data class FeatureEvent(
    val type: String,
    val packageName: String?,
    val className: String?,
    val text: String?,
    val timeMillis: Long,
)

/** SPI 版本（破坏性变更时递增，供特性做兼容分支）。 */
const val FEATURE_API_VERSION = 1
