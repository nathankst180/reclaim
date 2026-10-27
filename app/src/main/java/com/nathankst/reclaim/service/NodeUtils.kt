package com.nathankst.reclaim.service

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

// recycle() is a no-op from API 33 and still needed on 28-32.
@Suppress("DEPRECATION")
internal fun AccessibilityNodeInfo.release() = recycle()

@Suppress("DEPRECATION")
internal fun AccessibilityWindowInfo.release() = recycle()
