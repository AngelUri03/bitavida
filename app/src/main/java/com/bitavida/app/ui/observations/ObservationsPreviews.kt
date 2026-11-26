package com.bitavida.app.ui.observations

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.R
import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.usecase.*
import com.bitavida.app.ui.components.BitavidaDateTimePickerHost
import com.bitavida.app.ui.components.BitavidaFluidBackground
import com.bitavida.app.ui.medical.CareSectionHeader
import com.bitavida.app.ui.measurements.MeasurementPanel
import com.bitavida.app.ui.measurements.SectionTitle
import com.bitavida.app.ui.theme.BitavidaSpacing
import com.bitavida.app.ui.theme.BitavidaTheme
import java.time.LocalDate
import java.time.LocalTime

@Preview(name = "Claro", widthDp = 390, heightDp = 900)
@Preview(name = "Oscuro", widthDp = 390, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Compacto", widthDp = 320, heightDp = 900)
@Preview(name = "Texto 200 %", widthDp = 320, heightDp = 1200, fontScale = 2f)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
private annotation class ObservationPreviewVariants

private val previewDay = LocalDate.of(2026, 11, 18)
private val previewNow = previewDay.atTime(16, 0)

/** Fictitious fixtures only. Production receives the actual profile and encrypted records. */
private val previewRecord = ObservationRecord(
    id = "fictional-record", observationId = "OBS-001", personUnderCareId = "fictional-patient",
    occurredOn = previewDay, occurredAt = LocalTime.of(9, 15),
    timePrecision = ObservationTimePrecision.EXACT, result = ObservationResult.NO_CHANGES,
    answers = mapOf("foot" to listOf("Ambos")), description = "",
    localPhotoIds = listOf("fictional-photo"),
    createdAt = previewDay.atTime(9, 15), actorId = "fictional-caregiver",
)

private val previewFollowUp = ObservationFollowUp("OBS-001", GlucoseFollowUp(
    id = "fictional-follow-up", personUnderCareId = "fictional-patient",
    createdAt = previewDay.minusDays(1).atTime(12, 0), createdByActorId = "fictional-caregiver",
    revisions = listOf(GlucoseFollowUpRevision(
        id = "fictional-revision", followUpId = "fictional-follow-up", revisionNumber = 1,
        indication = "Indicación ficticia de revisión de pies", frequency = GlucoseFollowUpFrequency.DAILY,
        startsOn = previewDay, effectiveFrom = previewDay,
        slots = listOf(GlucoseFollowUpSlot("fictional-slot", time = LocalTime.of(9, 0),
            suggestedContext = GlucoseContext.UNSPECIFIED, reminderEnabled = true, reminderTime = LocalTime.of(9, 0))),
        remindersEnabled = true, changeKind = GlucoseFollowUpChangeKind.CREATED,
        capturedAt = previewDay.minusDays(1).atTime(12, 0), authorActorId = "fictional-caregiver",
    )),
))

@Composable
private fun ObservationPreviewFrame(content: @Composable () -> Unit) {
    BitavidaTheme {
        BitavidaDateTimePickerHost {
            val scroll = rememberScrollState()
            BitavidaFluidBackground(scrollPixels = { scroll.value.toFloat() }, motionAllowed = false) {
                Surface(color = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground) {
                    Column(Modifier.verticalScroll(scroll).padding(BitavidaSpacing.ScreenHorizontal),
                        verticalArrangement = Arrangement.spacedBy(BitavidaSpacing.Medium)) { content() }
                }
            }
        }
    }
}

@ObservationPreviewVariants
@Composable
private fun ObservationLibraryPreview() {
    ObservationPreviewFrame {
        CareSectionHeader("Observaciones", "Elena", R.drawable.ic_v2_stethoscope, {}, false,
            subtitle = "Cambios y señales del paciente")
        ObservationLibraryContent(ObservationsUiState(loading = false, expanded = emptySet()), false, {})
    }
}

@ObservationPreviewVariants
@Composable
private fun ObservationExpandedCategoryPreview() {
    ObservationPreviewFrame {
        ObservationLibraryContent(ObservationsUiState(loading = false,
            expanded = setOf(ObservationCategory.FEET), searchExpanded = true, query = "pies"), false, {})
    }
}

@ObservationPreviewVariants
@Composable
private fun ObservationEmptyDetailPreview() {
    ObservationPreviewFrame {
        ObservationDetailContent(ObservationsUiState(loading = false, selectedObservationId = "OBS-001"),
            false, {}, {}, previewNow)
    }
}

@ObservationPreviewVariants
@Composable
private fun ObservationDetailPreview() {
    ObservationPreviewFrame {
        ObservationDetailContent(ObservationsUiState(loading = false, selectedObservationId = "OBS-001",
            snapshot = ObservationSnapshot(listOf(previewRecord), listOf(previewFollowUp))),
            false, {}, {}, previewNow)
    }
}

@ObservationPreviewVariants
@Composable
private fun ObservationRecordDetailPreview() {
    ObservationPreviewFrame {
        ObservationRecordDetailContent(ObservationsUiState(loading = false,
            selectedObservationId = "OBS-001", selectedRecordId = previewRecord.id,
            snapshot = ObservationSnapshot(records = listOf(previewRecord))), false, {})
    }
}

@ObservationPreviewVariants
@Composable
private fun ObservationFormPreview() {
    ObservationPreviewFrame {
        ObservationFormContent(ObservationsUiState(loading = false, draft = previewRecord.copy(
            observationId = "OBS-006", result = ObservationResult.CHANGES,
            answers = mapOf("event" to listOf("Se sintió mareado"), "repeated" to listOf("Ocurrió una vez")))), false, {})
    }
}

@ObservationPreviewVariants
@Composable
private fun ObservationCaptureSheetPreview() {
    BitavidaTheme {
        BitavidaDateTimePickerHost {
            Surface(color = MaterialTheme.colorScheme.background) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    ObservationCaptureBody(
                        ObservationsUiState(loading = false, draft = previewRecord),
                        motionAllowed = false,
                        onAction = {},
                        photoContent = {
                            MeasurementPanel {
                                SectionTitle("Fotografía (opcional)", small = true)
                                ObservationPhotoSourceActions(true, {}, {})
                            }
                        },
                    )
                }
            }
        }
    }
}

