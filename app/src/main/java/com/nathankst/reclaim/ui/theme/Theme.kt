package com.nathankst.reclaim.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ReclaimColorScheme = darkColorScheme(
    primary = ReclaimColors.Mint,
    onPrimary = ReclaimColors.Background,
    background = ReclaimColors.Background,
    onBackground = ReclaimColors.Text,
    surface = ReclaimColors.Card,
    onSurface = ReclaimColors.Text,
    surfaceVariant = ReclaimColors.Card,
    onSurfaceVariant = ReclaimColors.Muted,
    outline = ReclaimColors.Line,
    outlineVariant = ReclaimColors.Line,
)

/** Dark only for v1. */
@Composable
fun ReclaimTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ReclaimColorScheme,
        typography = ReclaimTypography,
        content = content,
    )
}
