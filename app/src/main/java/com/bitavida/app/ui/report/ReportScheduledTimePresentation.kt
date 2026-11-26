package com.bitavida.app.ui.report

import com.bitavida.app.domain.usecase.FollowUpReportRow
import com.bitavida.app.domain.usecase.ReportScheduledTimeMeaning
import com.bitavida.app.domain.model.MedicationIntakeOutcome
import com.bitavida.app.domain.model.MedicationProductForm
import java.time.format.DateTimeFormatter

/**
 * Reword only a persisted outcome whose non-oral form was resolved from the
 * linked historical occurrence. The underlying report row and counts stay unchanged.
 */
internal fun reportMedicationDisplayDetail(row: FollowUpReportRow): String {
    val form = row.medicationForm ?: return row.detail
    val outcome = row.medicationOutcome ?: return row.detail
    val first = row.detail.substringBefore(" · ")
    val expected = when (outcome) {
        MedicationIntakeOutcome.COMPLETED -> "Se tomó"
        MedicationIntakeOutcome.PARTIAL -> "Toma parcial"
        MedicationIntakeOutcome.OMITTED -> "No se tomó"
        MedicationIntakeOutcome.REFUSED -> "Se rechazó"
        MedicationIntakeOutcome.UNCONFIRMED -> "Sin confirmar"
    }
    if (first != expected) return row.detail
    val label = when (form) {
        MedicationProductForm.TOPICAL, MedicationProductForm.PATCH -> when (outcome) {
            MedicationIntakeOutcome.COMPLETED -> "Se aplicó"
            MedicationIntakeOutcome.PARTIAL -> "Aplicación parcial"
            MedicationIntakeOutcome.OMITTED -> "No se aplicó"
            else -> expected
        }
        MedicationProductForm.INHALER -> when (outcome) {
            MedicationIntakeOutcome.COMPLETED -> "Se usó"
            MedicationIntakeOutcome.PARTIAL -> "Uso parcial"
            MedicationIntakeOutcome.OMITTED -> "No se usó"
            else -> expected
        }
        MedicationProductForm.INJECTABLE -> when (outcome) {
            MedicationIntakeOutcome.COMPLETED -> "Se administró"
            MedicationIntakeOutcome.PARTIAL -> "Administración parcial"
            MedicationIntakeOutcome.OMITTED -> "No se administró"
            else -> expected
        }
        else -> expected
    }
    return label + row.detail.removePrefix(expected)
}

internal fun reportMedicationWasNotPerformed(row: FollowUpReportRow): Boolean =
    when (row.medicationOutcome) {
        MedicationIntakeOutcome.OMITTED, MedicationIntakeOutcome.REFUSED -> true
        null -> row.detail.startsWith("No se tomó") || row.detail.startsWith("Se rechazó")
        else -> false
    }

internal fun reportMedicationIsUnconfirmed(row: FollowUpReportRow): Boolean =
    row.medicationCountsAsUnconfirmed ?: when (row.medicationOutcome) {
        MedicationIntakeOutcome.UNCONFIRMED -> true
        null -> row.detail.startsWith("Sin confirmar")
        else -> false
    }

/** A contextual reminder is never presented as the clinical administration time. */
internal fun reportScheduledTimeLabel(row: FollowUpReportRow): String {
    val moment = row.scheduledAt ?: return "—"
    val formatted = moment.format(DateTimeFormatter.ofPattern(
        if (moment.toLocalDate() == row.occurredAt.toLocalDate()) "HH:mm" else "dd/MM/yyyy HH:mm"
    ))
    val kind = when (row.scheduledTimeMeaning) {
        ReportScheduledTimeMeaning.EXPECTED -> "Prevista"
        ReportScheduledTimeMeaning.ACTIVITY_NOTICE -> "Aviso de actividad"
        ReportScheduledTimeMeaning.UNKNOWN -> "Tipo no verificable"
    }
    return "$kind\n$formatted"
}
