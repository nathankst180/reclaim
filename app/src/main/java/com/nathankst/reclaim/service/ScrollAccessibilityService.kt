package com.nathankst.reclaim.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nathankst.reclaim.AppContainer
import com.nathankst.reclaim.BuildConfig
import com.nathankst.reclaim.ReclaimApp

/** Thin entry point: turns Instagram and YouTube accessibility events into small signals. */
class ScrollAccessibilityService : AccessibilityService() {

    private val container: AppContainer
        get() = (application as ReclaimApp).container

    override fun onServiceConnected() {
        super.onServiceConnected()
        container.setServiceRunning(true)
        if (BuildConfig.DEBUG) Log.d(DebugInspector.TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (BuildConfig.DEBUG) DebugInspector.onEvent(event) { rootInActiveWindow }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        container.setServiceRunning(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        container.setServiceRunning(false)
        super.onDestroy()
    }
}
