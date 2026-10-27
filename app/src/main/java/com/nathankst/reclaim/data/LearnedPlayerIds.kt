package com.nathankst.reclaim.data

import android.content.Context
import com.nathankst.reclaim.counting.Source

/**
 * The first time a scroll is recognised as the Reels or Shorts player, its real view ID is remembered here,
 * so opening a reel can be spotted later even if the app uses a different name from the built-in guesses.
 * Only view IDs are kept, never any text.
 */
class LearnedPlayerIds(context: Context) {

    private val prefs = context.getSharedPreferences("reclaim_learned", Context.MODE_PRIVATE)
    private val ids = HashMap<Source, Set<String>>().also { map ->
        for (source in Source.entries) {
            map[source] = prefs.getStringSet(source.name, null)?.toSet().orEmpty()
        }
    }

    fun idsFor(source: Source): Set<String> = ids[source].orEmpty()

    fun learn(source: Source, id: String) {
        val current = idsFor(source)
        if (id in current || current.size >= MAX_PER_SOURCE) return
        val next = current + id
        ids[source] = next
        prefs.edit().putStringSet(source.name, next).apply()
    }

    fun describe(): String = Source.entries.joinToString("; ") { source ->
        "$source: ${idsFor(source).joinToString().ifEmpty { "none yet" }}"
    }

    private companion object {
        const val MAX_PER_SOURCE = 6
    }
}
