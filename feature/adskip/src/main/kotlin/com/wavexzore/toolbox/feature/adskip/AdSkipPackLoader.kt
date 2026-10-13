package com.wavexzore.toolbox.feature.adskip

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

/**
 * 规则包装载（T-003）：assets 内置 JSON5（目录 `rules/adskip/`）+ 用户目录 JSON5（`files/adskip/`）覆盖。
 *
 * - 同名 pkg：用户文件整包覆盖内置（并记录来源）；
 * - 坏文件隔离：单个文件解析失败不影响其余，错误进 [state] 供 UI；
 * - 装载幂等（重复 reload 结果一致）；首次装载在构造时触发。
 */
@Singleton
class AdSkipPackLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatchers: AppDispatchers,
) : PackGroupsSource {

    data class Pack(
        val pkg: String,
        val groups: List<AdSkipGroup>,
        val source: String,
        val packVersion: Int,
    )

    data class State(
        val packs: Map<String, Pack> = emptyMap(),
        val errors: List<String> = emptyList(),
        val loadedVersion: Int = 0,
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val _state = MutableStateFlow(State())

    /** 装载结果（UI 展示用）。 */
    val state: StateFlow<State> = _state

    init {
        reload()
    }

    /** 幂等装载：内置 → 用户覆盖。 */
    fun reload() {
        scope.launch {
            val previousVersion = _state.value.loadedVersion
            val result = runCatching { loadAll() }.getOrElse { t ->
                Log.e(TAG, "loadAll failed", t)
                State(errors = listOf("装载失败：${t.message}"), loadedVersion = previousVersion)
            }
            _state.value = result
            Log.i(TAG, "packs loaded: ${result.packs.size} apps, errors=${result.errors.size}, version=${result.loadedVersion}")
        }
    }

    override fun groupsFor(pkg: String): List<AdSkipGroup> =
        _state.value.packs[pkg]?.groups?.filter { it.enabled }?.sortedBy { it.order } ?: emptyList()

    fun userDir(): File = File(context.filesDir, "adskip").apply { mkdirs() }

    private fun loadAll(): State {
        val packs = LinkedHashMap<String, Pack>()
        val errors = ArrayList<String>()

        loadFromAssets(packs, errors)
        loadFromUser(packs, errors)

        return State(
            packs = packs,
            errors = errors,
            loadedVersion = _state.value.loadedVersion + 1,
        )
    }

    private fun loadFromAssets(packs: MutableMap<String, Pack>, errors: MutableList<String>) {
        val files: Array<String> = runCatching { context.assets.list(ASSET_DIR) }.getOrNull() ?: emptyArray()
        for (name in files.sorted()) {
            if (!name.endsWith(".json5")) continue
            runCatching {
                val text = context.assets.open("$ASSET_DIR/$name").bufferedReader().readText()
                val parsed = parse(text)
                packs[parsed.pkg] = Pack(parsed.pkg, parsed.groups, "builtin:$name", parsed.packVersion)
            }.onFailure { t ->
                errors += "内置 $name：${t.message}"
                Log.w(TAG, "bad builtin pack: $name", t)
            }
        }
    }

    private fun loadFromUser(packs: MutableMap<String, Pack>, errors: MutableList<String>) {
        val files = userDir().listFiles()?.sortedBy { it.name } ?: return
        for (file in files) {
            if (!file.name.endsWith(".json5")) continue
            runCatching {
                val parsed = parse(file.readText())
                packs[parsed.pkg] = Pack(parsed.pkg, parsed.groups, "user:${file.name}", parsed.packVersion)
            }.onFailure { t ->
                errors += "用户 ${file.name}：${t.message}"
                Log.w(TAG, "bad user pack: ${file.name}", t)
            }
        }
    }

    internal fun parse(text: String): AdSkipPackFile = AdSkipPackCodec.parse(text)

    companion object {
        const val TAG = "WaveAdSkip.Pack"
        const val ASSET_DIR = "rules/adskip"
    }
}
