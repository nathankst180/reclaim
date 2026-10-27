package com.nathankst.reclaim

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nathankst.reclaim.counting.CountingConfig
import com.nathankst.reclaim.service.DebugInspector
import com.nathankst.reclaim.service.Diagnostics
import com.nathankst.reclaim.service.isTrackingServiceEnabled
import com.nathankst.reclaim.ui.debug.DebugScreen
import com.nathankst.reclaim.ui.home.HomeScreen
import com.nathankst.reclaim.ui.home.MAX_WEEK_OFFSET
import com.nathankst.reclaim.ui.home.homeState
import com.nathankst.reclaim.ui.settings.SettingsScreen
import com.nathankst.reclaim.ui.theme.ReclaimTheme
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as ReclaimApp).container
        setContent {
            ReclaimTheme {
                ReclaimNavHost(container)
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val DEBUG = "debug"
}

@Composable
private fun ReclaimNavHost(container: AppContainer) {
    val context = LocalContext.current
    val navController = rememberNavController()

    val serviceRunning by container.isRunning.collectAsStateWithLifecycle()
    var enabledInSettings by remember { mutableStateOf(isTrackingServiceEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabledInSettings = isTrackingServiceEnabled(context)
        onPauseOrDispose {}
    }
    val isTracking = serviceRunning || enabledInSettings

    val days by container.stats.days.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val today by produceState(LocalDate.now()) {
        while (true) {
            delay(30_000)
            value = LocalDate.now()
        }
    }

    // Which day the big card shows, as "days before today", and which week the chart shows.
    var selectedBack by rememberSaveable { mutableIntStateOf(0) }
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    val home = remember(days, settings.dailyLimit, today, selectedBack, weekOffset) {
        homeState(
            days = days,
            limit = settings.dailyLimit,
            distancePerVideoCm = container.distancePerVideoCm,
            today = today,
            selected = today.minusDays(selectedBack.toLong()),
            weekOffset = weekOffset,
        )
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                state = home,
                isTracking = isTracking,
                onTurnOnTracking = { openAccessibilitySettings(context) },
                onOpenAppInfo = { openAppInfo(context) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenDebug = { navController.navigate(Routes.DEBUG) },
                onCopyReport = if (isTracking && home.total == 0) ({ copyReport(context, container) }) else null,
                onSelectDay = { date -> selectedBack = ChronoUnit.DAYS.between(date, today).toInt() },
                onOlderWeek = {
                    weekOffset = (weekOffset + 1).coerceAtMost(MAX_WEEK_OFFSET)
                    selectedBack = 7 * weekOffset
                },
                onNewerWeek = {
                    weekOffset = (weekOffset - 1).coerceAtLeast(0)
                    selectedBack = 7 * weekOffset
                },
                onBackToToday = {
                    weekOffset = 0
                    selectedBack = 0
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                onLimitChange = container.settings::setDailyLimit,
                onOverlayEnabledChange = container.settings::setOverlayEnabled,
                onCornerChange = container.settings::setOverlayCorner,
                onCountBackwardChange = container.settings::setCountBackward,
                onCopyReport = { copyReport(context, container) },
                onDeleteAll = container::deleteAllData,
                onBack = { navController.popBackStack() },
            )
        }
        if (BuildConfig.INSPECTOR) {
            composable(Routes.DEBUG) {
                DebugScreen(
                    isTracking = isTracking,
                    counter = container.counter,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

private fun copyReport(context: Context, container: AppContainer) {
    fun versionOf(pkg: String): String? =
        runCatching { context.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull()

    var report = Diagnostics.report(
        reclaimVersion = BuildConfig.VERSION_NAME,
        instagramVersion = versionOf(CountingConfig.INSTAGRAM_PACKAGE),
        youtubeVersion = versionOf(CountingConfig.YOUTUBE_PACKAGE),
        learned = container.learnedIds.describe(),
    )
    if (BuildConfig.INSPECTOR) {
        // Inspector builds add what the app saw event by event, and which views were on screen.
        report += "\n\nEVENTS (newest last)\n" + DebugInspector.eventsText()
        report += "\n\n" + (DebugInspector.dumpText() ?: "No screen snapshots were taken.")
    }
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("Reclaim report", report))
    Toast.makeText(context, "Report copied. Paste it to the developer.", Toast.LENGTH_LONG).show()
}

private fun openAppInfo(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}
