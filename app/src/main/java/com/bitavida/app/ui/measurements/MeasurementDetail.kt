package com.bitavida.app.ui.measurements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseScheduledOccurrence
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.WeightTarget
import com.bitavida.app.domain.usecase.MeasurementHistoryWeek
import com.bitavida.app.domain.usecase.MeasurementSummary
import com.bitavida.app.domain.usecase.projectMeasurementHistoryWeek
import com.bitavida.app.domain.usecase.recordsForMeasurementKind
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.components.LocalBitavidaDateTimePickerController
import com.bitavida.app.ui.components.bitavidaFluidInteraction
import com.bitavida.app.ui.theme.BitavidaSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Requests a real operation; the host owns authorization, confirmation and versioned storage. */
enum class MeasurementFollowUpAction { CONFIGURE, CORRECT, CHANGE_INDICATIONS, VIEW_CHANGES, SUSPEND }

@Composable
internal fun MeasurementDetail(
    summary: MeasurementSummary,
    records: List<MeasurementRecord>,
    today: LocalDate,
    onRegister: ((MeasurementKind) -> Unit)?,
    onFollowUpAction: ((MeasurementKind, MeasurementFollowUpAction) -> Unit)?,
    onHelp: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    glucoseFollowUp: GlucoseFollowUp? = null,
    glucoseDecisions: List<GlucoseOccurrenceDecision> = emptyList(),
    onRegisterOccurrence: ((GlucoseScheduledOccurrence) -> Unit)? = null,
    onNotPerformed: ((GlucoseScheduledOccurrence, String?) -> Unit)? = null,
    bloodPressureFollowUp: GlucoseFollowUp? = null,
    bloodPressureDecisions: List<GlucoseOccurrenceDecision> = emptyList(),
    onRegisterBloodPressureOccurrence:
        ((GlucoseScheduledOccurrence) -> Unit)? = null,
    onBloodPressureNotPerformed:
        ((GlucoseScheduledOccurrence, String?) -> Unit)? = null,
    weightFollowUp: GlucoseFollowUp? = null,
    weightDecisions: List<GlucoseOccurrenceDecision> = emptyList(),
    onRegisterWeightOccurrence:
        ((GlucoseScheduledOccurrence) -> Unit)? = null,
    onWeightNotPerformed:
        ((GlucoseScheduledOccurrence, String?) -> Unit)? = null,
    weightTarget: WeightTarget? = null,
    onConfigureWeightTarget: (() -> Unit)? = null,
) {
    val isolatedRecords = remember(records, summary.kind) {
        recordsForMeasurementKind(records, summary.kind)
    }
    if (summary.kind == MeasurementKind.CAPILLARY_GLUCOSE) {
        GlucoseDetailContent(
            summary,
            isolatedRecords,
            today,
            onRegister,
            onFollowUpAction,
            onHelp,
            onHistory,
            glucoseFollowUp,
            glucoseDecisions,
            onRegisterOccurrence,
            onNotPerformed,
        )
        return
    }
    if (summary.kind == MeasurementKind.BLOOD_PRESSURE) {
        BloodPressureDetailContent(
            summary = summary,
            records = isolatedRecords,
            today = today,
            onRegister = onRegister,
            onFollowUpAction = onFollowUpAction,
            onHelp = onHelp,
            onHistory = onHistory,
            followUp = bloodPressureFollowUp,
            decisions = bloodPressureDecisions,
            onRegisterOccurrence =
                onRegisterBloodPressureOccurrence,
            onNotPerformed = onBloodPressureNotPerformed,
        )
        return
    }
    if (summary.kind == MeasurementKind.WEIGHT) {
        WeightDetailContent(
            summary = summary,
            records = isolatedRecords,
            today = today,
            onRegister = onRegister,
            onFollowUpAction = onFollowUpAction,
            onHelp = null,
            onHistory = onHistory,
            followUp = weightFollowUp,
            decisions = weightDecisions,
            onRegisterOccurrence = onRegisterWeightOccurrence,
            onNotPerformed = onWeightNotPerformed,
            target = weightTarget,
            onConfigureTarget = onConfigureWeightTarget,
        )
        return
    }
    var selectedDateString by rememberSaveable(summary.kind) { mutableStateOf(today.toString()) }
    val selectedDate = LocalDate.parse(selectedDateString)
    val week = projectMeasurementHistoryWeek(
        isolatedRecords,
        summary.kind,
        selectedDate,
        today,
    )

    MeasurementPanel(modifier = Modifier.testTag("measurement_latest")) {
        SectionTitle("Última medición", small = true)
        summary.latest?.let { record ->
            if (record.value is MeasurementValue.BloodPressure) SecondaryText("Sistólica / diastólica")
            MeasurementReading(record, prominent = true)
            record.complement()?.let { SecondaryText(it) }
            SecondaryText(record.occurredAt.format(measurementDateTime))
        } ?: Text(
            text = "Sin registros",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MeasurementButton(
            label = if (summary.latest == null) "Registrar primera medición" else "Registrar medición",
            onClick = { onRegister?.invoke(summary.kind) },
            modifier = Modifier.fillMaxWidth().testTag("measurement_detail_register"),
            enabled = onRegister != null,
            icon = R.drawable.ic_v2_plus,
            accessibleLabel = if (onRegister == null)
                "Registrar ${summary.kind.label().lowercase(measurementLocale)}, captura no disponible"
                else "Registrar ${summary.kind.label().lowercase(measurementLocale)}",
        )
        if (onRegister == null) SecondaryText("La captura de ${summary.kind.label().lowercase(measurementLocale)} aún no está disponible.")
    }

    MeasurementCalendarHistory(
        week = week,
        selectedDate = selectedDate,
        today = today,
        onSelect = { selectedDateString = it.toString() },
    )
    MeasurementWeekChart(week, summary.kind)
    MeasurementFollowUpOperations(summary, onFollowUpAction)
}

@Composable
internal fun MeasurementCalendarHistory(
    week: MeasurementHistoryWeek,
    selectedDate: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val dateTimePicker = LocalBitavidaDateTimePickerController.current
    val month = selectedDate.format(DateTimeFormatter.ofPattern("MMMM uuuu", measurementLocale))
        .replaceFirstChar { it.titlecase(measurementLocale) }
    val recordsOnDay = week.recordsOn(selectedDate)
    MeasurementPanel(modifier = Modifier.testTag("measurement_calendar_history")) {
        SectionTitle("Calendario e historial", small = true)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MeasurementWeekArrow(previous = true) { onSelect(selectedDate.minusWeeks(1)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MeasurementButton(
                    label = month,
                    onClick = {
                        dateTimePicker.showDate(
                            selectedDate = selectedDate,
                            onConfirm = onSelect,
                        )
                    },
                    style = MeasurementButtonStyle.TEXT,
                    accessibleLabel = "Elegir fecha, $month",
                )
            }
            MeasurementWeekArrow(previous = false) { onSelect(selectedDate.plusWeeks(1)) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
        ) {
            Column(Modifier.weight(1f)) {
                SecondaryText(
                    week.start.format(measurementShortDate) + " – " +
                        week.start.plusDays(6).format(measurementShortDate),
                )
            }
            MeasurementButton("Hoy", { onSelect(today) }, style = MeasurementButtonStyle.OUTLINED)
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cellWidth = if (LocalDensity.current.fontScale >= 1.5f) 76.dp
                else (maxWidth / 7).coerceAtLeast(BitavidaSpacing.MinimumTouchTarget)
            val stripScroll = rememberScrollState()
            val density = LocalDensity.current
            val viewport = maxWidth
            LaunchedEffect(week.start, selectedDate, cellWidth, viewport) {
                val selectedIndex = selectedDate.dayOfWeek.value - 1
                val target = with(density) {
                    (cellWidth.toPx() * (selectedIndex + 0.5f) - viewport.toPx() / 2).roundToInt()
                }
                stripScroll.scrollTo(target.coerceAtLeast(0))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(stripScroll)) {
                week.days.forEach { day ->
                    val isSelected = day.date == selectedDate
                    val foreground = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
                    Surface(
                        onClick = { onSelect(day.date) },
                        modifier = Modifier.width(cellWidth)
                            .heightIn(min = BitavidaSpacing.MinimumTouchTarget)
                            .semantics {
                                selected = isSelected
                                contentDescription = day.date.format(measurementFullDate) +
                                    ", " + day.description() + if (day.date == today) ", hoy" else ""
                            },
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = BitavidaSpacing.Small),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                        ) {
                            Text(
                                day.date.format(measurementWeekday).replace(".", ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = foreground,
                            )
                            Text(
                                day.date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                color = foreground,
                            )
                            Text(day.symbol(), style = MaterialTheme.typography.labelSmall, color = foreground)
                        }
                    }
                }
            }
        }
        SecondaryText("N.º de registros  /  — Sin registro  /  · Futuro")
        ActionDivider()
        Text(
            selectedDate.format(measurementFullDate).replaceFirstChar { it.titlecase(measurementLocale) },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (recordsOnDay.isEmpty()) {
            SecondaryText(if (selectedDate > today) "Día futuro · Sin registros" else "Sin registros en esta fecha.")
        } else {
            recordsOnDay.forEachIndexed { index, record ->
                if (index > 0) ActionDivider()
                MeasurementHistoryRow(record)
            }
            SecondaryText("Registros declarados por la persona cuidadora")
        }
    }
}

@Composable
internal fun MeasurementHistoryRow(record: MeasurementRecord) {
    val time = record.occurredAt.format(DateTimeFormatter.ofPattern("HH:mm", measurementLocale))
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
            Text(time, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            MeasurementReading(record, compact = true)
            record.complement()?.let { SecondaryText(it) }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = BitavidaSpacing.ExtraSmall),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
        ) {
            Text(
                time,
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                MeasurementReading(record, compact = true)
                record.complement()?.let { SecondaryText(it) }
            }
        }
    }
}

