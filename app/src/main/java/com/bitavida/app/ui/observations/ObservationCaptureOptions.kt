package com.bitavida.app.ui.observations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bitavida.app.ui.measurements.GlucoseCaptureChip
import com.bitavida.app.ui.theme.BitavidaSpacing

/**
 * Short answers form balanced two/three-column grids. Long or numerous answers use one
 * horizontal rail, avoiding uneven FlowRow endings and truncated labels.
 */
@Composable
internal fun ObservationChoiceOptions(
    options: List<String>,
    selected: (String) -> Boolean,
    enabled: Boolean,
    motionAllowed: Boolean,
    onSelect: (String) -> Unit,
) {
    if (options.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val columns = observationChoiceColumns(
            options,
            maxWidth.value,
            fontScale,
        )
        if (columns == 0) {
            Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                LazyRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                    contentPadding = PaddingValues(horizontal = 1.dp),
                ) {
                    itemsIndexed(options, key = { index, option -> "$index:$option" }) { _, option ->
                        GlucoseCaptureChip(
                            option, selected(option), enabled, motionAllowed,
                            { onSelect(option) },
                            Modifier.widthIn(min = 152.dp)
                                .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
                        )
                    }
                }
                Text(
                    "Desliza →",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                options.chunked(columns).forEach { rowOptions ->
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                    ) {
                        rowOptions.forEach { option ->
                            GlucoseCaptureChip(
                                option, selected(option), enabled, motionAllowed,
                                { onSelect(option) },
                                Modifier.weight(1f).fillMaxHeight()
                                    .heightIn(min = BitavidaSpacing.MinimumTouchTarget),
                            )
                        }
                        repeat(columns - rowOptions.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

internal fun observationChoiceColumns(
    options: List<String>,
    availableWidthDp: Float,
    fontScale: Float,
): Int {
    if (options.size == 1) return 1
    val normalizedWidth = availableWidthDp / fontScale.coerceAtLeast(1f)
    val longest = options.maxOfOrNull(String::length) ?: 0
    return when (options.size) {
        2 -> if (normalizedWidth >= 210f && longest <= 20) 2 else 0
        3 -> if (normalizedWidth >= 315f && longest <= 12) 3 else 0
        4 -> if (normalizedWidth >= 250f && longest <= 16) 2 else 0
        6 -> if (normalizedWidth >= 330f && longest <= 12) 3 else 0
        else -> 0
    }
}
