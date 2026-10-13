package com.wavexzore.toolbox.core.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 服务开启状态观测（T-003）：设置项 ContentObserver + AccessibilityManager 状态监听双通道，
 * 供首页/引导页实时展示"未开启 → 去开启"。
 */
@Singleton
class AccessibilityStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 服务是否已在系统无障碍设置中启用（实时）。 */
    val serviceEnabled: Flow<Boolean> = callbackFlow {
        fun emit() {
            trySend(isEnabled())
        }

        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                emit()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            contentObserver,
        )

        val manager = context.getSystemService(AccessibilityManager::class.java)
        val stateListener = AccessibilityManager.AccessibilityStateChangeListener { emit() }
        manager?.addAccessibilityStateChangeListener(stateListener)

        emit()
        awaitClose {
            context.contentResolver.unregisterContentObserver(contentObserver)
            manager?.removeAccessibilityStateChangeListener(stateListener)
        }
    }.distinctUntilChanged()

    /** 当前是否启用（一次性查询）。MIUI 上 manager 列表偶发返回空，优先解析系统设置串。 */
    fun isEnabled(): Boolean {
        if (isInEnabledListSetting()) return true
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                serviceInfo.packageName == context.packageName &&
                    serviceInfo.name == SelectToSpeakService::class.java.name
            }
    }

    private fun isInEnabledListSetting(): Boolean {
        val flat = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull() ?: return false
        if (flat.isBlank()) return false
        val expected = ComponentName(context.packageName, SelectToSpeakService::class.java.name)
        return flat.split(':').any { part ->
            ComponentName.unflattenFromString(part) == expected
        }
    }

    /** 跳转系统无障碍设置页。 */
    fun openAccessibilitySettings() {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
