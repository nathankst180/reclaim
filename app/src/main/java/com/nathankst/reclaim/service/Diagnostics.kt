package com.nathankst.reclaim.service

import com.nathankst.reclaim.counting.Source

/**
 * Remembers, in memory only, which views Instagram and YouTube scrolled and whether Reclaim recognised
 * them as the player. If counting ever looks wrong, "Copy a report" shares this.
 * Only view names and screen class names are kept: never any text, captions or usernames.
 */
object Diagnostics {

    private const val MAX_VIEWS = 14
    private const val MAX_SCREENS = 8

    private class Seen {
        var scrolls = 0
        var recognised = 0
        val views = LinkedHashMap<String, Int>()
        val screens = LinkedHashSet<String>()
    }

    private val seen = HashMap<Source, Seen>()
    private val lock = Any()
    private var foregroundChecks = 0
    private var otherAppInFront = 0

    private var probes = 0
    private var probesFound = 0
    private var probesWithoutWindows = 0

    /** One search for the player after a tap, a screen change or a stopped scroll. [found] is null if Android gave no windows. */
    fun recordProbe(found: Boolean?) {
        synchronized(lock) {
            probes++
            when (found) {
                true -> probesFound++
                null -> probesWithoutWindows++
                false -> Unit
            }
        }
    }

    /** One look at which app is in front while the player is open. */
    fun recordForegroundCheck(playerAppInFront: Boolean) {
        synchronized(lock) {
            foregroundChecks++
            if (!playerAppInFront) otherAppInFront++
        }
    }

    /** [chain] is the IDs of the scrolled view's parents, nearest first. */
    fun recordScroll(
        source: Source,
        viewId: String?,
        className: String?,
        recognised: Boolean,
        chain: String? = null,
    ) {
        synchronized(lock) {
            val entry = seen.getOrPut(source) { Seen() }
            entry.scrolls++
            if (recognised) entry.recognised++
            val parents = if (chain.isNullOrEmpty()) "" else " in [$chain]"
            val key = "${viewId ?: "no id"}$parents (${className?.substringAfterLast('.') ?: "?"})"
            if (entry.views.size < MAX_VIEWS || entry.views.containsKey(key)) {
                entry.views[key] = (entry.views[key] ?: 0) + 1
            }
        }
    }

    fun recordScreen(source: Source, className: String?) {
        if (className == null) return
        synchronized(lock) {
            val entry = seen.getOrPut(source) { Seen() }
            if (entry.screens.size < MAX_SCREENS) entry.screens.add(className.substringAfterLast('.'))
        }
    }

    fun report(
        reclaimVersion: String,
        instagramVersion: String?,
        youtubeVersion: String?,
        learned: String = "",
    ): String {
        val lines = mutableListOf<String>()
        lines += "Reclaim $reclaimVersion report"
        lines += "Instagram app: ${instagramVersion ?: "unknown"}, YouTube app: ${youtubeVersion ?: "unknown"}"
        if (learned.isNotEmpty()) lines += "Learned player IDs: $learned"
        synchronized(lock) {
            lines += "Foreground checks: $foregroundChecks, other app in front: $otherAppInFront"
            lines += "Player searches: $probes, found: $probesFound, no windows from Android: $probesWithoutWindows"
            if (seen.isEmpty()) {
                lines += "Nothing seen yet. Open Instagram Reels or YouTube Shorts, swipe a few videos, then copy again."
            }
            for ((source, entry) in seen) {
                lines += "$source: ${entry.scrolls} scroll checks, ${entry.recognised} recognised as the player"
                entry.views.forEach { (view, count) -> lines += "  $view x$count" }
                if (entry.screens.isNotEmpty()) lines += "  screens: ${entry.screens.joinToString(", ")}"
            }
        }
        return lines.joinToString("\n")
    }

    /** For tests. */
    fun clear() {
        synchronized(lock) {
            seen.clear()
            foregroundChecks = 0
            otherAppInFront = 0
            probes = 0
            probesFound = 0
            probesWithoutWindows = 0
        }
    }
}
