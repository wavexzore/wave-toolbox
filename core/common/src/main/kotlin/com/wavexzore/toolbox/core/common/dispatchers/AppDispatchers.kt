package com.wavexzore.toolbox.core.common.dispatchers

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Main

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Io

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Default

/** 事件管线专用单线程调度器（D-006：event/query/action 三组隔离，防 ANR 与顺序错乱）。 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Event

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Query

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Action

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @Singleton
    @Main
    fun provideMain(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @Singleton
    @Io
    fun provideIo(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @Default
    fun provideDefault(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @Event
    fun provideEvent(): CoroutineDispatcher = singleThread("wave-event")

    @Provides
    @Singleton
    @Query
    fun provideQuery(): CoroutineDispatcher = singleThread("wave-query")

    @Provides
    @Singleton
    @Action
    fun provideAction(): CoroutineDispatcher = singleThread("wave-action")

    private fun singleThread(name: String): CoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, name) }
            .asCoroutineDispatcher()
}

/**
 * 注入用调度器聚合（`de.inject`：`class X @Inject constructor(dispatchers: AppDispatchers)`）。
 */
@Singleton
class AppDispatchers @Inject constructor(
    @Main val main: CoroutineDispatcher,
    @Io val io: CoroutineDispatcher,
    @Default val default: CoroutineDispatcher,
    @Event val event: CoroutineDispatcher,
    @Query val query: CoroutineDispatcher,
    @Action val action: CoroutineDispatcher,
)
