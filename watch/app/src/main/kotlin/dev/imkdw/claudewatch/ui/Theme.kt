package dev.imkdw.claudewatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val claudeColors = ColorScheme(
    primary = Color(0xFFD97757),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3A2A22),
    onPrimaryContainer = Color.White,
)

@Composable
fun ClaudeWatchTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = claudeColors, content = content)
