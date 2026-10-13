package com.wavexzore.toolbox.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 全局设置仓库（04 §11 配置语义；来源优先级：默认值 < DataStore）。
 * T-002 落地存储与读改；T-004 起被引擎消费。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 引擎总开关（`engine.enabled`，默认 true）。 */
    val engineEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[KEY_ENGINE_ENABLED] ?: DEFAULT_ENGINE_ENABLED }

    /** CONTENT_CHANGED 节流毫秒（`engine.contentThrottleMs`，50~1000，默认 100）。 */
    val contentThrottleMs: Flow<Int> = context.settingsDataStore.data
        .map { prefs -> (prefs[KEY_CONTENT_THROTTLE_MS] ?: DEFAULT_CONTENT_THROTTLE_MS).coerceIn(MIN_CONTENT_THROTTLE_MS, MAX_CONTENT_THROTTLE_MS) }

    /** 应用白名单（`engine.allowPackages`，空 = 全部应用）。 */
    val allowPackages: Flow<Set<String>> = context.settingsDataStore.data
        .map { prefs -> prefs[KEY_ALLOW_PACKAGES] ?: emptySet() }

    /** 坐标兜底（`engine.clickFallback`，performAction 失败/不可用时改用手势点按，默认 true）。 */
    val clickFallback: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[KEY_CLICK_FALLBACK] ?: DEFAULT_CLICK_FALLBACK }

    /** 点击校验毫秒（`engine.clickVerifyMs`，动作后多久复核节点仍在，10~2000，默认 200）。 */
    val clickVerifyMs: Flow<Int> = context.settingsDataStore.data
        .map { prefs -> (prefs[KEY_CLICK_VERIFY_MS] ?: DEFAULT_CLICK_VERIFY_MS).coerceIn(MIN_CLICK_VERIFY_MS, MAX_CLICK_VERIFY_MS) }

    /** 首启引导是否已完成（`setup.completed`，默认 false）。 */
    val setupCompleted: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[KEY_SETUP_COMPLETED] ?: false }

    suspend fun setEngineEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_ENGINE_ENABLED] = enabled }
    }

    suspend fun setClickFallback(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_CLICK_FALLBACK] = enabled }
    }

    suspend fun setClickVerifyMs(ms: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_CLICK_VERIFY_MS] = ms.coerceIn(MIN_CLICK_VERIFY_MS, MAX_CLICK_VERIFY_MS)
        }
    }

    suspend fun setSetupCompleted(completed: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_SETUP_COMPLETED] = completed }
    }

    suspend fun setContentThrottleMs(ms: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_CONTENT_THROTTLE_MS] = ms.coerceIn(MIN_CONTENT_THROTTLE_MS, MAX_CONTENT_THROTTLE_MS)
        }
    }

    suspend fun setAllowPackages(packages: Set<String>) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_ALLOW_PACKAGES] = packages }
    }

    companion object {
        val KEY_ENGINE_ENABLED = booleanPreferencesKey("engine.enabled")
        val KEY_CONTENT_THROTTLE_MS = intPreferencesKey("engine.contentThrottleMs")
        val KEY_ALLOW_PACKAGES = stringSetPreferencesKey("engine.allowPackages")
        val KEY_CLICK_FALLBACK = booleanPreferencesKey("engine.clickFallback")
        val KEY_CLICK_VERIFY_MS = intPreferencesKey("engine.clickVerifyMs")
        val KEY_SETUP_COMPLETED = booleanPreferencesKey("setup.completed")

        const val DEFAULT_ENGINE_ENABLED = true
        const val DEFAULT_CONTENT_THROTTLE_MS = 100
        const val MIN_CONTENT_THROTTLE_MS = 50
        const val MAX_CONTENT_THROTTLE_MS = 1000

        const val DEFAULT_CLICK_FALLBACK = true
        const val DEFAULT_CLICK_VERIFY_MS = 200
        const val MIN_CLICK_VERIFY_MS = 10
        const val MAX_CLICK_VERIFY_MS = 2000
    }
}
