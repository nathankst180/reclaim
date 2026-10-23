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
import androidx.compose.ui.unit.dp
import com.nathankst.reclaim.BuildConfig
import com.nathankst.reclaim.R
import com.nathankst.reclaim.ui.theme.ReclaimColors
import com.nathankst.reclaim.ui.theme.ReclaimText
import com.nathankst.reclaim.util.DateUtils

@Composable
fun HomeScreen(
    isTracking: Boolean,
    onTurnOnTracking: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
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
        Header(onOpenSettings = onOpenSettings, onOpenDebug = onOpenDebug)
        if (!isTracking) {
            TrackingPausedCard(onClick = onTurnOnTracking)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Header(onOpenSettings: () -> Unit, onOpenDebug: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = DateUtils.headerLabel(),
                style = ReclaimText.Overline,
                color = ReclaimColors.Muted,
            )
            Spacer(Modifier.height(4.dp))
            // Long-press on the title opens the debug inspector (debug builds only).
            val titleModifier = if (BuildConfig.DEBUG) {
                Modifier.combinedClickable(onClick = {}, onLongClick = onOpenDebug)
            } else {
                Modifier
            }
            Text(
                text = stringResource(R.string.today),
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
private fun TrackingPausedCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = ReclaimColors.Card,
        border = BorderStroke(1.dp, ReclaimColors.Line),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
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
    }
}
