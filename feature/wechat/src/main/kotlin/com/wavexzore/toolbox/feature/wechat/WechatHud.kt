package com.wavexzore.toolbox.feature.wechat

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 进度 HUD（T-006，D-206）：无障碍 overlay（免 SYSTEM_ALERT_WINDOW），左侧竖排，
 * 含 状态文本 + 暂停/继续 + 停止；不抢焦点、可触碰；不遮右上"跳过/完成"区。
 */
@Singleton
class WechatHud @Inject constructor() {

    private val main = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var titleView: TextView? = null
    private var toggleView: TextView? = null

    var onToggle: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null

    private var paused = false

    fun show(initial: String) {
        if (root != null) {
            update(initial)
            return
        }
        val service = SelectToSpeakService.instance ?: return
        main.post {
            runCatching {
                val density = service.resources.displayMetrics.density
                fun dp(v: Int) = (v * density).toInt()

                val pill = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.argb(210, 0x22, 0x22, 0x22))
                }
                val container = LinearLayout(service).apply {
                    orientation = LinearLayout.VERTICAL
                    background = pill
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                }
                val title = TextView(service).apply {
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    text = initial
                    maxWidth = dp(150)
                }
                val row = LinearLayout(service).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                val toggle = TextView(service)
                toggle.apply {
                    setTextColor(Color.rgb(0x9E, 0xE0, 0x9E))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setPadding(dp(6), dp(4), dp(6), dp(4))
                    text = "暂停"
                    setOnClickListener {
                        paused = !paused
                        toggle.text = if (paused) "继续" else "暂停"
                        onToggle?.invoke()
                    }
                }
                val stop = TextView(service).apply {
                    setTextColor(Color.rgb(0xFF, 0xB4, 0xB4))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setPadding(dp(6), dp(4), dp(6), dp(4))
                    text = "停止"
                    setOnClickListener { onStop?.invoke() }
                }
                row.addView(toggle)
                row.addView(stop)
                container.addView(title)
                container.addView(row)

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    android.graphics.PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    x = dp(4)
                }
                service.getSystemService(WindowManager::class.java).addView(container, params)
                root = container
                titleView = title
                toggleView = toggle
                paused = false
                toggle.text = "暂停"
            }
        }
    }

    fun update(text: String) {
        main.post { titleView?.text = text }
    }

    fun hide() {
        val service = SelectToSpeakService.instance
        main.post {
            runCatching {
                root?.let { view -> service?.getSystemService(WindowManager::class.java)?.removeView(view) }
            }
            root = null
            titleView = null
            toggleView = null
        }
    }
}
