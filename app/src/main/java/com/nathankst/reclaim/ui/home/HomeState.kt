package com.nathankst.reclaim.ui.home

import com.nathankst.reclaim.data.DayStats
import com.nathankst.reclaim.util.DateUtils
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Weeks of history that can be browsed. The repository keeps [MAX_WEEK_OFFSET] + 1 weeks. */
const val MAX_WEEK_OFFSET = 8

data class DayBar(
    val date: LocalDate,
    val label: Char,
    val total: Int,
    val isToday: Boolean,
    val isSelected: Boolean,
)

/** Everything the Home screen shows, worked out in one place so it can be unit tested. */
data class HomeState(
    val selectedDate: LocalDate,
    val isToday: Boolean,
    val dateLabel: String,
    val dayTitle: String,
    val whenLabel: String,
    val total: Int = 0,
    val instagram: Int = 0,
    val youtube: Int = 0,
    val timeMs: Long = 0,
    val instagramTimeMs: Long = 0,
    val youtubeTimeMs: Long = 0,
    val distanceMeters: Double = 0.0,
    val limit: Int = 0,                 // 0 = off
    val filledSegments: Int = 0,        // out of 10
    val overLimit: Boolean = false,
    val percentOfLimit: Int = 0,
    val leftOrOver: Int = 0,            // positive = left, negative = over
    val week: List<DayBar> = emptyList(),
    val weekLabel: String = "",
    val weekChangePercent: Int? = null, // negative = less than the 7 days before
    val canGoOlder: Boolean = false,
    val canGoNewer: Boolean = false,
)

/**
 * @param selected the day shown in the big card, [today] by default
 * @param weekOffset 0 shows the 7 days ending today, 1 the 7 days before that, and so on
 */
fun homeState(
    days: List<DayStats>,
    limit: Int,
    distancePerVideoCm: Double,
    today: LocalDate,
    selected: LocalDate = today,
    weekOffset: Int = 0,
): HomeState {
    val byDate = days.associateBy { it.date }
    val shown = byDate[selected] ?: DayStats(selected)

    val weekEnd = today.minusDays(7L * weekOffset)
    val week = (6 downTo 0).map { back ->
        val date = weekEnd.minusDays(back.toLong())
        DayBar(
            date = date,
            label = date.dayOfWeek.name.first(),
            total = byDate[date]?.total ?: 0,
            isToday = date == today,
            isSelected = date == selected,
        )
    }
    val thisWeek = week.sumOf { it.total }
    val before = (13 downTo 7).sumOf { byDate[weekEnd.minusDays(it.toLong())]?.total ?: 0 }
    val change = if (before > 0) ((thisWeek - before) * 100.0 / before).roundToInt() else null

    val hasLimit = limit > 0
    return HomeState(
        selectedDate = selected,
        isToday = selected == today,
        dateLabel = DateUtils.headerLabel(selected),
        dayTitle = DateUtils.dayTitle(selected, today),
        whenLabel = DateUtils.whenLabel(selected, today),
        total = shown.total,
        instagram = shown.instagram,
        youtube = shown.youtube,
        timeMs = shown.timeMs,
        instagramTimeMs = shown.instagramTimeMs,
        youtubeTimeMs = shown.youtubeTimeMs,
        distanceMeters = shown.total * distancePerVideoCm / 100.0,
        limit = limit,
        filledSegments = if (hasLimit) ceil(shown.total * 10.0 / limit).toInt().coerceIn(0, 10) else 0,
        overLimit = hasLimit && shown.total > limit,
        percentOfLimit = if (hasLimit) (shown.total * 100.0 / limit).roundToInt() else 0,
        leftOrOver = if (hasLimit) limit - shown.total else 0,
        week = week,
        weekLabel = if (weekOffset == 0) {
            "LAST 7 DAYS"
        } else {
            "${DateUtils.shortDate(week.first().date)} – ${DateUtils.shortDate(week.last().date)}"
        },
        weekChangePercent = change?.takeIf { it != 0 },
        canGoOlder = weekOffset < MAX_WEEK_OFFSET,
        canGoNewer = weekOffset > 0,
    )
}