@Composable
private fun MeasurementWeekArrow(previous: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(BitavidaSpacing.MinimumTouchTarget).semantics {
            contentDescription = if (previous) "Semana anterior" else "Semana siguiente"
        },
    ) {
        Image(
            painterResource(R.drawable.ic_v2_chevron),
            contentDescription = null,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = if (previous) 180f else 0f },
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun MeasurementFollowUpOperations(
    summary: MeasurementSummary,
    onAction: ((MeasurementKind, MeasurementFollowUpAction) -> Unit)?,
) {
    val followUp = summary.followUp
    Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
        SectionTitle("Seguimiento", small = true)
        if (followUp == null) {
            MeasurementButton(
                label = "Configurar seguimiento",
                onClick = { onAction?.invoke(summary.kind, MeasurementFollowUpAction.CONFIGURE) },
                modifier = Modifier.fillMaxWidth(),
                enabled = onAction != null,
                style = MeasurementButtonStyle.OUTLINED,
                icon = R.drawable.ic_v2_clock,
            )
            if (onAction == null) SecondaryText("La configuración del seguimiento aún no está disponible.")
        } else {
            SecondaryText(followUp.displaySummary())
            // Same group, separators and row proportions as the approved medication operations.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column {
                    MeasurementManagementRow(
                        R.drawable.ic_v2_edit, "Corregir datos", onAction != null,
                    ) { onAction?.invoke(summary.kind, MeasurementFollowUpAction.CORRECT) }
                    HorizontalDivider(
                        Modifier.padding(horizontal = BitavidaSpacing.Medium),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    )
                    MeasurementManagementRow(
                        R.drawable.ic_v2_stethoscope, "Cambiar indicaciones médicas", onAction != null,
                    ) { onAction?.invoke(summary.kind, MeasurementFollowUpAction.CHANGE_INDICATIONS) }
                    HorizontalDivider(
                        Modifier.padding(horizontal = BitavidaSpacing.Medium),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    )
                    MeasurementManagementRow(
                        R.drawable.ic_v2_history, "Historial de cambios", onAction != null,
                    ) { onAction?.invoke(summary.kind, MeasurementFollowUpAction.VIEW_CHANGES) }
                }
            }
            MeasurementButton(
                label = if (followUp.suspended) "Seguimiento suspendido" else "Suspender",
                onClick = { onAction?.invoke(summary.kind, MeasurementFollowUpAction.SUSPEND) },
                modifier = Modifier.fillMaxWidth(),
                enabled = onAction != null && !followUp.suspended,
                icon = R.drawable.ic_v2_pause,
                style = MeasurementButtonStyle.OUTLINED,
            )
            if (onAction == null) SecondaryText("La edición del seguimiento aún no está disponible.")
            if (followUp.suspended) SecondaryText("La programación está detenida. Los registros se conservan.")
        }
    }
}

@Composable
internal fun MeasurementManagementRow(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val motion = rememberBitavidaMotionAllowed(null)
    val foreground = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .bitavidaFluidInteraction(
                source, enabled, motion, shape = MaterialTheme.shapes.medium, restingElevationDp = 0f,
            )
            .clickable(
                interactionSource = source,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = BitavidaSpacing.Medium, vertical = BitavidaSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeasurementIcon(icon, tint = if (enabled) MaterialTheme.colorScheme.primary else foreground)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = foreground)
        MeasurementIcon(R.drawable.ic_v2_chevron, 16, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
