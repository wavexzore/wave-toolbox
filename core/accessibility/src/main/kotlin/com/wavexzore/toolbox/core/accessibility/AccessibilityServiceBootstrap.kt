package com.wavexzore.toolbox.core.accessibility

/**
 * 无障碍服务 Secure Settings 自愈引导（移植自 coc-player-v2 `AccessibilityServiceBootstrap`）。
 *
 * 背景：MIUI/HyperOS 及 Android 12+ 会因内存回收/异常将无障碍服务置为
 * 「已启用但未绑定」或打上 crashed 标记；仅翻转总开关无法恢复。可恢复序列为
 * 把自身组件从 `enabled_accessibility_services` 中移除、短暂等待后再加回
 * （受控重绑，coc-player-v2 B-002 设备实验结论）。
 *
 * 需要 `android.permission.WRITE_SECURE_SETTINGS`：非常规安装权限（signature|development），
 * 非 root 设备需一次性 `adb shell pm grant`；未授权时读写抛 [SecurityException] → [Result.NOT_GRANTED]。
 */
object AccessibilityServiceBootstrap {

    /** 移除与加回之间的等待（毫秒）：给系统时间注销旧绑定。 */
    const val REBIND_DELAY_MS = 300L

    enum class Result {
        ENABLED,
        ALREADY_ENABLED,
        NOT_GRANTED,
        WRITE_FAILED,
        INTERRUPTED,
    }

    /** 将 Secure Settings 访问隔离为可替换的最小网关（纯 JVM 可测）。 */
    interface SecureSettingsGateway {
        fun getEnabledAccessibilityServices(): String?

        fun putEnabledAccessibilityServices(value: String): Boolean

        fun putAccessibilityEnabled(value: Boolean): Boolean
    }

    /** 合并自身组件（去重、保持他方服务与顺序；自身不存在则追加）。 */
    fun mergeEnabledServices(enabledServices: String?, ownComponent: String): String {
        val merged = ArrayList<String>()
        var ownAdded = false
        if (!enabledServices.isNullOrEmpty()) {
            for (service in enabledServices.split(':')) {
                if (service.isEmpty()) continue
                if (service == ownComponent) {
                    if (!ownAdded) {
                        merged.add(service)
                        ownAdded = true
                    }
                } else {
                    merged.add(service)
                }
            }
        }
        if (!ownAdded) merged.add(ownComponent)
        return merged.joinToString(":")
    }

    /** 从列表剔除自身组件（保留其他全部服务；用于重绑的移除阶段）。 */
    fun removeEnabledServices(enabledServices: String?, ownComponent: String): String {
        if (enabledServices.isNullOrEmpty()) return ""
        return enabledServices.split(':')
            .filter { it.isNotEmpty() && it != ownComponent }
            .joinToString(":")
    }

    /**
     * 确保自身在启用列表且服务已连接：
     * - 列表缺自身 → 合并写回 + 打开总开关；
     * - 列表已含自身但服务未连接（进程重启常见态/被回收）→ 受控重绑；
     * - 已含自身且已连接 → [Result.ALREADY_ENABLED]（幂等，不写设置）。
     */
    fun ensureEnabled(
        gateway: SecureSettingsGateway,
        ownComponent: String,
        isConnected: () -> Boolean,
        sleep: (Long) -> Unit = { Thread.sleep(it) },
    ): Result {
        return try {
            val enabledServices = gateway.getEnabledAccessibilityServices()
            val mergedServices = mergeEnabledServices(enabledServices, ownComponent)
            if (mergedServices == enabledServices) {
                if (isConnected()) Result.ALREADY_ENABLED
                else rebindEnabled(gateway, ownComponent, sleep)
            } else {
                if (!gateway.putEnabledAccessibilityServices(mergedServices)) return Result.WRITE_FAILED
                if (gateway.putAccessibilityEnabled(true)) Result.ENABLED else Result.WRITE_FAILED
            }
        } catch (e: SecurityException) {
            Result.NOT_GRANTED
        }
    }

    /**
     * 受控重绑：移除 → 等待 → 加回 → 打开总开关。
     * 全程不翻转总开关中间态；始终以完整合并列表写回，他方服务 token 不丢失；
     * 中断发生在移除之后时自身保持移除（fail-closed，下次冷启动恢复）。
     */
    fun rebindEnabled(
        gateway: SecureSettingsGateway,
        ownComponent: String,
        sleep: (Long) -> Unit = { Thread.sleep(it) },
    ): Result {
        return try {
            val enabledServices = gateway.getEnabledAccessibilityServices()
            if (!gateway.putEnabledAccessibilityServices(removeEnabledServices(enabledServices, ownComponent))) {
                return Result.WRITE_FAILED
            }
            try {
                sleep(REBIND_DELAY_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return Result.INTERRUPTED
            }
            if (!gateway.putEnabledAccessibilityServices(mergeEnabledServices(enabledServices, ownComponent))) {
                return Result.WRITE_FAILED
            }
            if (gateway.putAccessibilityEnabled(true)) Result.ENABLED else Result.WRITE_FAILED
        } catch (e: SecurityException) {
            Result.NOT_GRANTED
        }
    }
}
