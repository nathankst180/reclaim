package com.nathankst.reclaim.counting

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountingConfigTest {

    private val ig = Source.INSTAGRAM_REELS
    private val yt = Source.YOUTUBE_SHORTS

    @Test
    fun the_built_in_instagram_ids_match() {
        assertTrue(CountingConfig.isPlayerId(ig, "com.instagram.android:id/clips_viewer_view_pager"))
        assertTrue(CountingConfig.isPlayerId(ig, "com.instagram.android:id/clips_viewer_pager"))
    }

    @Test
    fun a_renamed_instagram_reels_view_still_matches_by_hint() {
        assertTrue(CountingConfig.isPlayerId(ig, "com.instagram.android:id/clips_viewer_recycler"))
    }

    @Test
    fun instagram_stories_and_the_feed_do_not_match() {
        assertFalse(CountingConfig.isPlayerId(ig, "com.instagram.android:id/reel_viewer_media_layout"))
        assertFalse(CountingConfig.isPlayerId(ig, "com.instagram.android:id/reels_tray_container"))
        assertFalse(CountingConfig.isPlayerId(ig, "android:id/list"))
        assertFalse(CountingConfig.isPlayerId(ig, "com.instagram.android:id/feed_tab"))
    }

    @Test
    fun the_built_in_youtube_ids_match() {
        assertTrue(CountingConfig.isPlayerId(yt, "com.google.android.youtube:id/reel_recycler"))
        assertTrue(CountingConfig.isPlayerId(yt, "com.google.android.youtube:id/reel_player_page_container"))
    }

    @Test
    fun renamed_youtube_shorts_views_still_match_by_hint() {
        assertTrue(CountingConfig.isPlayerId(yt, "com.google.android.youtube:id/reel_watch_pager"))
        assertTrue(CountingConfig.isPlayerId(yt, "com.google.android.youtube:id/shorts_container"))
    }

    @Test
    fun ordinary_youtube_views_do_not_match() {
        assertFalse(CountingConfig.isPlayerId(yt, "com.google.android.youtube:id/results_list"))
        assertFalse(CountingConfig.isPlayerId(yt, "com.google.android.youtube:id/watch_list"))
        assertFalse(CountingConfig.isPlayerId(yt, "com.google.android.youtube:id/pivot_bar"))
    }

    @Test
    fun an_id_for_one_app_does_not_match_the_other() {
        assertFalse(CountingConfig.isPlayerId(yt, "com.instagram.android:id/clips_viewer_view_pager"))
        assertFalse(CountingConfig.isPlayerId(ig, "com.google.android.youtube:id/reel_recycler"))
    }
}
