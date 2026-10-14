package com.wavexzore.toolbox.feature.settings.update

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 更新功能 DI（T-305）：三窄接口绑定 + 系统时钟。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {

    @Binds
    @Singleton
    abstract fun bindUpdateHttp(impl: UrlConnectionUpdateHttp): UpdateHttp

    @Binds
    @Singleton
    abstract fun bindUpdatePrefs(impl: DataStoreUpdatePrefs): UpdatePrefs

    @Binds
    @Singleton
    abstract fun bindUpdatePlatform(impl: AndroidUpdatePlatform): UpdatePlatform
}

@Module
@InstallIn(SingletonComponent::class)
object UpdateClockModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }
}
