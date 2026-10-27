package com.nathankst.reclaim.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.nathankst.reclaim.AppContainer
import com.nathankst.reclaim.BuildConfig
import com.nathankst.reclaim.ReclaimApp
import com.nathankst.reclaim.counting.CountingConfig
import com.nathankst.reclaim.counting.ReelCounter
import com.nathankst.reclaim.counting.ScrollSignal
import com.nathankst.reclaim.counting.Source
import com.nathankst.reclaim.data.LearnedPlayerIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Thin entry point: turns Instagram and YouTube accessibility events into small signals for the counter.
 * Runs on the main thread, so every event must be cheap.
 */
class ScrollAccessibilityService : AccessibilityService() {

    private val container: AppContainer
        get() = (application as ReclaimApp).container

    private val counter: ReelCounter
        get() = container.counter

    private val learnedIds: LearnedPlayerIds
        get() = container.learnedIds

    private val detector = PlayerDetector()
    private val handler = Handler(Looper.getMainLooper())
    private val timer = SessionTimer { source, ms -> container.stats.addTime(source, ms) }

    private var scope: CoroutineScope? = null
    private var overlay: CounterOverlay? = null
    private var screenHeightPx = 0
    private var screenReceiverRegistered = false

    private var probeScheduled = false
    private var probeSource: Source? = null
    private var probeReason = "tap"
    private var probeStartedMs = 0L
    private var probeUntilMs = 0L
    private var lastInFrontMs = 0L
    private var missedProbes = 0

    // Which pager view was last seen. A different one means another reel was opened.
    private var lastPlayerInstance = 0
    private var candidateInstance = 0

