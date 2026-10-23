package com.nathankst.reclaim.service

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nathankst.reclaim.counting.CountingConfig
import com.nathankst.reclaim.counting.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Debug builds only. Logs what Instagram and YouTube send us so the real player view IDs can be confirmed.
 * Never reads text, captions, usernames or content descriptions: only event types, class names and view IDs.
 */
object DebugInspector {

    const val TAG = "ReclaimDbg"

    private const val CONTENT_LOG_THROTTLE_MS = 250L
    private const val LOG_CHUNK_CHARS = 3500

    private val _logEnabled = MutableStateFlow(true)
    val logEnabled: StateFlow<Boolean> = _logEnabled.asStateFlow()

    private val _dumpPending = MutableStateFlow(false)
    val dumpPending: StateFlow<Boolean> = _dumpPending.asStateFlow()

    private val _lastDump = MutableStateFlow<String?>(null)
    val lastDump: StateFlow<String?> = _lastDump.asStateFlow()

    private var lastContentLogMs = 0L

    fun setLogEnabled(enabled: Boolean) {
        _logEnabled.value = enabled
    }

    fun requestDump() {
        _lastDump.value = null
        _dumpPending.value = true
    }

    fun cancelDump() {
        _dumpPending.value = false
    }

    fun onEvent(event: AccessibilityEvent, rootProvider: () -> AccessibilityNodeInfo?) {
        val pkg = event.packageName?.toString() ?: return
        val source = Source.fromPackage(pkg) ?: return
        if (_logEnabled.value) logEvent(event, pkg, source)
        if (_dumpPending.value) dumpViewIds(pkg, rootProvider())
    }

    private fun logEvent(event: AccessibilityEvent, pkg: String, source: Source) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val now = SystemClock.uptimeMillis()
            if (now - lastContentLogMs < CONTENT_LOG_THROTTLE_MS) return
            lastContentLogMs = now
        }
        val node = event.source
        val viewId = node?.viewIdResourceName
        node?.release()
        val knownPlayerId = viewId != null && viewId in CountingConfig.playerIdsFor(source)
        Log.d(
            TAG,
            "${AccessibilityEvent.eventTypeToString(event.eventType)} pkg=$pkg view=$viewId " +
                "class=${event.className} from=${event.fromIndex} to=${event.toIndex} " +
                "count=${event.itemCount} dY=${event.scrollDeltaY} scrollY=${event.scrollY} " +
                "knownPlayerId=$knownPlayerId",
        )
    }

    private fun dumpViewIds(pkg: String, root: AccessibilityNodeInfo?) {
        if (root == null) return
        if (root.packageName?.toString() != pkg) {
            root.release()
            return
        }
        _dumpPending.value = false
        val lines = mutableListOf<String>()
        val stats = DumpStats()
        walk(root, 0, lines, stats, Rect())
        root.release()

        Log.d(TAG, "DUMP begin pkg=$pkg")
        val chunk = StringBuilder()
        for (line in lines) {
            if (chunk.length + line.length > LOG_CHUNK_CHARS) {
                Log.d(TAG, chunk.toString().trimEnd())
                chunk.clear()
            }
            chunk.append(line).append('\n')
        }
        if (chunk.isNotEmpty()) Log.d(TAG, chunk.toString().trimEnd())
        Log.d(TAG, "DUMP end pkg=$pkg views=${stats.views} withId=${stats.withId}")
        _lastDump.value = "$pkg: ${stats.views} views, ${stats.withId} with IDs"
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int,
        lines: MutableList<String>,
        stats: DumpStats,
        rect: Rect,
    ) {
        stats.views++
        val id = node.viewIdResourceName
        if (id != null) stats.withId++
        if (id != null || node.isScrollable) {
            node.getBoundsInScreen(rect)
            lines += "DUMP depth=$depth id=${id ?: "-"} class=${node.className} " +
                "visible=${node.isVisibleToUser} scrollable=${node.isScrollable} bounds=${rect.flattenToString()}"
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, depth + 1, lines, stats, rect)
            child.release()
        }
    }

    private class DumpStats {
        var views = 0
        var withId = 0
    }

    // recycle() is a no-op from API 33 and still needed on 28-32.
    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.release() = recycle()
}
