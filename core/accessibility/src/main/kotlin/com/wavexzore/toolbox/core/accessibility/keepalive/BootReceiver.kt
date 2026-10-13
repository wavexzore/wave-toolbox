package com.wavexzore.toolbox.core.accessibility.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机自启（T-009，D-005）：`BOOT_COMPLETED` + `LOCKED_BOOT_COMPLETED`（直启兜底）。
 * 无障碍服务本身由系统在开机后自动重绑；本接收器补前台常驻通知。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
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