    private class Sighting(val windowsAvailable: Boolean, val instance: Int?)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = SystemClock.uptimeMillis()
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> counter.onScreenOff(now)
                Intent.ACTION_SCREEN_ON -> counter.onScreenOn(now)
            }
        }
    }

    private val gestureTick = Runnable { counter.onTick(SystemClock.uptimeMillis()) }

    /**
     * Looks for the player: after a tap, a screen change or a stopped scroll (a reel was probably just opened),
     * and then once a second for as long as it is open (so leaving it is noticed). We only listen to Instagram
     * and YouTube, and Android shows us no window at all while another app is in front, so a few misses in a
     * row mean we left.
     */
    private val probe = object : Runnable {
        override fun run() {
            probeScheduled = false
            val now = SystemClock.uptimeMillis()
            val state = counter.result.value
            val source = state.source ?: probeSource ?: return
            if (!state.inPlayer && now > probeUntilMs) return

            val active = rootInActiveWindow
            val frontSource = Source.fromPackage(active?.packageName?.toString())
            active?.release()
            Diagnostics.recordForegroundCheck(frontSource != null)

            if (frontSource == source) {
                val sighting = lookForPlayer(source)
                Diagnostics.recordProbe(if (sighting.windowsAvailable) sighting.instance != null else null)
                val instance = sighting.instance
                when {
                    !sighting.windowsAvailable -> Unit
                    instance != null -> playerSeen(source, now, instance)
                    else -> {
                        playerNotSeen(source, appInFront = true)
                        if (BuildConfig.INSPECTOR && !state.inPlayer && now - probeStartedMs >= DUMP_AFTER_MS) {
                            DebugInspector.autoDump("${source.name} after $probeReason", packageOf(source), rootInActiveWindow)
                        }
                    }
                }
            } else {
                playerNotSeen(source, appInFront = false)
            }
            if (counter.result.value.inPlayer || now < probeUntilMs) scheduleProbe(CountingConfig.LEAVE_CHECK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        stopWork()
        screenHeightPx = resources.displayMetrics.heightPixels
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiverRegistered = true

        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        overlay = CounterOverlay(this)
        newScope.launch { trackTimeSpent() }
        newScope.launch { showCornerNumber() }
        newScope.launch { saveEveryThirtySeconds() }

        container.setServiceRunning(true)
        if (BuildConfig.INSPECTOR) DebugInspector.note("service connected")
    }

    /**
     * Time spent runs only while the player is in front and the screen is on. It starts and stops at the
     * moment the counter says the player was opened or left, which for a leave is the last time it was seen.
     */
    private suspend fun trackTimeSpent() {
        counter.result.map { Triple(it.inPlayer, it.source, it.changedAtMs) }.distinctUntilChanged()
            .collect { (inPlayer, source, changedAtMs) ->
                if (inPlayer && source != null) {
                    timer.start(source, changedAtMs)
                } else {
                    timer.stop(changedAtMs)
                    container.stats.flush()
                }
            }
    }

    private suspend fun showCornerNumber() {
        combine(counter.result, container.stats.days, container.settings.settings) { result, days, settings ->
            CounterOverlay.Model(
                visible = result.inPlayer && settings.overlayEnabled,
                count = days.lastOrNull()?.total ?: 0,
                corner = settings.overlayCorner,
            )
        }.distinctUntilChanged().collect { overlay?.render(it) }
    }

    private suspend fun saveEveryThirtySeconds() {
        while (true) {
            delay(FLUSH_INTERVAL_MS)
            timer.checkpoint(SystemClock.uptimeMillis())
            container.stats.flush()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val source = Source.fromPackage(pkg) ?: return
        val now = SystemClock.uptimeMillis()
        if (BuildConfig.INSPECTOR) DebugInspector.maybeDump(pkg) { rootInActiveWindow }
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleScroll(event, source, now)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowChange(event, source, now)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handleClick(event, source, now)
        }
    }

    private fun handleScroll(event: AccessibilityEvent, source: Source, now: Long) {
        val verdict = detector.scrollVerdict(event, source, now)
        if (verdict.resolved) {
            Diagnostics.recordScroll(source, verdict.viewId, event.className?.toString(), verdict.accepted, verdict.chain)
        }
        if (BuildConfig.INSPECTOR && (verdict.resolved || verdict.accepted)) {
            val decision = if (verdict.accepted) "pager" else "ignored: not a player view"
            DebugInspector.logEvent(event, source, verdict.viewId, decision)
        }
        if (!verdict.accepted) {
            // Scrolling a feed or a search page: once it stops, a tapped reel may have opened, so look for the player.
            if (verdict.resolved && !counter.result.value.inPlayer) requestProbes(source, now, "scroll stopped")
            return
        }
        if (verdict.resolved && verdict.viewId != null) learnedIds.learn(source, verdict.viewId)

        // A sideways carousel, or a long list such as comments, is not the pager.
        val sideways = event.scrollDeltaX != 0 && event.scrollDeltaY == 0
        val longList = event.fromIndex >= 0 && event.toIndex - event.fromIndex > CountingConfig.MAX_PAGE_SPAN
        if (sideways || longList) return

        lastInFrontMs = now
        missedProbes = 0
        counter.onEnterPlayer(source, now)
        counter.onScroll(
            ScrollSignal(
                timeMs = now,
                source = source,
                fromIndex = event.fromIndex,
                toIndex = event.toIndex,
                itemCount = event.itemCount,
                deltaY = event.scrollDeltaY,
                screenHeightPx = screenHeightPx,
            ),
        )
        handler.removeCallbacks(gestureTick)
        handler.postDelayed(gestureTick, CountingConfig.GESTURE_END_MS + 20)
        probeSource = source
        scheduleProbe(CountingConfig.LEAVE_CHECK_MS)
    }

    /** A new screen may be a reel that was just opened. */
    private fun handleWindowChange(event: AccessibilityEvent, source: Source, now: Long) {
        Diagnostics.recordScreen(source, event.className?.toString())
        if (BuildConfig.INSPECTOR) DebugInspector.logEvent(event, source, null, "screen changed, looking for the player")
        requestProbes(source, now, "screen change")
    }

    /** Tapping a reel in search, on a profile or in a message opens the player: look for it. */
    private fun handleClick(event: AccessibilityEvent, source: Source, now: Long) {
        if (BuildConfig.INSPECTOR) DebugInspector.logEvent(event, source, null, "tap, looking for the player")
        requestProbes(source, now, "tap")
    }

    private fun requestProbes(source: Source, now: Long, reason: String) {
        probeSource = source
        probeReason = reason
        probeStartedMs = now
        probeUntilMs = now + CountingConfig.PROBE_WINDOW_MS
        scheduleProbe(CountingConfig.FIRST_PROBE_MS, restart = true)
    }

    private fun scheduleProbe(delayMs: Long, restart: Boolean = false) {
        if (probeScheduled) {
            if (!restart) return
            handler.removeCallbacks(probe)
        }
        probeScheduled = true
        handler.postDelayed(probe, delayMs)
    }

    private fun playerSeen(source: Source, now: Long, instance: Int) {
        if (counter.result.value.inPlayer && lastPlayerInstance != 0 && instance != lastPlayerInstance) {
            // A different pager view while we thought we were still in the old one: another reel was opened.
            // Seen twice in a row before it is believed, so a view flickering for a moment is not counted.
            if (instance != candidateInstance) {
                candidateInstance = instance
                return
            }
            leavePlayer(lastInFrontMs, closed = true)
        }
        candidateInstance = 0
        lastPlayerInstance = instance
        lastInFrontMs = now
        missedProbes = 0
        counter.onEnterPlayer(source, now)
    }

    private fun playerNotSeen(source: Source, appInFront: Boolean) {
        if (!counter.result.value.inPlayer) return
        // With the app in front, "not found" is only believable once the real player ID is known.
        val believable = !appInFront || learnedIds.idsFor(source).isNotEmpty()
        if (!believable) return
        missedProbes++
        if (missedProbes >= CountingConfig.LEAVE_AFTER_MISSES) {
            missedProbes = 0
            leavePlayer(lastInFrontMs, closed = appInFront)
        }
    }

    /** Searches every window of the app, so a comments sheet on top of the player does not hide it. */
    private fun lookForPlayer(source: Source): Sighting {
        val all = windows
        if (all.isNullOrEmpty()) return Sighting(windowsAvailable = false, instance = null)
        val ids = learnedIds.idsFor(source)
        var instance: Int? = null
        for (window in all) {
            if (instance == null) {
                val root = window.root
                instance = detector.findVisiblePlayer(root, source, ids)
                root?.release()
            }
            window.release()
        }
        return Sighting(windowsAvailable = true, instance = instance)
    }

    private fun leavePlayer(timeMs: Long, closed: Boolean) {
        counter.onLeavePlayer(timeMs, closed)
        detector.reset()
        lastPlayerInstance = 0
        candidateInstance = 0
    }

    private fun packageOf(source: Source): String = when (source) {
        Source.INSTAGRAM_REELS -> CountingConfig.INSTAGRAM_PACKAGE
        Source.YOUTUBE_SHORTS -> CountingConfig.YOUTUBE_PACKAGE
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        stopWork()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopWork()
        super.onDestroy()
    }

    private fun stopWork() {
        handler.removeCallbacksAndMessages(null)
        probeScheduled = false
        probeUntilMs = 0L
        lastPlayerInstance = 0
        candidateInstance = 0
        if (screenReceiverRegistered) {
            unregisterReceiver(screenReceiver)
            screenReceiverRegistered = false
        }
        val now = SystemClock.uptimeMillis()
        counter.onLeavePlayer(now)
        timer.stop(now)
        container.stats.flush()
        overlay?.destroy()
        overlay = null
        scope?.cancel()
        scope = null
        container.setServiceRunning(false)
    }

    private companion object {
        const val FLUSH_INTERVAL_MS = 30_000L
        const val DUMP_AFTER_MS = 1_200L
    }
}
