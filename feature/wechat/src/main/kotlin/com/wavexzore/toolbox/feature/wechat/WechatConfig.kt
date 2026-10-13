package com.wavexzore.toolbox.feature.wechat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wechatDataStore: DataStore<Preferences> by preferencesDataStore(name = "wechat_settings")

/** 微信僵尸好友特性配置（T-001；键语义见特性 README §6.2）。 */
@Singleton
class WechatConfig @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 单批进群人数（≤40 保守 30）。 */
    val batchSize: Flow<Int> = context.wechatDataStore.data.map { it[KEY_BATCH] ?: DEFAULT_BATCH }

    /** 检测后是否自动退群（断点续跑时留在群里会干扰）。 */
    val dryRun: Flow<Boolean> = context.wechatDataStore.data.map { it[KEY_DRY_RUN] ?: false }

    /** 保留最近 N 轮结果。 */
    val keepRuns: Flow<Int> = context.wechatDataStore.data.map { it[KEY_KEEP_RUNS] ?: DEFAULT_KEEP_RUNS }

    /** 结果默认勾选策略：`deletedOnly` | `all`。 */
    val defaultSelection: Flow<String> = context.wechatDataStore.data.map { it[KEY_SELECTION] ?: DEFAULT_SELECTION }

    /** 删除好友时是否同时删除聊天记录（默认保留=false）。 */
    val deleteChatRecord: Flow<Boolean> = context.wechatDataStore.data.map { it[KEY_DELETE_RECORD] ?: false }

    /** 会话总超时（ms）。 */
    val sessionTimeoutMs: Flow<Long> = context.wechatDataStore.data.map { it[KEY_SESSION_TIMEOUT] ?: DEFAULT_TIMEOUT }

    /** 进度 HUD 开关。 */
    val hudEnabled: Flow<Boolean> = context.wechatDataStore.data.map { it[KEY_HUD] ?: true }

    suspend fun setBatchSize(value: Int) = context.wechatDataStore.edit { it[KEY_BATCH] = value.coerceIn(5, 39) }
    suspend fun setDryRun(value: Boolean) = context.wechatDataStore.edit { it[KEY_DRY_RUN] = value }
    suspend fun setKeepRuns(value: Int) = context.wechatDataStore.edit { it[KEY_KEEP_RUNS] = value.coerceIn(1, 20) }
    suspend fun setDefaultSelection(value: String) = context.wechatDataStore.edit { it[KEY_SELECTION] = value }
    suspend fun setDeleteChatRecord(value: Boolean) = context.wechatDataStore.edit { it[KEY_DELETE_RECORD] = value }
    suspend fun setHudEnabled(value: Boolean) = context.wechatDataStore.edit { it[KEY_HUD] = value }

    /** 异常好友标签名（默认「僵尸」；已存在则复用）。 */
    val tagName: Flow<String> = context.wechatDataStore.data.map { it[KEY_TAG] ?: DEFAULT_TAG }

    suspend fun setTagName(value: String) = context.wechatDataStore.edit { it[KEY_TAG] = value }

    /** 转账验证每个好友的附加停留（ms，稳定起见）。 */
    val verifyPaceMs: Flow<Long> = context.wechatDataStore.data.map { it[KEY_VERIFY_PACE] ?: DEFAULT_PACE }

    suspend fun setVerifyPaceMs(value: Long) = context.wechatDataStore.edit { it[KEY_VERIFY_PACE] = value.coerceIn(100, 3_000) }

    /** 删除前确认弹窗的「同时删除聊天记录」勾选（运行期，不落库也可）。 */
    var pendingDeleteChatRecord: Boolean = false

    companion object {
        val KEY_BATCH = intPreferencesKey("wechat.batchSize")
        val KEY_DRY_RUN = booleanPreferencesKey("wechat.dryRun")
        val KEY_KEEP_RUNS = intPreferencesKey("wechat.keepRuns")
        val KEY_SELECTION = stringPreferencesKey("wechat.defaultSelection")
        val KEY_DELETE_RECORD = booleanPreferencesKey("wechat.deleteChatRecord")
        val KEY_SESSION_TIMEOUT = longPreferencesKey("wechat.sessionTimeoutMs")
        val KEY_HUD = booleanPreferencesKey("wechat.hudEnabled")
        val KEY_TAG = stringPreferencesKey("wechat.tagName")
        val KEY_VERIFY_PACE = longPreferencesKey("wechat.verifyPaceMs")

        const val DEFAULT_BATCH = 30
        const val DEFAULT_KEEP_RUNS = 5
        const val DEFAULT_SELECTION = "deletedOnly"
        const val DEFAULT_TIMEOUT = 15 * 60_000L
        const val DEFAULT_TAG = "僵尸"
        const val DEFAULT_PACE = 350L
    }
}
