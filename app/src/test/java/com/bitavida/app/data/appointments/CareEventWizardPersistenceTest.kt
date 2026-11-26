package com.bitavida.app.data.appointments

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class CareEventWizardPersistenceTest {
    private val actor = CareEventActor("profile", "caregiver", "person", true)
    private val now = LocalDateTime.of(2026, 11, 21, 10, 0)
    private val draft = CareEventDraft(
        kind = CareEventKind.APPOINTMENT, title = "Consulta ficticia",
        status = CareEventStatus.CONFIRMED, scheduledAt = now, reminderEnabled = true,
        purpose = CareEventPurpose.PROCEDURE, timeMode = CareEventTimeMode.EXACT,
        duration = CareEventDuration.ONE_HOUR, modality = CareEventModality.VIDEO,
        professional = "Profesional ficticio", reference = "Referencia ficticia",
        contactInformation = "Acceso ficticio", otherPreparation = "Nota ficticia",
        preparations = setOf(CareEventPreparation.OTHER, CareEventPreparation.FASTING),
        reminderOffsetsMinutes = setOf(1440, 30), showInToday = false,
    )

    @Test fun allWizardFieldsRoundTripAlongsideExistingIdentity() {
        val original = CareEvent("event", "person", draft, now, now, 2)
        assertEquals(original, original.toEntity(actor).toDomain())
    }

    @Test fun fingerprintIsStableForSetOrderAndChangesForWizardFields() {
        val a = CareEventCommand.Create("operation", "event", draft)
        val reordered = draft.copy(reminderOffsetsMinutes = linkedSetOf(30, 1440),
            preparations = linkedSetOf(CareEventPreparation.FASTING, CareEventPreparation.OTHER))
        assertEquals(careEventFingerprint(a), careEventFingerprint(a.copy(draft = reordered)))
        assertNotEquals(careEventFingerprint(a), careEventFingerprint(a.copy(draft = draft.copy(showInToday = true))))
    }

    @Test fun legacyDefaultsSurviveRoundTrip() {
        val oldDraft = CareEventDraft(CareEventKind.APPOINTMENT, "Cita ficticia",
            status = CareEventStatus.CONFIRMED, scheduledAt = now, reminderEnabled = true)
        val original = CareEvent("old", "person", oldDraft, now, now, 1)
        assertEquals(original, original.toEntity(actor).toDomain())
        assertTrue(original.toEntity(actor).reminderOffsetsMinutes.isEmpty())
    }
}
