package com.bitavida.app.ui.appointments

import com.bitavida.app.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class CareEventTypeLabelTest {
    private fun draft() = CareEventDraft(kind = CareEventKind.APPOINTMENT, title = "Consulta ficticia",
        purpose = CareEventPurpose.MEDICAL_CONSULTATION, status = CareEventStatus.CONFIRMED)

    @Test fun consultationLabelsFollowOnlyExplicitType() {
        assertEquals("Medicina familiar", careEventTypeLabel(draft().copy(consultationType = CareConsultationType.FAMILY_MEDICINE)))
        assertEquals("Medicina general", careEventTypeLabel(draft().copy(consultationType = CareConsultationType.GENERAL_MEDICINE)))
        assertEquals("Especialidad", careEventTypeLabel(draft().copy(consultationType = CareConsultationType.SPECIALIST)))
        assertEquals("Otra consulta", careEventTypeLabel(draft().copy(consultationType = CareConsultationType.OTHER)))
    }

    @Test fun legacySpecialtyCategoryDoesNotConvertAmbiguousConsultationIntoSpecialty() {
        val ambiguous = draft().copy(purpose = CareEventPurpose.LEGACY,
            category = CareEventCategory.SPECIALTY, specialty = "Cardiología ficticia")
        assertEquals("Consulta · tipo no especificado", careEventTypeLabel(ambiguous))
    }

    @Test fun studyAndProcedureKeepSeparateTypes() {
        assertEquals("Estudio", careEventTypeLabel(draft().copy(purpose = CareEventPurpose.STUDY)))
        assertEquals("Procedimiento", careEventTypeLabel(draft().copy(purpose = CareEventPurpose.PROCEDURE)))
    }
}
