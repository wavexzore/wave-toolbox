package com.wavexzore.toolbox.core.accessibility

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 无障碍自愈（Secure Settings）纯 JVM 测试：移植自 coc-player-v2
 * `AccessibilityServiceBootstrapTest`，覆盖合并/剔除/未授权/写失败/受控重绑/中断。
 */
class AccessibilityServiceBootstrapTest {

    private companion object {
        const val OWN = "com.wavexzore.toolbox/com.google.android.accessibility.selecttospeak.SelectToSpeakService"
    }

    @Test
    fun mergeEnabledServicesKeepsExternalServicesAndAddsOwnOnce() {
        assertEquals("a/b:c/d:$OWN", AccessibilityServiceBootstrap.mergeEnabledServices("a/b:c/d", OWN))
    }

    @Test
    fun mergeEnabledServicesDeduplicatesOwnComponent() {
        assertEquals(
            "a/b:$OWN:c/d",
            AccessibilityServiceBootstrap.mergeEnabledServices("a/b:$OWN:c/d:$OWN", OWN),
        )
    }

    @Test
    fun ensureEnabledReturnsNotGrantedWithoutWrites() {
        val gateway = FakeGateway("a/b:c/d").apply { throwSecurityException = true }

        assertEquals(
            AccessibilityServiceBootstrap.Result.NOT_GRANTED,
            AccessibilityServiceBootstrap.ensureEnabled(gateway, OWN, isConnected = { false }, sleep = {}),
        )
        assertEquals(0, gateway.enabledServicesWrites)
        assertEquals(0, gateway.accessibilityEnabledWrites)
    }

    @Test
    fun ensureEnabledDoesNotEnableAccessibilityWhenServiceListWriteFails() {
        val gateway = FakeGateway("a/b:c/d").apply { enabledServicesWriteSucceeds = false }

        assertEquals(
            AccessibilityServiceBootstrap.Result.WRITE_FAILED,
            AccessibilityServiceBootstrap.ensureEnabled(gateway, OWN, isConnected = { false }, sleep = {}),
        )
        assertEquals(1, gateway.enabledServicesWrites)
        assertEquals(0, gateway.accessibilityEnabledWrites)
    }

    @Test
    fun ensureEnabledAppendsOwnWhenMissing() {
        val gateway = FakeGateway("a/b:c/d")

        assertEquals(
            AccessibilityServiceBootstrap.Result.ENABLED,
            AccessibilityServiceBootstrap.ensureEnabled(gateway, OWN, isConnected = { false }, sleep = {}),
        )
        assertEquals("a/b:c/d:$OWN", gateway.enabledServices)
        assertEquals(1, gateway.enabledServicesWrites)
        assertTrue(gateway.accessibilityEnabledValues.single())
    }

    @Test
    fun ensureEnabledIsNoopWhenConnectedAndAlreadyPresent() {
        val gateway = FakeGateway("a/b:$OWN:c/d")

        assertEquals(
            AccessibilityServiceBootstrap.Result.ALREADY_ENABLED,
            AccessibilityServiceBootstrap.ensureEnabled(gateway, OWN, isConnected = { true }, sleep = {}),
        )
        assertEquals(0, gateway.enabledServicesWrites)
        assertEquals(0, gateway.accessibilityEnabledWrites)
    }

    /**
     * 进程重启断连重绑：列表已含自身但服务未连接（设备上「Enabled 未 Bound」等价态）
     * → 必须执行「移除-再加回」受控序列（coc-player-v2 B-002：crashed 标记仅此序列可清）。
     */
    @Test
    fun ensureEnabledRebindsWhenOwnAlreadyPresentAndServiceNotConnected() {
        val gateway = FakeGateway("a/b:c/d:$OWN")

        assertEquals(
            AccessibilityServiceBootstrap.Result.ENABLED,
            AccessibilityServiceBootstrap.ensureEnabled(gateway, OWN, isConnected = { false }, sleep = {}),
        )
        assertEquals(2, gateway.enabledServicesWrites)
        assertEquals("a/b:c/d", gateway.enabledServicesValues[0])
        assertEquals("a/b:c/d:$OWN", gateway.enabledServicesValues[1])
        assertEquals("a/b:c/d:$OWN", gateway.enabledServices)
        assertTrue(gateway.accessibilityEnabledValues.single())
    }

