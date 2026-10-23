package com.nathankst.reclaim.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {

    private val headerFormat = DateTimeFormatter.ofPattern("EEE · d MMM", Locale.ENGLISH)

    /** "THU · 22 OCT" */
    fun headerLabel(date: LocalDate = LocalDate.now()): String =
        date.format(headerFormat).uppercase(Locale.ENGLISH)
}
