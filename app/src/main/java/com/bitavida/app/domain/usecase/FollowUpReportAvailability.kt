package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.CareEvent
import com.bitavida.app.domain.model.CareReminder
import com.bitavida.app.domain.model.ClinicalContentMode
import com.bitavida.app.domain.model.MedicalCareOverview
import com.bitavida.app.domain.model.MeasurementRecord
import com.bitavida.app.domain.model.ObservationRecord
import com.bitavida.app.domain.model.ObservationFollowUp
import com.bitavida.app.domain.model.Note

/** Distinguishes the absence of all stored information from an empty selected period. */
enum class FollowUpAvailability {
    NO_INFORMATION,
    EMPTY_PERIOD,
    AVAILABLE,
}

/**
 * Inspects already loaded, authorized snapshots without modifying them. Measurements are
 * scoped by their repository; the other records are filtered by the selected patient.
 * A future appointment or pending reminder remains a record, never an accomplished activity.
 */
fun followUpAvailability(
    personUnderCareId: String,
    overview: MedicalCareOverview,
    measurements: List<MeasurementRecord>,
    observations: List<ObservationRecord>,
    reminders: List<CareReminder>,
    events: List<CareEvent>,
    report: FollowUpReport,
    measurementSchedules: List<ReportMeasurementSchedule> = emptyList(),
    observationFollowUps: List<ObservationFollowUp> = emptyList(),
    notes: List<Note> = emptyList(),
): FollowUpAvailability {
    val hasGlobalInformation =
        overview.medicationPlans.any {
            it.personUnderCareId == personUnderCareId &&
                it.contentMode == ClinicalContentMode.REAL_CLINICAL_DATA
        } ||
        overview.indications.any {
            it.personUnderCareId == personUnderCareId &&
                it.contentMode == ClinicalContentMode.REAL_CLINICAL_DATA
        } ||
        measurements.isNotEmpty() ||
        measurementSchedules.any { it.followUp != null } ||
        observationFollowUps.isNotEmpty() ||
        observations.any { it.personUnderCareId == personUnderCareId } ||
        reminders.any { it.personUnderCareId == personUnderCareId } ||
        notes.any { it.personUnderCareId == personUnderCareId && it.deletedAt == null } ||
        events.any { it.personUnderCareId == personUnderCareId }

    // A declared non-performance is a reportable fact, but an unconfirmed or future
    // scheduled occurrence alone does not become a performed activity or a useful report.
    val hasPeriodInformation = report.rows.any {
        it.provenance != "Seguimiento programado" || it.detail == "No realizada, declarado"
    }
    return when {
        hasPeriodInformation -> FollowUpAvailability.AVAILABLE
        hasGlobalInformation -> FollowUpAvailability.EMPTY_PERIOD
        else -> FollowUpAvailability.NO_INFORMATION
    }
}
