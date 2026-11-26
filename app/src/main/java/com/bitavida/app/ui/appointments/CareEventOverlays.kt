package com.bitavida.app.ui.appointments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.bitavida.app.domain.model.*

internal fun categoryLabel(category: CareEventCategory) = when (category) {
    CareEventCategory.GENERAL -> "Consulta general"
    CareEventCategory.SPECIALTY -> "Consulta de especialidad"
    CareEventCategory.LABORATORY -> "Estudio de laboratorio"
    CareEventCategory.IMAGING -> "Estudio de gabinete"
    CareEventCategory.ADMINISTRATIVE -> "Trámite de salud"
}

/** Labels only explicitly recorded consultation types; old ambiguous categories remain unspecified. */
internal fun careEventTypeLabel(draft: CareEventDraft): String = when (draft.purpose) {
    CareEventPurpose.STUDY -> "Estudio"
    CareEventPurpose.PROCEDURE -> "Procedimiento"
    CareEventPurpose.ADMINISTRATIVE -> "Trámite"
    CareEventPurpose.OTHER -> "Otra cita"
    CareEventPurpose.MEDICAL_CONSULTATION, CareEventPurpose.LEGACY -> when {
        draft.purpose == CareEventPurpose.LEGACY && (draft.kind == CareEventKind.STUDY ||
            draft.category in setOf(CareEventCategory.LABORATORY, CareEventCategory.IMAGING)) -> "Estudio"
        draft.purpose == CareEventPurpose.LEGACY && draft.category == CareEventCategory.ADMINISTRATIVE -> "Trámite"
        else -> when (draft.consultationType) {
            CareConsultationType.FAMILY_MEDICINE -> "Medicina familiar"
            CareConsultationType.GENERAL_MEDICINE -> "Medicina general"
            CareConsultationType.SPECIALIST -> "Especialidad"
            CareConsultationType.OTHER -> "Otra consulta"
            CareConsultationType.UNSPECIFIED -> "Consulta · tipo no especificado"
        }
    }
}

@Composable
fun CareEventOverlays(
    state: CareEventsState,
    onAction: (CareEventsAction) -> Unit,
    attachments: @Composable (CareEvent) -> Unit,
) {
    if (state.consentRequested) {
        AlertDialog(
            onDismissRequest = { onAction(CareEventsAction.DeclineConsent) },
            title = { Text("Guardar información de salud") },
            text = {
                Column {
                    Text("Bitavida guardará esta información localmente y de forma cifrada para organizar el cuidado.")
                    Row {
                        Checkbox(checked = state.consentAccepted, onCheckedChange = { onAction(CareEventsAction.ChangeConsent(it)) })
                        Text("Acepto guardar esta información sanitaria en este dispositivo.")
                    }
                    Text("Esta aceptación no declara representación legal ni comparte datos por Internet.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { onAction(CareEventsAction.ConfirmConsent) }, enabled = state.consentAccepted && !state.saving) { Text("Aceptar y guardar") } },
            dismissButton = { TextButton(onClick = { onAction(CareEventsAction.DeclineConsent) }, enabled = !state.saving) { Text("No guardar") } },
        )
    }
}

internal fun findPossibleDuplicate(draft: CareEventDraft, events: List<CareEvent>, editingId: String?): CareEvent? {
    val title = draft.title.trim().replace(Regex("\\s+"), " ")
    if (title.isEmpty()) return null
    val date = draft.scheduledAt?.toLocalDate() ?: draft.scheduledDate
    return events.firstOrNull { event ->
        event.id != editingId && event.draft.category == draft.category &&
            event.draft.title.trim().replace(Regex("\\s+"), " ").equals(title, ignoreCase = true) &&
            (event.draft.scheduledAt?.toLocalDate() ?: event.draft.scheduledDate) == date
    }
}
