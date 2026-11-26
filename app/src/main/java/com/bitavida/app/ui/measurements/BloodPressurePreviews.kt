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
import com.bitavida.app.domain.model.BloodPressureEntryKind
import com.bitavida.app.domain.model.BloodPressureRecordMetadata
import com.bitavida.app.domain.model.BloodPressureRegistrationContext
import com.bitavida.app.domain.model.GlucoseContext
import com.bitavida.app.domain.model.GlucoseFollowUp
import com.bitavida.app.domain.model.GlucoseFollowUpChangeKind
import com.bitavida.app.domain.model.GlucoseFollowUpFrequency
import com.bitavida.app.domain.model.GlucoseFollowUpRevision
import com.bitavida.app.domain.model.GlucoseFollowUpSlot
import com.bitavida.app.domain.model.MeasurementKind
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.MeasurementValue
import com.bitavida.app.domain.model.toMeasurementFollowUp
import com.bitavida.app.domain.usecase.BloodPressureDraft
import com.bitavida.app.domain.usecase.projectMeasurementOverview
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.medical.CurrentMedicationBack
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Preview(name = "Presión detalle · claro", showBackground = true)
@Preview(
    name = "Presión detalle · oscuro",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(
    name = "Presión detalle · 320 dp",
    widthDp = 320,
    heightDp = 1000,
    showBackground = true,
)
@Preview(
    name = "Presión detalle · texto 200 %",
    widthDp = 320,
    heightDp = 1200,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun BloodPressureDetailPreview() {
    val today = LocalDate.of(2026, 11, 17)
    val records = pressurePreviewRecords(today)
    val followUp = pressurePreviewFollowUp(today)
    val summary = projectMeasurementOverview(
        records,
        today,
        listOf(
            followUp.toMeasurementFollowUp(
                MeasurementKind.BLOOD_PRESSURE,
            ),
        ),
    ).primary.first {
        it.kind == MeasurementKind.BLOOD_PRESSURE
    }
    BitavidaTheme {
        BitavidaFluidBackground {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(BitavidaSpacing.ScreenHorizontal),
                verticalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.Default),
            ) {
                CurrentMedicationBack(
                    onBack = {},
                    motionAllowed = false,
                    enabled = true,
                    label = "Mediciones",
                )
                MeasurementHeader(
                    title = "Presión arterial",
                    person = "Persona de ejemplo",
                    showSubtitle = false,
                    kind = MeasurementKind.BLOOD_PRESSURE,
                    followUp = summary.followUp,
                )
                BloodPressureDetailContent(
                    summary = summary,
                    records = records,
                    today = today,
                    onRegister = {},
                    onFollowUpAction = { _, _ -> },
                    onHelp = {},
                    onHistory = {},
                    followUp = followUp,
                )
            }
        }
    }
}

