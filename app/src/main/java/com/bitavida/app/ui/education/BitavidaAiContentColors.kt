package com.bitavida.app.ui.education

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The educational pages live over a transparent background, not a Material Surface.
 * Establish their semantic foreground without adding a layout or changing the app-wide theme.
 * Nested Material controls still provide their own onPrimary/onPrimaryContainer foregrounds.
 */
@Composable
internal fun BitavidaAiContentColors(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        content = content,
    )
}
