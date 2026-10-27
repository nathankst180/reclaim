package com.nathankst.reclaim.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun durations_use_the_largest_sensible_unit() {
        assertEquals("0s", Formatters.duration(0))
        assertEquals("45s", Formatters.duration(45_000))
        assertEquals("1m", Formatters.duration(60_000))
        assertEquals("12m", Formatters.duration(12 * 60_000L))
        assertEquals("1h", Formatters.duration(3_600_000))
        assertEquals("1h 23m", Formatters.duration(83 * 60_000L))
    }

    @Test
    fun distance_switches_to_kilometres_at_a_thousand_metres() {
        assertEquals("0 m", Formatters.distance(0.0))
        assertEquals("84 m", Formatters.distance(84.4))
        assertEquals("1.2 km", Formatters.distance(1234.0))
    }
}
