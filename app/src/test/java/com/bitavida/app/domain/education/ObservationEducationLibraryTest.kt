package com.bitavida.app.domain.education

import com.bitavida.app.data.education.LocalEducationLibrary
import com.bitavida.app.data.education.ObservationEducationLibrary
import com.bitavida.app.domain.model.ObservationCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationEducationLibraryTest {
    private val entries = ObservationEducationLibrary.entries

    @Test fun libraryHasExactlyOneStructuredGuideForEveryFixedObservation() {
        val guides = entries.values.filter { it.observationId != null }
        assertEquals(27, guides.size)
        assertEquals(ObservationCatalog.all.map { it.id }.toSet(), guides.mapNotNull { it.observationId }.toSet())
        assertEquals(29, entries.size)
        assertEquals(
            setOf(EducationTopic.OBSERVATION_GENERAL, EducationTopic.OBSERVATION_APP_USE),
            entries.keys - guides.map { it.topic }.toSet(),
        )
        guides.forEach { entry ->
            assertEquals(EducationGroup.OBSERVATIONS, entry.topic.group)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, entry.reviewStatus)
            assertTrue(entry.importance.orEmpty().isNotBlank())
            assertTrue(entry.possibleMeanings.isNotEmpty())
            assertTrue(entry.delayedAttention.orEmpty().isNotBlank())
            assertTrue(entry.whatToObserve.isNotEmpty())
            assertTrue(entry.medicalGuidance.orEmpty().isNotBlank())
            assertTrue(entry.searchTerms.isNotEmpty())
            assertTrue(entry.source.title.isNotBlank())
            assertTrue(entry.source.reference.isNotBlank())
            assertTrue(entry.source.consultedOn.isNotBlank())
        }
    }

    @Test fun inheritedSourcesDoNotInventConsultationDatesAndExplicitDatesArePreserved() {
        val feet = entries.getValue(EducationTopic.OBS_001_FEET_REVIEW)
        assertTrue(feet.source.reference.contains("imss.gob.mx"))
        assertEquals("Fecha no documentada", feet.source.consultedOn)
        val niddk = feet.additionalSources.single { it.reference.contains("niddk.nih.gov") }
        assertEquals("22 de noviembre de 2026", niddk.consultedOn)
        assertEquals(
            "Fecha no documentada",
            entries.getValue(EducationTopic.OBSERVATION_GENERAL).source.consultedOn,
        )
    }

    @Test fun relationshipsStayInsideTheCompleteOfflineLibraryAndNeverSelfReference() {
        val complete = LocalEducationLibrary.entries + entries
        entries.values.forEach { entry ->
            assertTrue(entry.related.size in 2..3)
            assertFalse(entry.topic in entry.related)
            assertTrue(entry.related.all { it in complete })
            assertTrue(entry.additionalSources.none { it.reference.isBlank() || it.title.isBlank() })
        }
    }

    @Test fun revisedObservationGuidesDescribeSymptomsAndDoNotDelayUrgentCare() {
        val tingling = entries.getValue(EducationTopic.OBS_017_TINGLING_NUMBNESS)
        assertTrue(tingling.explanation.contains("entumecimiento"))
        assertTrue(tingling.medicalGuidance.orEmpty().contains("incluso si no hay otros síntomas"))
        assertTrue(tingling.additionalSources.any { it.reference.contains("stroke") })
        val confusion = entries.getValue(EducationTopic.OBS_020_CONFUSION_MEMORY)
        assertTrue(confusion.importance.orEmpty().contains("de repente"))
        assertTrue(confusion.medicalGuidance.orEmpty().contains("aunque después mejoren"))
        val swelling = entries.getValue(EducationTopic.OBS_004_SWELLING)
        assertTrue(swelling.explanation.contains("edema"))
        assertTrue(swelling.medicalGuidance.orEmpty().contains("dificultad para respirar"))
        val vision = entries.getValue(EducationTopic.OBS_016_VISION)
        assertTrue(vision.medicalGuidance.orEmpty().contains("incluso sin debilidad"))
        val urine = entries.getValue(EducationTopic.OBS_012_URINATION_DIFFICULTY)
        assertTrue(urine.medicalGuidance.orEmpty().contains("no puede orinar"))
        assertTrue(urine.additionalSources.any { it.reference.contains("urinary-retention") })
        val wounds = entries.getValue(EducationTopic.OBS_002_WOUNDS)
        assertTrue(wounds.importance.orEmpty().contains("ausencia de dolor"))
        listOf(tingling, confusion, swelling, vision, wounds).forEach { assertEquals("1.2", it.version) }
        assertEquals("1.2", urine.version)
        listOf(tingling, confusion, swelling, vision, urine, wounds).forEach {
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, it.reviewStatus)
        }
    }

    @Test fun revisedGuidesExplainDistinctObservableChangesWithoutAssumingDiagnosis() {
        val swelling = entries.getValue(EducationTopic.OBS_004_SWELLING)
        assertTrue(swelling.details.any { it.contains("calzado") && it.contains("contorno") })
        assertTrue(swelling.details.any { it.contains("no lo suspendas por tu cuenta") })
        assertTrue(swelling.whatToObserve.any { it.contains("ambos lados") })

        val tingling = entries.getValue(EducationTopic.OBS_017_TINGLING_NUMBNESS)
        assertTrue(tingling.details.any { it.contains("piquetes") && it.contains("tacto") })
        assertTrue(tingling.details.any { it.contains("ampolla") && it.contains("aunque no haya dolor") })
        assertTrue(tingling.details.any { it.contains("no permiten identificar") })

        val confusion = entries.getValue(EducationTopic.OBS_020_CONFUSION_MEMORY)
        assertTrue(confusion.details.any { it.contains("conversación") && it.contains("lugar conocido") })
        assertTrue(confusion.details.any { it.contains("confusión repentina") && it.contains("no son equivalentes") })
        assertTrue(confusion.details.any { it.contains("busca ayuda urgente primero") && it.contains("no deduzcas") })
        assertTrue(confusion.additionalSources.any { it.reference.contains("stroke") })
    }

    @Test fun otherCommonChangesAreSpecificAndKeepUrgentSignalsVisible() {
        val wounds = entries.getValue(EducationTopic.OBS_002_WOUNDS)
        assertTrue(wounds.details.any { it.contains("ampolla") && it.contains("líquido") })
        assertTrue(wounds.details.any { it.contains("ausencia de dolor") })
        assertTrue(wounds.medicalGuidance.orEmpty().contains("no retrases"))

        val vision = entries.getValue(EducationTopic.OBS_016_VISION)
        assertTrue(vision.details.any { it.contains("ver doble") && it.contains("dos imágenes") })
        assertTrue(vision.medicalGuidance.orEmpty().contains("cortina"))
        assertTrue(vision.additionalSources.any { it.reference.contains("nei.nih.gov") })

        val dizziness = entries.getValue(EducationTopic.OBS_006_DIZZINESS)
        assertTrue(dizziness.details.any { it.contains("Vértigo") && it.contains("giran") })
        assertTrue(dizziness.medicalGuidance.orEmpty().contains("equilibrio"))
        assertTrue(dizziness.additionalSources.any { it.reference.endsWith("/003093.htm") })

        val sweating = entries.getValue(EducationTopic.OBS_007_TREMOR_SWEATING)
        assertTrue(sweating.details.any { it.contains("por sí solo no confirma") })
        assertTrue(sweating.medicalGuidance.orEmpty().contains("confusión repentina"))
        listOf(wounds, vision, dizziness, sweating).forEach {
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, it.reviewStatus)
        }
    }

    @Test fun urinaryDigestiveAndFootChangesHaveDistinctDescriptionsAndSafeguards() {
        val nails = entries.getValue(EducationTopic.OBS_003_CALLOUSES_NAILS)
        assertTrue(nails.details.any { it.contains("callo") && it.contains("grieta") })
        assertTrue(nails.details.any { it.contains("No cortes callos") })

        val frequent = entries.getValue(EducationTopic.OBS_011_FREQUENT_URINATION)
        assertTrue(frequent.details.any { it.contains("más veces") && it.contains("habitual") })
        assertTrue(frequent.details.any { it.contains("cantidad total") })

        val difficulty = entries.getValue(EducationTopic.OBS_012_URINATION_DIFFICULTY)
        assertTrue(difficulty.details.any { it.contains("escape") && it.contains("chorro débil") })
        assertTrue(difficulty.medicalGuidance.orEmpty().contains("no puede orinar"))

        val burning = entries.getValue(EducationTopic.OBS_013_PAINFUL_URINATION)
        assertTrue(burning.details.any { it.contains("no la confirma") && it.contains("tratamiento") })
        assertTrue(burning.source.reference.contains("niddk.nih.gov"))

        val vomiting = entries.getValue(EducationTopic.OBS_014_NAUSEA_VOMITING)
        assertTrue(vomiting.details.any { it.contains("náusea") && it.contains("estómago") })
        assertTrue(vomiting.medicalGuidance.orEmpty().contains("sangre") &&
            vomiting.medicalGuidance.orEmpty().contains("atención urgente"))

        val stool = entries.getValue(EducationTopic.OBS_015_BOWEL_CHANGES)
        assertTrue(stool.details.any { it.contains("diarrea") && it.contains("estreñimiento") })
        assertTrue(stool.additionalSources.any { it.reference.endsWith("/constipation.html") })
        listOf(nails, frequent, burning, vomiting, stool).forEach { assertEquals("1.1", it.version) }
        listOf(nails, frequent, difficulty, burning, vomiting, stool).forEach {
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, it.reviewStatus)
        }
    }

    @Test fun feetFatiguePainAndDryMouthDescribeDistinctObservableChanges() {
        val feet = entries.getValue(EducationTopic.OBS_001_FEET_REVIEW)
        assertTrue(feet.details.any { it.contains("plantas") && it.contains("espacios entre los dedos") })
        assertTrue(feet.details.any { it.contains("falta de dolor") && it.contains("No significa") })

        val fatigue = entries.getValue(EducationTopic.OBS_008_WEAKNESS_FATIGUE)
        assertTrue(fatigue.details.any { it.contains("fatiga") && it.contains("somnolencia") })
        assertTrue(fatigue.details.any { it.contains("uno o varios músculos") })
        assertTrue(fatigue.additionalSources.any { it.reference.endsWith("/003088.htm") })

        val pain = entries.getValue(EducationTopic.OBS_009_PAIN)
        assertTrue(pain.details.any { it.contains("ardor") && it.contains("punzada") })
        assertTrue(pain.details.any { it.contains("sueño") && it.contains("persona") })
        assertTrue(pain.additionalSources.any { it.reference.endsWith("/chronicpain.html") })

        val thirst = entries.getValue(EducationTopic.OBS_010_THIRST_DRY_MOUTH)
        assertTrue(thirst.details.any { it.contains("saliva") && it.contains("tragar") })
        assertTrue(thirst.details.any { it.contains("No es correcto concluir") })
        assertTrue(thirst.medicalGuidance.orEmpty().contains("confusión repentina"))
        assertTrue(thirst.additionalSources.any { it.reference.endsWith("/dehydration.html") })
        listOf(feet, fatigue, pain, thirst).forEach {
            assertEquals("1.1", it.version)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, it.reviewStatus)
        }
    }

    @Test fun appetiteSleepFallsWalkingAndSkinExplainDifferentChangesAndUrgencies() {
        val appetite = entries.getValue(EducationTopic.OBS_018_APPETITE)
        assertTrue(appetite.details.any { it.contains("masticar") && it.contains("tragar") })
        assertTrue(appetite.medicalGuidance.orEmpty().contains("confusión repentina"))
        assertTrue(appetite.additionalSources.any { it.reference.endsWith("/003121.htm") })

        val sleep = entries.getValue(EducationTopic.OBS_019_SLEEP)
        assertTrue(sleep.details.any { it.contains("quedarse dormido") && it.contains("durante el día") })
        assertTrue(sleep.medicalGuidance.orEmpty().contains("atención urgente"))

        val fall = entries.getValue(EducationTopic.OBS_022_FALL)
        assertTrue(fall.details.any { it.contains("cabeza") && it.contains("No inventes") })
        assertTrue(fall.details.any { it.contains("no la retrases") })

        val walking = entries.getValue(EducationTopic.OBS_023_WALKING_BALANCE)
        assertTrue(walking.details.any { it.contains("oído interno") && it.contains("músculos") })
        assertTrue(walking.medicalGuidance.orEmpty().contains("incluso si no se observan otros síntomas"))

        val skin = entries.getValue(EducationTopic.OBS_025_SKIN_CHANGES)
        assertTrue(skin.details.any { it.contains("roncha") && it.contains("lesión abierta") })
        assertTrue(skin.medicalGuidance.orEmpty().contains("dificultad para respirar"))
        assertTrue(skin.additionalSources.any { it.reference.contains("nhs.uk/conditions/hives") })
        listOf(appetite, sleep, fall, walking, skin).forEach {
            assertEquals("1.1", it.version)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, it.reviewStatus)
        }
    }

    @Test fun feverMouthMoodAndOtherChangeTeachWhatToObserveWithoutDiagnosis() {
        val fever = entries.getValue(EducationTopic.OBS_024_FEVER_CHILLS)
        assertTrue(fever.details.any { it.contains("escalofríos") && it.contains("músculos") })
        assertTrue(fever.details.any { it.contains("sin inventar un número") })
        assertTrue(fever.additionalSources.any { it.reference.endsWith("/003091.htm") })

        val mouth = entries.getValue(EducationTopic.OBS_026_MOUTH_GUMS)
        assertTrue(mouth.details.any { it.contains("saliva") && it.contains("tragar") })
        assertTrue(mouth.medicalGuidance.orEmpty().contains("dificultad para respirar o tragar"))
        assertTrue(mouth.medicalGuidance.orEmpty().contains("atención urgente"))

        val mood = entries.getValue(EducationTopic.OBS_021_MOOD_BEHAVIOR)
        assertTrue(mood.details.any { it.contains("no diagnostica") && it.contains("depresión") })
        assertTrue(mood.details.any { it.contains("riesgo") && it.contains("ayuda urgente") })

        val other = entries.getValue(EducationTopic.OBS_027_OTHER_CHANGE)
        assertTrue(other.details.any { it.contains("propias palabras") && it.contains("diagnóstico") })
        assertTrue(other.details.any { it.contains("no es un servicio de emergencias") })
        listOf(fever, mouth, mood, other).forEach {
            assertEquals("1.1", it.version)
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, it.reviewStatus)
        }
    }

    @Test fun footColorAndTemperatureGuideDescribesChangesWithoutInferringTheirCause() {
        val guide = entries.getValue(EducationTopic.OBS_005_COLOR_TEMPERATURE)
        assertTrue(guide.details.any { it.contains("aspecto habitual") && it.contains("otro lado") })
        assertTrue(guide.details.any { it.contains("no permite a Bitavida diagnosticar") })
        assertTrue(guide.medicalGuidance.orEmpty().contains("cambio repentino") &&
            guide.medicalGuidance.orEmpty().contains("atención urgente"))
        assertTrue(guide.additionalSources.any { it.reference.endsWith("/000170.htm") })
        assertEquals("1.1", guide.version)
        assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
    }

    @Test fun observationUseDistinguishesRecordsFromScheduledReviewsAndSupportsConsultation() {
        val use = entries.getValue(EducationTopic.OBSERVATION_APP_USE)
        val details = use.details.joinToString(" ")
        assertTrue(details.contains("observación espontánea"))
        assertTrue(details.contains("no resuelve automáticamente"))
        assertTrue(details.contains("«Sin confirmar»") && details.contains("«No realizada»"))
        assertTrue(details.contains("historial permanecen guardados"))
        assertTrue(details.contains("no consulta datos personales"))
        assertEquals("1.1", use.version)

        val general = entries.getValue(EducationTopic.OBSERVATION_GENERAL)
        assertTrue(general.details.any { it.contains("consulta") && it.contains("historial") })
        assertTrue(general.details.any { it.contains("información sensible") })
        assertTrue(general.additionalSources.any { it.reference.endsWith("/000953.htm") })
        assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, general.reviewStatus)
    }

    @Test fun expandedObservationGuidesExplainRealExamplesAndQuestions() {
        val checks = listOf(
            Triple(EducationTopic.OBS_013_PAINFUL_URINATION, "cultivo", "niddk.nih.gov"),
            Triple(EducationTopic.OBS_025_SKIN_CHANGES, "tono de piel", "niams.nih.gov"),
            Triple(EducationTopic.OBS_015_BOWEL_CHANGES, "patrón habitual", "niddk.nih.gov"),
            Triple(EducationTopic.OBS_023_WALKING_BALANCE, "actividades son seguras", "nidcd.nih.gov"),
            Triple(EducationTopic.OBS_019_SLEEP, "somnolencia necesitan valoración", "nia.nih.gov"),
        )
        checks.forEach { (topic, phrase, domain) ->
            val guide = entries.getValue(topic)
            val details = guide.details.joinToString(" ")
            assertTrue("$topic no explica $phrase", details.contains(phrase))
            assertTrue("$topic no contiene preguntas propias", details.contains("¿"))
            assertTrue("$topic no muestra la fuente institucional", (listOf(guide.source) + guide.additionalSources).any { it.reference.contains(domain) })
            assertEquals(EducationReviewStatus.PENDING_HUMAN_REVIEW, guide.reviewStatus)
        }
        listOf(
            EducationTopic.OBS_013_PAINFUL_URINATION,
            EducationTopic.OBS_019_SLEEP,
            EducationTopic.OBS_023_WALKING_BALANCE,
            EducationTopic.OBS_025_SKIN_CHANGES,
        ).forEach { topic ->
            val guide = entries.getValue(topic)
            assertTrue(
                "$topic conserva una referencia /ency/",
                (listOf(guide.source) + guide.additionalSources).none { it.reference.contains("/spanish/ency/") },
            )
        }
    }

    @Test fun educationalDraftsKeepNonDiagnosticProductBoundaries() {
        val text = entries.values.joinToString(" ") { entry ->
            buildList {
                add(entry.explanation)
                add(entry.importance.orEmpty())
                addAll(entry.possibleMeanings)
                add(entry.delayedAttention.orEmpty())
                addAll(entry.whatToObserve)
                add(entry.medicalGuidance.orEmpty())
            }.joinToString(" ")
        }.lowercase()
        listOf("tienes diabetes", "esto indica que", "debes tomar", "cambia la dosis").forEach {
            assertFalse(it, it in text)
        }
        assertTrue(text.contains("puede"))
        assertTrue(text.contains("valoración") || text.contains("orientación"))
    }
}
