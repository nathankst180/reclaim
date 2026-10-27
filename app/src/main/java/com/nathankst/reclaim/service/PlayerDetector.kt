package com.nathankst.reclaim.service

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nathankst.reclaim.counting.CountingConfig
import com.nathankst.reclaim.counting.Source

/**
 * Answers "is this the Reels / Shorts player?" with as few calls into the other app as possible.
 * Asking an app about its views makes that app do work, so every answer is reused for one gesture.
 */
class PlayerDetector {

    /**
     * [viewId] is the player's ID when accepted, otherwise the scrolled view's own ID. Both, and [chain]
     * (the parents' IDs), are only filled when this answer was freshly asked from the app ([resolved]).
     */
    class Verdict(
        val accepted: Boolean,
        val viewId: String?,
        val resolved: Boolean,
        val chain: String? = null,
    )

    private class Match(val matchedId: String?, val ownId: String?, val chain: String)

    private var verdictClass: String? = null
    private var acceptUntilMs = 0L
    private var rejectUntilMs = 0L

    /** Is this scroll event coming from the player's pager, not from the feed or a comments list? */
    fun scrollVerdict(event: AccessibilityEvent, source: Source, nowMs: Long): Verdict {
        val className = event.className?.toString()
        if (className == verdictClass) {
            if (nowMs < acceptUntilMs) {
                acceptUntilMs = nowMs + CountingConfig.SCROLL_VERDICT_MS
                return Verdict(accepted = true, viewId = null, resolved = false)
            }
            if (nowMs < rejectUntilMs) return Verdict(accepted = false, viewId = null, resolved = false)
        }
        val node = event.source
        val match = findPlayerId(node, source)
        node?.release()
        val accepted = match.matchedId != null
        verdictClass = className
        if (accepted) {
            acceptUntilMs = nowMs + CountingConfig.SCROLL_VERDICT_MS
            rejectUntilMs = 0L
        } else {
            rejectUntilMs = nowMs + CountingConfig.SCROLL_VERDICT_MS
            acceptUntilMs = 0L
        }
        return Verdict(accepted, match.matchedId ?: match.ownId, resolved = true, chain = match.chain)
    }

    /**
     * The scrolled view is often a RecyclerView inside the pager, and only the pager has the ID,
     * so the view itself and its first few parents are checked.
     */
    private fun findPlayerId(node: AccessibilityNodeInfo?, source: Source): Match {
        if (node == null) return Match(null, null, "")
        val ownId = node.viewIdResourceName
        val parents = mutableListOf<String>()
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth <= MAX_PARENTS) {
            val id = current.viewIdResourceName
            if (id != null && CountingConfig.isPlayerId(source, id)) {
                if (current !== node) current.release()
                return Match(id, ownId, parents.joinToString(" > "))
            }
            val parent = current.parent
            if (current !== node) current.release()
            if (parent != null) parents += parent.viewIdResourceName?.substringAfter(":id/") ?: "-"
            current = parent
            depth++
        }
        current?.release()
        return Match(null, ownId, parents.joinToString(" > "))
    }

    /**
     * Searches one window for the player's view IDs, the built-in ones and the ones learned from real use.
     * Returns a number that identifies that exact view on screen, or null if the player is not visible.
     * A new pager view gets a new number, which is how a newly opened reel is told apart from the one before.
     */
    fun findVisiblePlayer(root: AccessibilityNodeInfo?, source: Source, learnedIds: Set<String>): Int? {
        if (root == null) return null
        for (id in (CountingConfig.playerIdsFor(source) + learnedIds).distinct()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            val visible = nodes.firstOrNull { it.isVisibleToUser }
            val instance = visible?.hashCode()
            nodes.forEach { it.release() }
            if (instance != null) return instance
        }
        return null
    }

    fun reset() {
        verdictClass = null
        acceptUntilMs = 0L
        rejectUntilMs = 0L
    }

    private companion object {
        const val MAX_PARENTS = 3
    }
}
