package com.bitavida.app.ui.measurements

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseEntryKind
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseFollowUpChangeKind
import com.bitavida.app.domain.model.GlucoseFollowUpFrequency
import com.bitavida.app.domain.model.GlucoseFollowUpRevision
import com.bitavida.app.domain.model.GlucoseFollowUpSlot
import com.bitavida.app.domain.model.GlucoseOccurrenceDecision
import com.bitavida.app.domain.model.GlucoseOccurrenceDecisionKind
import com.bitavida.app.domain.model.GlucoseRecordMetadata
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.occurrencesOn
import com.bitavida.app.domain.usecase.MeasurementScheduleHistory
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

private val calendarPreviewDate = LocalDate.of(2026, 11, 18)

@Preview(name = "Calendario · claro", widthDp = 411, heightDp = 1000, showBackground = true)
@Preview(name = "Calendario · oscuro", widthDp = 411, heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Calendario · 320 dp", widthDp = 320, heightDp = 900, showBackground = true)
@Preview(name = "Calendario · texto 200 %", widthDp = 320, heightDp = 1100, fontScale = 2f)
@Composable
private fun MeasurementCalendarPreview() {
    CalendarPreviewContent(empty = false)
}

@Preview(name = "Calendario · vacío y movimiento reducido", widthDp = 320, heightDp = 800)
@Composable
private fun MeasurementCalendarEmptyPreview() {
    CalendarPreviewContent(empty = true)
}

@Composable
private fun CalendarPreviewContent(empty: Boolean) {
    val date = calendarPreviewDate
    val followUp = calendarPreviewFollowUp()
    val occurrences = followUp.occurrencesOn(date)
    val decision = GlucoseOccurrenceDecision(
        id = "preview-decision",
        followUpId = followUp.id,
        followUpRevisionId = followUp.currentRevision.id,
        occurrenceId = occurrences[1].id,
        scheduledAt = occurrences[1].scheduledAt,
        kind = GlucoseOccurrenceDecisionKind.NOT_PERFORMED,
        reason = null,
        capturedAt = date.atTime(12, 30),
        actorId = "preview-caregiver",
    )
    // All examples are fictitious and used only by previews.
    val records = listOf(
        MeasurementRecord(
            "preview-glucose", MeasurementValue.Glucose("112", GlucoseContext.FASTING),
            date.atTime(8, 5), date.atTime(8, 10),
            glucoseMetadata = GlucoseRecordMetadata(
                GlucoseEntryKind.SCHEDULED, followUp.id, occurrences[0].id, occurrences[0].scheduledAt,
            ),
        ),
        MeasurementRecord(
            "preview-additional", MeasurementValue.Glucose("118", GlucoseContext.AFTER_MEAL),
            date.atTime(15, 45), date.atTime(15, 45),
        ),
        MeasurementRecord(
            "preview-pressure", MeasurementValue.BloodPressure("120", "80"),
            date.atTime(16, 30), date.atTime(16, 30),
        ),
        MeasurementRecord(
            "preview-weight", MeasurementValue.Weight(BigDecimal("74.2")),
            date.atTime(10, 10), date.atTime(10, 10),
        ),
    )
    BitavidaTheme {
        val scroll = rememberScrollState()
        BitavidaFluidBackground(scrollPixels = { scroll.value.toFloat() }, motionAllowed = false) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll)
                    .padding(BitavidaSpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Default),
            ) {
                MeasurementHeader("Calendario e historial", "Persona de ejemplo", false)
                MeasurementCalendarContent(
                    records = if (empty) emptyList() else records,
                    schedules = if (empty) emptyList() else listOf(
                        MeasurementScheduleHistory(MeasurementKind.CAPILLARY_GLUCOSE, followUp, listOf(decision)),
                    ),
                    today = date,
                    now = date.atTime(18, 0),
                    motionAllowed = false,
                )
            }
        }
    }
}

private fun calendarPreviewFollowUp(): GlucoseFollowUp {
    val date = calendarPreviewDate
    return GlucoseFollowUp(
        id = "preview-follow-up",
        personUnderCareId = "preview-person",
        createdAt = date.atStartOfDay(),
        createdByActorId = "preview-caregiver",
        revisions = listOf(
            GlucoseFollowUpRevision(
                id = "preview-revision",
                followUpId = "preview-follow-up",
                revisionNumber = 1,
                indication = "Indicación ficticia transcrita.",
                frequency = GlucoseFollowUpFrequency.DAILY,
                startsOn = date,
                effectiveFrom = date,
                slots = listOf(8, 12, 16).map { hour ->
                    GlucoseFollowUpSlot(
                        id = "preview-slot-$hour",
                        time = LocalTime.of(hour, 0),
                        suggestedContext = GlucoseContext.FASTING,
                        reminderEnabled = false,
                    )
                },
                remindersEnabled = false,
                changeKind = GlucoseFollowUpChangeKind.CREATED,
                capturedAt = date.atStartOfDay(),
                authorActorId = "preview-caregiver",
            ),
        ),
    )
}
