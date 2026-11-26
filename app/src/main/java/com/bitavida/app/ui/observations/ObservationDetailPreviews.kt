package com.bitavida.app.ui.observations

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bitavida.app.R
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.usecase.projectObservationOccurrences
import com.bitavida.app.ui.measurements.MeasurementIcon
import com.bitavida.app.ui.medical.CurrentMedicationBack
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import com.bitavida.app.ui.theme.bitavidaColors
import java.time.LocalDate
import java.time.LocalTime

@Preview(name = "Claro", widthDp = 390, heightDp = 1100)
@Preview(name = "Oscuro", widthDp = 390, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Compacto", widthDp = 320, heightDp = 1100)
@Preview(name = "Texto 200 %", widthDp = 320, heightDp = 1500, fontScale = 2f)
@Target(AnnotationTarget.FUNCTION)
private annotation class ObservationDetailPreviewModes

private val observationPreviewDay = LocalDate.of(2026, 11, 18)
private val observationPreviewNow = observationPreviewDay.atTime(15, 0)

/** All identifiers, declarations and photographic placeholders in this file are fictitious. */
private fun observationPreviewFollowUp(suspended: Boolean = false): ObservationFollowUp {
    val revision = GlucoseFollowUpRevision(
        id = "fictitious-follow-up-v1", followUpId = "fictitious-follow-up", revisionNumber = 1,
        indication = "Indicación ficticia: revisar ambos pies en los horarios transcritos.",
        frequency = GlucoseFollowUpFrequency.DAILY,
        startsOn = observationPreviewDay, effectiveFrom = observationPreviewDay,
        slots = listOf(8, 10, 12, 14, 16).map { hour ->
            GlucoseFollowUpSlot(id = "fictitious-slot-" + hour, time = LocalTime.of(hour, 0),
                suggestedContext = GlucoseContext.UNSPECIFIED, reminderEnabled = true,
                reminderTime = LocalTime.of(hour, 0))
        },
        remindersEnabled = true, changeKind = GlucoseFollowUpChangeKind.CREATED,
        capturedAt = observationPreviewDay.minusDays(1).atTime(16, 0),
        authorActorId = "fictitious-caregiver",
    )
    val revisions = if (!suspended) listOf(revision) else listOf(revision, revision.copy(
        id = "fictitious-follow-up-v2", revisionNumber = 2,
        changeKind = GlucoseFollowUpChangeKind.REMOVED, capturedAt = observationPreviewDay.atTime(14, 30),
        changeReason = "Suspensión ficticia confirmada",
    ))
    return ObservationFollowUp("OBS-001", GlucoseFollowUp(
        id = "fictitious-follow-up", personUnderCareId = "fictitious-person",
        createdAt = revision.capturedAt, createdByActorId = "fictitious-caregiver", revisions = revisions,
    ))
}

private fun observationPreviewSnapshot(suspended: Boolean = false): ObservationSnapshot {
    val followUp = observationPreviewFollowUp(suspended)
    val events = projectObservationOccurrences(observationPreviewDay, listOf(followUp), emptyList(), observationPreviewNow)
    val records = events.take(3).mapIndexed { index, event ->
        ObservationRecord(
            id = "fictitious-record-" + index, observationId = "OBS-001", personUnderCareId = "fictitious-person",
            occurredOn = observationPreviewDay, occurredAt = event.scheduledAt.toLocalTime().plusMinutes(5),
            timePrecision = ObservationTimePrecision.EXACT,
            result = when (index) {
                0 -> ObservationResult.CHANGES
                1 -> ObservationResult.NO_CHANGES
                else -> ObservationResult.NOT_PERFORMED
            },
            answers = when (index) {
                0 -> mapOf("foot" to listOf("Izquierdo"), "changes" to listOf("Ampolla"))
                1 -> mapOf("foot" to listOf("Ambos"))
                else -> emptyMap()
            },
            description = if (index == 0) "Se observó una ampolla en el pie izquierdo." else "",
            localPhotoIds = if (index == 0) listOf("fictitious-photo") else emptyList(),
            followUpId = event.followUpId, occurrenceId = event.id, scheduledAt = event.scheduledAt,
            createdAt = event.scheduledAt.plusMinutes(10), actorId = "fictitious-caregiver",
        )
    }
    return ObservationSnapshot(records, listOf(followUp))
}

@Composable
private fun ObservationDetailPreviewFrame(content: @Composable () -> Unit) {
    BitavidaTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(BitavidaSpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) {
                Spacer(Modifier.height(BitavidaSpacing.Small))
                CurrentMedicationBack({}, false, enabled = true, label = "Observaciones")
                content()
                Spacer(Modifier.height(BitavidaSpacing.Medium))
            }
        }
    }
}

