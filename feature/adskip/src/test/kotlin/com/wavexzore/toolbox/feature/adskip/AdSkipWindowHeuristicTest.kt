package com.wavexzore.toolbox.feature.adskip

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 窗口启发式单测（误操作修复 · 2026-10-14）：
 * - [looksLikeActivity]：识别真实 Activity 窗口与弹窗/抽屉/框架窗口（仅开屏模式据此不碰弹窗）；
 * - [isUniformlyScaled]：识别应用开关动画中的等比缩放窗口（避免把开屏误判为小窗）。
 */
class AdSkipWindowHeuristicTest {

    @Test
    fun activityClassesAreActivityLike() {
        assertTrue(looksLikeActivity("com.psyone.brainmusic.ui.activity.SplashHotAdActivity"))
        assertTrue(looksLikeActivity("com.psyone.brainmusic.ui.activity.MainActivity"))
        assertTrue(looksLikeActivity("com.tencent.mm.ui.LauncherUI"))
        assertTrue(looksLikeActivity("com.taobao.tao.welcome.Welcome"))
    }

    @Test
    fun frameworkWindowsAreNotActivityLike() {
        assertFalse(looksLikeActivity("android.app.Dialog"))
        assertFalse(looksLikeActivity("android.widget.FrameLayout"))
        assertFalse(looksLikeActivity("androidx.drawerlayout.widget.DrawerLayout"))
        assertFalse(looksLikeActivity("com.foo.widget.SomePopupWindow"))
        assertFalse(looksLikeActivity("com.foo.policy.MyWindow"))
        assertFalse(looksLikeActivity("android.support.v7.widget.RecyclerView"))
        assertFalse(looksLikeActivity("com.android.internal.policy.DecorView"))
    }

    @Test
    fun blankOrBareClassIsNotActivityLike() {
        assertFalse(looksLikeActivity(null))
        assertFalse(looksLikeActivity(""))
        assertFalse(looksLikeActivity("   "))
        assertFalse(looksLikeActivity("Main"))
    }

    @Test
    fun popupWindowClassesAreDetected() {
        assertTrue(isPopupWindowClass("android.app.Dialog"))
        assertTrue(isPopupWindowClass("android.widget.PopupWindow\$PopupDecorView"))
        assertFalse(isPopupWindowClass("androidx.viewpager.widget.ViewPager"))
        assertFalse(isPopupWindowClass("androidx.drawerlayout.widget.DrawerLayout"))
        assertFalse(isPopupWindowClass("com.psyone.brainmusic.ui.activity.MainActivity"))
        assertFalse(isPopupWindowClass(null))
    }

    @Test
    fun uniformSmallScalingIsAnimation() {
        // MIUI 应用打开动画：0.26 倍等比缩放（实测 315x685 / 1208x2624）
        assertTrue(isUniformlyScaled(0.26f, 0.26f))
        assertTrue(isUniformlyScaled(0.2f, 0.3f))
        assertTrue(isUniformlyScaled(0.5f, 0.55f))
    }

    @Test
    fun asymmetricWindowsAreSmallNotAnimation() {
        // 抽屉：宽 75% 高 100%
        assertFalse(isUniformlyScaled(0.75f, 1.0f))
        // 底部弹层：宽 100% 高 40%
        assertFalse(isUniformlyScaled(1.0f, 0.4f))
        // 比例差超阈值
        assertFalse(isUniformlyScaled(0.3f, 0.6f))
        // 未达到"都明显小于屏"
        assertFalse(isUniformlyScaled(0.9f, 0.85f))
    }
}