    @Test
    fun rebindEnabledPreservesExternalServicesAndRestoresOwnComponent() {
        val gateway = FakeGateway("a/b:c/d")

        assertEquals(AccessibilityServiceBootstrap.Result.ENABLED, AccessibilityServiceBootstrap.rebindEnabled(gateway, OWN, sleep = {}))
        assertEquals("a/b:c/d:$OWN", gateway.enabledServices)
        assertEquals(2, gateway.enabledServicesWrites)
        assertEquals("a/b:c/d", gateway.enabledServicesValues[0])
        assertEquals("a/b:c/d:$OWN", gateway.enabledServicesValues[1])
        assertTrue(gateway.accessibilityEnabledValues.single())
    }

    /** 序列顺序敏感：先写不含自身的列表，再写回完整列表。 */
    @Test
    fun rebindEnabledWritesRemovalBeforeRestoreInOrder() {
        val gateway = FakeGateway("a/b:$OWN:c/d")

        assertEquals(AccessibilityServiceBootstrap.Result.ENABLED, AccessibilityServiceBootstrap.rebindEnabled(gateway, OWN, sleep = {}))
        assertEquals("a/b:c/d", gateway.enabledServicesValues[0])
        assertEquals("a/b:$OWN:c/d", gateway.enabledServicesValues[1])
    }

    /** 自身为唯一服务：移除阶段写空串，恢复阶段写回自身。 */
    @Test
    fun rebindEnabledHandlesOwnOnlyList() {
        val gateway = FakeGateway(OWN)

        assertEquals(AccessibilityServiceBootstrap.Result.ENABLED, AccessibilityServiceBootstrap.rebindEnabled(gateway, OWN, sleep = {}))
        assertEquals("", gateway.enabledServicesValues[0])
        assertEquals(OWN, gateway.enabledServicesValues[1])
    }

    @Test
    fun rebindEnabledStopsWhenInterrupted() {
        val gateway = FakeGateway("a/b:c/d")
        Thread.currentThread().interrupt()
        try {
            assertEquals(
                AccessibilityServiceBootstrap.Result.INTERRUPTED,
                // 真实 Thread.sleep：中断置位时立即抛出（清除标记），实现按 fail-closed 重新置位
                AccessibilityServiceBootstrap.rebindEnabled(gateway, OWN),
            )
            // 中断发生在移除之后：自身保持移除（下次冷启动恢复），未写总开关
            assertEquals("a/b:c/d", gateway.enabledServices)
            assertEquals(1, gateway.enabledServicesWrites)
            assertEquals(0, gateway.accessibilityEnabledWrites)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun removeEnabledServicesDropsOwnAndKeepsOthers() {
        assertEquals("a/b:c/d", AccessibilityServiceBootstrap.removeEnabledServices("a/b:$OWN:c/d:$OWN", OWN))
        assertEquals("", AccessibilityServiceBootstrap.removeEnabledServices(OWN, OWN))
        assertEquals("", AccessibilityServiceBootstrap.removeEnabledServices(null, OWN))
    }

    private class FakeGateway(enabledServices: String?) :
        AccessibilityServiceBootstrap.SecureSettingsGateway {

        var enabledServices: String? = enabledServices
        var enabledServicesWriteSucceeds = true
        var throwSecurityException = false
        var enabledServicesWrites = 0
        var accessibilityEnabledWrites = 0
        val enabledServicesValues = ArrayList<String>()
        val accessibilityEnabledValues = ArrayList<Boolean>()

        override fun getEnabledAccessibilityServices(): String? {
            if (throwSecurityException) throw SecurityException()
            return enabledServices
        }

        override fun putEnabledAccessibilityServices(value: String): Boolean {
            enabledServicesWrites++
            enabledServicesValues.add(value)
            if (enabledServicesWriteSucceeds) enabledServices = value
            return enabledServicesWriteSucceeds
        }

        override fun putAccessibilityEnabled(value: Boolean): Boolean {
            accessibilityEnabledWrites++
            accessibilityEnabledValues.add(value)
            return true
        }
    }
}
