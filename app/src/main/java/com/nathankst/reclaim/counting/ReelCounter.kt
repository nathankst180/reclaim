package com.nathankst.reclaim.counting

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the accessibility service tells the counter about one scroll event of the player's pager. */
data class ScrollSignal(
    val timeMs: Long,
    val source: Source,
    val fromIndex: Int,     // -1 if unknown
    val toIndex: Int,       // -1 if unknown
    val itemCount: Int,     // -1 if unknown
    val deltaY: Int,        // scrollDeltaY, positive = towards the next video; 0 if unknown
    val screenHeightPx: Int,
)

data class CounterResult(
    val total: Int = 0,
    val instagram: Int = 0,
    val youtube: Int = 0,
    val inPlayer: Boolean = false,
    val source: Source? = null,
    /** When the player was last opened or left, so time spent can start and stop at the real moment. */
    val changedAtMs: Long = 0,
)

/**
 * Decides when the Reels or Shorts player shows a new video, and adds 1 each time.
 * Pure Kotlin, no Android imports, so every rule can be unit tested.
 *
 * Opening the player counts the first video. After that a video counts when it is further along than any
 * video already counted in this visit, so swiping back, or forward again to a reel you already saw, adds
 * nothing (unless [countBackward] is on, which counts every move).
 *
 * Primary method: the pager reports which item is visible. Fallback (index is -1): add up scrollDeltaY
 * for one gesture and count whole screens moved.
 */
