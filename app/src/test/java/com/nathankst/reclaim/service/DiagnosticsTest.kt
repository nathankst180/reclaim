package com.nathankst.reclaim.service

import com.nathankst.reclaim.counting.Source
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticsTest {

    @Before
    fun reset() = Diagnostics.clear()

    @Test
    fun an_empty_report_tells_you_what_to_do() {
        val text = Diagnostics.report("0.4.0", "300.1", "19.17")
        assertTrue(text.contains("Reclaim 0.4.0 report"))
        assertTrue(text.contains("Instagram app: 300.1, YouTube app: 19.17"))
        assertTrue(text.contains("Nothing seen yet"))
    }

    @Test
    fun the_report_lists_views_with_counts_and_whether_they_were_recognised() {
        repeat(3) { Diagnostics.recordScroll(Source.INSTAGRAM_REELS, "com.instagram.android:id/list", "androidx.recyclerview.widget.RecyclerView", false) }
        Diagnostics.recordScroll(Source.INSTAGRAM_REELS, "com.instagram.android:id/clips_viewer_view_pager", "androidx.viewpager.widget.ViewPager", true)
        Diagnostics.recordScreen(Source.INSTAGRAM_REELS, "com.instagram.android.activity.MainTabActivity")

        val text = Diagnostics.report("0.4.0", null, null)
        assertTrue(text.contains("INSTAGRAM_REELS: 4 scroll checks, 1 recognised as the player"))
        assertTrue(text.contains("com.instagram.android:id/list (RecyclerView) x3"))
        assertTrue(text.contains("clips_viewer_view_pager (ViewPager) x1"))
        assertTrue(text.contains("screens: MainTabActivity"))
        assertTrue(text.contains("Instagram app: unknown"))
    }

    @Test
    fun a_view_without_an_id_is_still_reported() {
        Diagnostics.recordScroll(Source.YOUTUBE_SHORTS, null, "androidx.recyclerview.widget.RecyclerView", false)
        assertTrue(Diagnostics.report("0.4.0", null, null).contains("no id (RecyclerView) x1"))
    }
}
