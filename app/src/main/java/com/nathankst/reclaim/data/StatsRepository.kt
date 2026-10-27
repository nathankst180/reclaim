package com.nathankst.reclaim.data

import android.content.Context
import com.nathankst.reclaim.counting.Source
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DayStats(
    val date: LocalDate,
    val instagram: Int = 0,
    val youtube: Int = 0,
    val instagramTimeMs: Long = 0,
    val youtubeTimeMs: Long = 0,
) {
    val total: Int get() = instagram + youtube
    val timeMs: Long get() = instagramTimeMs + youtubeTimeMs
}

/**
 * Keeps the last nine weeks in memory, so the screen and the corner number are always live,
 * and writes only the new part to the database every 30 seconds and when the player is left.
 */
class StatsRepository(context: Context, private val scope: CoroutineScope) {

    private class Cell(var count: Int = 0, var timeMs: Long = 0)

    private val database = StatsDatabase(context.applicationContext)
    private val dbDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val lock = Any()
    private val totals = HashMap<Pair<String, Source>, Cell>()
    private val unsaved = HashMap<Pair<String, Source>, Cell>()

    private val _days = MutableStateFlow(buildDays())

    /** The last [DAYS_KEPT] days, oldest first. The last item is today. */
    val days: StateFlow<List<DayStats>> = _days.asStateFlow()

    init {
        scope.launch(dbDispatcher) {
            val since = LocalDate.now().minusDays(DAYS_KEPT - 1L).toString()
            val rows = runCatching { database.loadSince(since) }.getOrDefault(emptyList())
            synchronized(lock) {
                for (row in rows) {
                    val source = Source.entries.firstOrNull { it.name == row.source } ?: continue
                    val cell = totals.getOrPut(row.date to source) { Cell() }
                    cell.count += row.count
                    cell.timeMs += row.timeMs
                }
                publishLocked()
            }
        }
    }

    fun recordCount(source: Source) = add(source, 1, 0L)

    fun addTime(source: Source, timeMs: Long) = add(source, 0, timeMs)

    /** Writes everything not yet saved. Safe to call often. */
    fun flush() {
        val batch = synchronized(lock) {
            if (unsaved.isEmpty()) return
            unsaved.map { (key, cell) -> StatsRow(key.first, key.second.name, cell.count, cell.timeMs) }
                .also { unsaved.clear() }
        }
        scope.launch(dbDispatcher) {
            runCatching { database.addTo(batch) }.onFailure { requeue(batch) }
        }
    }

    /** Debug builds only, called from a broadcast: fills the past weeks with made-up numbers, in memory only. */
    fun seedSampleHistory() {
        val today = LocalDate.now()
        synchronized(lock) {
            for (back in 1..40) {
                val date = today.minusDays(back.toLong()).toString()
                totals[date to Source.INSTAGRAM_REELS] = Cell((back * 37) % 130 + 25, ((back * 11) % 50 + 5) * 60_000L)
                totals[date to Source.YOUTUBE_SHORTS] = Cell((back * 53) % 90 + 10, ((back * 7) % 30 + 3) * 60_000L)
            }
            publishLocked()
        }
    }

    fun deleteAll() {
        synchronized(lock) {
            totals.clear()
            unsaved.clear()
            publishLocked()
        }
        scope.launch(dbDispatcher) { runCatching { database.deleteAll() } }
    }

    private fun add(source: Source, count: Int, timeMs: Long) {
        if (count == 0 && timeMs <= 0L) return
        val key = LocalDate.now().toString() to source
        synchronized(lock) {
            totals.getOrPut(key) { Cell() }.also { it.count += count; it.timeMs += timeMs }
            unsaved.getOrPut(key) { Cell() }.also { it.count += count; it.timeMs += timeMs }
            publishLocked()
        }
    }

    private fun requeue(batch: List<StatsRow>) {
        synchronized(lock) {
            for (row in batch) {
                val source = Source.entries.firstOrNull { it.name == row.source } ?: continue
                unsaved.getOrPut(row.date to source) { Cell() }.also {
                    it.count += row.count
                    it.timeMs += row.timeMs
                }
            }
        }
    }

    private fun publishLocked() {
        _days.value = buildDays()
    }

    private fun buildDays(): List<DayStats> {
        val today = LocalDate.now()
        return (DAYS_KEPT - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val key = date.toString()
            val instagram = totals[key to Source.INSTAGRAM_REELS]
            val youtube = totals[key to Source.YOUTUBE_SHORTS]
            DayStats(
                date = date,
                instagram = instagram?.count ?: 0,
                youtube = youtube?.count ?: 0,
                instagramTimeMs = instagram?.timeMs ?: 0L,
                youtubeTimeMs = youtube?.timeMs ?: 0L,
            )
        }
    }

    private companion object {
        const val DAYS_KEPT = 63
    }
}
