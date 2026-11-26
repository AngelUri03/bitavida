package com.bitavida.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Closed product taxonomy; user input never creates catalog entries. */
enum class ObservationCategory(val title: String, val shortTitle: String, val description: String) {
    FEET("Pies y piel", "Pies y piel", "Cambios visibles en pies, piernas y piel."),
    PHYSICAL("Malestares físicos", "Malestares", "Sensaciones o molestias diferentes a lo habitual."),
    DIGESTION("Sed, orina y digestión", "Sed y orina", "Cambios al beber, ir al baño o digerir alimentos."),
    VISION("Visión y sensibilidad", "Visión", "Cambios al ver o sentir partes del cuerpo."),
    WELLBEING("Bienestar y comportamiento", "Bienestar", "Apetito, descanso, memoria y estado de ánimo."),
    MOBILITY("Movilidad y seguridad", "Movilidad", "Desplazamientos, equilibrio, caídas y golpes."),
    GENERAL("Infecciones y salud general", "Salud general", "Cambios observados en piel, boca o bienestar general."),
    OTHER("Otros", "Otros", "Algo diferente que no aparece en las opciones anteriores."),
}

enum class ObservationFieldType { SINGLE_CHOICE, MULTIPLE_CHOICE, TEXT }

data class ObservationField(
    val id: String,
    val label: String,
    val type: ObservationFieldType,
    val options: List<String> = emptyList(),
    val required: Boolean = false,
)

data class ObservationDefinition(
    val id: String,
    val category: ObservationCategory,
    val name: String,
    val description: String,
    val context: String,
    val synonyms: List<String>,
    val fields: List<ObservationField>,
    val photoAllowed: Boolean = false,
    val eventOnly: Boolean = false,
    val supportsFollowUp: Boolean = true,
    val schemaVersion: Int = 1,
)

enum class ObservationTimePrecision { EXACT, APPROXIMATE, UNKNOWN }
enum class ObservationResult { CHANGES, NO_CHANGES, NOT_PERFORMED, EVENT }

/** Caregiver declaration. Real time and scheduled time are deliberately independent. */
data class ObservationRecord(
    val id: String,
    val observationId: String,
    val personUnderCareId: String,
    val occurredOn: LocalDate,
    val occurredAt: LocalTime?,
    val timePrecision: ObservationTimePrecision,
    val result: ObservationResult,
    val answers: Map<String, List<String>>,
    val description: String,
    val localPhotoIds: List<String> = emptyList(),
    val followUpId: String? = null,
    val occurrenceId: String? = null,
    val scheduledAt: LocalDateTime? = null,
    val createdAt: LocalDateTime,
    val actorId: String,
    val schemaVersion: Int = 1,
)

/** Reuses the shared explicit recurrence, time-window and append-only revision model. */
data class ObservationFollowUp(
    val observationId: String,
    val schedule: GlucoseFollowUp,
)

/** Immutable original (version 1) or explicit correction. Snapshot keeps the stable fact identity. */
data class ObservationRecordRevision(
    val id: String,
    val recordId: String,
    val revisionNumber: Int,
    val snapshot: ObservationRecord,
    val reason: String?,
    val correctedAt: LocalDateTime,
    val actorId: String,
)

data class ObservationSnapshot(
    val records: List<ObservationRecord> = emptyList(),
    val followUps: List<ObservationFollowUp> = emptyList(),
    val recordRevisions: List<ObservationRecordRevision> = emptyList(),
)

enum class ObservationOccurrenceState { RECORDED, UNCONFIRMED, UPCOMING }

data class ObservationOccurrence(
    val id: String,
    val observationId: String,
    val followUpId: String,
    val followUpRevisionId: String,
    val slotId: String,
    val scheduledAt: LocalDateTime,
    val windowEndAt: LocalDateTime? = null,
    val reminderAt: LocalDateTime? = null,
    val record: ObservationRecord? = null,
    val state: ObservationOccurrenceState = ObservationOccurrenceState.UPCOMING,
)

data class ObservationTimelineEntry(
    val observationId: String,
    val record: ObservationRecord? = null,
    val occurrence: ObservationOccurrence? = null,
)
