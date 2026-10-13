package com.wavexzore.toolbox.feature.adskip

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.adSkipDataStore: DataStore<Preferences> by preferencesDataStore(name = "adskip_settings")

/** 开屏跳过配置（T-004；键语义见特性 README §6.2）。 */
@Singleton
class AdSkipConfig @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** `splash`（仅开屏窗口期，默认）/ `any`（任意弹窗）。 */
    val mode: Flow<String> = context.adSkipDataStore.data
        .map { prefs -> prefs[KEY_MODE] ?: DEFAULT_MODE }

    /** 用户追加关键词。 */
    val extraKeywords: Flow<Set<String>> = context.adSkipDataStore.data
        .map { prefs -> prefs[KEY_EXTRA_KEYWORDS] ?: emptySet() }

    /** 用户关闭的包（未设置 = 跟随总开关）。 */
    val disabledPackages: Flow<Set<String>> = context.adSkipDataStore.data
        .map { prefs -> prefs[KEY_DISABLED_PACKAGES] ?: emptySet() }

    suspend fun setMode(mode: String) {
        context.adSkipDataStore.edit { prefs ->
            prefs[KEY_MODE] = if (mode == AdSkipGroup.MODE_ANY) AdSkipGroup.MODE_ANY else AdSkipGroup.MODE_SPLASH
        }
    }

    suspend fun setExtraKeywords(keywords: Set<String>) {
        context.adSkipDataStore.edit { prefs ->
            prefs[KEY_EXTRA_KEYWORDS] = keywords.filter { it.isNotBlank() }.toSet()
        }
    }

    suspend fun setPackageDisabled(pkg: String, disabled: Boolean) {
        context.adSkipDataStore.edit { prefs ->
            val current = prefs[KEY_DISABLED_PACKAGES] ?: emptySet()
            prefs[KEY_DISABLED_PACKAGES] = if (disabled) current + pkg else current - pkg
        }
    }

    /** 全部开启（清空禁用集合）。 */
    suspend fun clearDisabled() {
        context.adSkipDataStore.edit { prefs -> prefs[KEY_DISABLED_PACKAGES] = emptySet() }
    }

    companion object {
        val KEY_MODE = stringPreferencesKey("adskip.mode")
        val KEY_EXTRA_KEYWORDS = stringSetPreferencesKey("adskip.keywords.extra")
        val KEY_DISABLED_PACKAGES = stringSetPreferencesKey("adskip.perAppDisabled")

        const val DEFAULT_MODE = AdSkipGroup.MODE_SPLASH
    }
}
