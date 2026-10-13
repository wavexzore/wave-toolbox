package com.wavexzore.toolbox.core.accessibility.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 开机自启（T-009，D-005）：`BOOT_COMPLETED` + `LOCKED_BOOT_COMPLETED`（直启兜底）。
 * 无障碍服务本身由系统在开机后自动重绑；本接收器补前台常驻通知。
 * 受设置项 `boot.autostart` 控制（关闭后开机不拉起常驻服务）。
 */
class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun settingsRepository(): SettingsRepository
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                val enabled = runCatching {
                    runBlocking {
                        EntryPointAccessors
                            .fromApplication(context.applicationContext, BootEntryPoint::class.java)
                            .settingsRepository()
                            .bootAutostartEnabled
                            .first()
                    }
                }.getOrDefault(SettingsRepository.DEFAULT_BOOT_AUTOSTART)
                if (!enabled) {
                    Log.i(TAG, "boot: autostart disabled, skip (${intent.action})")
                    return
                }
                runCatching { KeepAliveService.start(context) }
                    .onSuccess { Log.i(TAG, "boot: keepalive started (${intent.action})") }
                    .onFailure { Log.w(TAG, "boot: keepalive start failed", it) }
            }
        }
    }

    companion object {
        const val TAG = "WaveA11y.KeepAlive"
    }
}
