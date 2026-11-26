package com.bitavida.app.ui.measurements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.usecase.MeasurementDay
import com.bitavida.app.domain.usecase.MeasurementDayState
import com.bitavida.app.domain.usecase.MeasurementOverview
import com.bitavida.app.domain.usecase.MeasurementSummary
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.bitavidaFluidInteraction
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDate

@Composable
internal fun MeasurementsHome(
    overview: MeasurementOverview,
    today: LocalDate,
    onDetail: (MeasurementKind) -> Unit,
    onHistory: () -> Unit,
    onOther: () -> Unit,
    onHelp: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
        SectionTitle("Mis mediciones")
        (overview.primary + overview.secondary).forEach { summary ->
            MeasurementOverviewCard(summary, onDetail)
        }
    }
    WeeklyMeasurements(overview.week, today, onHistory)

    MeasurementGuideActions("Agregar mediciones", onOther, onHelp)
}

/** One approved action group shared by Mediciones and Medicamentos. */
@Composable
internal fun MeasurementGuideActions(
    addLabel: String,
    onAdd: () -> Unit,
    onHelp: () -> Unit,
    helpLabel: String = "Guías y ayuda",
    thirdLabel: String? = null,
    onThird: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            MeasurementManagementRow(
                R.drawable.ic_v2_plus, addLabel, true, onAdd,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = BitavidaSpacing.Medium),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            )
            MeasurementManagementRow(
                R.drawable.ic_v2_file, helpLabel, true, onHelp,
            )
            if (thirdLabel != null && onThird != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = BitavidaSpacing.Medium),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                )
                MeasurementManagementRow(R.drawable.ic_bitavida_ai, thirdLabel, true, onThird)
            }
        }
    }
}

@Composable
private fun MeasurementOverviewCard(
    summary: MeasurementSummary,
    onDetail: (MeasurementKind) -> Unit,
) {
    val featured = summary.kind == MeasurementKind.CAPILLARY_GLUCOSE
    MeasurementPanel(
        modifier = Modifier.testTag("measurement_${summary.kind.name}"),
        color = if (featured) MaterialTheme.bitavidaColors.mint else MaterialTheme.colorScheme.surface,
        onClick = { onDetail(summary.kind) },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
            verticalAlignment = Alignment.Top,
        ) {
            MeasurementBadge(summary.kind)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = summary.kind.label(),
                        modifier = Modifier.weight(1f),
                        style = if (featured) MaterialTheme.typography.titleMedium
                            else MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    MeasurementIcon(R.drawable.ic_v2_chevron, 16)
                }
                summary.latest?.let { record ->
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        MeasurementReading(record, prominent = featured, compact = !featured)
                        record.complement()?.let { context ->
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = if (featured) MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    text = context,
                                    modifier = Modifier.padding(
                                        horizontal = BitavidaSpacing.Small,
                                        vertical = BitavidaSpacing.ExtraSmall,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MeasurementIcon(R.drawable.ic_v2_clock, 16)
                        SecondaryText(record.occurredAt.format(measurementDateTime))
                    }
                } ?: Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                    Text(
                        text = "Sin registros",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SecondaryText("Estás comenzando. Verás la última medición y la periodicidad configurada.")
                }
                summary.followUp?.let { followUp ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MeasurementIcon(R.drawable.ic_v2_calendar, 16)
                        SecondaryText(followUp.displaySummary())
                    }
                }
            }
        }
    }
}

internal fun com.bitavida.app.domain.model.MeasurementFollowUp.displaySummary(): String =
    if (suspended) "Seguimiento suspendido · $summary" else summary

