package com.bitavida.app.ui.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.bitavida.app.ui.theme.KodchasanFamily

/** Local scope: custom Mi día surfaces and the existing capture sheets share the approved family. */
@Composable
internal fun TodayTypography(content: @Composable () -> Unit) {
    val source = MaterialTheme.typography
    val typography = remember(source) { todayTypography(source) }
    MaterialTheme(typography = typography) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
    }
}

/** Unspecified Material roles borrow an existing Bitavida style instead of falling back to Roboto. */
internal fun todayTypography(source: Typography): Typography = source.copy(
    displayLarge = source.displaySmall.copy(fontFamily = KodchasanFamily),
    displayMedium = source.displaySmall.copy(fontFamily = KodchasanFamily),
    displaySmall = source.displaySmall.copy(fontFamily = KodchasanFamily),
    headlineLarge = source.headlineLarge.copy(fontFamily = KodchasanFamily),
    headlineMedium = source.headlineSmall.copy(fontFamily = KodchasanFamily),
    headlineSmall = source.headlineSmall.copy(fontFamily = KodchasanFamily),
    titleLarge = source.titleLarge.copy(fontFamily = KodchasanFamily),
    titleMedium = source.titleMedium.copy(fontFamily = KodchasanFamily),
    titleSmall = source.titleSmall.copy(fontFamily = KodchasanFamily),
    bodyLarge = source.bodyLarge.copy(fontFamily = KodchasanFamily),
    bodyMedium = source.bodyMedium.copy(fontFamily = KodchasanFamily),
    bodySmall = source.bodySmall.copy(fontFamily = KodchasanFamily),
    labelLarge = source.labelLarge.copy(fontFamily = KodchasanFamily),
    labelMedium = source.labelMedium.copy(fontFamily = KodchasanFamily),
    labelSmall = source.labelSmall.copy(fontFamily = KodchasanFamily),
)
