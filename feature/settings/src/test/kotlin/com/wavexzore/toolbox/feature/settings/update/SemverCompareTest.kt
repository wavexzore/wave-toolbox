package com.wavexzore.toolbox.feature.settings.update

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [SemverCompare] 规则锁定（T-303；设计 04 §15.1.1）。 */
class SemverCompareTest {

    @Test
    fun `v 前缀剥离并可解析`() {
        assertContentEquals(intArrayOf(1, 2, 3), SemverCompare.parse("v1.2.3"))
        assertContentEquals(intArrayOf(1, 2, 3), SemverCompare.parse("V1.2.3"))
        assertContentEquals(intArrayOf(1, 2, 3), SemverCompare.parse("1.2.3"))
    }

    @Test
    fun `两段与四段可解析`() {
        assertContentEquals(intArrayOf(1, 2), SemverCompare.parse("v1.2"))
        assertContentEquals(intArrayOf(1, 2, 3, 4), SemverCompare.parse("v1.2.3.4"))
    }

    @Test
    fun `前导尾随空白容忍`() {
        assertContentEquals(intArrayOf(1, 2, 3), SemverCompare.parse("  v1.2.3  "))
    }

    @Test
    fun `非法串返回 null`() {
        assertNull(SemverCompare.parse("abc"))
        assertNull(SemverCompare.parse("v1"))
        assertNull(SemverCompare.parse("1.2.x"))
        assertNull(SemverCompare.parse("v1.2.3.4.5"))
        assertNull(SemverCompare.parse("v"))
        assertNull(SemverCompare.parse(""))
    }

    @Test
    fun `段长不等按 0 补齐`() {
        assertFalse(SemverCompare.isNewer("1.0", "1.0.0"))
        assertFalse(SemverCompare.isNewer("1.0.0", "1.0"))
        assertTrue(SemverCompare.isNewer("1.0.1", "1.0"))
    }

    @Test
    fun `逐段数值比较`() {
        assertTrue(SemverCompare.isNewer("1.0.1", "1.0.0"))
        assertFalse(SemverCompare.isNewer("1.0.0", "1.0.1"))
        assertFalse(SemverCompare.isNewer("1.0.0", "1.0.0"))
        assertTrue(SemverCompare.isNewer("2.0.0", "1.9.9"))
        assertFalse(SemverCompare.isNewer("1.2.0", "1.10.0"))
        assertTrue(SemverCompare.isNewer("1.10.0", "1.2.0"))
    }

    @Test
    fun `构建元数据忽略`() {
        assertContentEquals(intArrayOf(1, 0, 0), SemverCompare.parse("v1.0.0+build.5"))
        assertFalse(SemverCompare.isNewer("1.0.0+build.5", "1.0.0"))
    }

    @Test
    fun `先行版低于同版本正式版`() {
        assertFalse(SemverCompare.isNewer("1.0.1-rc1", "1.0.1"))
        assertTrue(SemverCompare.isNewer("1.0.1", "1.0.1-rc1"))
        assertTrue(SemverCompare.isNewer("1.0.1-rc1", "1.0.0"))
    }

    @Test
    fun `非法输入不更新`() {
        assertFalse(SemverCompare.isNewer("abc", "1.0.0"))
        assertFalse(SemverCompare.isNewer("1.0.1", "abc"))
    }
}