@Composable
private fun WeeklyMeasurements(
    days: List<MeasurementDay>,
    today: LocalDate,
    onHistory: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val motion = rememberBitavidaMotionAllowed(null)
    val largeText = LocalDensity.current.fontScale >= 1.5f
    MeasurementPanel {
        val heading: @Composable () -> Unit = {
            Column {
                SectionTitle("Seguimiento semanal", small = true)
                SecondaryText(
                    days.first().date.format(measurementShortDate) + " – " +
                        days.last().date.format(measurementShortDate),
                )
            }
        }
        val history: @Composable () -> Unit = {
            TextButton(
                onClick = onHistory,
                interactionSource = source,
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = BitavidaSpacing.MinimumTouchTarget)
                    .testTag("measurement_week_history")
                    .bitavidaFluidInteraction(
                        source, true, motion, shape = MaterialTheme.shapes.medium, restingElevationDp = 0f,
                    ),
            ) {
                Text("Ver historial", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(BitavidaSpacing.ExtraSmall))
                MeasurementIcon(R.drawable.ic_v2_chevron, 16)
            }
        }
        if (largeText) {
            Column {
                heading()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { history() }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) { heading() }
                history()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            days.forEach { day ->
                Column(
                    modifier = Modifier.weight(1f).clearAndSetSemantics {
                        contentDescription = day.date.format(measurementFullDate) + ", " +
                            day.description() + if (day.date == today) ", hoy" else ""
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                ) {
                    Text(
                        text = day.date.format(measurementWeekday).replace(".", "")
                            .let { if (largeText) it.take(1) else it }
                            .replaceFirstChar { it.titlecase(measurementLocale) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MeasurementDayIndicator(day.state)
                }
            }
        }
        FlowRow(
            modifier = Modifier.padding(top = BitavidaSpacing.ExtraSmall),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            MeasurementDayLegend(MeasurementDayState.WITH_RECORDS, "Con registro")
            MeasurementDayLegend(MeasurementDayState.WITHOUT_RECORDS, "Sin registro")
            MeasurementDayLegend(MeasurementDayState.FUTURE, "Futuro")
        }
    }
}

@Composable
private fun MeasurementDayIndicator(state: MeasurementDayState, diameter: Dp = 26.dp) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val empty = MaterialTheme.colorScheme.outline
    val future = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(diameter).clearAndSetSemantics {}) {
        val radius = size.minDimension / 2 - 1.dp.toPx()
        when (state) {
            MeasurementDayState.WITH_RECORDS -> {
                drawCircle(primary, radius)
                val checkWidth = size.minDimension * 0.075f
                drawLine(
                    color = onPrimary,
                    start = Offset(size.width * 0.28f, size.height * 0.51f),
                    end = Offset(size.width * 0.44f, size.height * 0.66f),
                    strokeWidth = checkWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = onPrimary,
                    start = Offset(size.width * 0.44f, size.height * 0.66f),
                    end = Offset(size.width * 0.74f, size.height * 0.34f),
                    strokeWidth = checkWidth,
                    cap = StrokeCap.Round,
                )
            }
            MeasurementDayState.WITHOUT_RECORDS -> drawCircle(empty, radius)
            MeasurementDayState.FUTURE -> drawCircle(
                color = future,
                radius = radius,
                style = Stroke(width = 1.25.dp.toPx()),
            )
        }
    }
}

@Composable
private fun MeasurementDayLegend(state: MeasurementDayState, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
    ) {
        MeasurementDayIndicator(state, diameter = 14.dp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// These representations remain exclusive to the existing detail calendar.
internal fun MeasurementDay.symbol(): String = when (state) {
    MeasurementDayState.WITH_RECORDS -> recordCount.toString()
    MeasurementDayState.WITHOUT_RECORDS -> "—"
    MeasurementDayState.FUTURE -> "·"
}

internal fun MeasurementDay.description(): String = when (state) {
    MeasurementDayState.WITH_RECORDS -> if (recordCount == 1) "1 registro" else "$recordCount registros"
    MeasurementDayState.WITHOUT_RECORDS -> "sin registros"
    MeasurementDayState.FUTURE -> "día futuro"
}
