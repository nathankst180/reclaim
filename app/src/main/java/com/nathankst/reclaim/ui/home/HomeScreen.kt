package com.nathankst.reclaim.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathankst.reclaim.BuildConfig
import com.nathankst.reclaim.R
import com.nathankst.reclaim.ui.theme.ReclaimColors
import com.nathankst.reclaim.ui.theme.ReclaimText
import com.nathankst.reclaim.util.Formatters
import java.time.LocalDate
import kotlin.math.abs

@Composable
fun HomeScreen(
    state: HomeState,
    isTracking: Boolean,
    onTurnOnTracking: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
    onCopyReport: (() -> Unit)?,
    onSelectDay: (LocalDate) -> Unit,
    onOlderWeek: () -> Unit,
    onNewerWeek: () -> Unit,
    onBackToToday: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReclaimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Header(state, onOpenSettings = onOpenSettings, onOpenDebug = onOpenDebug)
        if (!isTracking) {
            TrackingPausedCard(onTurnOn = onTurnOnTracking, onOpenAppInfo = onOpenAppInfo)
        }
        HeroCard(state, onCopyReport = onCopyReport.takeIf { state.isToday }, onBackToToday = onBackToToday)
        StatGrid(state)
        WeekCard(state, onSelectDay = onSelectDay, onOlderWeek = onOlderWeek, onNewerWeek = onNewerWeek)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Header(state: HomeState, onOpenSettings: () -> Unit, onOpenDebug: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.dateLabel,
                style = ReclaimText.Overline,
                color = ReclaimColors.Muted,
            )
            Spacer(Modifier.height(4.dp))
            // Long-press on the title opens the debug inspector (inspector builds only).
            val titleModifier = if (BuildConfig.INSPECTOR) {
                Modifier.combinedClickable(onClick = {}, onLongClick = onOpenDebug)
            } else {
                Modifier
            }
            Text(
                text = state.dayTitle,
                style = ReclaimText.Title,
                color = ReclaimColors.Text,
                modifier = titleModifier,
            )
        }
        SettingsButton(onClick = onOpenSettings)
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, ReclaimColors.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = stringResource(R.string.settings),
            tint = ReclaimColors.Muted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CardSurface(radius: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius),
        color = ReclaimColors.Card,
        border = BorderStroke(1.dp, ReclaimColors.Line),
        content = content,
    )
}

@Composable
private fun TrackingPausedCard(onTurnOn: () -> Unit, onOpenAppInfo: () -> Unit) {
    CardSurface(radius = 20.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTurnOn),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tracking_paused),
                    style = ReclaimText.Body,
                    color = ReclaimColors.Text,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.turn_on),
                    style = ReclaimText.Label,
                    color = ReclaimColors.Mint,
                )
            }
            Text(
                text = "Can't switch it on? Open app info",
                style = ReclaimText.Label,
                color = ReclaimColors.Muted,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickable(onClick = onOpenAppInfo),
            )
        }
    }
}

