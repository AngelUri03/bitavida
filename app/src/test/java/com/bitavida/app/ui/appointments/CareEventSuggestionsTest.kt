package com.bitavida.app.ui.appointments

import com.bitavida.app.domain.model.CareEventPurpose
import org.junit.Assert.*
import org.junit.Test

class CareEventSuggestionsTest {
    @Test fun suggestionsIgnoreAccentsAndSupportPartialAndAliases() {
        assertEquals("Consulta con oftalmología",
            findAppointmentSuggestions("OFTALMO", appointmentTitles[CareEventPurpose.MEDICAL_CONSULTATION].orEmpty()).single().label)
        assertEquals("Hemoglobina glucosilada (HbA1c)",
            findAppointmentSuggestions("a1c", appointmentTitles[CareEventPurpose.STUDY].orEmpty()).single().label)
        assertEquals("Ginecología y obstetricia",
            findAppointmentSuggestions("ginecologia", appointmentSpecialties).single().label)
        assertTrue(findAppointmentSuggestions("", appointmentSpecialties).isEmpty())
    }

    @Test fun userCanTypeSomethingOutsideTheCatalog() {
        assertTrue(findAppointmentSuggestions("texto personal diferente", appointmentSpecialties).isEmpty())
    }

    @Test fun frequentOptionsFollowTheChosenPurposeWithoutPreselectingAnEvent() {
        val medical = appointmentTitles.getValue(CareEventPurpose.MEDICAL_CONSULTATION).map { it.label }
        val studies = appointmentTitles.getValue(CareEventPurpose.STUDY).map { it.label }
        val procedures = appointmentTitles.getValue(CareEventPurpose.PROCEDURE).map { it.label }
        val administrative = appointmentTitles.getValue(CareEventPurpose.ADMINISTRATIVE).map { it.label }
        assertTrue("Consulta de seguimiento de diabetes" in medical)
        assertTrue("Hemoglobina glucosilada (HbA1c)" in studies)
        assertTrue("Vacunación contra influenza" in procedures)
        assertTrue("Entrega de resultados" in administrative)
        assertTrue(appointmentTitles.getValue(CareEventPurpose.OTHER).isEmpty())
        assertFalse("Hemoglobina glucosilada (HbA1c)" in medical)
        assertFalse("Consulta de seguimiento de diabetes" in studies)
    }
}
