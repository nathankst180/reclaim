package com.nathankst.reclaim.ui.settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nathankst.reclaim.BuildConfig
import com.nathankst.reclaim.data.OverlayCorner
import com.nathankst.reclaim.data.Settings
import com.nathankst.reclaim.ui.theme.ReclaimColors
import com.nathankst.reclaim.ui.theme.ReclaimText

private const val LIMIT_STEP = 50
private const val LIMIT_MIN = 50
private const val LIMIT_MAX = 1000
private const val LIMIT_DEFAULT = 300

@Composable
fun SettingsScreen(
    settings: Settings,
    onLimitChange: (Int) -> Unit,
    onOverlayEnabledChange: (Boolean) -> Unit,
    onCornerChange: (OverlayCorner) -> Unit,
    onCountBackwardChange: (Boolean) -> Unit,
    onCopyReport: () -> Unit,
    onDeleteAll: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

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
                text = "Settings",
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

        SettingCard {
            ToggleRow(
                title = "Daily limit",
                subtitle = "Shown as a bar on the home screen. It never blocks anything.",
                checked = settings.dailyLimit > 0,
                onCheckedChange = { onLimitChange(if (it) LIMIT_DEFAULT else 0) },
            )
            if (settings.dailyLimit > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(
                        onClick = { onLimitChange((settings.dailyLimit - LIMIT_STEP).coerceAtLeast(LIMIT_MIN)) },
                        border = BorderStroke(1.dp, ReclaimColors.Line),
                    ) {
                        Text("−", style = ReclaimText.Label, color = ReclaimColors.Mint)
                    }
                    Text(
                        text = settings.dailyLimit.toString(),
                        style = ReclaimText.StatValue,
                        color = ReclaimColors.Text,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(
                        onClick = { onLimitChange((settings.dailyLimit + LIMIT_STEP).coerceAtMost(LIMIT_MAX)) },
                        border = BorderStroke(1.dp, ReclaimColors.Line),
                    ) {
                        Text("+", style = ReclaimText.Label, color = ReclaimColors.Mint)
                    }
                }
            }
        }

        SettingCard {
            ToggleRow(
                title = "Corner counter",
                subtitle = "A small number in the corner while you watch Reels or Shorts.",
                checked = settings.overlayEnabled,
                onCheckedChange = onOverlayEnabledChange,
            )
            if (settings.overlayEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Choice(
                        label = "Top right",
                        selected = settings.overlayCorner == OverlayCorner.TOP_RIGHT,
                        onClick = { onCornerChange(OverlayCorner.TOP_RIGHT) },
                        modifier = Modifier.weight(1f),
                    )
                    Choice(
                        label = "Top left",
                        selected = settings.overlayCorner == OverlayCorner.TOP_LEFT,
                        onClick = { onCornerChange(OverlayCorner.TOP_LEFT) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        SettingCard {
            ToggleRow(
                title = "Count swiping back",
                subtitle = "Off: each reel counts once, even if you swipe back to it. On: every move counts.",
                checked = settings.countBackward,
                onCheckedChange = onCountBackwardChange,
            )
        }

        SettingCard {
            Text("Privacy", style = ReclaimText.Body, color = ReclaimColors.Text)
            Text(
                text = "Everything stays on this phone. Reclaim has no internet permission, and it never reads " +
                    "captions, messages or any text. It only notices which screen is open and when the video changes.",
                style = ReclaimText.Label,
                color = ReclaimColors.Muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SettingCard {
            Text("Not counting right?", style = ReclaimText.Body, color = ReclaimColors.Text)
            Text(
                text = "Copy what Reclaim saw in Instagram and YouTube (view names only, never any text) " +
                    "and send it to the developer.",
                style = ReclaimText.Label,
                color = ReclaimColors.Muted,
                modifier = Modifier.padding(top = 6.dp),
            )
            OutlinedButton(
                onClick = onCopyReport,
                border = BorderStroke(1.dp, ReclaimColors.Line),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Copy a report", style = ReclaimText.Label, color = ReclaimColors.Mint)
            }
        }

        OutlinedButton(
            onClick = { confirmDelete = true },
            border = BorderStroke(1.dp, ReclaimColors.Line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Delete all data", style = ReclaimText.Label, color = ReclaimColors.Amber)
        }

        Text(
            text = "Reclaim ${BuildConfig.VERSION_NAME}",
            style = ReclaimText.Label,
            color = ReclaimColors.Muted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = ReclaimColors.Card,
            titleContentColor = ReclaimColors.Text,
            textContentColor = ReclaimColors.Muted,
            title = { Text("Delete all data?") },
            text = { Text("This removes every count and total from this phone. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onDeleteAll(); confirmDelete = false }) {
                    Text("Delete", color = ReclaimColors.Amber)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", color = ReclaimColors.Mint)
                }
            },
        )
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
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

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = ReclaimText.Body, color = ReclaimColors.Text)
            Text(subtitle, style = ReclaimText.Label, color = ReclaimColors.Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
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

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) ReclaimColors.Mint else ReclaimColors.Card,
        border = BorderStroke(1.dp, if (selected) ReclaimColors.Mint else ReclaimColors.Line),
    ) {
        Text(
            text = label,
            style = ReclaimText.Label,
            color = if (selected) ReclaimColors.Background else ReclaimColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        )
    }
}
