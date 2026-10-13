package com.wavexzore.toolbox.feature.wechat

import android.content.Context
import android.util.Log
import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * 结果与轮次存储（T-004/T-007）：JSON 文件 + 原子写（tmp→rename），StateFlow 供 UI。
 * 保留策略：`keepRuns` 轮（默认 5，Q-204）；清理时保留已清理标记。
 */
@Singleton
class WechatStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: WechatConfig,
    dispatchers: AppDispatchers,
) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val file: File get() = File(context.filesDir, "wechat/data.json").apply { parentFile?.mkdirs() }

    private val _data = MutableStateFlow(WechatData())
    val data: StateFlow<WechatData> = _data

    init {
        scope.launch { load() }
    }

    private suspend fun load() {
        runCatching {
            val text = file.takeIf { it.exists() }?.readText() ?: return
            _data.value = json.decodeFromString(WechatData.serializer(), text)
        }.onFailure { Log.w(TAG, "load failed", it) }
    }

    private suspend fun persist() {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(WechatData.serializer(), _data.value))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }.onFailure { Log.w(TAG, "persist failed", it) }
    }

    fun startRun(wechatVersion: String?): String {
        val runId = "run-" + System.currentTimeMillis()
        scope.launch {
            _data.value = _data.value.copy(
                runs = (_data.value.runs + RunMeta(runId, System.currentTimeMillis(), wechatVersion = wechatVersion))
                    .takeLast(20),
            )
            persist()
        }
        return runId
    }

    fun finishRun(runId: String, status: String, batches: Int, processed: Int, found: Int) {
        scope.launch {
            _data.value = _data.value.copy(
                runs = _data.value.runs.map { run ->
                    if (run.runId == runId) {
                        run.copy(finishedAt = System.currentTimeMillis(), status = status, batches = batches, processed = processed, found = found)
                    } else {
                        run
                    }
                },
            )
            prune()
            persist()
        }
    }

    fun addEntries(entries: List<ResultEntry>) {
        if (entries.isEmpty()) return
        scope.launch {
            val existing = _data.value.entries.map { it.name + "#" + it.runId }.toHashSet()
            val fresh = entries.filter { it.name + "#" + it.runId !in existing }
            _data.value = _data.value.copy(entries = _data.value.entries + fresh)
            persist()
        }
    }

    fun markCleaned(names: Collection<String>, alreadyFriendless: Boolean = false) {
        if (names.isEmpty()) return
        val set = names.toHashSet()
        scope.launch {
            val now = System.currentTimeMillis()
            _data.value = _data.value.copy(
                entries = _data.value.entries.map { entry ->
                    if (entry.name in set && !entry.cleaned) {
                        entry.copy(cleaned = true, cleanedAt = now, alreadyFriendless = alreadyFriendless)
                    } else {
                        entry
                    }
                },
            )
            persist()
        }
    }

    fun clearAll() {
        scope.launch {
            _data.value = WechatData()
            persist()
        }
    }

    /** 保留最近 5 轮（Q-204；更早轮次仅保留未清理项）。 */
    private suspend fun prune() {
        val keepCount = 5
        val runs = _data.value.runs
        if (runs.size <= keepCount) return
        val keepIds = runs.takeLast(keepCount).map { it.runId }.toHashSet()
        _data.value = _data.value.copy(
            runs = runs.takeLast(keepCount),
            entries = _data.value.entries.filter { it.runId in keepIds || !it.cleaned }.takeLast(5000),
        )
    }

    suspend fun csv(): String = buildString {
        appendLine("name,category,cleaned,alreadyFriendless,runId,at")
        _data.value.entries.forEach { entry ->
            appendLine("${entry.name},${entry.category.name},${entry.cleaned},${entry.alreadyFriendless},${entry.runId},${entry.at}")
        }
    }

    // ---- v1.1 转账验证（好友列表 + 验证状态） ----

    /** 全量保存好友列表（收集会话；同名按出现次序编 seq）。 */
    fun saveFriends(names: List<String>) {
        scope.launch {
            val counter = HashMap<String, Int>()
            val list = names.map { raw ->
                val n = raw.trim()
                val seq = (counter[n] ?: 0) + 1
                counter[n] = seq
                FriendEntry(name = n, seq = seq)
            }
            _data.value = _data.value.copy(friends = list, friendsCollectedAt = System.currentTimeMillis())
            persist()
        }
    }

    /** 按索引更新单个好友验证结果。 */
    fun updateFriendAt(index: Int, transform: (FriendEntry) -> FriendEntry) {
        scope.launch {
            val list = _data.value.friends.toMutableList()
            if (index !in list.indices) return@launch
            list[index] = transform(list[index])
            _data.value = _data.value.copy(friends = list)
            persist()
        }
    }

    fun markVerifyRun() {
        scope.launch {
            _data.value = _data.value.copy(verifyRunAt = System.currentTimeMillis())
            persist()
        }
    }

    /** 重置全部验证状态为待验证（下次全量重验）。 */
    fun resetFriendsStatus() {
        scope.launch {
            _data.value = _data.value.copy(friends = _data.value.friends.map { it.copy(status = FriendStatus.PENDING, signal = null, tagged = false) })
            persist()
        }
    }

    suspend fun friendsCsv(): String = buildString {
        appendLine("name,status,wxid,region,phone,signal,verifiedAt,tagged")
        _data.value.friends.forEach { f ->
            appendLine("${f.name},${f.status.name},${f.wxid ?: ""},${f.region ?: ""},${f.phone ?: ""},${(f.signal ?: "").replace(",", "，")},${f.verifiedAt ?: ""},${f.tagged}")
        }
    }

    companion object {
        const val TAG = "WaveWechat.Store"
    }
}