@Composable
private fun ObservationFictitiousPhoto() {
    Surface(Modifier.fillMaxWidth().height(88.dp), color = MaterialTheme.bitavidaColors.mint,
        shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(BitavidaSpacing.Small), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BitavidaSpacing.Small)) {
            MeasurementIcon(R.drawable.ic_v2_foot)
            Text("Fotografía ficticia", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@ObservationDetailPreviewModes
@Composable
private fun ObservationEmptyDetailPreview() {
    ObservationDetailPreviewFrame {
        ObservationDetailContent(ObservationsUiState(loading = false, selectedObservationId = "OBS-001"),
            false, {}, {}, observationPreviewNow)
    }
}

@ObservationDetailPreviewModes
@Composable
private fun ObservationActiveDetailPreview() {
    ObservationDetailPreviewFrame {
        ObservationDetailContent(ObservationsUiState(loading = false, selectedObservationId = "OBS-001",
            snapshot = observationPreviewSnapshot()), false, {}, {}, observationPreviewNow)
    }
}

@ObservationDetailPreviewModes
@Composable
private fun ObservationSuspendedDetailPreview() {
    ObservationDetailPreviewFrame {
        ObservationDetailContent(ObservationsUiState(loading = false, selectedObservationId = "OBS-001",
            snapshot = observationPreviewSnapshot(suspended = true)), false, {}, {}, observationPreviewNow)
    }
}

@ObservationDetailPreviewModes
@Composable
private fun ObservationCorrectedRecordPreview() {
    val snapshot = observationPreviewSnapshot()
    val original = snapshot.records.first()
    val corrected = original.copy(description = "Al revisar de nuevo la captura, la ampolla estaba en el talón izquierdo.")
    val revisions = listOf(
        ObservationRecordRevision(original.id, original.id, 1, original, null, original.createdAt, original.actorId),
        ObservationRecordRevision("fictitious-correction", original.id, 2, corrected,
            "Se precisó la zona observada.", observationPreviewDay.atTime(9, 0), original.actorId),
    )
    ObservationDetailPreviewFrame {
        ObservationRecordDetailContent(ObservationsUiState(loading = false, selectedObservationId = "OBS-001",
            selectedRecordId = original.id, snapshot = snapshot.copy(
                records = snapshot.records.map { if (it.id == original.id) corrected else it },
                recordRevisions = revisions)), false, {}, photoContent = { ObservationFictitiousPhoto() })
    }
}

@ObservationDetailPreviewModes
@Composable
private fun ObservationFocusedHistoryPreview() {
    val date = observationPreviewDay.minusDays(2)
    val record = ObservationRecord(
        id = "fictitious-swelling", observationId = "OBS-004", personUnderCareId = "fictitious-person",
        occurredOn = date, occurredAt = LocalTime.of(10, 30), timePrecision = ObservationTimePrecision.EXACT,
        result = ObservationResult.CHANGES, answers = mapOf("zone" to listOf("Pies"), "side" to listOf("Derecho")),
        description = "Se observó hinchazón en el pie derecho.",
        createdAt = date.atTime(10, 35), actorId = "fictitious-caregiver",
    )
    ObservationDetailPreviewFrame {
        ObservationHistoryContent(ObservationsUiState(loading = false, page = ObservationsPage.HISTORY,
            selectedDate = date, focusedObservationId = "OBS-004", selectedObservationId = "OBS-004",
            snapshot = ObservationSnapshot(records = listOf(record, record.copy(id = "fictitious-other-type",
                observationId = "OBS-002", description = "Esta observación de otro tipo queda fuera del filtro.")))),
            false, {}, observationPreviewNow)
    }
}

@ObservationDetailPreviewModes
@Composable
private fun ObservationVisualHistoryPreview() {
    ObservationDetailPreviewFrame {
        ObservationHistoryContent(ObservationsUiState(loading = false, selectedDate = observationPreviewDay,
            snapshot = observationPreviewSnapshot()), false, {}, observationPreviewNow)
    }
}