class ReelCounter(
    @Volatile var countBackward: Boolean = CountingConfig.COUNT_BACKWARD_DEFAULT,
    private val onCounted: (Source, Long) -> Unit = { _, _ -> },
) {

    private val _result = MutableStateFlow(CounterResult())
    val result: StateFlow<CounterResult> = _result.asStateFlow()

    private var screenOn = true
    private var inPlayer = false
    private var source: Source? = null

    private var lastPosition = NO_POSITION
    private var highWater = NO_POSITION
    private var awaitingFirstSignal = false
    private var firstSignalDeadlineMs = ALWAYS
    private var lastCountMs = NEVER

    private var leftAtMs = NEVER
    private var leftSource: Source? = null
    private var resumeAfterScreenOff = false
    private var screenOnAtMs = NEVER

    private var gestureSum = 0
    private var gestureLastMs = NEVER
    private var gestureHeightPx = 0
    private var gestureSource: Source? = null
    private var virtualPosition = 0
    private var virtualHigh = 0

    /** The player became visible. Counts the first video, unless this is a quick return. */
    fun onEnterPlayer(source: Source, timeMs: Long) {
        if (!screenOn) return
        if (inPlayer) {
            if (this.source == source) return
            onLeavePlayer(timeMs)
        }
        finishGesture()
        val quickReturn = leftSource == source &&
            timeMs - leftAtMs < CountingConfig.REENTRY_WINDOW_MS
        val resumed = resumeAfterScreenOff && leftSource == source &&
            timeMs - screenOnAtMs < CountingConfig.SCREEN_RESUME_WINDOW_MS
        inPlayer = true
        this.source = source
        awaitingFirstSignal = true
        if (quickReturn || resumed) {
            firstSignalDeadlineMs = timeMs + CountingConfig.RESET_WINDOW_MS
        } else {
            lastPosition = NO_POSITION
            highWater = NO_POSITION
            virtualPosition = 0
            virtualHigh = 0
            firstSignalDeadlineMs = ALWAYS
            count(source, timeMs)
        }
        resumeAfterScreenOff = false
        publish(timeMs)
    }

    /**
     * @param closed the player itself went away (for example back to the search results), as opposed to the
     * whole app being covered for a moment. Opening a reel again after that is always a new reel.
     */
    fun onLeavePlayer(timeMs: Long, closed: Boolean = false) {
        if (!screenOn || !inPlayer) return
        finishGesture()
        leftAtMs = timeMs
        leftSource = if (closed) null else source
        inPlayer = false
        source = null
        publish(timeMs)
    }

    fun onScroll(signal: ScrollSignal) {
        if (!screenOn || !inPlayer || signal.source != source) return
        finishGestureIfStale(signal.timeMs)
        if (signal.fromIndex >= 0) onIndexSignal(signal) else onDeltaSignal(signal)
    }

    /** Lets the fallback close a finished gesture when no further scroll event arrives. */
    fun onTick(timeMs: Long) {
        if (!screenOn || gestureLastMs == NEVER) return
        if (timeMs - gestureLastMs >= CountingConfig.GESTURE_END_MS) finishGesture()
    }

    fun onScreenOff(timeMs: Long) {
        if (!screenOn) return
        finishGesture()
        screenOn = false
        if (inPlayer) {
            resumeAfterScreenOff = true
            leftSource = source
            leftAtMs = timeMs
            inPlayer = false
            source = null
        }
        publish(timeMs)
    }

    fun onScreenOn(timeMs: Long) {
        screenOn = true
        screenOnAtMs = timeMs
    }

    fun resetCounts() {
        _result.update { it.copy(total = 0, instagram = 0, youtube = 0) }
    }

    private fun onIndexSignal(signal: ScrollSignal) {
        val position = signal.fromIndex
        if (awaitingFirstSignal) {
            awaitingFirstSignal = false
            if (lastPosition == NO_POSITION || signal.timeMs <= firstSignalDeadlineMs) {
                // The first position only says where we are. A different one after coming back means
                // the app reloaded its list, so what was seen before no longer applies.
                if (position != lastPosition) highWater = position
                lastPosition = position
                return
            }
        }
        if (lastPosition == NO_POSITION) {
            lastPosition = position
            highWater = position
            return
        }
        if (position == lastPosition) return
        if (signal.timeMs - lastCountMs < CountingConfig.MIN_GAP_BETWEEN_COUNTS_MS) return
        lastPosition = position
        val isNewReel = position > highWater
        if (isNewReel) highWater = position
        if (isNewReel || countBackward) count(signal.source, signal.timeMs)
    }

    private fun onDeltaSignal(signal: ScrollSignal) {
        awaitingFirstSignal = false
        if (signal.deltaY == 0 || signal.screenHeightPx <= 0) return
        gestureSum += signal.deltaY
        gestureLastMs = signal.timeMs
        gestureHeightPx = signal.screenHeightPx
        gestureSource = signal.source
    }

    private fun finishGestureIfStale(nowMs: Long) {
        if (gestureLastMs != NEVER && nowMs - gestureLastMs > CountingConfig.GESTURE_END_MS) {
            finishGesture()
        }
    }

    private fun finishGesture() {
        if (gestureLastMs == NEVER) return
        val sum = gestureSum
        val heightPx = gestureHeightPx
        val gestureSource = gestureSource
        val endedAtMs = gestureLastMs
        gestureSum = 0
        gestureLastMs = NEVER
        if (heightPx <= 0 || gestureSource == null) return

        val screens = abs(sum).toFloat() / heightPx
        if (screens < CountingConfig.FALLBACK_MIN_SCREEN_FRACTION) return
        if (endedAtMs - lastCountMs < CountingConfig.MIN_GAP_BETWEEN_COUNTS_MS) return
        val step = if (sum > 0) 1 else -1
        repeat(screens.roundToInt().coerceAtLeast(1)) {
            virtualPosition += step
            val isNewReel = virtualPosition > virtualHigh
            if (isNewReel) virtualHigh = virtualPosition
            if (isNewReel || countBackward) count(gestureSource, endedAtMs)
        }
    }

    private fun count(source: Source, timeMs: Long) {
        lastCountMs = timeMs
        _result.update {
            it.copy(
                total = it.total + 1,
                instagram = if (source == Source.INSTAGRAM_REELS) it.instagram + 1 else it.instagram,
                youtube = if (source == Source.YOUTUBE_SHORTS) it.youtube + 1 else it.youtube,
            )
        }
        onCounted(source, timeMs)
    }

    private fun publish(timeMs: Long) {
        _result.update { it.copy(inPlayer = inPlayer, source = source, changedAtMs = timeMs) }
    }

    private companion object {
        const val NO_POSITION = Int.MIN_VALUE
        const val NEVER = Long.MIN_VALUE / 4
        const val ALWAYS = Long.MAX_VALUE
    }
}
