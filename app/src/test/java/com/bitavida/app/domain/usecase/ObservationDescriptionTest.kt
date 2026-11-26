package com.bitavida.app.domain.usecase

import com.bitavida.app.domain.model.ObservationCatalog
import com.bitavida.app.domain.model.ObservationRecord
import com.bitavida.app.domain.model.ObservationResult
import com.bitavida.app.domain.model.ObservationTimePrecision
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ObservationDescriptionTest {
    private val now = LocalDateTime.of(2026, 11, 18, 15, 47)
    private fun record(id: Int, vararg answers: Pair<String, List<String>>) = ObservationRecord(
        id = "fictional-record", observationId = "OBS-" + id.toString().padStart(3, '0'),
        personUnderCareId = "fictional-person", occurredOn = now.toLocalDate(),
        occurredAt = LocalTime.of(15, 47), timePrecision = ObservationTimePrecision.EXACT,
        result = if (id == 22) ObservationResult.EVENT else ObservationResult.CHANGES,
        answers = mapOf(*answers), description = "", createdAt = now, actorId = "fictional-caregiver",
    )
    private fun answer(id: String, vararg values: String) = id to values.toList()

    @Test fun everyCatalogTypeHasSpecificNaturalWording() {
        val examples = listOf(
            record(1, answer("foot", "Izquierdo"), answer("changes", "Ampolla")) to "una ampolla en el pie izquierdo",
            record(2, answer("zone", "pie"), answer("lesion", "Ampolla")) to "una ampolla en el pie",
            record(3, answer("foot", "Ambos"), answer("change", "Grieta")) to "una grieta en ambos pies",
            record(4, answer("zone", "Pies"), answer("side", "Derecho")) to "hinchazón en el pie derecho",
            record(5, answer("zone", "pie"), answer("change", "Más fría")) to "piel más fría en el pie",
            record(6, answer("event", "Se sintió mareado")) to "La persona se sintió mareada",
            record(7, answer("symptom", "Temblor", "Sudor frío")) to "temblor y sudor frío",
            record(8, answer("change", "Menos fuerza")) to "La persona tuvo menos fuerza",
            record(9, answer("zone", "espalda")) to "dolor en la espalda",
            record(10, answer("change", "Más sed")) to "La persona tuvo más sed",
            record(11, answer("change", "Va más veces al baño")) to "La persona fue más veces al baño",
            record(12, answer("situation", "Escape de orina")) to "La persona presentó escapes de orina",
            record(13, answer("symptom", "Ardor")) to "La persona refirió ardor al orinar",
            record(14, answer("symptom", "Ambos")) to "La persona presentó náuseas y vómitos",
            record(15, answer("change", "Evacuaciones más líquidas")) to "La persona tuvo evacuaciones más líquidas",
            record(16, answer("change", "Ve borroso")) to "La persona refirió que veía borroso",
            record(17, answer("sensation", "Hormigueo"), answer("zone", "manos"),
                answer("side", "Ambos")) to "hormigueo en ambas manos",
            record(18, answer("change", "Come menos")) to "La persona comió menos",
            record(19, answer("change", "Duerme menos")) to "La persona durmió menos",
            record(20, answer("event", "Olvidó algo importante")) to "La persona olvidó algo importante",
            record(21, answer("change", "Más triste")) to "La persona se mostró más triste",
            record(22, answer("event", "Se cayó"), answer("place", "patio ficticio")) to "La persona se cayó",
            record(23, answer("difficulty", "Le costó caminar")) to "La persona tuvo dificultad para caminar",
            record(24, answer("symptom", "Escalofríos")) to "La persona tuvo escalofríos",
            record(25, answer("zone", "brazo"), answer("change", "Enrojecimiento")) to "enrojecimiento en el brazo",
            record(26, answer("zone", "Encías"), answer("change", "Sangrado")) to "sangrado en las encías",
            record(27, answer("zone", "espalda")) to "El cambio se observó en la espalda",
        )
        assertEquals(ObservationCatalog.all.map { it.id }, examples.map { it.first.observationId })
        examples.forEach { (input, expected) ->
            val proposed = buildObservationDescription(input)
            assertTrue("${input.observationId}: $proposed", proposed.contains(expected))
            assertFalse("No question-label fallback for ${input.observationId}", proposed.contains(':'))
            assertFalse(proposed.contains('¿'))
            assertEquals("", input.description)
        }
    }

    @Test fun swellingUsesDeclaredZoneAndSideWithNaturalAgreement() {
        assertEquals("Se observó hinchazón en el pie derecho.",
            buildObservationDescription(record(4, answer("zone", "Pies"), answer("side", "Derecho"))))
        assertEquals("Se observó hinchazón en ambas piernas.",
            buildObservationDescription(record(4, answer("zone", "Piernas"), answer("side", "Ambos"))))
        assertEquals("Se observó hinchazón en el tobillo izquierdo.",
            buildObservationDescription(record(4, answer("zone", "Tobillos"), answer("side", "Izquierdo"))))
        assertEquals("Se observó hinchazón en los pies.",
            buildObservationDescription(record(4, answer("zone", "Pies"), answer("side", "No se conoce"))))
    }

    @Test fun woundProposalKeepsOnlyDeclaredFactsAndDoesNotSupplyDatesOrClinicalInterpretation() {
        val input = record(2, answer("lesion", "Ampolla"), answer("zone", "pie"),
            answer("side", "Izquierdo"), answer("onset", "ayer"))
        assertEquals("Se observó una ampolla en el pie izquierdo. El cambio se notó desde ayer.",
            buildObservationDescription(input))
        val sparse = buildObservationDescription(record(2, answer("lesion", "Ampolla")))
        assertEquals("Se observó una ampolla.", sparse)
        assertFalse(sparse.contains("pie"))
        assertFalse(sparse.contains("ayer"))
        assertFalse(sparse.contains("15:47"))
        assertFalse(sparse.contains("2026"))
        assertFalse(sparse.contains("infección", ignoreCase = true))
        assertFalse(sparse.contains("grave", ignoreCase = true))
    }

    @Test fun unknownAnswersAreOmittedWithoutBecomingNegativeFindings() {
        val proposed = buildObservationDescription(record(22, answer("event", "Se cayó"),
            answer("head", "No se conoce"), answer("discomfort", "No se conoce")))
        assertEquals("La persona se cayó.", proposed)
        val explicitNegative = buildObservationDescription(record(22, answer("event", "Se cayó"),
            answer("head", "No"), answer("discomfort", "No")))
        assertTrue(explicitNegative.contains("La persona no se golpeó la cabeza."))
        assertTrue(explicitNegative.contains("No se declararon golpes ni molestias."))
    }

    @Test fun unrecognizedFieldsAndOptionsCannotAddSymptomsToTheProposal() {
        val input = record(7, answer("symptom", "Temblor", "Infección", "Otro cambio", "Temblor"),
            answer("dose", "25"), answer("onset", "ayer"), answer("duration", "cinco minutos"))
        assertEquals("Se observó temblor. La duración aproximada indicada fue de cinco minutos.",
            buildObservationDescription(input))
        assertFalse(buildObservationDescription(input).contains("ayer"))
        assertEquals("", buildObservationDescription(record(7, answer("symptom", "No existe"))))
        assertEquals("", buildObservationDescription(record(27).copy(description = "Texto libre ficticio")))
    }

    @Test fun reviewOutcomesDoNotReuseStaleChangeAnswersOrInventAnExamination() {
        val original = record(1, answer("foot", "Ambos"), answer("changes", "Herida"))
        assertEquals("No se realizó la revisión.",
            buildObservationDescription(original.copy(result = ObservationResult.NOT_PERFORMED)))
        assertEquals("No se observaron cambios. Se revisaron ambos pies.",
            buildObservationDescription(original.copy(result = ObservationResult.NO_CHANGES)))
        assertEquals("No se observaron cambios.",
            buildObservationDescription(original.copy(result = ObservationResult.NO_CHANGES, answers = emptyMap())))
    }

    @Test fun declaredContinuationAndNeurologicalTimingRemainSeparateFromDiagnoses() {
        val proposed = buildObservationDescription(record(20, answer("event", "Parecía desorientado"),
            answer("sudden", "Sí"), answer("repeated", "Se ha repetido")))
        assertEquals("La persona parecía desorientada. El cambio fue repentino. Se ha repetido.", proposed)
        assertFalse(proposed.contains("diagnóstico", ignoreCase = true))
        assertFalse(proposed.contains("demencia", ignoreCase = true))
        assertFalse(proposed.contains("tratamiento", ignoreCase = true))
    }
}
