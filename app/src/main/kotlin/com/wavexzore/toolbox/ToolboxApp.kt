package com.wavexzore.toolbox

import android.app.Application
import android.util.Log
import com.wavexzore.toolbox.core.accessibility.keepalive.KeepAliveService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ToolboxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // T-009：进程启动即尝试拉起常驻前台服务（后台限制下静默失败，由 BootReceiver/无障碍服务兜底）
        runCatching { KeepAliveService.start(this) }
            .onFailure { Log.w("WaveA11y.KeepAlive", "app-start keepalive deferred: ${it::class.simpleName}") }
    }
}
