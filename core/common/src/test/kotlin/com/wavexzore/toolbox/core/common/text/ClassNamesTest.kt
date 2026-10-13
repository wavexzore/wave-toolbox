package com.wavexzore.toolbox.core.common.text

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ClassNames 单测（R-209）：严格版语义 + 三处副本差异用例（裸类名/null/框架类）。 */
class ClassNamesTest {

    @Test
    fun activityClassesAreActivityLike() {
        assertTrue(ClassNames.isActivityLike("com.psyone.brainmusic.ui.activity.SplashHotAdActivity"))
        assertTrue(ClassNames.isActivityLike("com.psyone.brainmusic.ui.activity.MainActivity"))
        assertTrue(ClassNames.isActivityLike("com.tencent.mm.ui.LauncherUI"))
        assertTrue(ClassNames.isActivityLike("com.taobao.tao.welcome.Welcome"))
    }

    @Test
    fun frameworkWindowsAreNotActivityLike() {
        assertFalse(ClassNames.isActivityLike("android.app.Dialog"))
        assertFalse(ClassNames.isActivityLike("android.widget.FrameLayout"))
        assertFalse(ClassNames.isActivityLike("androidx.drawerlayout.widget.DrawerLayout"))
        assertFalse(ClassNames.isActivityLike("com.foo.widget.SomePopupWindow"))
        assertFalse(ClassNames.isActivityLike("com.foo.policy.MyWindow"))
        assertFalse(ClassNames.isActivityLike("android.support.v7.widget.RecyclerView"))
        assertFalse(ClassNames.isActivityLike("com.android.internal.policy.DecorView"))
        assertFalse(ClassNames.isActivityLike("com.android.systemui.recents.RecentsActivity2Window"))
    }

    /** 差异用例（R-209 取舍：统一为严格版——裸类名不算 Activity）。 */
    @Test
    fun bareOrBlankClassIsNotActivityLike() {
        assertFalse(ClassNames.isActivityLike(null))
        assertFalse(ClassNames.isActivityLike(""))
        assertFalse(ClassNames.isActivityLike("   "))
        assertFalse(ClassNames.isActivityLike("Main"))
        assertFalse(ClassNames.isActivityLike("Activity"))
    }
}
