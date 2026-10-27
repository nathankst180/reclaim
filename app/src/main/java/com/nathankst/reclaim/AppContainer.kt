package com.nathankst.reclaim

import android.content.Context
import com.nathankst.reclaim.counting.ReelCounter
import com.nathankst.reclaim.data.LearnedPlayerIds
import com.nathankst.reclaim.data.SettingsRepository
import com.nathankst.reclaim.data.StatsRepository
import com.nathankst.reclaim.service.DebugInspector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Shared by the UI and the accessibility service. Everything stays on the phone. */
class AppContainer(context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context)
    val stats = StatsRepository(context, appScope)
    val learnedIds = LearnedPlayerIds(context)

    /** How far one swipe moves your thumb: one screen height, in centimetres. */
    val distancePerVideoCm: Double = context.resources.displayMetrics.let {
        it.heightPixels / it.ydpi * 2.54
    }

    val counter = ReelCounter(
        countBackward = settings.settings.value.countBackward,
        onCounted = { source, _ ->
            stats.recordCount(source)
            if (BuildConfig.INSPECTOR) DebugInspector.note("COUNTED +1 $source")
        },
    )

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    init {
        appScope.launch {
            settings.settings.map { it.countBackward }.distinctUntilChanged().collect {
                counter.countBackward = it
            }
        }
    }

    fun setServiceRunning(running: Boolean) {
        _isRunning.value = running
    }

    fun deleteAllData() {
        stats.deleteAll()
        counter.resetCounts()
    }
}
