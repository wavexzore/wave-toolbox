package com.wavexzore.toolbox.core.accessibility.keepalive

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 直启广播不能在用户解锁前读设置（凭据加密 DataStore 不可用）。 */
class BootReceiverTest {

    @Test
    fun `locked boot is deferred before user unlock`() {
        assertTrue(BootReceiver.shouldDeferLockedBoot("android.intent.action.LOCKED_BOOT_COMPLETED", userUnlocked = false))
    }

    @Test
    fun `locked boot is handled after user unlock`() {
        assertFalse(BootReceiver.shouldDeferLockedBoot("android.intent.action.LOCKED_BOOT_COMPLETED", userUnlocked = true))
    }

    @Test
    fun `normal boot is never deferred`() {
        assertFalse(BootReceiver.shouldDeferLockedBoot("android.intent.action.BOOT_COMPLETED", userUnlocked = false))
        assertFalse(BootReceiver.shouldDeferLockedBoot("android.intent.action.BOOT_COMPLETED", userUnlocked = true))
        assertFalse(BootReceiver.shouldDeferLockedBoot(null, userUnlocked = false))
    }
}
