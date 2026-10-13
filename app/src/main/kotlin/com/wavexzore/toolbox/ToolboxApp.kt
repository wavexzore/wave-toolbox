package com.wavexzore.toolbox

import android.app.Application
import android.util.Log
import com.wavexzore.toolbox.core.accessibility.keepalive.KeepAliveService
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
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
