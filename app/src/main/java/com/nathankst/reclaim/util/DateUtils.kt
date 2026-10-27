package com.nathankst.reclaim.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object DateUtils {

    private val headerFormat = DateTimeFormatter.ofPattern("EEE · d MMM", Locale.ENGLISH)
    private val shortFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /** "THU · 22 OCT" */
    fun headerLabel(date: LocalDate = LocalDate.now()): String =
        date.format(headerFormat).uppercase(Locale.ENGLISH)

    /** "22 OCT" */
    fun shortDate(date: LocalDate): String = date.format(shortFormat).uppercase(Locale.ENGLISH)

    fun dayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    /** "Today", "Yesterday", or the weekday. */
    fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> dayName(date)
    }

    /** "today", "yesterday", or "on Sunday", for finishing a sentence. */
    fun whenLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> "on ${dayName(date)}"
    }
}
