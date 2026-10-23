package com.nathankst.reclaim.ui.debug

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathankst.reclaim.service.DebugInspector
import com.nathankst.reclaim.ui.theme.ReclaimColors
import com.nathankst.reclaim.ui.theme.ReclaimText

/** Debug builds only. Reached by long-pressing the title on Home. */
@Composable
fun DebugScreen(isTracking: Boolean, onBack: () -> Unit) {
    val logEnabled by DebugInspector.logEnabled.collectAsStateWithLifecycle()
    val dumpPending by DebugInspector.dumpPending.collectAsStateWithLifecycle()
    val lastDump by DebugInspector.lastDump.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReclaimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Debug",
                style = ReclaimText.Title,
                color = ReclaimColors.Text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Back",
                style = ReclaimText.Label,
                color = ReclaimColors.Mint,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(8.dp),
            )
        }

        DebugCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Accessibility service",
                    style = ReclaimText.Body,
                    color = ReclaimColors.Text,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (isTracking) "ON" else "OFF",
                    style = ReclaimText.StatValue,
                    color = if (isTracking) ReclaimColors.Mint else ReclaimColors.Amber,
                )
            }
        }

        DebugCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Inspector log", style = ReclaimText.Body, color = ReclaimColors.Text)
                    Text(
                        text = "Logcat tag ReclaimDbg. Event types, view IDs and indexes only, no text.",
                        style = ReclaimText.Label,
                        color = ReclaimColors.Muted,
                    )
                }
                Switch(
                    checked = logEnabled,
                    onCheckedChange = DebugInspector::setLogEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ReclaimColors.Background,
                        checkedTrackColor = ReclaimColors.Mint,
                        uncheckedThumbColor = ReclaimColors.Muted,
                        uncheckedTrackColor = ReclaimColors.Track,
                        uncheckedBorderColor = ReclaimColors.Line,
                    ),
                )
            }
        }

        DebugCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Dump view IDs", style = ReclaimText.Body, color = ReclaimColors.Text)
                Text(
                    text = when {
                        dumpPending -> "Waiting. Open Instagram Reels or YouTube Shorts now."
                        lastDump != null -> "Done: $lastDump"
                        else -> "Tap, then open Reels or Shorts. IDs only go to Logcat."
                    },
                    style = ReclaimText.Label,
                    color = ReclaimColors.Muted,
                )
                Button(
                    onClick = { if (dumpPending) DebugInspector.cancelDump() else DebugInspector.requestDump() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ReclaimColors.Mint,
                        contentColor = ReclaimColors.Background,
                    ),
                ) {
                    Text(if (dumpPending) "Cancel" else "Dump view IDs", style = ReclaimText.Label)
                }
            }
        }
    }
}

@Composable
private fun DebugCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ReclaimColors.Card,
        border = BorderStroke(1.dp, ReclaimColors.Line),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            content()
        }
    }
}
