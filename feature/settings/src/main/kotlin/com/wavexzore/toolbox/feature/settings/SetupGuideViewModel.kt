package com.wavexzore.toolbox.feature.settings

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavexzore.toolbox.core.accessibility.AccessibilityStateMonitor
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 首启引导（T-009）：三步检查（无障碍 → 电池优化 → OEM 自启）+ 通知权限（33+）。
 * 状态以系统真实状态实时派生（轮询刷新，页面不可见即停）。
 */
@HiltViewModel
class SetupGuideViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateMonitor: AccessibilityStateMonitor,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    data class UiState(
        val serviceEnabled: Boolean = false,
        val batteryIgnoring: Boolean = false,
        val notificationGranted: Boolean = true,
        val notificationSupported: Boolean = Build.VERSION.SDK_INT >= 33,
        val setupCompleted: Boolean = false,
        val guide: OemGuide = OemGuide.forManufacturer(Build.MANUFACTURER),
    ) {
        /** 引导完成判定：无障碍 + 电池优化（通知在 33+ 建议授权，不阻塞）。 */
        val stepsSatisfied: Boolean get() = serviceEnabled && batteryIgnoring
    }

    private val refreshTick = MutableStateFlow(0L)

    val uiState: StateFlow<UiState> = combine(
        stateMonitor.serviceEnabled,
        settingsRepository.setupCompleted,
        refreshTick,
    ) { enabled, completed, _ ->
        UiState(
            serviceEnabled = enabled,
            batteryIgnoring = isIgnoringBatteryOptimizations(),
            notificationGranted = isNotificationGranted(),
            setupCompleted = completed,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UiState(guide = OemGuide.forManufacturer(Build.MANUFACTURER)),
    )

    fun refresh() {
        refreshTick.value = System.currentTimeMillis()
    }

    fun openAccessibilitySettings() {
        stateMonitor.openAccessibilitySettings()
    }

    /** 受限设置分支：应用信息页（Android 13+ 需在此允许受限设置后才能开无障碍）。 */
    fun openAppDetails() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null)),
        )
    }

    /** 请求忽略电池优化；部分 ROM 无直接弹窗权限时退化为电池设置页。 */
    fun requestIgnoreBatteryOptimization() {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.fromParts("package", context.packageName, null))
        if (startActivity(direct)) return
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /** 按厂商逐个尝试打开自启动管理页；失败退化应用信息页。 */
    fun openOemAutostart() {
        val guide = OemGuide.forManufacturer(Build.MANUFACTURER)
        for (name in guide.autostartCandidates) {
            val intent = Intent().setComponent(name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (startActivity(intent)) return
        }
        openAppDetails()
    }

    fun completeSetup() {
        viewModelScope.launch { settingsRepository.setSetupCompleted(true) }
    }

    private fun startActivity(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun isNotificationGranted(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}

/** 厂商自启引导（逐机型文案 + 候选入口；失败退化应用信息页）。 */
data class OemGuide(
    val brandLabel: String,
    val steps: List<String>,
    val autostartCandidates: List<ComponentName>,
) {
    companion object {

        private fun component(text: String): ComponentName? = ComponentName.unflattenFromString(text)

        fun forManufacturer(manufacturer: String): OemGuide {
            val m = manufacturer.lowercase()
            return when {
                m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> OemGuide(
                    brandLabel = "MIUI / HyperOS",
                    steps = listOf(
                        "设置 → 应用设置 → 应用管理 → wave工具箱 → 允许自启动",
                        "或：安全中心 → 应用管理 → 权限 → 自启动管理",
                        "后台策略设为「无限制」更稳（省电策略 → 无限制）",
                    ),
                    autostartCandidates = listOfNotNull(
                        component("com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity"),
                    ),
                )
                m.contains("huawei") || m.contains("honor") -> OemGuide(
                    brandLabel = "EMUI / HarmonyOS",
                    steps = listOf(
                        "手机管家 → 应用启动管理 → wave工具箱 → 关闭「自动管理」，三项手动全开",
                        "设置 → 电池 → 更多电池设置 → 保持唤醒",
                    ),
                    autostartCandidates = listOfNotNull(
                        component("com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity"),
                        component("com.huawei.systemmanager/.appcontrol.activity.StartupAppControlActivity"),
                    ),
                )
                m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> OemGuide(
                    brandLabel = "ColorOS / realme UI",
                    steps = listOf(
                        "设置 → 电池 → 更多设置 → 睡眠待机优化 → 关闭",
                        "设置 → 应用 → wave工具箱 → 允许自启动/关联启动/后台运行",
                    ),
                    autostartCandidates = listOfNotNull(
                        component("com.coloros.safecenter/.permission.startup.StartupAppListActivity"),
                        component("com.oppo.safe/.permission.startup.StartupAppListActivity"),
                        component("com.oneplus.security/.chainlaunch.view.ChainLaunchAppListActivity"),
                    ),
                )
                m.contains("vivo") || m.contains("iqoo") -> OemGuide(
                    brandLabel = "OriginOS / Funtouch",
                    steps = listOf(
                        "i管家 → 应用管理 → 权限管理 → 自启动 → 允许",
                        "设置 → 电池 → 后台高耗电 → 允许",
                    ),
                    autostartCandidates = listOfNotNull(
                        component("com.vivo.permissionmanager/.activity.BgStartUpManagerActivity"),
                        component("com.iqoo.secure/.safeguard.PurviewTabActivity"),
                    ),
                )
                m.contains("meizu") -> OemGuide(
                    brandLabel = "Flyme",
                    steps = listOf(
                        "手机管家 → 权限管理 → 后台管理 → wave工具箱 → 允许后台运行",
                    ),
                    autostartCandidates = listOfNotNull(
                        component("com.meizu.safe/.permission.SmartBGActivity"),
                    ),
                )
                m.contains("samsung") -> OemGuide(
                    brandLabel = "One UI",
                    steps = listOf(
                        "设置 → 电池 → 后台使用限制 → 从「已休眠应用」移除",
                    ),
                    autostartCandidates = emptyList(),
                )
                else -> OemGuide(
                    brandLabel = manufacturer.ifBlank { "通用" },
                    steps = listOf(
                        "设置 → 电池/应用管理 → wave工具箱 → 允许后台运行与自启动",
                    ),
                    autostartCandidates = emptyList(),
                )
            }
        }
    }
}
