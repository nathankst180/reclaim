package com.nathankst.reclaim.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nathankst.reclaim.ReclaimApp

/** Debug builds only. Lets the history screen be checked on an emulator without waiting weeks. */
class SeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as ReclaimApp).container.stats.seedSampleHistory()
    }
}
