package com.nathankst.reclaim

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared state for the UI and the accessibility service. Database, settings and live stats join later. */
class AppContainer {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun setServiceRunning(running: Boolean) {
        _isRunning.value = running
    }
}
