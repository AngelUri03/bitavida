package com.bitavida.app.data.appointments

import com.bitavida.app.domain.model.*
import com.bitavida.app.domain.repository.*
import com.bitavida.app.domain.usecase.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CareEventConsultationPersistenceTest {
    private val now = LocalDateTime.of(2026, 11, 22, 10, 0)
    private val actor = CareEventActor("profile", "caregiver", "patient", true)

    @Test fun classificationRoundTripsAndFingerprintIncludesExplicitChanges() {
        val draft = CareEventDraft(CareEventKind.APPOINTMENT, "Consulta ficticia",
            status = CareEventStatus.CONFIRMED, scheduledAt = now.plusDays(1),
            purpose = CareEventPurpose.MEDICAL_CONSULTATION,
            consultationType = CareConsultationType.FAMILY_MEDICINE, isFollowUp = true)
        val event = CareEvent("event", "patient", draft, now, now, 1)
        assertEquals(event, event.toEntity(actor).toDomain())
        val command = CareEventCommand.Create("operation", "event", draft)
        assertNotEquals(careEventFingerprint(command), careEventFingerprint(command.copy(
            draft = draft.copy(isFollowUp = false))))
    }

    @Test fun historicalAmbiguityCanBeClarifiedWithoutChangingClinicalFacts() {
        val previous = CareEvent("event", "patient", CareEventDraft(CareEventKind.APPOINTMENT,
            "Consulta ficticia", status = CareEventStatus.COMPLETED, scheduledAt = now.minusDays(4),
            category = CareEventCategory.SPECIALTY), now.minusDays(5), now.minusDays(4), 1)
        val clarified = previous.draft.copy(purpose = CareEventPurpose.MEDICAL_CONSULTATION,
            consultationType = CareConsultationType.FAMILY_MEDICINE, isFollowUp = true)
        val result = applyCareEventCommand(listOf(previous), "patient",
            CareEventCommand.Revise("revision", "event", 1, clarified), now)
        assertEquals(2, result.revision)
        assertEquals(previous.draft.scheduledAt, result.draft.scheduledAt)
        assertEquals(CareConsultationType.UNSPECIFIED, previous.draft.consultationType)
        val altered = clarified.copy(scheduledAt = now.plusDays(2))
        val failure = runCatching { applyCareEventCommand(listOf(previous), "patient",
            CareEventCommand.Revise("other", "event", 1, altered), now) }.exceptionOrNull()
        assertEquals(CareEventProblem.HISTORICAL_READ_ONLY, (failure as CareEventValidationException).problem)
    }

    @Test fun nonConsultationCannotBecomeFollowUpByAccident() {
        val draft = CareEventDraft(CareEventKind.STUDY, "Estudio ficticio",
            status = CareEventStatus.SCHEDULED, scheduledAt = now.plusDays(1),
            purpose = CareEventPurpose.STUDY,
            consultationType = CareConsultationType.SPECIALIST, isFollowUp = true)
        val failure = runCatching { applyCareEventCommand(emptyList(), "patient",
            CareEventCommand.Create("operation", "study", draft), now) }.exceptionOrNull()
        assertEquals(CareEventProblem.INVALID_STATE, (failure as CareEventValidationException).problem)
    }
}
