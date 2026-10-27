package com.nathankst.reclaim.ui.home

import com.nathankst.reclaim.data.DayStats
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStateTest {

    private val today = LocalDate.of(2026, 10, 24) // a Saturday

    private fun day(back: Int, instagram: Int = 0, youtube: Int = 0, timeMs: Long = 0) =
        DayStats(today.minusDays(back.toLong()), instagram, youtube, timeMs, 0)

    @Test
    fun an_empty_day_shows_zero() {
        val s = homeState(emptyList(), limit = 300, distancePerVideoCm = 14.0, today = today)
        assertEquals(0, s.total)
        assertEquals(0, s.filledSegments)
        assertFalse(s.overLimit)
        assertEquals(300, s.leftOrOver)
        assertNull(s.weekChangePercent)
    }

    @Test
    fun today_is_split_by_app() {
        val s = homeState(listOf(day(0, instagram = 120, youtube = 127)), 300, 14.0, today)
        assertEquals(247, s.total)
        assertEquals(120, s.instagram)
        assertEquals(127, s.youtube)
    }

    @Test
    fun the_bar_fills_by_percent_of_the_limit() {
        val s = homeState(listOf(day(0, instagram = 247)), 300, 14.0, today)
        assertEquals(82, s.percentOfLimit)
        assertEquals(9, s.filledSegments)
        assertEquals(53, s.leftOrOver)
        assertFalse(s.overLimit)
    }

    @Test
    fun going_over_the_limit_is_flagged() {
        val s = homeState(listOf(day(0, instagram = 340)), 300, 14.0, today)
        assertTrue(s.overLimit)
        assertEquals(10, s.filledSegments)
        assertEquals(-40, s.leftOrOver)
    }

    @Test
    fun with_the_limit_off_there_is_no_bar() {
        val s = homeState(listOf(day(0, instagram = 500)), 0, 14.0, today)
        assertEquals(0, s.filledSegments)
        assertFalse(s.overLimit)
    }

    @Test
    fun distance_is_videos_times_one_screen_height() {
        val s = homeState(listOf(day(0, instagram = 100)), 300, 14.0, today)
        assertEquals(14.0, s.distanceMeters, 0.001)
    }

    @Test
    fun the_week_runs_oldest_to_today_with_day_letters() {
        val s = homeState(listOf(day(0, instagram = 50)), 300, 14.0, today)
        assertEquals(7, s.week.size)
        assertEquals("SMTWTFS", s.week.map { it.label }.joinToString(""))
        assertTrue(s.week.last().isToday)
        assertEquals('S', s.week.last().label)
        assertEquals(50, s.week.last().total)
    }

    @Test
    fun the_week_change_compares_with_the_seven_days_before() {
        val thisWeek = (0..6).map { day(it, instagram = 88) }
        val before = (7..13).map { day(it, instagram = 100) }
        val s = homeState(thisWeek + before, 300, 14.0, today)
        assertEquals(-12, s.weekChangePercent)
    }

    @Test
    fun more_than_before_is_positive() {
        val thisWeek = (0..6).map { day(it, instagram = 108) }
        val before = (7..13).map { day(it, instagram = 100) }
        val s = homeState(thisWeek + before, 300, 14.0, today)
        assertEquals(8, s.weekChangePercent)
    }

    @Test
    fun a_past_day_shows_that_days_numbers_and_title() {
        val days = listOf(day(0, instagram = 40), day(1, instagram = 120, youtube = 30, timeMs = 90 * 60_000L))
        val s = homeState(days, 300, 14.0, today, selected = today.minusDays(1))
        assertEquals(150, s.total)
        assertEquals(120, s.instagram)
        assertEquals(30, s.youtube)
        assertEquals(90 * 60_000L, s.timeMs)
        assertFalse(s.isToday)
        assertEquals("Yesterday", s.dayTitle)
        assertEquals("yesterday", s.whenLabel)
        assertEquals("FRI · 23 OCT", s.dateLabel)
        assertTrue(s.week.single { it.isSelected }.date == today.minusDays(1))
    }

    @Test
    fun an_older_day_is_named_by_its_weekday() {
        val s = homeState(emptyList(), 300, 14.0, today, selected = today.minusDays(3))
        assertEquals("Wednesday", s.dayTitle)
        assertEquals("on Wednesday", s.whenLabel)
    }

    @Test
    fun the_chart_can_move_back_a_week() {
        val lastWeek = (7..13).map { day(it, instagram = 10) }
        val s = homeState(lastWeek, 300, 14.0, today, selected = today.minusDays(7), weekOffset = 1)
        assertEquals(70, s.week.sumOf { it.total })
        assertEquals("11 OCT – 17 OCT", s.weekLabel)
        assertTrue(s.canGoNewer)
        assertTrue(s.canGoOlder)
        assertEquals(today.minusDays(7), s.week.last().date)
    }

    @Test
    fun the_first_week_cannot_go_newer_and_the_last_cannot_go_older() {
        assertFalse(homeState(emptyList(), 300, 14.0, today).canGoNewer)
        assertFalse(homeState(emptyList(), 300, 14.0, today, weekOffset = MAX_WEEK_OFFSET).canGoOlder)
    }

    @Test
    fun the_current_week_is_labelled_last_seven_days() {
        assertEquals("LAST 7 DAYS", homeState(emptyList(), 300, 14.0, today).weekLabel)
    }

    @Test
    fun no_change_is_hidden() {
        val thisWeek = (0..6).map { day(it, instagram = 100) }
        val before = (7..13).map { day(it, instagram = 100) }
        assertNull(homeState(thisWeek + before, 300, 14.0, today).weekChangePercent)
    }
}
