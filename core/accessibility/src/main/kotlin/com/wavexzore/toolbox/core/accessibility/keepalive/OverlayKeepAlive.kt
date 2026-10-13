package com.wavexzore.toolbox.core.accessibility.keepalive

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 1×1 透明 overlay（T-009，D-005）：`TYPE_ACCESSIBILITY_OVERLAY` 可触碰性关闭，
 * 作为独立于 FGS 的第二重保活（上游 gkd 验证）。
 */
class OverlayKeepAlive(private val service: AccessibilityService) {

    private var view: View? = null

    fun attach() {
        if (view != null) return
        try {
            val overlay = View(service)
            val params = WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSPARENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            val manager = service.getSystemService(WindowManager::class.java)
            manager.addView(overlay, params)
            view = overlay
            Log.i(TAG, "overlay attached")
        } catch (t: Throwable) {
            Log.w(TAG, "overlay attach failed", t)
        }
    }

    fun detach() {
        val overlay = view ?: return
        view = null
        runCatching { service.getSystemService(WindowManager::class.java).removeView(overlay) }
            .onSuccess { Log.i(TAG, "overlay detached") }
            .onFailure { Log.w(TAG, "overlay detach failed", it) }
    }

    companion object {
        const val TAG = "WaveA11y.KeepAlive"
    }
}
