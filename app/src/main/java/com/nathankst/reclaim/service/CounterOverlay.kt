package com.nathankst.reclaim.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.nathankst.reclaim.R
import com.nathankst.reclaim.data.OverlayCorner

/**
 * The small glass pill in the corner. Added once as an accessibility overlay, which needs no extra
 * permission, and then only shown or hidden. It never takes touches.
 */
class CounterOverlay(private val service: AccessibilityService) {

    data class Model(val visible: Boolean, val count: Int, val corner: OverlayCorner)

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    )

    private val view = TextView(service).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = service.resources.getFont(R.font.jetbrains_mono_variable)
        fontVariationSettings = "'wght' 700"
        setPadding(dp(9), dp(4), dp(9), dp(4))
        background = GradientDrawable().apply {
            setColor(Color.argb(0x8C, 0, 0, 0))
            cornerRadius = dp(999).toFloat()
        }
        visibility = View.GONE
    }

    private var attached = false
    private var shownCount = NOT_SHOWN
    private var shownCorner: OverlayCorner? = null

    fun render(model: Model) {
        if (!model.visible) {
            if (attached) view.visibility = View.GONE
            shownCount = NOT_SHOWN
            return
        }
        if (!attached && !attach(model.corner)) return
        if (model.corner != shownCorner) {
            applyCorner(model.corner)
            runCatching { windowManager.updateViewLayout(view, params) }
        }
        val countChanged = shownCount != NOT_SHOWN && shownCount != model.count
        view.text = "↕ ${model.count}"
        view.visibility = View.VISIBLE
        shownCount = model.count
        if (countChanged) pop()
    }

    fun destroy() {
        if (attached) runCatching { windowManager.removeView(view) }
        attached = false
        shownCount = NOT_SHOWN
    }

    private fun attach(corner: OverlayCorner): Boolean {
        applyCorner(corner)
        return try {
            windowManager.addView(view, params)
            attached = true
            true
        } catch (e: RuntimeException) {
            Log.w("Reclaim", "Could not add the corner counter", e)
            false
        }
    }

    private fun applyCorner(corner: OverlayCorner) {
        shownCorner = corner
        val side = if (corner == OverlayCorner.TOP_RIGHT) Gravity.END else Gravity.START
        params.gravity = Gravity.TOP or side
        params.x = dp(12)
        params.y = statusBarHeight() + dp(8)
    }

    private fun pop() {
        view.animate().cancel()
        view.animate()
            .scaleX(1.15f).scaleY(1.15f).setDuration(75)
            .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(75).start() }
            .start()
    }

    private fun statusBarHeight(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private companion object {
        const val NOT_SHOWN = -1
    }
}
