package com.nathankst.reclaim.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

/** True when the user has switched Reclaim on in Android's accessibility settings. */
fun isTrackingServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}
