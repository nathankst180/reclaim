package com.nathankst.reclaim.service

import com.nathankst.reclaim.counting.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTimerTest {

    private val reported = mutableListOf<Pair<Source, Long>>()
    private val timer = SessionTimer { source, ms -> reported += source to ms }

    @Test
    fun stopping_reports_the_time_since_start() {
        timer.start(Source.INSTAGRAM_REELS, 1_000)
        timer.stop(6_000)
        assertEquals(listOf(Source.INSTAGRAM_REELS to 5_000L), reported)
    }

    @Test
    fun checkpoints_report_in_parts_and_never_twice() {
        timer.start(Source.YOUTUBE_SHORTS, 0)
        timer.checkpoint(30_000)
        timer.checkpoint(60_000)
        timer.stop(65_000)
        assertEquals(listOf(30_000L, 30_000L, 5_000L), reported.map { it.second })
    }

    @Test
    fun a_timer_that_is_not_running_reports_nothing() {
        timer.checkpoint(10_000)
        timer.stop(20_000)
        assertEquals(emptyList<Pair<Source, Long>>(), reported)
    }

    @Test
    fun starting_another_source_closes_the_first() {
        timer.start(Source.INSTAGRAM_REELS, 0)
        timer.start(Source.YOUTUBE_SHORTS, 4_000)
        timer.stop(10_000)
        assertEquals(
            listOf(Source.INSTAGRAM_REELS to 4_000L, Source.YOUTUBE_SHORTS to 6_000L),
            reported,
        )
    }
}
