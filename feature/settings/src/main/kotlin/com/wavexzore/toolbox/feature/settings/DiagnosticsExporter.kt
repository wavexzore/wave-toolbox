package com.wavexzore.toolbox.feature.settings

import android.content.Context
import android.os.Build
import com.wavexzore.toolbox.core.accessibility.AccessibilityStateMonitor
import com.wavexzore.toolbox.core.accessibility.action.RuleEngine
import com.wavexzore.toolbox.core.accessibility.diagnostics.DisplayDiagnostics
import com.wavexzore.toolbox.core.accessibility.feature.FeatureRegistry
import com.wavexzore.toolbox.core.accessibility.keepalive.KeepAliveHealth
import com.wavexzore.toolbox.core.accessibility.pipeline.EventPipeline
import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import com.wavexzore.toolbox.core.data.rules.RuleRepository
import androidx.core.content.pm.PackageInfoCompat
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import com.wavexzore.toolbox.core.data.stats.StatsRepository
import com.wavexzore.toolbox.core.rules.model.RawRuleGroup
import com.wavexzore.toolbox.feature.settings.update.UpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 诊断导出（T-011）：把设备信息/引擎设置/规则/统计/事件与动作环/最近快照/本应用日志
 * 打包为 zip（cacheDir/exports），供反馈与排障。
 */