@ObservationPreviewVariants
@Composable
private fun ObservationCorrectionPreview() {
    ObservationPreviewFrame {
        ObservationFormContent(ObservationsUiState(loading = false, draft = previewRecord,
            correctionOriginalId = previewRecord.id, correctionReason = "Detalle ficticio corregido"), false, {})
    }
}

@ObservationPreviewVariants
@Composable
private fun ObservationHistoryPreview() {
    ObservationPreviewFrame {
        ObservationHistoryContent(ObservationsUiState(loading = false, selectedDate = previewDay,
            snapshot = ObservationSnapshot(records = listOf(previewRecord))), false, {}, now = previewNow)
    }
}

@ObservationPreviewVariants
@Composable
private fun ObservationHistoryPastDatePreview() {
    val selected = previewDay.minusDays(2)
    ObservationPreviewFrame {
        ObservationHistoryContent(
            ObservationsUiState(
                loading = false,
                selectedDate = selected,
                focusedObservationId = "OBS-001",
                snapshot = ObservationSnapshot(records = listOf(previewRecord.copy(occurredOn = selected))),
            ),
            false,
            {},
            now = previewNow,
        )
    }
}

@ObservationPreviewVariants
@Composable
private fun ObservationGuidePreview() {
    ObservationPreviewFrame {
        ObservationGuideContent(
            ObservationsUiState(
                loading = false,
                page = ObservationsPage.GUIDE,
                selectedObservationId = "OBS-001",
                draft = previewRecord.copy(result = ObservationResult.CHANGES),
            ),
        )
    }
}

@ObservationPreviewVariants
@Composable
private fun ObservationPendingOccurrencePreview() {
    val snapshot = ObservationSnapshot(followUps = listOf(previewFollowUp))
    val occurrence = projectObservationOccurrences(
        previewDay,
        snapshot.followUps,
        snapshot.records,
        previewNow,
    ).single()
    ObservationPreviewFrame {
        ObservationOccurrenceDetailContent(
            state = ObservationsUiState(
                loading = false,
                page = ObservationsPage.OCCURRENCE_DETAIL,
                selectedObservationId = occurrence.observationId,
                selectedOccurrence = occurrence,
                snapshot = snapshot,
            ),
            onAction = {},
            onRegister = {},
        )
    }
}

@Composable
private fun ObservationScheduleStepPreview(step: Int) {
    ObservationPreviewFrame {
        ObservationScheduleContent(ObservationsUiState(loading = false, scheduleStep = step,
            scheduleDraft = ObservationScheduleDraft("OBS-001",
                previewFollowUp.schedule.toDraft(GlucoseFollowUpChangeKind.CORRECTED, previewDay))),
            false, {}, {}, "Elena")
    }
}

@ObservationPreviewVariants
@Composable
private fun ObservationSchedulePreview() = ObservationScheduleStepPreview(0)

@ObservationPreviewVariants
@Composable
private fun ObservationFrequencyPreview() = ObservationScheduleStepPreview(1)

@ObservationPreviewVariants
@Composable
private fun ObservationSchedulesPreview() = ObservationScheduleStepPreview(2)

@ObservationPreviewVariants
@Composable
private fun ObservationRemindersPreview() = ObservationScheduleStepPreview(3)

@ObservationPreviewVariants
@Composable
private fun ObservationReviewPreview() = ObservationScheduleStepPreview(4)

@ObservationPreviewVariants
@Composable
private fun ObservationEmptyChangesPreview() {
    ObservationPreviewFrame {
        ObservationFollowUpHistory(ObservationsUiState(loading = false, historyObservationId = "OBS-001"))
    }
}
