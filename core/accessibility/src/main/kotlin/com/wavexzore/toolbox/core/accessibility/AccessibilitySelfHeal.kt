package com.wavexzore.toolbox.core.accessibility

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 无障碍自愈（Android 侧装配）：Gateway 绑定 ContentResolver、连接态取
 * [SelectToSpeakService.instance]、授权检查与 adb 授权命令文案。
 *
 * 触发点：应用冷启动（`ToolboxApp`，后台线程）+ 设置页「立即修复」按钮。
 */
@Singleton
class AccessibilitySelfHeal @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 自身服务组件（`pkg/全限定类名`，与系统设置串同格式）。 */
    val ownComponent: String =
        ComponentName(context.packageName, SelectToSpeakService::class.java.name).flattenToString()

    /** 一次性 adb 授权命令（展给用户复制）。 */
    val grantCommand: String =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    /** WRITE_SECURE_SETTINGS 是否已授权。 */
    fun isGranted(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** 服务当前是否已连接（与自愈共用同一判据）。 */
    fun isConnected(): Boolean = SelectToSpeakService.instance != null

    /** 授权 × 连接联合状态（UI 展示用）。 */
    data class HealStatus(val granted: Boolean, val connected: Boolean)

    /** 冷启动、设置页按钮、状态轮询可能同时触发自愈；串行化避免交叉读写 Secure Settings。 */
    private val healMutex = Mutex()

    /** 状态轮询流（2s 间隔，仅订阅期运行；grant/返回页面后可自更新）。 */
    val status: Flow<HealStatus> = flow {
        while (true) {
            emit(HealStatus(isGranted(), isConnected()))
            delay(STATUS_POLL_MS)
        }
    }.distinctUntilChanged()

    /**
     * 执行自愈（可在主线程调用；内部切 Default，重绑等待不阻塞 UI）。
     * 未授权/写失败等以 [AccessibilityServiceBootstrap.Result] 返回，不抛异常。
     */
    suspend fun heal(): AccessibilityServiceBootstrap.Result = healMutex.withLock {
        withContext(Dispatchers.Default) {
            AccessibilityServiceBootstrap.ensureEnabled(
                gateway = AndroidSecureSettingsGateway(context.contentResolver),
                ownComponent = ownComponent,
                isConnected = { SelectToSpeakService.instance != null },
            )
        }
    }

    /** 仅使用 ContentResolver 访问 Secure Settings。 */
    private class AndroidSecureSettingsGateway(private val contentResolver: ContentResolver) :
        AccessibilityServiceBootstrap.SecureSettingsGateway {

        override fun getEnabledAccessibilityServices(): String? =
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

        override fun putEnabledAccessibilityServices(value: String): Boolean =
            Settings.Secure.putString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, value)

        override fun putAccessibilityEnabled(value: Boolean): Boolean =
            Settings.Secure.putString(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, if (value) "1" else "0")
    }

    companion object {
        private const val STATUS_POLL_MS = 2_000L
    }
}
