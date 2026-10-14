package com.wavexzore.toolbox.feature.settings.update

import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** 更新持久化窄接口（3 键；测试可 fake；T-306）。 */
interface UpdatePrefs {
    val autoCheck: Flow<Boolean>
    val lastCheckAt: Flow<Long>
    val skippedTag: Flow<String>

    suspend fun setAutoCheck(enabled: Boolean)
    suspend fun setLastCheckAt(epochMs: Long)
    suspend fun setSkippedTag(tag: String)
}

/** 生产实现：委托既有 [SettingsRepository]（纯增量键，不另建 DataStore）。 */
@Singleton
class DataStoreUpdatePrefs @Inject constructor(
    settings: SettingsRepository,
) : UpdatePrefs {

    override val autoCheck: Flow<Boolean> = settings.updateAutoCheck
    override val lastCheckAt: Flow<Long> = settings.updateLastCheckAt
    override val skippedTag: Flow<String> = settings.updateSkippedTag

    private val repository = settings

    override suspend fun setAutoCheck(enabled: Boolean) = repository.setUpdateAutoCheck(enabled)
    override suspend fun setLastCheckAt(epochMs: Long) = repository.setUpdateLastCheckAt(epochMs)
    override suspend fun setSkippedTag(tag: String) = repository.setUpdateSkippedTag(tag)
}
