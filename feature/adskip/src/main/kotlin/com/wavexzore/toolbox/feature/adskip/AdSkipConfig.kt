package com.wavexzore.toolbox.feature.adskip

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.adSkipDataStore: DataStore<Preferences> by preferencesDataStore(name = "adskip_settings")

/** 开屏跳过配置（T-004；键语义见特性 README §6.2；T-206 增三态与入口窗口）。 */
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

    /** 单 App 三态（`off`/`splash`/`any`；未设置=跟随全局）。存储条目 `pkg=mode`。 */
    val perAppMode: Flow<Map<String, String>> = context.adSkipDataStore.data
        .map { prefs -> parsePerAppModes(prefs[KEY_PER_APP_MODE] ?: emptySet()) }

    /** 旧禁用集合（迁移兼容视图；新写入请用 [perAppMode]）。 */
    val disabledPackages: Flow<Set<String>> = context.adSkipDataStore.data
        .map { prefs -> prefs[KEY_DISABLED_PACKAGES] ?: emptySet() }

    /** 入口窗口毫秒（开屏判定窗口；2~15s，默认 5s）。 */
    val entryWindowMs: Flow<Long> = context.adSkipDataStore.data
        .map { prefs -> clampEntryWindow(prefs[KEY_ENTRY_WINDOW_MS] ?: DEFAULT_ENTRY_WINDOW_MS) }

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

    /** 写入单 App 三态（[MODE_FOLLOW]=移除条目；同时清理旧禁用键）。 */
    suspend fun setPackageMode(pkg: String, mode: String) {
        context.adSkipDataStore.edit { prefs ->
            val entries = (prefs[KEY_PER_APP_MODE] ?: emptySet())
                .filterNot { it.substringBefore('=') == pkg }
                .toMutableSet()
            if (mode != MODE_FOLLOW) entries += "$pkg=$mode"
            prefs[KEY_PER_APP_MODE] = entries
            prefs[KEY_DISABLED_PACKAGES] = (prefs[KEY_DISABLED_PACKAGES] ?: emptySet()) - pkg
        }
    }

    /** 兼容旧开关：disabled=true → off；false → 跟随。 */
    suspend fun setPackageDisabled(pkg: String, disabled: Boolean) {
        setPackageMode(pkg, if (disabled) MODE_OFF else MODE_FOLLOW)
    }

    /** 全部开启（清理旧禁用集合 + 所有 off 三态）。 */
    suspend fun clearDisabled() {
        context.adSkipDataStore.edit { prefs ->
            prefs[KEY_DISABLED_PACKAGES] = emptySet()
            prefs[KEY_PER_APP_MODE] = (prefs[KEY_PER_APP_MODE] ?: emptySet())
                .filterNot { it.substringAfter('=') == MODE_OFF }
                .toSet()
        }
    }

    suspend fun setEntryWindowMs(ms: Long) {
        context.adSkipDataStore.edit { prefs -> prefs[KEY_ENTRY_WINDOW_MS] = clampEntryWindow(ms) }
    }

    companion object {
        val KEY_MODE = stringPreferencesKey("adskip.mode")
        val KEY_EXTRA_KEYWORDS = stringSetPreferencesKey("adskip.keywords.extra")
        val KEY_DISABLED_PACKAGES = stringSetPreferencesKey("adskip.perAppDisabled")
        val KEY_PER_APP_MODE = stringSetPreferencesKey("adskip.perAppMode")
        val KEY_ENTRY_WINDOW_MS = longPreferencesKey("adskip.entryWindowMs")

        const val DEFAULT_MODE = AdSkipGroup.MODE_SPLASH

        /** 三态：跟随全局。 */
        const val MODE_FOLLOW = "follow"

        /** 三态：关闭。 */
        const val MODE_OFF = "off"

        const val DEFAULT_ENTRY_WINDOW_MS = 5_000L
        const val MIN_ENTRY_WINDOW_MS = 2_000L
        const val MAX_ENTRY_WINDOW_MS = 15_000L

        fun clampEntryWindow(ms: Long): Long =
            ms.coerceIn(MIN_ENTRY_WINDOW_MS, MAX_ENTRY_WINDOW_MS)

        val VALID_MODES = setOf(MODE_OFF, AdSkipGroup.MODE_SPLASH, AdSkipGroup.MODE_ANY)

        /** 解析 `pkg=mode` 条目（非法条目忽略）。 */
        fun parsePerAppModes(entries: Set<String>): Map<String, String> = buildMap {
            for (entry in entries) {
                val idx = entry.indexOf('=')
                if (idx <= 0) continue
                val pkg = entry.substring(0, idx)
                val mode = entry.substring(idx + 1)
                if (pkg.isNotBlank() && mode in VALID_MODES) put(pkg, mode)
            }
        }
    }
}
