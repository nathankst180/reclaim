package com.nathankst.reclaim.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** One day and one source: how many videos and how long was spent. */
class StatsRow(val date: String, val source: String, val count: Int, val timeMs: Long)

/** Daily totals on the phone. Plain SQLite: no extra library, and nothing leaves the device. */
class StatsDatabase(context: Context) : SQLiteOpenHelper(context, "reclaim.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE daily_stats (" +
                "date TEXT NOT NULL, source TEXT NOT NULL, " +
                "count INTEGER NOT NULL, time_ms INTEGER NOT NULL, " +
                "PRIMARY KEY (date, source))",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Adds to the existing rows instead of replacing them. */
    fun addTo(rows: List<StatsRow>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (row in rows) {
                db.execSQL(
                    "UPDATE daily_stats SET count = count + ?, time_ms = time_ms + ? WHERE date = ? AND source = ?",
                    arrayOf<Any>(row.count, row.timeMs, row.date, row.source),
                )
                val changed = db.compileStatement("SELECT changes()").simpleQueryForLong()
                if (changed == 0L) {
                    db.insert(
                        "daily_stats",
                        null,
                        ContentValues().apply {
                            put("date", row.date)
                            put("source", row.source)
                            put("count", row.count)
                            put("time_ms", row.timeMs)
                        },
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadSince(date: String): List<StatsRow> {
        val rows = mutableListOf<StatsRow>()
        readableDatabase.rawQuery(
            "SELECT date, source, count, time_ms FROM daily_stats WHERE date >= ?",
            arrayOf(date),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += StatsRow(cursor.getString(0), cursor.getString(1), cursor.getInt(2), cursor.getLong(3))
            }
        }
        return rows
    }

    fun deleteAll() {
        writableDatabase.delete("daily_stats", null, null)
    }
}
