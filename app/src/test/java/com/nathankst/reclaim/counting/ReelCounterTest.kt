package com.nathankst.reclaim.counting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelCounterTest {

    private val ig = Source.INSTAGRAM_REELS
    private val yt = Source.YOUTUBE_SHORTS
    private val screenHeightPx = 2400

    private fun counter(countBackward: Boolean = false) = ReelCounter(countBackward = countBackward)

    /** The pager reports which video is visible. */
    private fun ReelCounter.page(timeMs: Long, position: Int, source: Source = ig) =
        onScroll(ScrollSignal(timeMs, source, position, position, 100, 0, screenHeightPx))

    /** The pager reports no index, only how far it moved. */
    private fun ReelCounter.drag(timeMs: Long, deltaY: Int, source: Source = ig) =
        onScroll(ScrollSignal(timeMs, source, -1, -1, -1, deltaY, screenHeightPx))

    /** One finger gesture of [totalPx], sent as [steps] events 100 ms apart, then the gesture ends. */
    private fun ReelCounter.gesture(startMs: Long, totalPx: Int, steps: Int = 6) {
        repeat(steps) { drag(startMs + it * 100L, totalPx / steps) }
        onTick(startMs + steps * 100L + 400)
    }

    private val ReelCounter.total: Int get() = result.value.total

    @Test
    fun entering_the_player_counts_the_first_video() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        assertEquals(1, c.total)
        assertTrue(c.result.value.inPlayer)
    }

    @Test
    fun first_position_after_entering_is_not_counted_again() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.page(100, 7)
        assertEquals(1, c.total)
    }

    @Test
    fun twenty_forward_changes_give_twenty_one_in_total() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.page(100, 0)
        for (i in 1..20) c.page(100L + i * 400, i)
        assertEquals(21, c.total)
    }

    @Test
    fun bouncing_on_the_same_index_counts_nothing_extra() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.page(100, 3)
        for (i in 1..30) c.page(100L + i * 50, 3)
        assertEquals(1, c.total)
    }

    @Test
    fun five_backward_changes_count_when_enabled() {
        val c = counter(countBackward = true)
        c.onEnterPlayer(ig, 0)
        c.page(100, 10)
        for (i in 1..5) c.page(100L + i * 400, 10 - i)
        assertEquals(6, c.total)
    }

    @Test
    fun five_backward_changes_count_nothing_when_disabled() {
        val c = counter(countBackward = false)
        c.onEnterPlayer(ig, 0)
        c.page(100, 10)
        for (i in 1..5) c.page(100L + i * 400, 10 - i)
        assertEquals(1, c.total)
    }

    @Test
    fun going_back_and_forward_again_does_not_count_the_same_reels_twice() {
        val c = counter(countBackward = false)
        c.onEnterPlayer(ig, 0)
        c.page(100, 5)
        c.page(500, 6)
        assertEquals(2, c.total)
        c.page(1000, 5)
        c.page(1500, 4)
        assertEquals(2, c.total)
        c.page(2000, 5)
        c.page(2500, 6)
        assertEquals(2, c.total)
        c.page(3000, 7)
        assertEquals(3, c.total)
    }

    @Test
    fun backward_is_off_by_default() {
        assertEquals(false, ReelCounter().countBackward)
    }

    @Test
    fun counting_every_move_when_backward_is_on_counts_the_return_too() {
        val c = counter(countBackward = true)
        c.onEnterPlayer(ig, 0)
        c.page(100, 5)
        c.page(500, 6)
        c.page(1000, 5)
        c.page(1500, 6)
        assertEquals(4, c.total)
    }

    @Test
    fun fallback_going_back_and_forward_does_not_count_the_same_screen_twice() {
        val c = counter(countBackward = false)
        c.onEnterPlayer(ig, 0)
        c.gesture(1000, screenHeightPx)          // forward: a new reel
        assertEquals(2, c.total)
        c.gesture(3000, -screenHeightPx)         // back: nothing
        assertEquals(2, c.total)
        c.gesture(5000, screenHeightPx)          // forward to the reel already counted: nothing
        assertEquals(2, c.total)
        c.gesture(7000, screenHeightPx)          // forward to a new one
        assertEquals(3, c.total)
    }

    @Test
    fun opening_a_reel_after_the_player_was_closed_counts_even_within_three_seconds() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.page(100, 0)
        c.onLeavePlayer(1000, closed = true)
        c.onEnterPlayer(ig, 2000)
        assertEquals(2, c.total)
    }

    @Test
    fun a_reloaded_list_starts_counting_from_its_new_start() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.page(100, 0)
        c.page(500, 1)
        c.page(1000, 2)
        c.onLeavePlayer(1500)
        c.onEnterPlayer(ig, 2500)
        c.page(2700, 0)
        assertEquals(3, c.total)
        c.page(3200, 1)
        assertEquals(4, c.total)
    }

    @Test
    fun duplicate_events_within_the_minimum_gap_count_once() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.page(100, 0)
        c.page(1000, 1)
        assertEquals(2, c.total)
        c.page(1100, 2)
        c.page(1150, 1)
        assertEquals(2, c.total)
    }

    @Test
    fun a_change_ignored_by_the_gap_is_counted_by_the_next_event() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.page(100, 0)
        c.page(1000, 1)
        c.page(1100, 2)
        c.page(1500, 2)
        assertEquals(3, c.total)
    }

    @Test
    fun leaving_and_returning_within_three_seconds_at_the_same_index_adds_nothing() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.page(100, 0)
        c.page(500, 1)
        assertEquals(2, c.total)
        c.onLeavePlayer(1000)
        c.onEnterPlayer(ig, 2000)
        c.page(2100, 1)
        assertEquals(2, c.total)
    }

    @Test
    fun leaving_for_ten_seconds_counts_a_new_entry() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.page(100, 0)
        c.page(500, 1)
        c.onLeavePlayer(1000)
        c.onEnterPlayer(ig, 11_000)
        assertEquals(3, c.total)
    }

    @Test
    fun index_reset_to_zero_right_after_reentering_adds_nothing() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.page(100, 0)
        c.page(500, 1)
        c.onLeavePlayer(1000)
        c.onEnterPlayer(ig, 2000)
        c.page(2200, 0)
        assertEquals(2, c.total)
    }

    @Test
    fun a_drag_of_sixty_percent_counts_one() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.gesture(1000, (screenHeightPx * 0.6).toInt())
        assertEquals(2, c.total)
    }

    @Test
    fun a_drag_of_thirty_percent_counts_nothing() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.gesture(1000, (screenHeightPx * 0.3).toInt())
        assertEquals(1, c.total)
    }

    @Test
    fun a_fling_of_two_hundred_ten_percent_counts_two() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.gesture(1000, (screenHeightPx * 2.1).toInt(), steps = 12)
        assertEquals(3, c.total)
    }

    @Test
    fun a_backward_drag_follows_the_setting() {
        val on = counter(countBackward = true)
        on.onEnterPlayer(ig, 0)
        on.gesture(1000, -(screenHeightPx * 0.6).toInt())
        assertEquals(2, on.total)

        val off = counter(countBackward = false)
        off.onEnterPlayer(ig, 0)
        off.gesture(1000, -(screenHeightPx * 0.6).toInt())
        assertEquals(1, off.total)
    }

    @Test
    fun a_gesture_still_in_progress_is_counted_when_the_player_is_left() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        repeat(6) { c.drag(1000L + it * 100, (screenHeightPx * 0.6).toInt() / 6) }
        c.onLeavePlayer(1700)
        assertEquals(2, c.total)
    }

    @Test
    fun signals_are_ignored_while_the_screen_is_off() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.page(100, 0)
        c.onScreenOff(500)
        c.page(1000, 1)
        c.page(1500, 2)
        c.onEnterPlayer(ig, 2000)
        assertEquals(1, c.total)
    }

    @Test
    fun screen_back_on_in_the_same_player_adds_nothing() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.page(100, 0)
        c.page(500, 1)
        c.onScreenOff(1000)
        c.onScreenOn(60_000)
        c.onEnterPlayer(ig, 60_200)
        c.page(60_300, 1)
        assertEquals(2, c.total)
        c.page(61_000, 2)
        assertEquals(3, c.total)
    }

    @Test
    fun signals_while_not_in_the_player_are_ignored() {
        val c = counter()
        c.page(100, 0)
        c.page(600, 1)
        assertEquals(0, c.total)
        c.onEnterPlayer(ig, 1000)
        c.onLeavePlayer(1500)
        c.page(2000, 5)
        c.page(2600, 6)
        assertEquals(1, c.total)
        assertFalse(c.result.value.inPlayer)
    }

    @Test
    fun counts_are_split_by_source() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.page(100, 0)
        c.page(500, 1)
        c.onLeavePlayer(1000)
        c.onEnterPlayer(yt, 20_000)
        c.page(20_100, 0, yt)
        c.page(20_500, 1, yt)
        val r = c.result.value
        assertEquals(2, r.instagram)
        assertEquals(2, r.youtube)
        assertEquals(4, r.total)
    }

    @Test
    fun a_signal_from_the_other_app_is_ignored() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.page(100, 0)
        c.page(500, 1, yt)
        assertEquals(1, c.total)
    }

    @Test
    fun resetting_zeroes_counts_but_keeps_the_player_state() {
        val c = counter()
        c.onEnterPlayer(ig, 0)
        c.page(100, 0)
        c.page(500, 1)
        c.resetCounts()
        assertEquals(0, c.total)
        assertTrue(c.result.value.inPlayer)
        c.page(1000, 2)
        assertEquals(1, c.total)
    }

    @Test
    fun the_counted_callback_reports_each_count() {
        val counted = mutableListOf<Pair<Source, Long>>()
        val c = ReelCounter(onCounted = { s, t -> counted += s to t })
        c.onEnterPlayer(yt, 0)
        c.page(100, 0, yt)
        c.page(600, 1, yt)
        assertEquals(listOf(yt to 0L, yt to 600L), counted)
    }
}
