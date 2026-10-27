package com.nathankst.reclaim.service

import com.nathankst.reclaim.counting.Source

/** Measures time spent inside the player. Pure Kotlin, so it can be unit tested. */
class SessionTimer(private val onElapsed: (Source, Long) -> Unit) {

    private var source: Source? = null
    private var startedMs = 0L

    fun start(source: Source, nowMs: Long) {
        stop(nowMs)
        this.source = source
        startedMs = nowMs
    }

    /** Reports the time since the last checkpoint and keeps running. */
    fun checkpoint(nowMs: Long) {
        val current = source ?: return
        val elapsed = nowMs - startedMs
        if (elapsed > 0) onElapsed(current, elapsed)
        startedMs = nowMs
    }

    fun stop(nowMs: Long) {
        checkpoint(nowMs)
        source = null
    }
}
