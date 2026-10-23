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
    // These are starting guesses. The debug inspector confirms the real IDs on the phone.

    const val COUNT_BACKWARD_DEFAULT = true
    const val MIN_GAP_BETWEEN_COUNTS_MS = 250L   // ignore duplicate events
    const val GESTURE_END_MS = 300L              // fallback: no event for 300 ms = gesture ended
    const val FALLBACK_MIN_SCREEN_FRACTION = 0.5f // fallback: must move at least 50% of screen
    const val PLAYER_CHECK_THROTTLE_MS = 500L

    fun playerIdsFor(source: Source): List<String> = when (source) {
        Source.INSTAGRAM_REELS -> INSTAGRAM_PLAYER_IDS
        Source.YOUTUBE_SHORTS -> YOUTUBE_PLAYER_IDS
    }
}
