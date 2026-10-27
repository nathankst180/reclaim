package dev.reclaim.fakereels

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/**
 * Emulator-only stand-in for the Reels or Shorts player, so Reclaim's counting can be tested without the real apps.
 * Start with: am start -n <package>/dev.reclaim.fakereels.ReelsActivity --es mode <mode>
 *  - pager2:   a vertical ViewPager2 whose container has the Instagram player ID (the scrolled view is a child without an ID)
 *  - recycler: a RecyclerView with the Instagram player ID that snaps page by page
 *  - shorts:   a RecyclerView with the YouTube player ID (reel_recycler)
 *  - hint:     a RecyclerView whose ID is only recognised by the "reel_" hint (reel_watch_pager)
 *  - feed:     a plain scrolling list with a different ID, which must never be counted
 *  - carousel: a sideways pager with the Instagram player ID, which must never be counted
 * SearchActivity opens this screen when a tile is tapped, like opening a reel from search.
 * Never install this on a phone that has the real Instagram or YouTube.
 */
class ReelsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
    }

    /** Starting this screen again while it is on top swaps in a brand new pager, without the old one ever closing. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setContentView(buildContent())
    }

    private fun buildContent() = when (intent.getStringExtra("mode")) {
        "recycler" -> snappingList(R.id.clips_viewer_view_pager)
        "shorts" -> snappingList(R.id.reel_recycler)
        "hint" -> snappingList(R.id.reel_watch_pager)
        "feed" -> RecyclerView(this).apply {
            id = R.id.feed_list
            layoutManager = LinearLayoutManager(context)
            adapter = PageAdapter(fullScreen = false)
        }
        "carousel" -> ViewPager2(this).apply {
            id = R.id.clips_viewer_view_pager
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            adapter = PageAdapter(fullScreen = true)
        }
        else -> ViewPager2(this).apply {
            id = R.id.clips_viewer_view_pager
            orientation = ViewPager2.ORIENTATION_VERTICAL
            adapter = PageAdapter(fullScreen = true)
        }
    }

    private fun snappingList(viewId: Int) = RecyclerView(this).apply {
        id = viewId
        layoutManager = LinearLayoutManager(context)
        adapter = PageAdapter(fullScreen = true)
        PagerSnapHelper().attachToRecyclerView(this)
    }
}

/** Like a search results page: tap a tile and a reel opens. Back returns here. */
class SearchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "pager2"
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        repeat(3) { index ->
            column.addView(
                TextView(this).apply {
                    id = R.id.grid_item
                    text = "Open reel ${'A' + index}"
                    textSize = 32f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.HSVToColor(floatArrayOf(index * 90f, 0.5f, 0.35f)))
                    setOnClickListener {
                        startActivity(Intent(this@SearchActivity, ReelsActivity::class.java).putExtra("mode", mode))
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        setContentView(column)
    }
}

private class PageAdapter(private val fullScreen: Boolean) : RecyclerView.Adapter<PageAdapter.Holder>() {

    class Holder(val label: TextView) : RecyclerView.ViewHolder(label)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val label = TextView(parent.context).apply {
            gravity = Gravity.CENTER
            textSize = 48f
            setTextColor(Color.WHITE)
            val height = if (fullScreen) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                (resources.displayMetrics.density * 260).toInt()
            }
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        }
        return Holder(label)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.label.text = "Reel ${position + 1}"
        holder.label.setBackgroundColor(Color.HSVToColor(floatArrayOf((position * 37f) % 360, 0.6f, 0.45f)))
    }

    override fun getItemCount() = 500
}
