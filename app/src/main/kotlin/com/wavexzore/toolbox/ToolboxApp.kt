package com.wavexzore.toolbox

import android.app.Application
import android.util.Log
import com.wavexzore.toolbox.core.accessibility.AccessibilitySelfHeal
import com.wavexzore.toolbox.core.accessibility.keepalive.KeepAliveService
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import com.wavexzore.toolbox.feature.settings.update.UpdateRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ToolboxApp : Application() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var accessibilitySelfHeal: AccessibilitySelfHeal

    @Inject
    lateinit var updateRepository: UpdateRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // 冷启动无障碍自愈（coc-player-v2 移植）：进程重启/被回收后系统可能遗留
        // 「Enabled 未 Bound」或 crashed 标记，受控重绑恢复绑定；后台执行，300ms
        // 重绑等待不阻塞冷启动。fail-closed：异常仅记录结果，不重试。
        appScope.launch {
            val outcome = runCatching { accessibilitySelfHeal.heal() }
            Log.i(
                "WaveA11y.SelfHeal",
                "bootstrap result=" +
                    (outcome.getOrNull()?.name ?: outcome.exceptionOrNull()?.let { it::class.simpleName }),
            )
        }
        // 应用内更新（T-309）：启动检查一次（受 autoCheck + 24h 节流约束）；
        // 自有 scope 内部执行，异常仅记录不崩启动。
        appScope.launch {
            runCatching { updateRepository.ensureFreshOnLaunch() }
                .onFailure { Log.w("WaveUpdate", "launch check failed: ${it::class.simpleName}") }
        }
        // T-009：跟随「前台保活」设置启动/停止常驻服务（后台限制下静默失败，由 BootReceiver/无障碍服务兜底）
        appScope.launch {
            settingsRepository.keepAliveEnabled.collect { enabled ->
                if (enabled) {
                    runCatching { KeepAliveService.start(this@ToolboxApp) }
                        .onFailure { Log.w("WaveA11y.KeepAlive", "app-start keepalive deferred: ${it::class.simpleName}") }
                } else {
                    KeepAliveService.stop(this@ToolboxApp)
                }
            }
        }
    }
}
