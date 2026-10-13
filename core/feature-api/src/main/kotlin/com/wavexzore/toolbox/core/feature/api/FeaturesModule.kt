package com.wavexzore.toolbox.core.feature.api

import dagger.Module
import dagger.hilt.InstallIn
import dagger.multibindings.Multibinds
import dagger.hilt.components.SingletonComponent

/**
 * SPI 注册容器（T-010，D-009）：特性在自身 Hilt 模块用 `@Binds @IntoSet` 注册 [ToolFeature]，
 * 无特性时容器也得存在（`@Multibinds` 声明空集，避免 Dagger 报缺绑定）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FeaturesModule {

    @Multibinds
    abstract fun toolFeatures(): Set<ToolFeature>
}
