package com.wavexzore.toolbox.core.selector

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ResourceMatcher 单测（R-209）：vid 片段包含 / 全等-后缀两种语义。 */
class ResourceMatcherTest {

    @Test
    fun containsIsCaseInsensitive() {
        assertTrue(ResourceMatcher.vidContains("com.app:id/itv_jump", "jump"))
        assertTrue(ResourceMatcher.vidContains("com.app:id/IV_SKIP", "skip"))
        assertFalse(ResourceMatcher.vidContains("com.app:id/close", "skip"))
        assertFalse(ResourceMatcher.vidContains(null, "skip"))
        assertFalse(ResourceMatcher.vidContains("com.app:id/skip", ""))
    }

    @Test
    fun equalsAcceptsFullOrResourceSuffix() {
        assertTrue(ResourceMatcher.vidEquals("close", "close"))
        assertTrue(ResourceMatcher.vidEquals("com.app:id/close", "close"))
        assertFalse(ResourceMatcher.vidEquals("com.app:id/close_btn", "close"))
        assertFalse(ResourceMatcher.vidEquals(null, "close"))
    }
}
