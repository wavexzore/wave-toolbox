package com.wavexzore.toolbox.core.accessibility.keepalive

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.wavexzore.toolbox.core.accessibility.AccessibilityStateMonitor
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 保活健康快照（同进程 best-effort）。
 *
 * 明确边界：FGS / overlay / 无障碍服务都在应用进程内，不是跨进程守护；
 * 进程被系统强杀后依赖系统重绑、`START_STICKY` 与开机接收器恢复。
 */
@Singleton
class KeepAliveHealth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val stateMonitor: AccessibilityStateMonitor,
) {

    data class Snapshot(
        val engineEnabled: Boolean,
        val keepAliveEnabled: Boolean,
        val foregroundServiceRunning: Boolean,
        val accessibilityEnabled: Boolean,
        val batteryOptimizationIgnored: Boolean,
        val bootAutostart: Boolean,
        val manufacturer: String,
        val oemHint: String,
        val warnings: List<String>,
    ) {
        val healthy: Boolean get() = warnings.isEmpty()

        val summary: String
            get() = when {
                !engineEnabled -> "引擎已关闭 · 保活不参与事件处理"
                warnings.isEmpty() -> "链路正常 · 同进程 best-effort"
                else -> warnings.first()
            }
    }

    suspend fun snapshot(): Snapshot {
        val engineEnabled = settingsRepository.engineEnabled.first()
        val keepAliveEnabled = settingsRepository.keepAliveEnabled.first()
        val bootAutostart = settingsRepository.bootAutostartEnabled.first()
        val accessibilityEnabled = stateMonitor.isEnabled()
        val foregroundRunning = KeepAliveService.isRunning
        val batteryIgnored = runCatching {
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        }.getOrDefault(false)
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val warnings = buildList {
            if (!engineEnabled) add("引擎总开关已关闭")
            if (!accessibilityEnabled) add("无障碍服务未启用或未绑定")
            if (keepAliveEnabled && !foregroundRunning) add("前台保活服务未运行")
            if (!batteryIgnored) add("电池优化未豁免，后台可能被冻结")
            if (!bootAutostart) add("开机自启已关闭")
        }
        return Snapshot(
            engineEnabled = engineEnabled,
            keepAliveEnabled = keepAliveEnabled,
            foregroundServiceRunning = foregroundRunning,
            accessibilityEnabled = accessibilityEnabled,
            batteryOptimizationIgnored = batteryIgnored,
            bootAutostart = bootAutostart,
            manufacturer = manufacturer,
            oemHint = oemHint(manufacturer),
            warnings = warnings,
        )
    }

    /** 诊断导出用文本。 */
    fun format(snapshot: Snapshot): String = buildString {
        appendLine("mode=同进程 best-effort（非跨进程守护）")
        appendLine("engine.enabled=${snapshot.engineEnabled}")
        appendLine("keepAlive.enabled=${snapshot.keepAliveEnabled}")
        appendLine("foregroundService.running=${snapshot.foregroundServiceRunning}")
        appendLine("accessibility.enabled=${snapshot.accessibilityEnabled}")
        appendLine("batteryOptimization.ignored=${snapshot.batteryOptimizationIgnored}")
        appendLine("bootAutostart.enabled=${snapshot.bootAutostart}")
        appendLine("manufacturer=${snapshot.manufacturer}")
        appendLine("oemHint=${snapshot.oemHint}")
        appendLine("healthy=${snapshot.healthy}")
        snapshot.warnings.forEach { appendLine("warning=$it") }
    }

    fun openBatteryOptimizationSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    companion object {
        fun oemHint(manufacturer: String): String = when (manufacturer.lowercase(Locale.US)) {
            "samsung" -> "三星：电池 → 后台使用限制 → 将本应用加入「不休眠的应用」"
            "xiaomi", "redmi", "poco" -> "小米/Redmi：应用管理 → 本应用 → 省电策略 → 无限制；允许自启动"
            "huawei", "honor" -> "华为/荣耀：应用启动管理 → 关闭自动管理，允许自启动/关联启动/后台活动"
            "oppo", "realme", "oneplus" -> "OPPO/realme/一加：电池 → 应用耗电管理 → 允许后台运行"
            "vivo", "iqoo" -> "vivo/iQOO：电池 → 后台耗电管理 → 允许后台高耗电；允许自启动"
            "meizu" -> "魅族：应用管理 → 权限 → 后台管理 → 允许后台运行"
            else -> "如系统提供「自启动 / 后台运行 / 省电策略」，请将本应用设为允许"
        }
    }
}
