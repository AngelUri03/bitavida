package com.bitavida.app.domain.education

import com.bitavida.app.data.education.LocalEducationLibrary
import org.junit.Assert.*
import org.junit.Test

class LocalEducationLibraryV2Test {
    @Test fun allThirtyTopicsHaveOriginalDevelopedAndVersionedContent() {
        val originalTopics = EducationTopic.entries.filter {
            it.group !in setOf(EducationGroup.OBSERVATIONS, EducationGroup.REMINDERS)
        }
        assertEquals(40, originalTopics.size)
        assertEquals(originalTopics.toSet(), LocalEducationLibrary.entries.keys)
        LocalEducationLibrary.entries.filterKeys { it.group != EducationGroup.MEDICATIONS }.forEach { (topic, entry) ->
            assertEquals(topic, entry.topic)
            assertTrue(entry.version in setOf("2.0", "2.1", "2.2"))
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, entry.reviewStatus)
            assertTrue(entry.title.isNotBlank())
            assertTrue(entry.explanation.length >= 90)
            assertTrue(!entry.importance.isNullOrBlank())
            assertTrue(!entry.example.isNullOrBlank())
            assertTrue(entry.details.size >= 2)
            assertTrue(entry.details.all { it.length >= 65 })
            assertTrue(entry.concepts.size >= 2)
            assertTrue(entry.related.size >= 2)
            assertTrue(entry.related.all { it != topic && it in LocalEducationLibrary.entries })
            assertTrue(entry.source.title.isNotBlank())
            assertTrue(entry.source.reference.isNotBlank())
        }
    }

    @Test fun mainContentDoesNotEmbedUrlsOrPersonalNumericRanges() {
        LocalEducationLibrary.entries.values.forEach { entry ->
            val content = listOfNotNull(
                entry.title, entry.explanation, entry.importance, entry.example,
            ) + entry.details + entry.concepts
            assertTrue(content.none { it.contains("http") || it.contains("www.") })
            // Educational concepts may name units; they do not introduce clinical cutoffs.
            assertTrue(content.none { Regex("\\d+\\s*[-–/]\\s*\\d+").containsMatchIn(it) })
        }
    }

    @Test fun revisedMeasurementGuidesExplainMechanismsAndLimits() {
        val glucose = LocalEducationLibrary.entries.getValue(EducationTopic.GLUCOSE_WHAT)
        assertTrue(glucose.details.any { it.contains("insulina") && it.contains("diabetes tipo 2") })
        assertTrue(glucose.details.any { it.contains("HbA1c") && it.contains("tres meses") })
        assertTrue(glucose.additionalSources.any { it.reference.contains("a1c-test") })
        val pressure = LocalEducationLibrary.entries.getValue(EducationTopic.PRESSURE_REST)
        assertTrue(pressure.details.any { it.contains("dos lecturas") && it.contains("equipo de salud") })
        val bmi = LocalEducationLibrary.entries.getValue(EducationTopic.WEIGHT_BMI)
        assertTrue(bmi.details.any { it.contains("adultos mayores", ignoreCase = true) && it.contains("fuerza") })
        assertTrue(bmi.details.any { it.contains("multiplica la estatura") && it.contains("grasa") })
        assertEquals("2.2", glucose.version)
        assertEquals("2.1", pressure.version)
        assertEquals("2.2", bmi.version)
        listOf(glucose, pressure, bmi).forEach {
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, it.reviewStatus)
        }
    }

    @Test fun homeMetersExplainTheirMechanismsAndLimitsWithoutPersonalDiagnosis() {
        val glucose = LocalEducationLibrary.entries.getValue(EducationTopic.GLUCOSE_WHAT)
        val glucoseText = glucose.details.joinToString(" ")
        assertTrue(glucoseText.contains("tira reactiva"))
        assertTrue(glucoseText.contains("electricidad o luz"))
        assertTrue(glucoseText.contains("no son pruebas intercambiables"))
        assertTrue(glucoseText.contains("atención urgente"))
        assertTrue(glucose.additionalSources.any { it.reference.contains("blood-glucose-monitoring-devices") })
        assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, glucose.reviewStatus)

        val messages = LocalEducationLibrary.entries.getValue(EducationTopic.GLUCOSE_INDICATORS)
        assertTrue(messages.details.any { it.contains("por encima") && it.contains("por debajo") })
        assertTrue(messages.details.any { it.contains("no les asigna un punto inventado") })

        val pressure = LocalEducationLibrary.entries.getValue(EducationTopic.PRESSURE_WHAT)
        val pressureText = pressure.details.joinToString(" ")
        assertTrue(pressureText.contains("manguito") && pressureText.contains("libera aire"))
        assertTrue(pressureText.contains("pulso") && pressureText.contains("diastólica"))
        assertTrue(pressureText.contains("no permite a Bitavida diagnosticar"))
        assertTrue(pressure.additionalSources.any { it.reference.endsWith("/007490.htm") })
    }

    @Test fun unspecifiedSourceDatesAreNotPresentedAsKnown() {
        val source = EducationSource("Fuente de prueba", "https://example.org")
        val drug = DrugReference("Entidad", "Documento", "https://example.org", "Versión")
        assertEquals("Fecha no documentada", source.consultedOn)
        assertEquals("Fecha no documentada", drug.reviewedOn)
    }

    @Test fun scheduledAndAdditionalGuideDoesNotInferAssociation() {
        val guide = LocalEducationLibrary.entries.getValue(EducationTopic.GLUCOSE_PLANNED)
        assertTrue(guide.details.any { it.contains("no ocupa automáticamente") })
        assertTrue(guide.details.any { it.contains("contexto previsto") && it.contains("contexto real") })
        assertTrue(guide.details.any { it.contains("sin confirmar") && it.contains("decisión explícita") })
        assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
    }

    @Test fun measurementPurposeGuidesExplainSeriesAndAvoidDiagnosis() {
        val glucose = LocalEducationLibrary.entries.getValue(EducationTopic.GLUCOSE_PURPOSE)
        assertTrue(glucose.details.any { it.contains("glucómetro") && it.contains("momento") })
        assertTrue(glucose.details.any { it.contains("HbA1c") && it.contains("meses") })
        val pressure = LocalEducationLibrary.entries.getValue(EducationTopic.PRESSURE_PURPOSE)
        assertTrue(pressure.details.any { it.contains("no notan síntomas") })
        assertTrue(pressure.details.any { it.contains("no permite a Bitavida diagnosticar") })
        listOf(glucose, pressure).forEach {
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, it.reviewStatus)
            assertEquals("2.2", it.version)
        }
    }

    @Test fun glucoseUnitsGuideDoesNotTreatDifferentUnitsAsInterchangeable() {
        val entry = LocalEducationLibrary.entries.getValue(EducationTopic.GLUCOSE_UNITS)
        assertTrue(entry.details.any { it.contains("concentración") && it.contains("decilitro") })
        assertTrue(entry.details.any { it.contains("mmol/L") && it.contains("no coincide") })
        assertTrue(entry.details.any { it.contains("sin adivinar") })
        assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, entry.reviewStatus)
        assertTrue(entry.source.reference.contains("blood-glucose-monitoring-devices"))
    }

    @Test fun weightGuidesExplainCompositionAndComparableConditions() {
        val tracking = LocalEducationLibrary.entries.getValue(EducationTopic.WEIGHT_TRACKING)
        assertTrue(tracking.details.any { it.contains("músculo") && it.contains("agua") })
        assertTrue(tracking.details.any { it.contains("pérdida de peso no buscada") })
        val conditions = LocalEducationLibrary.entries.getValue(EducationTopic.WEIGHT_CONDITIONS)
        assertTrue(conditions.details.any { it.contains("superficie firme") && it.contains("misma báscula") })
        assertTrue(conditions.details.any { it.contains("seguridad") && it.contains("alternativa accesible") })
        val variations = LocalEducationLibrary.entries.getValue(EducationTopic.WEIGHT_VARIATIONS)
        assertTrue(variations.details.any { it.contains("no permite identificar") })
        listOf(tracking, conditions, variations).forEach {
            assertEquals("2.2", it.version)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, it.reviewStatus)
        }
    }

    @Test fun glucoseContextDistinguishesFastingFromMealsAndDescribesLimits() {
        val fasting = LocalEducationLibrary.entries.getValue(EducationTopic.GLUCOSE_FASTING)
        assertTrue(fasting.details.any { it.contains("antes de la comida del mediodía") })
        assertTrue(fasting.details.any { it.contains("laboratorio") && it.contains("equipo de salud") })
        val context = LocalEducationLibrary.entries.getValue(EducationTopic.GLUCOSE_CONTEXT)
        assertTrue(context.details.any { it.contains("hora de la medición") && it.contains("comida") })
        val variation = LocalEducationLibrary.entries.getValue(EducationTopic.GLUCOSE_VARIATIONS)
        assertTrue(variation.details.any { it.contains("tiras deterioradas") && it.contains("manos") })
        assertTrue(variation.details.any { it.contains("No modifiques") && it.contains("medicamentos") })
        listOf(fasting, context, variation).forEach {
            assertEquals("2.2", it.version)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, it.reviewStatus)
        }
    }

    @Test fun pressureContextGuidesExplainVariationWithoutPrescribingChanges() {
        val activity = LocalEducationLibrary.entries.getValue(EducationTopic.PRESSURE_ACTIVITY)
        assertTrue(activity.details.any { it.contains("músculos") && it.contains("oxígeno") })
        assertTrue(activity.details.any { it.contains("pulso") && it.contains("presión") })
        assertTrue(activity.additionalSources.any { it.reference.contains("nhlbi.nih.gov/health/heart/heart-beats") })
        val variation = LocalEducationLibrary.entries.getValue(EducationTopic.PRESSURE_VARIATIONS)
        assertTrue(variation.details.any { it.contains("manguito") && it.contains("brazo") })
        assertTrue(variation.details.any { it.contains("suspender medicamentos") })
        assertTrue(variation.additionalSources.any { it.reference.contains("monitoring-your-blood-pressure-at-home") })
        listOf(activity, variation).forEach {
            assertEquals("2.2", it.version)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, it.reviewStatus)
        }
    }

    @Test fun forgottenReadingAndTargetsPreserveProductBoundaries() {
        val forgotten = LocalEducationLibrary.entries.getValue(EducationTopic.MEASUREMENT_FORGOTTEN)
        assertTrue(forgotten.explanation.contains("resultado real"))
        assertTrue(forgotten.details.any { it.contains("Sin confirmar") })
        val target = LocalEducationLibrary.entries.getValue(EducationTopic.WEIGHT_TARGET)
        assertTrue(target.explanation.contains("no lo calcula"))
        assertTrue(target.details.any { it.contains("no participa", ignoreCase = true) || it.contains("No entra") })
        val consultation = LocalEducationLibrary.entries.getValue(EducationTopic.MEASUREMENT_CONSULTATION)
        assertTrue(consultation.details.any { it.contains("información sensible") && it.contains("acción separada y explícita") })
    }
}
