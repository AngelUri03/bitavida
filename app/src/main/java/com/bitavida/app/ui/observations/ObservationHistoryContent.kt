package com.bitavida.app.ui.observations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.usecase.*
import com.bitavida.app.ui.components.LocalBitavidaDateTimePickerController
import com.bitavida.app.ui.measurements.*
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val historyLocale = Locale.forLanguageTag("es-MX")
private val historyDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(historyLocale)
private val historyTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(historyLocale)

/** One date-controlled timeline over the existing active records and their exact occurrences. */
@Composable
internal fun ObservationHistoryContent(
    state: ObservationsUiState,
    motionAllowed: Boolean,
    onAction: (ObservationsAction) -> Unit,
    now: LocalDateTime = LocalDateTime.now(),
) {
    val date = state.selectedDate
    val today = now.toLocalDate()
    val monday = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val picker = LocalBitavidaDateTimePickerController.current
    val days = (0L..6L).map { index ->
        val day = monday.plusDays(index)
        val records = state.snapshot.records.filter { it.occurredOn == day &&
            (state.focusedObservationId == null || it.observationId == state.focusedObservationId) &&
            (state.category == null || ObservationCatalog.get(it.observationId).category == state.category) }
        val pending = projectObservationOccurrences(day, state.snapshot.followUps, state.snapshot.records, now)
            .count { it.record == null && it.state == ObservationOccurrenceState.UNCONFIRMED &&
                (state.focusedObservationId == null || it.observationId == state.focusedObservationId) &&
                (state.category == null || ObservationCatalog.get(it.observationId).category == state.category) }
        BitavidaCalendarDay(day, recordedCount = records.count { it.result != ObservationResult.NOT_PERFORMED },
            unconfirmedCount = pending, notPerformedCount = records.count { it.result == ObservationResult.NOT_PERFORMED })
    }
    ObservationWeekCalendar(date, days, today, motionAllowed) {
        onAction(ObservationsAction.Date(it))
    }
    MeasurementButton(
        "Elegir fecha",
        { picker.showDate(date) { onAction(ObservationsAction.Date(it)) } },
        Modifier.fillMaxWidth().testTag("observation_choose_date"),
        style = MeasurementButtonStyle.OUTLINED,
        icon = R.drawable.ic_v2_calendar,
    )
    if (state.focusedObservationId == null) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            GlucoseCaptureChip("Todas", state.category == null, true, motionAllowed,
                { onAction(ObservationsAction.Filter(null)) })
            ObservationCategory.entries.forEach { category ->
                GlucoseCaptureChip(category.shortTitle, state.category == category, true, motionAllowed,
                    { onAction(ObservationsAction.Filter(category)) })
            }
        }
    } else {
        SectionTitle(ObservationCatalog.get(state.focusedObservationId).name, small = true)
    }
    ObservationHistoryChange(
        change = listOf(date, state.category, state.focusedObservationId),
        motionAllowed = motionAllowed,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            SectionTitle(date.format(historyDate), small = true)
            SecondaryText("Sin registros no significa ausencia de síntomas.")
            val timeline = projectObservationTimeline(
                date, state.category, state.snapshot, now,
                observationId = state.focusedObservationId,
            )
            if (timeline.isEmpty()) {
                InformationCard(
                    if (date > today) "Fecha futura" else "Sin observaciones registradas",
                    if (date > today) "Las próximas revisiones se consultan en Mi día."
                    else "No hay registros para esta fecha y filtro. Aquí aparecerá lo que la persona cuidadora confirme.",
                )
            }
            Column(Modifier.fillMaxWidth()) {
                timeline.forEachIndexed { index, entry ->
                    ObservationTimelineRow(
                        entry,
                        first = index == 0,
                        last = index == timeline.lastIndex,
                        corrected = entry.record?.let { record ->
                            state.snapshot.recordRevisions.any {
                                it.recordId == record.id && it.revisionNumber > 1
                            }
                        } == true,
                        onClick = entry.record?.let { record ->
                            { onAction(ObservationsAction.ViewRecord(record.id)) }
                        } ?: entry.occurrence?.let { occurrence ->
                            { onAction(ObservationsAction.OpenOccurrence(occurrence)) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ObservationTimelineRow(
    entry: ObservationTimelineEntry,
    first: Boolean,
    last: Boolean,
    corrected: Boolean,
    onClick: (() -> Unit)?,
) = BoxWithConstraints(Modifier.fillMaxWidth()) {
    val definition = ObservationCatalog.get(entry.observationId)
    val record = entry.record
    val large = LocalDensity.current.fontScale >= 1.5f || maxWidth < 330.dp
    val lineColor = MaterialTheme.colorScheme.outline
    val markerColor = MaterialTheme.colorScheme.primary
    val time = record?.let {
        if (it.timePrecision == ObservationTimePrecision.UNKNOWN) "Sin hora" else
            (if (it.timePrecision == ObservationTimePrecision.APPROXIMATE) "≈ " else "") +
                it.occurredAt?.format(historyTime).orEmpty()
    } ?: entry.occurrence?.scheduledAt?.toLocalTime()?.format(historyTime).orEmpty()
    Row(Modifier.fillMaxWidth().drawBehind {
        val x = (if (large) 6.dp else 72.dp + BitavidaSpacing.Small + 6.dp).toPx()
        val markerY = 24.dp.toPx()
        drawLine(lineColor, Offset(x, if (first) markerY else 0f),
            Offset(x, if (last) markerY else size.height), 1.dp.toPx())
    }.padding(bottom = BitavidaSpacing.Small)) {
        if (!large) {
            Surface(color = MaterialTheme.bitavidaColors.paleBlue, shape = MaterialTheme.shapes.small,
                modifier = Modifier.width(72.dp)) {
                Text(time, Modifier.padding(horizontal = 4.dp, vertical = BitavidaSpacing.Small),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center)
            }
            Spacer(Modifier.width(BitavidaSpacing.Small))
        }
        Canvas(Modifier.padding(top = 18.dp).size(12.dp).clearAndSetSemantics {}) {
            drawCircle(markerColor, 4.dp.toPx())
        }
        Spacer(Modifier.width(BitavidaSpacing.Small))
        val cardModifier = Modifier.weight(1f).heightIn(min = BitavidaSpacing.MinimumTouchTarget)
            .semantics(mergeDescendants = true) {}
            .testTag("observation_timeline_" + (record?.id ?: entry.occurrence?.id.orEmpty()))
        val content: @Composable () -> Unit = {
            Column(Modifier.padding(BitavidaSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall)) {
                if (large) Text(time, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small),
                    verticalAlignment = Alignment.Top) {
                    MeasurementIcon(observationIcon(definition.category))
                    Text(definition.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface)
                    if (onClick != null) MeasurementIcon(R.drawable.ic_v2_chevron, 16)
                }
                Text(record?.result?.label() ?: "Sin confirmar", style = MaterialTheme.typography.titleMedium,
                    color = if (record == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                if (record == null) {
                    SecondaryText("Revisión programada sin resultado registrado.")
                } else {
                    val summary = observationSummary(record)
                    if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    SecondaryText(if (record.occurrenceId == null) "Observación espontánea" else "Revisión programada")
                    if (corrected) Text("Corregida · conserva el original", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (record.localPhotoIds.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.ExtraSmall),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MeasurementIcon(R.drawable.ic_observation_photo, 16)
                            Text(
                                "Fotografía adjunta",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (onClick == null) {
            Surface(modifier = cardModifier, color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium, shadowElevation = 1.dp,
                border = BorderStroke(1.dp, lineColor.copy(alpha = .7f)), content = content)
        } else {
            Surface(onClick = onClick, modifier = cardModifier, color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium, shadowElevation = 1.dp,
                border = BorderStroke(1.dp, lineColor.copy(alpha = .7f)), content = content)
        }
    }
}
