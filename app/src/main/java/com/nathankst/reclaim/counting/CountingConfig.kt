package com.nathankst.reclaim.counting

object CountingConfig {
    const val INSTAGRAM_PACKAGE = "com.instagram.android"
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    val INSTAGRAM_PLAYER_IDS = listOf(
        "com.instagram.android:id/clips_viewer_view_pager",
        "com.instagram.android:id/clips_viewer_pager",
    )
    val YOUTUBE_PLAYER_IDS = listOf(
        "com.google.android.youtube:id/reel_recycler",
        "com.google.android.youtube:id/reel_player_page_container",
    )
    // Starting points only. The first scroll that is recognised teaches Reclaim the real ID, and it is kept.

    // Apps rename views between versions, so any ID that contains one of these also counts.
    // Instagram calls Reels "clips". Do not add "reel" there: Instagram calls Stories "reel".
    // YouTube has no Stories, and "reel" is how its Shorts views are named.
    private val INSTAGRAM_PLAYER_ID_HINTS = listOf("clips_viewer")
    private val YOUTUBE_PLAYER_ID_HINTS = listOf("reel_", "shorts_")

    /** Each reel counts once, even if you swipe back to it. Turn on in Settings to count every move. */
    const val COUNT_BACKWARD_DEFAULT = false
    const val MIN_GAP_BETWEEN_COUNTS_MS = 250L    // ignore duplicate events
    const val GESTURE_END_MS = 300L               // fallback: no event for 300 ms = gesture ended
    const val FALLBACK_MIN_SCREEN_FRACTION = 0.5f // fallback: must move at least 50% of screen
    const val PLAYER_CHECK_THROTTLE_MS = 500L

    const val REENTRY_WINDOW_MS = 3_000L          // leaving for less than this and coming back is not a new entry
    const val RESET_WINDOW_MS = 1_000L            // the first position after coming back only sets the position
    const val SCREEN_RESUME_WINDOW_MS = 5_000L    // screen back on in the same player is a resume, not a new entry
    const val SCROLL_VERDICT_MS = 400L            // one "is this the pager?" answer is reused for one gesture
    const val MAX_PAGE_SPAN = 1                   // a pager shows at most 2 videos at once; a longer list is not a pager

    // Looking for the player: after a tap or a screen change, then once a second while it is open.
    const val FIRST_PROBE_MS = 500L
    const val LEAVE_CHECK_MS = 1_000L
    const val PROBE_WINDOW_MS = 10_000L
    const val LEAVE_AFTER_MISSES = 2              // this many checks in a row without the player = we left it

    fun playerIdsFor(source: Source): List<String> = when (source) {
        Source.INSTAGRAM_REELS -> INSTAGRAM_PLAYER_IDS
        Source.YOUTUBE_SHORTS -> YOUTUBE_PLAYER_IDS
    }

    fun isPlayerId(source: Source, viewId: String): Boolean {
        if (viewId in playerIdsFor(source)) return true
        val hints = when (source) {
            Source.INSTAGRAM_REELS -> INSTAGRAM_PLAYER_ID_HINTS
            Source.YOUTUBE_SHORTS -> YOUTUBE_PLAYER_ID_HINTS
        }
        return hints.any { viewId.contains(it) }
    }
}