@Composable
private fun HeroCard(state: HomeState, onCopyReport: (() -> Unit)?, onBackToToday: () -> Unit) {
    CardSurface(radius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "You swiped",
                    style = ReclaimText.Body,
                    color = ReclaimColors.Muted,
                    modifier = Modifier.weight(1f),
                )
                if (!state.isToday) {
                    Text(
                        text = "Back to today",
                        style = ReclaimText.Label,
                        color = ReclaimColors.Mint,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onBackToToday)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Text(
                text = state.total.toString(),
                style = ReclaimText.BigNumber,
                color = ReclaimColors.Mint,
                maxLines = 1,
            )
            Text(
                text = if (state.total == 0) {
                    if (state.isToday) "Nothing yet today. Nice." else "Nothing ${state.whenLabel}. Nice."
                } else {
                    "reels & shorts ${state.whenLabel}"
                },
                style = ReclaimText.Body,
                color = ReclaimColors.Text,
            )
            if (state.timeMs > 0) {
                Text(
                    text = "${Formatters.duration(state.timeMs)} spent watching",
                    style = ReclaimText.Body,
                    color = ReclaimColors.Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (onCopyReport != null && state.total == 0) {
                Text(
                    text = "Watched Reels or Shorts but still 0? Copy a report",
                    style = ReclaimText.Label,
                    color = ReclaimColors.Mint,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clickable(onClick = onCopyReport),
                )
            }
            if (state.limit > 0) {
                Spacer(Modifier.height(20.dp))
                SegmentBar(filled = state.filledSegments, over = state.overLimit)
                Spacer(Modifier.height(10.dp))
                Row {
                    Text(
                        text = "${state.percentOfLimit}% of ${state.limit} limit",
                        style = ReclaimText.Label,
                        color = ReclaimColors.Muted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (state.leftOrOver >= 0) "${state.leftOrOver} left" else "${abs(state.leftOrOver)} over",
                        style = ReclaimText.Label,
                        color = if (state.overLimit) ReclaimColors.Amber else ReclaimColors.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentBar(filled: Int, over: Boolean) {
    val color = if (over) ReclaimColors.Amber else ReclaimColors.Mint
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(SEGMENTS) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (index < filled) color else ReclaimColors.Track),
            )
        }
    }
}

@Composable
private fun StatGrid(state: HomeState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Time spent", Formatters.duration(state.timeMs), modifier = Modifier.weight(1f))
            StatCard("Distance", Formatters.distance(state.distanceMeters), modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Instagram Reels",
                value = state.instagram.toString(),
                note = Formatters.duration(state.instagramTimeMs),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "YouTube Shorts",
                value = state.youtube.toString(),
                note = Formatters.duration(state.youtubeTimeMs),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier, note: String? = null) {
    CardSurface(radius = 20.dp, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(label, style = ReclaimText.Label, color = ReclaimColors.Muted)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = ReclaimText.StatValue, color = ReclaimColors.Text, maxLines = 1)
                if (note != null) {
                    Text(
                        text = "  $note",
                        style = ReclaimText.Label,
                        color = ReclaimColors.Muted,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekCard(
    state: HomeState,
    onSelectDay: (LocalDate) -> Unit,
    onOlderWeek: () -> Unit,
    onNewerWeek: () -> Unit,
) {
    CardSurface(radius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeekArrow("‹", enabled = state.canGoOlder, onClick = onOlderWeek)
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.weekLabel,
                        style = ReclaimText.Overline,
                        color = ReclaimColors.Muted,
                    )
                    state.weekChangePercent?.let { change ->
                        Text(
                            text = if (change < 0) "${-change}% less" else "$change% more",
                            style = ReclaimText.Label,
                            color = ReclaimColors.Mint,
                        )
                    }
                }
                WeekArrow("›", enabled = state.canGoNewer, onClick = onNewerWeek)
            }
            Spacer(Modifier.height(12.dp))
            val biggest = state.week.maxOfOrNull { it.total } ?: 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VALUE_HEIGHT + CHART_HEIGHT + LABEL_HEIGHT)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.week.forEach { bar ->
                    val barHeight = if (biggest == 0) MIN_BAR else maxOf(MIN_BAR, CHART_HEIGHT * (bar.total.toFloat() / biggest))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectDay(bar.date) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            text = bar.total.toString(),
                            style = ReclaimText.Label.copy(fontSize = 11.sp),
                            color = if (bar.isSelected) ReclaimColors.Text else ReclaimColors.Muted,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(barHeight)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (bar.isSelected) ReclaimColors.Mint else ReclaimColors.ChartBar),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = bar.label.toString(),
                            style = ReclaimText.Label,
                            color = if (bar.isToday || bar.isSelected) ReclaimColors.Text else ReclaimColors.Muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekArrow(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = ReclaimText.StatValue,
            color = if (enabled) ReclaimColors.Mint else ReclaimColors.Track,
        )
    }
}

private const val SEGMENTS = 10
private val CHART_HEIGHT = 72.dp
private val VALUE_HEIGHT = 18.dp
private val LABEL_HEIGHT = 30.dp
private val MIN_BAR = 4.dp
