package com.nathankst.reclaim.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OverlayCorner { TOP_RIGHT, TOP_LEFT }

data class Settings(
    val dailyLimit: Int = 300,          // 0 = off
    val overlayEnabled: Boolean = true,
    val overlayCorner: OverlayCorner = OverlayCorner.TOP_RIGHT,
    val countBackward: Boolean = false,
)

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("reclaim_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun setDailyLimit(limit: Int) = update { copy(dailyLimit = limit) }
    fun setOverlayEnabled(enabled: Boolean) = update { copy(overlayEnabled = enabled) }
    fun setOverlayCorner(corner: OverlayCorner) = update { copy(overlayCorner = corner) }
    fun setCountBackward(enabled: Boolean) = update { copy(countBackward = enabled) }

    private fun update(change: Settings.() -> Settings) {
        val next = _settings.value.change()
        _settings.value = next
        prefs.edit()
            .putInt(KEY_LIMIT, next.dailyLimit)
            .putBoolean(KEY_OVERLAY, next.overlayEnabled)
            .putString(KEY_CORNER, next.overlayCorner.name)
            .putBoolean(KEY_BACKWARD, next.countBackward)
            .apply()
    }

    private fun read(): Settings {
        val defaults = Settings()
        return Settings(
            dailyLimit = prefs.getInt(KEY_LIMIT, defaults.dailyLimit),
            overlayEnabled = prefs.getBoolean(KEY_OVERLAY, defaults.overlayEnabled),
            overlayCorner = prefs.getString(KEY_CORNER, null)
                ?.let { name -> OverlayCorner.entries.firstOrNull { it.name == name } }
                ?: defaults.overlayCorner,
            countBackward = prefs.getBoolean(KEY_BACKWARD, defaults.countBackward),
        )
    }

    private companion object {
        const val KEY_LIMIT = "dailyLimit"
        const val KEY_OVERLAY = "overlayEnabled"
        const val KEY_CORNER = "overlayCorner"
        // A new name: swiping back used to count by default, and that saved value must not carry over.
        const val KEY_BACKWARD = "countBackwardV2"
    }
}