@Singleton
class DiagnosticsExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ruleRepository: RuleRepository,
    private val statsRepository: StatsRepository,
    private val settingsRepository: SettingsRepository,
    private val stateMonitor: AccessibilityStateMonitor,
    private val pipeline: EventPipeline,
    private val ruleEngine: RuleEngine,
    private val displayDiagnostics: DisplayDiagnostics,
    private val keepAliveHealth: KeepAliveHealth,
    private val featureRegistry: FeatureRegistry,
    private val dispatchers: AppDispatchers,
    private val updateRepository: UpdateRepository,
) {

    data class Result(val file: File, val entries: List<String>)

    private val json = Json { prettyPrint = true }

    suspend fun export(): Result = withContext(dispatchers.io) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "wave-diagnostics-$stamp.zip")
        val entries = LinkedHashMap<String, ByteArray>()

        entries["device.txt"] = deviceInfo().toByteArray()
        entries["displays.txt"] = displayInfo().toByteArray()
        entries["settings.txt"] = settingsInfo().toByteArray()
        entries["keepalive.txt"] = runCatching {
            keepAliveHealth.format(keepAliveHealth.snapshot())
        }.getOrElse { "keepalive health failed: ${it.message}" }.toByteArray()
        entries["update.txt"] = updateInfo().toByteArray()
        entries["rules.json"] = runCatching {
            json.encodeToString(ListSerializer(RawRuleGroup.serializer()), ruleRepository.currentGroups())
        }.getOrDefault("[]").toByteArray()
        entries["stats.csv"] = statsCsv().toByteArray()
        entries["events.txt"] = pipeline.recent.value.joinToString("\n") { recent ->
            "${recent.timeMillis} ${recent.type} ${recent.packageName ?: "-"} ${recent.className ?: ""} ${recent.text ?: ""}"
        }.toByteArray()
        entries["actions.txt"] = ruleEngine.recentActions.value.joinToString("\n") { action ->
            "${action.timeMillis} ${action.kind} rule=${action.ruleId} pkg=${action.packageName ?: "-"} mech=${action.mechanism ?: "-"} ok=${action.ok} ${action.detail ?: ""}"
        }.toByteArray()
        latestSnapshot()?.let { snapshot -> entries["snapshot-latest.json"] = snapshot.readBytes() }
        logcatTail()?.let { entries["logcat.txt"] = it.toByteArray() }
        entries["README.txt"] = """
            wave工具箱 诊断包
            entries: ${entries.keys.joinToString(", ")}
            生成时间: ${Date()}
        """.trimIndent().toByteArray()

        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        Result(file, entries.keys.toList())
    }

    private fun deviceInfo(): String = buildString {
        appendLine("model=${Build.MODEL}")
        appendLine("manufacturer=${Build.MANUFACTURER}")
        appendLine("device=${Build.DEVICE}")
        appendLine("androidSdk=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE}")
        appendLine("abis=${Build.SUPPORTED_ABIS.joinToString(",")}")
        appendLine("appVersion=${appVersionName()}")
        appendLine("serviceEnabled=${stateMonitor.isEnabled()}")
    }

    /** 多屏诊断：每块 display 的尺寸/窗口/取树结果；主屏支持、副屏实验性。 */
    private fun displayInfo(): String = buildString {
        val report = runCatching { displayDiagnostics.snapshot() }
            .getOrElse { t ->
                appendLine("display diagnostics failed: ${t.message}")
                return@buildString
            }
        appendLine("# ${report.note}")
        report.displays.forEach { display ->
            appendLine(
                "display id=${display.id} name=${display.name} " +
                    "size=${display.width}x${display.height} dpi=${display.densityDpi} " +
                    "refresh=${String.format(Locale.US, "%.1f", display.refreshRate)} " +
                    "state=${DisplayDiagnostics.displayStateText(display.state)} rotation=${display.rotation} " +
                    "primary=${display.primary} support=${display.support}",
            )
            appendLine("  windows=${display.windows.size}")
            display.windows.forEach { window ->
                appendLine(
                    "    window id=${window.id} type=${DisplayDiagnostics.windowTypeText(window.type)} " +
                        "pkg=${window.rootPkg ?: "-"} focused=${window.focused} active=${window.active} " +
                        "bounds=${window.bounds ?: "-"}",
                )
            }
            appendLine(
                "  tree ok=${display.tree.ok} root=${display.tree.rootPkg ?: "-"} " +
                    "nodes=${display.tree.nodes} truncated=${display.tree.truncated} " +
                    "windowType=${DisplayDiagnostics.windowTypeText(display.tree.windowType)} " +
                    "bounds=${display.tree.bounds ?: "-"} " +
                    "message=${display.tree.message ?: "-"}",
            )
        }
    }

    private suspend fun settingsInfo(): String = buildString {
        appendLine("engine.enabled=${settingsRepository.engineEnabled.first()}")
        appendLine("engine.contentThrottleMs=${settingsRepository.contentThrottleMs.first()}")
        appendLine("engine.clickFallback=${settingsRepository.clickFallback.first()}")
        appendLine("engine.clickVerifyMs=${settingsRepository.clickVerifyMs.first()}")
        appendLine("engine.allowPackages=${settingsRepository.allowPackages.first().joinToString(",")}")
        appendLine("setup.completed=${settingsRepository.setupCompleted.first()}")
        featureRegistry.all.forEach { feature ->
            appendLine("feature.${feature.id}.enabled=${settingsRepository.featureEnabled(feature.id, feature.defaultEnabled).first()}")
        }
    }

    /** 更新子系统快照（T-310；设计 04 §12）：状态不敏感字段一律取自持久层/平台。 */
    private suspend fun updateInfo(): String = buildString {
        appendLine("appVersion=${appVersionName()}(${appVersionCode()})")
        appendLine("update.autoCheck=${settingsRepository.updateAutoCheck.first()}")
        appendLine("update.lastCheckAt=${settingsRepository.updateLastCheckAt.first()}")
        appendLine("update.skippedTag=${settingsRepository.updateSkippedTag.first()}")
        appendLine("lastState=${updateRepository.stateSummary()}")
    }

    private suspend fun statsCsv(): String {
        val since = System.currentTimeMillis() - 7 * StatsRepository.DAY_MS
        val rows = statsRepository.allSince(since)
        return buildString {
            appendLine("at,feature,pkg,rule,kind,ok,mechanism,durationMs")
            rows.forEach { row ->
                appendLine("${row.at},${row.featureId},${row.pkg ?: ""},${row.ruleId ?: ""},${row.kind},${row.ok},${row.mechanism ?: ""},${row.durationMs}")
            }
        }
    }

    private fun latestSnapshot(): File? =
        File(context.filesDir, "snapshots").listFiles()?.maxByOrNull { it.lastModified() }

    private fun logcatTail(): String? = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "2000", "-v", "time"))
        process.inputStream.bufferedReader().readText().also { process.destroy() }
    }.getOrNull()

    private fun appVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"

    private fun appVersionCode(): Long = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }.getOrDefault(-1L)
}
