package com.nathankst.reclaim

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nathankst.reclaim.service.isTrackingServiceEnabled
import com.nathankst.reclaim.ui.debug.DebugScreen
import com.nathankst.reclaim.ui.home.HomeScreen
import com.nathankst.reclaim.ui.theme.ReclaimTheme

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

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                isTracking = isTracking,
                onTurnOnTracking = { openAccessibilitySettings(context) },
                onOpenSettings = {},
                onOpenDebug = { navController.navigate(Routes.DEBUG) },
            )
        }
        if (BuildConfig.DEBUG) {
            composable(Routes.DEBUG) {
                DebugScreen(
                    isTracking = isTracking,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
