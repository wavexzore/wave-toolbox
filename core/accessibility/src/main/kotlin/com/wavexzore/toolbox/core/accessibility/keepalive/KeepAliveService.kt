package com.wavexzore.toolbox.core.accessibility.keepalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.wavexzore.toolbox.core.accessibility.AccessibilityStateMonitor
import com.wavexzore.toolbox.core.accessibility.R
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 常驻前台服务（T-009，D-005）：FGS(specialUse) + 低优先级常驻通知。
 *
 * - 通知文案实时含引擎/无障碍状态，操作按钮可切换引擎、点按进应用；
 * - 与 1×1 overlay、BOOT 接收器构成三重独立保活（互不依赖）；
 * - 仅承载通知与状态镜像，不参与事件处理（引擎在无障碍服务内）。
 */
@AndroidEntryPoint
class KeepAliveService : Service() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var stateMonitor: AccessibilityStateMonitor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var engineEnabled = true
    private var serviceEnabled = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(buildNotification())
        scope.launch {
            combine(
                settingsRepository.engineEnabled,
                stateMonitor.serviceEnabled,
            ) { engine, enabled -> engine to enabled }
                .collect { (engine, enabled) ->
                    engineEnabled = engine
                    serviceEnabled = enabled
                    notify(buildNotification())
                }
        }
        Log.i(TAG, "onCreate: foreground started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) {
            notify(buildNotification())
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        Log.i(TAG, "onDestroy")
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "引擎常驻", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "保持无障碍引擎在线（低打扰常驻通知）"
                },
            )
        }
    }

    private fun buildNotification(): Notification {
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(
                this,
                0,
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val toggle = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, ToggleEngineReceiver::class.java).setAction(ToggleEngineReceiver.ACTION_TOGGLE_ENGINE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val status = when {
            !engineEnabled -> "引擎已关闭"
            serviceEnabled -> "引擎已开启 · 无障碍已连接"
            else -> "引擎已开启 · 等待无障碍服务"
        }
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_keepalive)
            .setContentTitle("wave工具箱")
            .setContentText(status)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_keepalive),
                    if (engineEnabled) "关闭引擎" else "开启引擎",
                    toggle,
                ).build(),
            )
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
            builder.setPriority(Notification.PRIORITY_LOW)
        }
        return builder.build()
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val TAG = "WaveA11y.KeepAlive"
        private const val CHANNEL_ID = "keepalive"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_REFRESH = "com.wavexzore.toolbox.action.KEEPALIVE_REFRESH"

        /** 启动（重复调用安全；后台限制时静默失败由调用方处理）。 */
        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.startForegroundService(intent)
        }

        fun refresh(context: Context) {
            context.startService(Intent(context, KeepAliveService::class.java).setAction(ACTION_REFRESH))
        }
    }
}
