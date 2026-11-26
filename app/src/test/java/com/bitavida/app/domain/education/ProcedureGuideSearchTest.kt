package com.bitavida.app.domain.education

import com.bitavida.app.domain.usecase.AskBitavidaAi
import org.junit.Assert.*
import org.junit.Test

class ProcedureGuideSearchTest {
    private fun guide(id: String, title: String, vararg aliases: String) = ProcedureGuide(
        id = id, title = title, aliases = aliases.toList(), categories = listOf(ProcedureCategory.LABS),
        context = "Según indicación médica", description = "Contenido de prueba.", purpose = "Propósito de prueba.",
        during = "Atención de prueba.", method = "Consulta.", preparation = "Consultar instrucciones.",
        after = "Consultar resultados.", questions = listOf("¿Qué debo preguntar?"), important = "Consultar al equipo.",
        source = EducationSource("Fuente de prueba", "https://example.test"),
    )
    private val a1c = guide("a1c", "Hemoglobina glucosilada (HbA1c)", "HbA1c", "azúcar de tres meses")
    private val eye = guide("eye", "Fondo de ojo", "revisión de ojos")
    private val photo = guide("photo", "Retinografía", "revisión de ojos")
    private val b12 = guide("b12", "Medición de vitamina B12", "vitamina b12", "análisis de vitamina b12")
    private val guides = listOf(a1c, eye, photo, b12)
    private val repository = object : EducationRepository {
        override suspend fun classify(question: String) = IntentPrediction(null, 0.0, 0.0)
        override fun entry(topic: EducationTopic): EducationEntry = error("Not used")
        override fun procedures() = guides
    }
    private val ask = AskBitavidaAi(repository)

    @Test fun searchUsesAccentFoldingPartialMatchesAndBlankCatalogue() {
        assertEquals(guides, ask.searchProcedures(""))
        assertEquals(listOf(a1c), ask.searchProcedures("AZUCAR de tres meses"))
        assertEquals(listOf(a1c), ask.searchProcedures("glucosil"))
    }

    @Test fun questionUsesCuratedGuideWithoutGeneratingText() {
        assertEquals(ProcedureQuestionResult.Match(a1c), ask.askProcedure("¿Para qué sirve la HbA1c?"))
        assertEquals(ProcedureQuestionResult.Unknown, ask.askProcedure("¿Qué es un procedimiento inexistente?"))
    }

    @Test fun octIsRecognizedOnlyAsItsOwnWholeWordAlias() {
        val oct = guide("oct", "Tomografía de coherencia óptica", "OCT")
        val shortUnrelated = guide("other", "Otra prueba", "ct")
        val pool = listOf(oct, shortUnrelated)

        assertEquals(ProcedureQuestionResult.Match(oct), resolveProcedureQuestion("¿Qué es una OCT?", pool))
        assertEquals(ProcedureQuestionResult.Match(oct), resolveProcedureQuestion("¿Cómo es el aparato de la oct?", pool))
        assertTrue(searchProcedureGuides("doctor", pool).isEmpty())
        val shortAliasResult = resolveProcedureQuestion("¿Qué es ct?", pool)
        assertTrue(shortAliasResult is ProcedureQuestionResult.Choices)
        assertTrue((shortAliasResult as ProcedureQuestionResult.Choices).guides.contains(shortUnrelated))
    }

    @Test fun ambiguousAndRegisteredNamesRequireExplicitSelection() {
        assertEquals(setOf(eye, photo), ask.registeredProcedures("Revisión de ojos").toSet())
        assertTrue(ask.registeredProcedures("ojo").isEmpty())
        val answer = ask.askProcedure("¿Qué es la revisión de ojos?")
        assertTrue(answer is ProcedureQuestionResult.Choices)
        assertEquals(setOf(eye, photo), (answer as ProcedureQuestionResult.Choices).guides.toSet())
    }

    @Test fun urgentPersonalAndNumericQuestionsCannotReturnGuideAnswers() {
        assertEquals(ProcedureQuestionResult.Urgent, ask.askProcedure("Mi familiar no respira y tiene HbA1c"))
        assertEquals(ProcedureQuestionResult.OutsideScope, ask.askProcedure("Mi HbA1c es 9, ¿es normal?"))
        assertEquals(ProcedureQuestionResult.OutsideScope, ask.askProcedure("¿Debo suspender el medicamento antes del fondo de ojo?"))
        assertEquals(ProcedureQuestionResult.OutsideScope, ask.askProcedure("¿Me recomiendas el fondo de ojo?"))
        assertTrue(ask.searchProcedures("Mi HbA1c es 9").isEmpty())
    }

    @Test fun mixedPersonalSymptomsOrTreatmentRequestsDoNotResolveKnownGuide() {
        val questions = listOf(
            "Tengo visión borrosa, ¿qué es el fondo de ojo?",
            "A mi mamá le duele el pie, explíca el fondo de ojo",
            "Mi familiar tiene una herida que supura, ¿qué es HbA1c?",
            "¿Le quito la insulina antes del fondo de ojo?",
            "¿Le cambio las pastillas antes del fondo de ojo?",
            "Mi HbA1c salió en nueve, ¿qué hago?",
            "Mi papá necesita una cirugía de retina, ¿qué es el fondo de ojo?",
        )
        questions.forEach { question ->
            assertEquals(question, ProcedureQuestionResult.OutsideScope, ask.askProcedure(question))
            assertTrue(question, ask.searchProcedures(question).isEmpty())
        }
    }

    @Test fun urgentSignalsTakePrecedenceOverARecognizedGuide() {
        listOf(
            "Mi mamá tiene dolor en el pecho, ¿qué es HbA1c?",
            "Le falta el aire, ¿qué es fondo de ojo?",
            "Está inconsciente y tiene cita para fondo de ojo",
        ).forEach { question ->
            assertEquals(question, ProcedureQuestionResult.Urgent, ask.askProcedure(question))
        }
    }

    @Test fun vitaminB12NameIsSearchableButPersonalValuesRemainBlocked() {
        assertEquals(listOf(b12), ask.searchProcedures("vitamina B12"))
        assertEquals(ProcedureQuestionResult.Match(b12),
            ask.askProcedure("¿Qué es la medición de vitamina B12?"))
        assertEquals(ProcedureQuestionResult.OutsideScope,
            ask.askProcedure("Mi vitamina B12 está baja, ¿qué hago?"))
        assertEquals(ProcedureQuestionResult.OutsideScope,
            ask.askProcedure("Mi vitamina B12 es 300, ¿es normal?"))
        assertTrue(ask.registeredProcedures("Vitamina B12").isEmpty())
    }

    @Test fun genericAliasNeverAutomaticallyLinksAnExistingAppointment() {
        val specific = guide("diabetes", "Consulta de seguimiento de diabetes tipo 2",
            "consulta de seguimiento", "consulta de diabetes")
        assertTrue(exactRegisteredProcedureGuides("Consulta de seguimiento", listOf(specific)).isEmpty())
        assertTrue(exactRegisteredProcedureGuides("Consulta de diabetes", listOf(specific)).isEmpty())
        assertEquals(ProcedureQuestionResult.Choices(listOf(specific)),
            resolveProcedureQuestion("¿Qué es una consulta de seguimiento?", listOf(specific)))
    }
}