@Preview(name = "Historial presión · claro", showBackground = true)
@Preview(
    name = "Historial presión · oscuro",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(
    name = "Historial presión · 320 dp",
    widthDp = 320,
    heightDp = 1000,
    showBackground = true,
)
@Preview(
    name = "Historial presión · texto 200 %",
    widthDp = 320,
    heightDp = 1200,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun BloodPressureHistoryPreview() {
    val today = LocalDate.of(2026, 11, 17)
    BitavidaTheme {
        BitavidaFluidBackground {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(BitavidaSpacing.ScreenHorizontal),
                verticalArrangement =
                    Arrangement.spacedBy(BitavidaSpacing.Default),
            ) {
                CurrentMedicationBack(
                    onBack = {},
                    motionAllowed = false,
                    enabled = true,
                    label = "Presión arterial",
                )
                MeasurementHeader(
                    title = "Historial de presión arterial",
                    person = "Persona de ejemplo",
                    showSubtitle = false,
                )
                BloodPressureHistoryContent(
                    records = pressurePreviewRecords(today),
                    today = today,
                    followUp = pressurePreviewFollowUp(today),
                    decisions = emptyList(),
                    now = today.atTime(16, 0),
                    onRecord = {},
                    onOccurrence = {},
                )
            }
        }
    }
}

@Preview(name = "Registro presión · claro", showBackground = true)
@Preview(
    name = "Registro presión · oscuro",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(
    name = "Registro presión · 320 dp",
    widthDp = 320,
    heightDp = 900,
    showBackground = true,
)
@Preview(
    name = "Registro presión · texto 200 %",
    widthDp = 320,
    heightDp = 1100,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun BloodPressureRegistrationPreview() {
    val draft = BloodPressureDraft(
        id = "pressure-draft-preview",
        context = BloodPressureRegistrationContext.Independent,
        actualAt = LocalDateTime.of(2026, 11, 17, 11, 0),
        systolic = "128",
        diastolic = "78",
        moment = GlucoseContext.RESTING,
        note = "",
    )
    BitavidaTheme {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(BitavidaSpacing.ScreenHorizontal),
            verticalArrangement =
                Arrangement.spacedBy(BitavidaSpacing.Default),
        ) {
            BloodPressureRegistrationContent(
                state = BloodPressureUiState(
                    loading = false,
                    draft = draft,
                ),
                onAction = {},
                onSelectDate = {},
                onSelectTime = {},
                motionEnabledOverride = false,
            )
        }
    }
}

@Preview(name = "Seguimiento presión · claro", showBackground = true)
@Preview(
    name = "Seguimiento presión · oscuro",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(
    name = "Seguimiento presión · 320 dp",
    widthDp = 320,
    heightDp = 1000,
    showBackground = true,
)
@Preview(
    name = "Seguimiento presión · texto 200 %",
    widthDp = 320,
    heightDp = 1200,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun BloodPressureFollowUpPreview() {
    BitavidaTheme {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(BitavidaSpacing.ScreenHorizontal),
        ) {
            GlucoseFollowUpContent(
                existing = null,
                person = "Persona de ejemplo",
                today = LocalDate.of(2026, 11, 17),
                saving = false,
                error = null,
                changeKind = GlucoseFollowUpChangeKind.CREATED,
                onBack = {},
                measurementTitle = "Presión arterial",
                measurementIcon =
                    com.bitavida.app.R.drawable
                        .ic_measurement_pressure,
                onSave = {},
            )
        }
    }
}

private fun pressurePreviewRecords(
    today: LocalDate,
): List<MeasurementRecord> = listOf(
    pressurePreviewRecord(
        "pressure-one",
        today.minusDays(2).atTime(8, 10),
        "124",
        "76",
        GlucoseContext.FASTING,
    ),
    pressurePreviewRecord(
        "pressure-two",
        today.minusDays(1).atTime(13, 25),
        "132",
        "80",
        GlucoseContext.AFTER_MEAL,
    ),
    pressurePreviewRecord(
        "pressure-three",
        today.atTime(8, 5),
        "128",
        "78",
        GlucoseContext.FASTING,
    ),
    pressurePreviewRecord(
        "pressure-four",
        today.atTime(14, 20),
        "136",
        "82",
        GlucoseContext.AFTER_MEAL,
    ),
)

private fun pressurePreviewRecord(
    id: String,
    time: LocalDateTime,
    systolic: String,
    diastolic: String,
    context: GlucoseContext,
) = MeasurementRecord(
    id = id,
    value = MeasurementValue.BloodPressure(
        systolic,
        diastolic,
    ),
    occurredAt = time,
    capturedAt = time,
    bloodPressureMetadata = BloodPressureRecordMetadata(
        entryKind = BloodPressureEntryKind.INDEPENDENT,
        actualContext = context,
        note = "Dato ficticio para preview",
    ),
)

private fun pressurePreviewFollowUp(
    today: LocalDate,
): GlucoseFollowUp {
    val created = today.minusDays(3).atStartOfDay()
    return GlucoseFollowUp(
        id = "pressure-follow-up-preview",
        personUnderCareId = "person-preview",
        createdAt = created,
        createdByActorId = "caregiver-preview",
        revisions = listOf(
            GlucoseFollowUpRevision(
                id = "pressure-follow-up-revision-preview",
                followUpId = "pressure-follow-up-preview",
                revisionNumber = 1,
                indication =
                    "Indicación médica ficticia transcrita.",
                frequency = GlucoseFollowUpFrequency.DAILY,
                startsOn = created.toLocalDate(),
                effectiveFrom = created.toLocalDate(),
                slots = listOf(
                    GlucoseFollowUpSlot(
                        id = "morning-preview",
                        time = LocalTime.of(8, 0),
                        suggestedContext = GlucoseContext.FASTING,
                        reminderEnabled = true,
                        reminderTime = LocalTime.of(7, 55),
                    ),
                    GlucoseFollowUpSlot(
                        id = "afternoon-preview",
                        time = LocalTime.of(14, 0),
                        suggestedContext =
                            GlucoseContext.AFTER_MEAL,
                        reminderEnabled = false,
                    ),
                ),
                remindersEnabled = true,
                changeKind =
                    GlucoseFollowUpChangeKind.CREATED,
                capturedAt = created,
                authorActorId = "caregiver-preview",
            ),
        ),
    )
}
