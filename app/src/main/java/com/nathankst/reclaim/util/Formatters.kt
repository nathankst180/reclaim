package com.nathankst.reclaim.util

import java.util.Locale
import kotlin.math.roundToInt

object Formatters {

    /** "45s", "12m", "1h", "1h 23m" */
    fun duration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0 && minutes == 0L -> "${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${totalSeconds}s"
        }
    }

    /** "84 m" under a kilometre, "1.2 km" above. */
    fun distance(meters: Double): String =
        if (meters < 1000) "${meters.roundToInt()} m"
        else String.format(Locale.ENGLISH, "%.1f km", meters / 1000)
}
