package com.nathankst.reclaim.service

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nathankst.reclaim.counting.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Inspector builds only. Logs what Instagram and YouTube send us and what the counter did with it,
 * so the real player view IDs can be confirmed and accuracy can be tuned.
 * Never reads text, captions, usernames or content descriptions: only event types, class names and view IDs.
 */
object DebugInspector {

    const val TAG = "ReclaimDbg"

    private const val LOG_CHUNK_CHARS = 3500
    private const val MAX_BUFFERED_EVENTS = 150
    private const val DUMP_DELAY_MS = 10_000L
    private const val MAX_DUMPS = 4
    private const val MAX_DUMP_LINES = 120
    private const val AUTO_DUMP_GAP_MS = 12_000L

    private val _logEnabled = MutableStateFlow(true)
    val logEnabled: StateFlow<Boolean> = _logEnabled.asStateFlow()

    private val _dumpPending = MutableStateFlow(false)
    val dumpPending: StateFlow<Boolean> = _dumpPending.asStateFlow()

    private val _lastDump = MutableStateFlow<String?>(null)
    val lastDump: StateFlow<String?> = _lastDump.asStateFlow()

    private val _bufferedCount = MutableStateFlow(0)
    val bufferedCount: StateFlow<Int> = _bufferedCount.asStateFlow()

    private val buffer = ArrayDeque<String>()
    private val dumps = ArrayDeque<String>()
    private var dumpRequestedAtMs = 0L
    private var lastAutoDumpMs = 0L

    fun setLogEnabled(enabled: Boolean) {
        _logEnabled.value = enabled
    }

    /** The last events and counter decisions as plain text, for pasting when adb is not available. */
    fun eventsText(): String = synchronized(buffer) { buffer.joinToString("\n") }

    /** The last few snapshots of which views were on screen, or null if none were taken. */
    fun dumpText(): String? = synchronized(dumps) { if (dumps.isEmpty()) null else dumps.joinToString("\n\n") }

    fun clearCaptured() {
        synchronized(buffer) {
            buffer.clear()
            _bufferedCount.value = 0
        }
        synchronized(dumps) { dumps.clear() }
        _lastDump.value = null
    }

    /** The dump is taken on the first event 10 s after this, so there is time to open Reels or Shorts. */
    fun requestDump() {
        _lastDump.value = null
        dumpRequestedAtMs = SystemClock.uptimeMillis()
        _dumpPending.value = true
    }

    fun cancelDump() {
        _dumpPending.value = false
    }

    /** [viewId] is only known when the service asked the app for the event source. */
    fun logEvent(event: AccessibilityEvent, source: Source, viewId: String?, decision: String) {
        if (!_logEnabled.value) return
        emit(
            "${AccessibilityEvent.eventTypeToString(event.eventType).removePrefix("TYPE_")} src=$source " +
                "view=$viewId class=${event.className} from=${event.fromIndex} to=${event.toIndex} " +
                "count=${event.itemCount} dY=${event.scrollDeltaY} dX=${event.scrollDeltaX} -> $decision",
        )
    }

    fun note(text: String) {
        if (_logEnabled.value) emit(text)
    }

    fun maybeDump(pkg: String, rootProvider: () -> AccessibilityNodeInfo?) {
        if (!_dumpPending.value) return
        if (SystemClock.uptimeMillis() - dumpRequestedAtMs < DUMP_DELAY_MS) return
        val root = rootProvider() ?: return
        if (root.packageName?.toString() != pkg) {
            root.release()
            return
        }
        _dumpPending.value = false
        storeDump("manual", pkg, root)
    }

    /**
     * When a tap or a screen change did not lead to the player being found, snapshot which views are on screen,
     * so the real player view name can be read from the report. At most one every few seconds.
     */
    fun autoDump(label: String, pkg: String, root: AccessibilityNodeInfo?) {
        if (root == null) return
        val now = SystemClock.uptimeMillis()
        if (!_logEnabled.value || now - lastAutoDumpMs < AUTO_DUMP_GAP_MS || root.packageName?.toString() != pkg) {
            root.release()
            return
        }
        lastAutoDumpMs = now
        storeDump(label, pkg, root)
    }

    private fun emit(text: String) {
        val line = "t=${SystemClock.uptimeMillis() % 1_000_000} $text"
        Log.d(TAG, line)
        synchronized(buffer) {
            if (buffer.size >= MAX_BUFFERED_EVENTS) buffer.removeFirst()
            buffer.addLast(line)
            _bufferedCount.value = buffer.size
        }
    }

    private fun storeDump(label: String, pkg: String, root: AccessibilityNodeInfo) {
        val lines = mutableListOf<String>()
        val stats = DumpStats()
        walk(root, 0, lines, stats, Rect())
        root.release()
        val shown = lines.take(MAX_DUMP_LINES)
        val more = if (lines.size > shown.size) listOf("... ${lines.size - shown.size} more") else emptyList()
        val text = (listOf("VIEWS ($label) pkg=$pkg, ${stats.views} views, ${stats.withId} with IDs") + shown + more)
            .joinToString("\n")
        synchronized(dumps) {
            if (dumps.size >= MAX_DUMPS) dumps.removeFirst()
            dumps.addLast(text)
        }
        _lastDump.value = "$label: ${stats.views} views, ${stats.withId} with IDs"

        Log.d(TAG, "DUMP begin $label pkg=$pkg")
        val chunk = StringBuilder()
        for (line in shown) {
            if (chunk.length + line.length > LOG_CHUNK_CHARS) {
                Log.d(TAG, chunk.toString().trimEnd())
                chunk.clear()
            }
            chunk.append(line).append('\n')
        }
        if (chunk.isNotEmpty()) Log.d(TAG, chunk.toString().trimEnd())
        Log.d(TAG, "DUMP end $label views=${stats.views} withId=${stats.withId}")
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
            lines += "d=$depth id=${id?.substringAfter(":id/") ?: "-"} " +
                "cls=${node.className?.toString()?.substringAfterLast('.')} " +
                "vis=${if (node.isVisibleToUser) 1 else 0} scroll=${if (node.isScrollable) 1 else 0} " +
                "h=${rect.height()}"
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
}
