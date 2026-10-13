package com.wavexzore.toolbox.core.accessibility.action

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClickResolverTest {

    private val screen = 1080

    @Test
    fun `clickable target resolves direct to itself`() {
        val target = A11yTestTrees.node(text = "跳过", clickable = true)
        val resolution = ClickResolver.resolve(target, emptyList(), screen)
        assertTrue(resolution is ClickResolver.Resolution.Direct)
        assertEquals(target, (resolution as ClickResolver.Resolution.Direct).view)
    }

    @Test
    fun `climbs to nearest clickable ancestor`() {
        val target = A11yTestTrees.node(text = "跳过")
        val button = A11yTestTrees.node(cls = "android.widget.Button", clickable = true)
        val resolution = ClickResolver.resolve(target, listOf(button), screen)
        assertTrue(resolution is ClickResolver.Resolution.Direct)
        assertEquals(button, (resolution as ClickResolver.Resolution.Direct).view)
    }

    @Test
    fun `editable clickable is skipped for ancestor`() {
        val target = A11yTestTrees.node(text = "输入", cls = "android.widget.EditText", clickable = true, editable = true)
        val row = A11yTestTrees.node(cls = "android.widget.LinearLayout", clickable = true)
        val resolution = ClickResolver.resolve(target, listOf(row), screen)
        assertTrue(resolution is ClickResolver.Resolution.Direct)
        assertEquals(row, (resolution as ClickResolver.Resolution.Direct).view)
    }

    @Test
    fun `checkable clickable falls back to center tap`() {
        val target = A11yTestTrees.node(cls = "android.widget.CheckBox", clickable = true, text = "全选")
        val resolution = ClickResolver.resolve(target, emptyList(), screen)
        assertTrue(resolution is ClickResolver.Resolution.CenterTap)
        assertEquals(50, (resolution as ClickResolver.Resolution.CenterTap).x)
        assertEquals(25, resolution.y)
    }

    @Test
    fun `wide container without text is filtered`() {
        val wide = A11yTestTrees.node(text = null, right = 800, clickable = true)
        assertTrue(ClickResolver.isFilteredTarget(wide, screen))
        val wideWithText = A11yTestTrees.node(text = "标题", right = 800, clickable = true)
        assertFalse(ClickResolver.isFilteredTarget(wideWithText, screen))
        val narrow = A11yTestTrees.node(right = 300, clickable = true)
        assertFalse(ClickResolver.isFilteredTarget(narrow, screen))
    }

    @Test
    fun `scrollable clickable is filtered`() {
        val target = A11yTestTrees.node(cls = "androidx.recyclerview.widget.RecyclerView", clickable = true, scrollable = true)
        assertTrue(ClickResolver.isFilteredTarget(target, screen))
    }

    @Test
    fun `invisible clickable is skipped`() {
        val target = A11yTestTrees.node(text = "隐", clickable = true, visible = false)
        val resolution = ClickResolver.resolve(target, emptyList(), screen)
        assertTrue(resolution is ClickResolver.Resolution.CenterTap)
    }

    @Test
    fun `zero area without candidate is no target`() {
        val target = A11yTestTrees.node(left = 10, top = 10, right = 10, bottom = 10)
        assertEquals(ClickResolver.Resolution.NoTarget, ClickResolver.resolve(target, emptyList(), screen))
    }

    @Test
    fun `zero area target still climbs to clickable ancestor`() {
        val target = A11yTestTrees.node(left = 10, top = 10, right = 10, bottom = 10)
        val ancestor = A11yTestTrees.node(clickable = true)
        val resolution = ClickResolver.resolve(target, listOf(ancestor), screen)
        assertTrue(resolution is ClickResolver.Resolution.Direct)
    }

    @Test
    fun `checkable class names`() {
        assertTrue(ClickResolver.isCheckableClass("android.widget.CheckBox"))
        assertTrue(ClickResolver.isCheckableClass("android.widget.Switch"))
        assertFalse(ClickResolver.isCheckableClass("android.widget.Button"))
        assertFalse(ClickResolver.isCheckableClass(null))
    }
}
