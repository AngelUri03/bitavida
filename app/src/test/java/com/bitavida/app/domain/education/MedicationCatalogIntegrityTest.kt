package com.bitavida.app.domain.education

import com.bitavida.app.data.education.BundledEducationRepository
import com.bitavida.app.domain.usecase.AskBitavidaAi
import java.io.File
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Editorial structure and offline access; these checks do not claim clinical validation. */
class MedicationCatalogIntegrityTest {
    private val repository = BundledEducationRepository {
        error("Opening a packaged drug card must not initialize the classifier")
    }
    private val catalog get() = repository.drugs()

    @Test fun revisedNeuropathicPainAndStatinCardsRetainDistinctMechanismsAndSafetyContext() {
        fun text(id: String, intent: DrugIntent) =
            catalog.single { it.id == id }.sections.single { it.intent == intent }.body.lowercase()

        assertTrue(text("duloxetine", DrugIntent.MECHANISM).contains("recaptación"))
        assertTrue(text("duloxetine", DrugIntent.PRECAUTIONS).contains("presión"))
        assertTrue(text("duloxetine", DrugIntent.INTERACTIONS).contains("sangrado"))
        assertTrue(text("pregabalin", DrugIntent.MECHANISM).contains("canales de calcio"))
        assertTrue(text("pregabalin", DrugIntent.PRECAUTIONS).contains("riñones"))
        assertTrue(text("pregabalin", DrugIntent.INTERACTIONS).contains("respiración"))
        assertTrue(text("atorvastatin", DrugIntent.MECHANISM).contains("hígado"))
        assertTrue(text("atorvastatin", DrugIntent.MECHANISM).contains("ldl"))
        assertTrue(text("atorvastatin", DrugIntent.SIDE_EFFECTS).contains("muscular"))

        listOf("duloxetine", "pregabalin", "atorvastatin").forEach { id ->
            val guide = catalog.single { it.id == id }
            assertTrue(guide.statusNote.contains("pendiente de revisión clínica"))
            assertTrue(guide.sources.single().reviewedOn.contains("22 de noviembre de 2026"))
        }
    }

    @Test fun cardiovascularAndRenalCardsExplainDistinctTargetsAndPotassiumMonitoring() {
        fun text(id: String, intent: DrugIntent) =
            catalog.single { it.id == id }.sections.single { it.intent == intent }.body.lowercase()

        assertTrue(text("losartan", DrugIntent.MECHANISM).contains("bloquea un receptor"))
        assertTrue(text("lisinopril", DrugIntent.MECHANISM).contains("inhibe esa enzima"))
        assertTrue(text("finerenone", DrugIntent.MECHANISM).contains("aldosterona"))
        listOf("losartan", "lisinopril", "finerenone").forEach { id ->
            val guide = catalog.single { it.id == id }
            assertTrue(id, text(id, DrugIntent.PRECAUTIONS).contains("potasio"))
            assertTrue(id, guide.statusNote.contains("pendiente de revisión clínica"))
            assertEquals("22 de noviembre de 2026", guide.sources.single().reviewedOn)
        }
    }

    @Test fun combinationCardsExplainBothIngredientsAndTheirDistinctSafetyConcerns() {
        fun text(id: String, intent: DrugIntent) =
            catalog.single { it.id == id }.sections.single { it.intent == intent }.body.lowercase()

        assertTrue(text("metformin_sitagliptin", DrugIntent.MECHANISM).contains("incretinas"))
        assertTrue(text("metformin_empagliflozin", DrugIntent.MECHANISM).contains("riñones"))
        assertTrue(text("metformin_glibenclamide", DrugIntent.MECHANISM).contains("páncreas"))
        assertTrue(text("metformin_sitagliptin", DrugIntent.SIDE_EFFECTS).contains("páncreas"))
        assertTrue(text("metformin_empagliflozin", DrugIntent.SIDE_EFFECTS).contains("cetoacidosis"))
        assertTrue(text("metformin_glibenclamide", DrugIntent.SIDE_EFFECTS).contains("hipoglucemia"))
        listOf("metformin_sitagliptin", "metformin_empagliflozin", "metformin_glibenclamide").forEach { id ->
            val guide = catalog.single { it.id == id }
            assertTrue(id, guide.statusNote.contains("pendiente de revisión clínica"))
            assertEquals("22 de noviembre de 2026", guide.sources.single().reviewedOn)
        }
    }

    @Test fun additionalCombinationCardsKeepTheirDistinctMechanismsAndWarnings() {
        fun text(id: String, intent: DrugIntent) =
            catalog.single { it.id == id }.sections.single { it.intent == intent }.body.lowercase()

        assertTrue(text("metformin_linagliptin", DrugIntent.MECHANISM).contains("incretinas"))
        assertTrue(text("metformin_dapagliflozin", DrugIntent.MECHANISM).contains("sglt2"))
        assertTrue(text("metformin_pioglitazone", DrugIntent.MECHANISM).contains("ppar-gamma"))
        assertTrue(text("metformin_linagliptin", DrugIntent.SIDE_EFFECTS).contains("ampollas"))
        assertTrue(text("metformin_dapagliflozin", DrugIntent.SIDE_EFFECTS).contains("cetoacidosis"))
        assertTrue(text("metformin_pioglitazone", DrugIntent.SIDE_EFFECTS).contains("retención de líquidos"))
        listOf("metformin_linagliptin", "metformin_dapagliflozin", "metformin_pioglitazone").forEach { id ->
            val guide = catalog.single { it.id == id }
            assertTrue(id, guide.statusNote.contains("pendiente de revisión clínica"))
            assertEquals("22 de noviembre de 2026", guide.sources.single().reviewedOn)
        }
    }

    @Test fun furtherCombinedProductsRetainTheirSpecificIngredientsAndSafetyDetails() {
        fun text(id: String, intent: DrugIntent) =
            catalog.single { it.id == id }.sections.single { it.intent == intent }.body.lowercase()

        assertTrue(text("metformin_canagliflozin", DrugIntent.SIDE_EFFECTS).contains("amputaciones"))
        assertTrue(text("empagliflozin_linagliptin", DrugIntent.MECHANISM).contains("incretinas"))
        assertTrue(text("dapagliflozin_saxagliptin", DrugIntent.MECHANISM).contains("saxagliptina"))
        assertTrue(text("dapagliflozin_saxagliptin", DrugIntent.INTERACTIONS).contains("ketoconazol"))
        listOf("metformin_canagliflozin", "empagliflozin_linagliptin", "dapagliflozin_saxagliptin").forEach { id ->
            val guide = catalog.single { it.id == id }
            assertTrue(id, guide.statusNote.contains("pendiente de revisión clínica"))
            assertEquals("22 de noviembre de 2026", guide.sources.single().reviewedOn)
        }
    }

    @Test fun injectableGlp1CardsKeepFormulationSpecificWarningsAndSafetyReview() {
        fun text(id: String, intent: DrugIntent) = catalog.single { it.id == id }
            .sections.filter { it.intent == intent }.joinToString("\n") { it.body }.lowercase()

        assertTrue(text("liraglutida", DrugIntent.MECHANISM).contains("glucagón"))
        assertTrue(text("dulaglutida", DrugIntent.PRECAUTIONS).contains("visión") ||
            text("dulaglutida", DrugIntent.PRECAUTIONS).contains("visual"))
        assertTrue(text("exenatida_inmediata", DrugIntent.SIDE_EFFECTS).contains("plaquetas"))
        assertTrue(text("exenatida_inmediata", DrugIntent.FORMULATIONS).contains("prolongada"))
        listOf("liraglutida", "dulaglutida", "exenatida_inmediata").forEach { id ->
            val guide = catalog.single { it.id == id }
            assertTrue(id, guide.statusNote.contains("pendiente de revisión clínica"))
            assertEquals("22 de noviembre de 2026", guide.sources.single().reviewedOn)
        }
    }

    @Test fun extendedReleaseAndDualAgonistsRetainSpecificSafetyContext() {
        fun text(id: String, intent: DrugIntent) = catalog.single { it.id == id }
            .sections.filter { it.intent == intent }.joinToString("\n") { it.body }.lowercase()

        assertTrue(text("exenatida_prolongada", DrugIntent.FORMULATIONS).contains("autoinyector"))
        assertTrue(text("exenatida_prolongada", DrugIntent.INTERACTIONS).contains("warfarina"))
        assertTrue(text("lixisenatida", DrugIntent.INTERACTIONS).contains("anticonceptivos"))
        assertTrue(text("tirzepatida", DrugIntent.MECHANISM).contains("gip"))
        assertTrue(text("tirzepatida", DrugIntent.MECHANISM).contains("glp-1"))
        listOf("exenatida_prolongada", "lixisenatida", "tirzepatida").forEach { id ->
            val guide = catalog.single { it.id == id }
            assertTrue(id, guide.statusNote.contains("pendiente de revisión clínica"))
            assertEquals("22 de noviembre de 2026", guide.sources.single().reviewedOn)
        }
    }

    @Test fun mealtimeMedicinesAndMiglitolExplainDifferentActionsAndSafety() {
        fun text(id: String, intent: DrugIntent) = catalog.single { it.id == id }
            .sections.filter { it.intent == intent }.joinToString("\n") { it.body }.lowercase()

        assertTrue(text("repaglinida", DrugIntent.INTERACTIONS).contains("gemfibrozilo"))
        assertTrue(text("repaglinida", DrugIntent.PRECAUTIONS).contains("comidas"))
        assertTrue(text("nateglinida", DrugIntent.MECHANISM).contains("insulina"))
        assertTrue(text("nateglinida", DrugIntent.PRECAUTIONS).contains("comida"))
        assertTrue(text("miglitol", DrugIntent.MECHANISM).contains("alfa-glucosidasas"))
        assertTrue(text("miglitol", DrugIntent.INTERACTIONS).contains("sacarosa"))
        listOf("repaglinida", "nateglinida", "miglitol").forEach { id ->
            val guide = catalog.single { it.id == id }
            assertTrue(id, guide.statusNote.contains("pendiente de revisión clínica"))
            assertEquals("22 de noviembre de 2026", guide.sources.single().reviewedOn)
        }
    }

    @Test fun threePreviouslyUndatedCardsKeepSpecificDocumentsAndExplanations() {
        fun text(id: String, intent: DrugIntent) = catalog.single { it.id == id }
            .sections.filter { it.intent == intent }.joinToString("\n") { it.body }.lowercase()

        assertTrue(text("metformin_vildagliptin", DrugIntent.MECHANISM).contains("incretinas"))
        assertTrue(text("metformin_vildagliptin", DrugIntent.PRECAUTIONS).contains("hígado"))
        assertTrue(text("metformin_glimepiride", DrugIntent.MECHANISM).contains("sulfonilurea"))
        assertTrue(text("metformin_glimepiride", DrugIntent.FORMULATIONS).contains("india"))
        assertTrue(text("colesevelam", DrugIntent.MECHANISM).contains("ácidos biliares"))
        assertTrue(text("colesevelam", DrugIntent.INTERACTIONS).contains("warfarina"))
        listOf("metformin_vildagliptin", "metformin_glimepiride", "colesevelam").forEach { id ->
            val guide = catalog.single { it.id == id }
            assertTrue(id, guide.statusNote.contains("pendiente de revisión clínica"))
            assertEquals("22 de noviembre de 2026", guide.sources.single().reviewedOn)
        }
    }

    @Test fun historicalAndSpecializedMedicinesKeepLimitsAndDistinctMechanisms() {
        fun text(id: String, intent: DrugIntent) = catalog.single { it.id == id }
            .sections.filter { it.intent == intent }.joinToString("\n") { it.body }.lowercase()

        assertTrue(text("rosiglitazona", DrugIntent.MECHANISM).contains("ppar-gamma"))
        assertTrue(catalog.single { it.id == "rosiglitazona" }.statusNote.contains("histórica"))
        assertTrue(text("bromocriptina_rapida", DrugIntent.MECHANISM).contains("dopamina"))
        assertTrue(text("pramlintida", DrugIntent.MECHANISM).contains("amilina"))
        assertTrue(text("pramlintida", DrugIntent.SIDE_EFFECTS).contains("hipoglucemia grave"))
        listOf("rosiglitazona", "bromocriptina_rapida", "pramlintida").forEach { id ->
            val guide = catalog.single { it.id == id }
            assertTrue(id, guide.statusNote.lowercase().contains("pendiente de revisión clínica"))
            assertEquals("22 de noviembre de 2026", guide.sources.single().reviewedOn)
        }
    }

    @Test fun regionSpecificCardsExplainLimitationsAndHaveDocumentedSourceDates() {
        fun text(id: String, intent: DrugIntent) = catalog.single { it.id == id }
            .sections.filter { it.intent == intent }.joinToString("\n") { it.body }.lowercase()

        assertTrue(text("teneligliptina", DrugIntent.MECHANISM).contains("glp-1"))
        assertTrue(text("teneligliptina", DrugIntent.PRECAUTIONS).contains("intervalo qt"))
        assertTrue(text("voglibosa", DrugIntent.MECHANISM).contains("alfa-glucosidasas"))
        assertTrue(text("voglibosa", DrugIntent.INTERACTIONS).contains("azúcar común"))
        listOf("teneligliptina", "voglibosa").forEach { id ->
            val guide = catalog.single { it.id == id }
            assertTrue(id, guide.statusNote.lowercase().contains("pendiente de revisión clínica"))
            assertTrue(id, text(id, DrugIntent.FORMULATIONS).contains("méxico"))
            assertEquals("22 de noviembre de 2026", guide.sources.single().reviewedOn)
        }
    }

    @Test fun revisedRepresentativeCardsRetainMechanismsAndSpecificSafetyInformation() {
        fun text(id: String, intent: DrugIntent) = catalog.single { it.id == id }
            .sections.filter { it.intent == intent }.joinToString("\n") { it.body }.lowercase()

        assertTrue(text("sitagliptina", DrugIntent.MECHANISM).contains("incretinas"))
        assertTrue(text("sitagliptina", DrugIntent.PRECAUTIONS).contains("riñones"))
        assertTrue(text("semaglutida_inyectable", DrugIntent.PRECAUTIONS).contains("anestesia"))
        assertTrue(text("insulin_degludec", DrugIntent.MECHANISM).contains("depósito"))
        assertTrue(text("insulin_degludec", DrugIntent.FORMULATIONS).contains("jeringa"))
        listOf("sitagliptina", "semaglutida_inyectable", "insulin_degludec").forEach { id ->
            val guide = catalog.single { it.id == id }
            assertTrue(id, guide.statusNote.lowercase().contains("pendiente de revisión clínica"))
            assertEquals("22 de noviembre de 2026", guide.sources.single().reviewedOn)
        }
    }

    @Test fun insulinCardsExplainProductSpecificAppearanceAndDeviceRisks() {
        fun text(id: String, intent: DrugIntent) = catalog.single { it.id == id }
            .sections.filter { it.intent == intent }.joinToString("\n") { it.body }.lowercase()

        assertTrue(text("insulin_regular", DrugIntent.FORMULATIONS).contains("transparente"))
        assertTrue(text("insulin_nph", DrugIntent.FORMULATIONS).contains("turbia"))
        assertTrue(text("insulin_nph", DrugIntent.FORMULATIONS).contains("partículas"))
        assertTrue(text("insulin_lispro", DrugIntent.FORMULATIONS).contains("jeringa"))
        assertTrue(text("insulin_lispro", DrugIntent.FORMULATIONS).contains("u-200"))
        listOf("insulin_regular", "insulin_nph", "insulin_lispro").forEach { id ->
            val guide = catalog.single { it.id == id }
            assertTrue(id, guide.statusNote.lowercase().contains("pendiente de revisión clínica"))
            assertEquals("22 de noviembre de 2026", guide.sources.single().reviewedOn)
        }
    }

    @Test fun dpp4CardsRetainProductSpecificRenalCardiacAndLiverDetails() {
        fun text(id: String, intent: DrugIntent) = catalog.single { it.id == id }
            .sections.filter { it.intent == intent }.joinToString("\n") { it.body }.lowercase()

        assertTrue(text("linagliptina", DrugIntent.PRECAUTIONS).contains("función renal"))
        assertTrue(text("linagliptina", DrugIntent.INTERACTIONS).contains("rifampicina"))
        assertTrue(text("saxagliptina", DrugIntent.PRECAUTIONS).contains("tartrazina"))
        assertTrue(text("saxagliptina", DrugIntent.SIDE_EFFECTS).contains("insuficiencia cardiaca"))
        assertTrue(text("alogliptina", DrugIntent.PRECAUTIONS).contains("hígado") ||
            text("alogliptina", DrugIntent.PRECAUTIONS).contains("hepátic"))
        assertTrue(text("alogliptina", DrugIntent.SIDE_EFFECTS).contains("páncreas"))
        listOf("linagliptina", "saxagliptina", "alogliptina").forEach { id ->
            val guide = catalog.single { it.id == id }
            assertTrue(id, guide.statusNote.lowercase().contains("pendiente de revisión clínica"))
            assertEquals("22 de noviembre de 2026", guide.sources.single().reviewedOn)
        }
    }

    @Test fun premixedInsulinsExplainFixedComponentsWithoutEquatingProducts() {
        fun text(id: String, intent: DrugIntent) = catalog.single { it.id == id }
            .sections.filter { it.intent == intent }.joinToString("\n") { it.body }.lowercase()

        assertTrue(text("insulin_human_mix_70_30", DrugIntent.FORMULATIONS).contains("nph y regular"))
        assertTrue(text("insulin_human_mix_70_30", DrugIntent.FORMULATIONS).contains("aspart"))
        assertTrue(text("insulin_lispro_mix_75_25", DrugIntent.FORMULATIONS).contains("bomba"))
        assertTrue(text("insulin_lispro_mix_75_25", DrugIntent.IDENTIFY).contains("75/25"))
        assertTrue(text("insulin_aspart_mix_70_30", DrugIntent.FORMULATIONS).contains("nph/regular"))
        listOf("insulin_human_mix_70_30", "insulin_lispro_mix_75_25", "insulin_aspart_mix_70_30").forEach { id ->
            val guide = catalog.single { it.id == id }
            assertTrue(id, guide.statusNote.lowercase().contains("pendiente de revisión clínica"))
            assertEquals("22 de noviembre de 2026", guide.sources.single().reviewedOn)
        }
    }

    @Test fun rapidInsulinAndFixedCombinationsHaveSpecificAnswersAndQuestions() {
        fun text(id: String, intent: DrugIntent) = catalog.single { it.id == id }
            .sections.filter { it.intent == intent }.joinToString("\n") { it.body }.lowercase()

        assertTrue(text("insulin_aspart", DrugIntent.PRECAUTIONS).contains("cetoacidosis"))
        assertTrue(text("insulin_aspart", DrugIntent.FORMULATIONS).contains("bomba"))
        assertTrue(text("insulin_glargine_lixisenatide", DrugIntent.INTERACTIONS).contains("glp-1"))
        assertTrue(text("insulin_glargine_lixisenatide", DrugIntent.FORMULATIONS).contains("bomba"))
        assertTrue(text("insulin_degludec_liraglutide", DrugIntent.INTERACTIONS).contains("glp-1"))
        assertTrue(text("insulin_degludec_liraglutide", DrugIntent.CONTRAINDICATIONS).contains("tiroides"))
        listOf("insulin_aspart", "insulin_glargine_lixisenatide", "insulin_degludec_liraglutide")
            .forEach { id ->
                val guide = catalog.single { it.id == id }
                assertTrue(id, guide.statusNote.lowercase().contains("pendiente de revisión clínica"))
                assertEquals("22 de noviembre de 2026", guide.sources.single().reviewedOn)
                assertEquals(3, guide.relatedQuestions.size)
                guide.relatedQuestions.forEach { question ->
                    assertTrue(id, question.question.isNotBlank())
                    assertTrue(id, guide.sections.any { it.intent == question.intent && it.body.isNotBlank() })
                }
            }
        assertEquals(
            DrugIntent.PRECAUTIONS,
            catalog.single { it.id == "insulin_aspart" }.relatedQuestions
                .single { it.question.contains("bomba") }.intent,
        )
        assertEquals(
            DrugIntent.CONTRAINDICATIONS,
            catalog.single { it.id == "insulin_degludec_liraglutide" }.relatedQuestions
                .single { it.question.contains("tiroideos") }.intent,
        )
    }

    @Test fun priorityDrugQuestionsOpenRelevantExistingSectionsOffline() {
        val expected = mapOf(
            "metformina" to listOf(
                Triple("hígado", DrugIntent.MECHANISM, "higado"),
                Triple("riñones", DrugIntent.PRECAUTIONS, "eliminan metformina"),
                Triple("liberación inmediata", DrugIntent.FORMULATIONS, "liberacion prolongada"),
            ),
            "empagliflozina" to listOf(
                Triple("orina", DrugIntent.MECHANISM, "sglt2"),
                Triple("genitales", DrugIntent.SIDE_EFFECTS, "infecciones"),
                Triple("líquidos", DrugIntent.PRECAUTIONS, "liquido"),
            ),
            "glibenclamida" to listOf(
                Triple("liberar insulina", DrugIntent.MECHANISM, "pancreas"),
                Triple("señales", DrugIntent.SIDE_EFFECTS, "temblor"),
                Triple("riesgo", DrugIntent.PRECAUTIONS, "ingesta"),
            ),
            "gliclazida" to listOf(
                Triple("páncreas", DrugIntent.MECHANISM, "insulina"),
                Triple("liberación modificada", DrugIntent.FORMULATIONS, "gradual"),
                Triple("interactuar", DrugIntent.INTERACTIONS, "miconazol"),
            ),
            "insulin_glargine" to listOf(
                Triple("basal", DrugIntent.IDENTIFY, "prolongada"),
                Triple("lentamente", DrugIntent.MECHANISM, "depositos"),
                Triple("vial", DrugIntent.FORMULATIONS, "concentracion"),
            ),
            "insulin_regular" to listOf(
                Triple("NPH", DrugIntent.IDENTIFY, "intermedia"),
                Triple("utilizar glucosa", DrugIntent.MECHANISM, "higado"),
                Triple("vial", DrugIntent.FORMULATIONS, "transparente"),
            ),
            "insulin_nph" to listOf(
                Triple("¿Qué es la suspensión", DrugIntent.IDENTIFY, "protamina"),
                Triple("protamina", DrugIntent.MECHANISM, "absorcion"),
                Triple("regular", DrugIntent.FORMULATIONS, "concentracion"),
            ),
            "insulin_lispro" to listOf(
                Triple("mezcla", DrugIntent.IDENTIFY, "protamina"),
                Triple("comidas", DrugIntent.PURPOSE, "rapido"),
                Triple("U-200", DrugIntent.FORMULATIONS, "bomba"),
            ),
            "semaglutida_inyectable" to listOf(
                Triple("GLP-1", DrugIntent.MECHANISM, "receptores"),
                Triple("digestivas", DrugIntent.SIDE_EFFECTS, "nauseas"),
                Triple("anestesia", DrugIntent.PRECAUTIONS, "sedacion"),
            ),
            "sitagliptina" to listOf(
                Triple("incretinas", DrugIntent.MECHANISM, "dpp-4"),
                Triple("renal", DrugIntent.PRECAUTIONS, "rinones"),
                Triple("sulfonilureas", DrugIntent.INTERACTIONS, "insulina"),
            ),
            "dapagliflozina" to listOf(
                Triple("glucosa en la orina", DrugIntent.MECHANISM, "sglt2"),
                Triple("molestias urinarias", DrugIntent.SIDE_EFFECTS, "infecciones"),
                Triple("diuréticos", DrugIntent.INTERACTIONS, "liquido"),
            ),
            "glimepirida" to listOf(
                Triple("liberar insulina", DrugIntent.MECHANISM, "pancreas"),
                Triple("señales", DrugIntent.SIDE_EFFECTS, "temblor"),
                Triple("G6PD", DrugIntent.PRECAUTIONS, "globulos rojos"),
            ),
            "liraglutida" to listOf(
                Triple("GLP-1", DrugIntent.MECHANISM, "receptores"),
                Triple("digestivas", DrugIntent.SIDE_EFFECTS, "nauseas"),
                Triple("anestesia", DrugIntent.PRECAUTIONS, "sedacion"),
            ),
            "insulin_degludec" to listOf(
                Triple("combinación", DrugIntent.IDENTIFY, "liraglutida"),
                Triple("después de la aplicación", DrugIntent.MECHANISM, "deposito"),
                Triple("dispositivo", DrugIntent.FORMULATIONS, "concentracion"),
            ),
            "insulin_detemir" to listOf(
                Triple("basal", DrugIntent.IDENTIFY, "prolongada"),
                Triple("albúmina", DrugIntent.MECHANISM, "proteina"),
                Triple("señales", DrugIntent.SIDE_EFFECTS, "temblor"),
            ),
            "semaglutida_oral" to listOf(
                Triple("GLP-1", DrugIntent.MECHANISM, "receptor"),
                Triple("demás medicamentos", DrugIntent.INTERACTIONS, "levotiroxina"),
                Triple("intercambiables", DrugIntent.FORMULATIONS, "inyeccion"),
            ),
            "linagliptina" to listOf(
                Triple("incretinas", DrugIntent.MECHANISM, "dpp-4"),
                Triple("combinada", DrugIntent.FORMULATIONS, "metformina"),
                Triple("rifampicina", DrugIntent.INTERACTIONS, "rifampicina"),
            ),
            "saxagliptina" to listOf(
                Triple("incretinas", DrugIntent.MECHANISM, "dpp-4"),
                Triple("alergias", DrugIntent.PRECAUTIONS, "tartrazina"),
                Triple("ketoconazol", DrugIntent.INTERACTIONS, "concentracion"),
            ),
            "alogliptina" to listOf(
                Triple("incretinas", DrugIntent.MECHANISM, "dpp-4"),
                Triple("renal", DrugIntent.PRECAUTIONS, "eliminacion"),
                Triple("combinado", DrugIntent.FORMULATIONS, "advertencias"),
            ),
            "pioglitazona" to listOf(
                Triple("responder", DrugIntent.MECHANISM, "sensibilidad"),
                Triple("vejiga", DrugIntent.PRECAUTIONS, "cardiacos"),
                Triple("combinación", DrugIntent.INTERACTIONS, "retencion"),
            ),
            "exenatida_inmediata" to listOf(
                Triple("distingue", DrugIntent.FORMULATIONS, "prolongada"),
                Triple("warfarina", DrugIntent.INTERACTIONS, "anticoagulante"),
                Triple("plaquetas", DrugIntent.CONTRAINDICATIONS, "trombocitopenia"),
            ),
            "exenatida_prolongada" to listOf(
                Triple("libera", DrugIntent.MECHANISM, "suspension"),
                Triple("zona de inyección", DrugIntent.SIDE_EFFECTS, "nodulos"),
                Triple("dispositivo", DrugIntent.FORMULATIONS, "autoinyector"),
            ),
            "dulaglutida" to listOf(
                Triple("GLP-1", DrugIntent.MECHANISM, "receptores"),
                Triple("digestivas", DrugIntent.SIDE_EFFECTS, "nauseas"),
                Triple("anestesia", DrugIntent.PRECAUTIONS, "sedacion"),
            ),
            "canagliflozina" to listOf(
                Triple("orina", DrugIntent.MECHANISM, "sglt2"),
                Triple("pies", DrugIntent.PRECAUTIONS, "ulceras"),
                Triple("digoxina", DrugIntent.INTERACTIONS, "diureticos"),
            ),
            "glipizida" to listOf(
                Triple("liberar insulina", DrugIntent.MECHANISM, "pancreas"),
                Triple("riesgo", DrugIntent.PRECAUTIONS, "ingesta"),
                Triple("liberación", DrugIntent.FORMULATIONS, "prolongada"),
            ),
            "atorvastatin" to listOf(
                Triple("colesterol", DrugIntent.MECHANISM, "ldl"),
                Triple("musculares", DrugIntent.SIDE_EFFECTS, "debilidad"),
                Triple("toronja", DrugIntent.INTERACTIONS, "claritromicina"),
            ),
            "losartan" to listOf(
                Triple("angiotensina", DrugIntent.MECHANISM, "receptor"),
                Triple("riñones", DrugIntent.PRECAUTIONS, "funcion renal"),
                Triple("productos", DrugIntent.INTERACTIONS, "suplementos"),
            ),
            "finerenone" to listOf(
                Triple("receptores", DrugIntent.MECHANISM, "aldosterona"),
                Triple("potasio", DrugIntent.PRECAUTIONS, "funcion renal"),
                Triple("medicamentos", DrugIntent.INTERACTIONS, "cyp3a4"),
            ),
            "duloxetine" to listOf(
                Triple("neuronas", DrugIntent.MECHANISM, "serotonina"),
                Triple("equilibrio", DrugIntent.PRECAUTIONS, "confusion"),
                Triple("cápsula", DrugIntent.FORMULATIONS, "retardada"),
            ),
            "pregabalin" to listOf(
                Triple("nerviosas", DrugIntent.MECHANISM, "calcio"),
                Triple("riñones", DrugIntent.PRECAUTIONS, "eliminan pregabalina"),
                Triple("opioides", DrugIntent.INTERACTIONS, "respiracion"),
            ),
            "metformin_sitagliptin" to listOf(
                Triple("cada componente", DrugIntent.MECHANISM, "incretinas"),
                Triple("vitamina B12", DrugIntent.PRECAUTIONS, "riñones"),
                Triple("inmediata", DrugIntent.FORMULATIONS, "liberación prolongada"),
            ),
            "metformin_linagliptin" to listOf(
                Triple("¿Cómo actúan", DrugIntent.MECHANISM, "dpp-4"),
                Triple("función renal", DrugIntent.PRECAUTIONS, "metformina"),
                Triple("rifampicina", DrugIntent.INTERACTIONS, "linagliptina"),
            ),
            "metformin_vildagliptin" to listOf(
                Triple("¿Qué hacen", DrugIntent.MECHANISM, "incretinas"),
                Triple("riñones y el hígado", DrugIntent.PRECAUTIONS, "función renal"),
                Triple("presión", DrugIntent.INTERACTIONS, "angioedema"),
            ),
            "metformin_glibenclamide" to listOf(
                Triple("¿Cómo actúan", DrugIntent.MECHANISM, "páncreas"),
                Triple("señales", DrugIntent.SIDE_EFFECTS, "temblor"),
                Triple("bosentán", DrugIntent.INTERACTIONS, "hígado"),
            ),
            "metformin_glimepiride" to listOf(
                Triple("¿Qué hacen", DrugIntent.MECHANISM, "sulfonilurea"),
                Triple("alimentación", DrugIntent.PRECAUTIONS, "comidas"),
                Triple("India", DrugIntent.FORMULATIONS, "liberación prolongada"),
            ),
            "metformin_empagliflozin" to listOf(
                Triple("¿Cómo actúan", DrugIntent.MECHANISM, "SGLT2"),
                Triple("XR", DrugIntent.FORMULATIONS, "liberación prolongada"),
                Triple("diuréticos", DrugIntent.INTERACTIONS, "líquidos"),
            ),
            "metformin_dapagliflozin" to listOf(
                Triple("¿Cómo actúan", DrugIntent.MECHANISM, "SGLT2"),
                Triple("XR", DrugIntent.FORMULATIONS, "liberación prolongada"),
                Triple("vitamina B12", DrugIntent.PRECAUTIONS, "función renal"),
            ),
            "metformin_canagliflozin" to listOf(
                Triple("¿Cómo actúan", DrugIntent.MECHANISM, "SGLT2"),
                Triple("XR", DrugIntent.FORMULATIONS, "liberación prolongada"),
                Triple("fracturas", DrugIntent.PRECAUTIONS, "pies"),
            ),
            "empagliflozin_linagliptin" to listOf(
                Triple("ingredientes", DrugIntent.IDENTIFY, "no contiene metformina"),
                Triple("¿Cómo actúan", DrugIntent.MECHANISM, "incretinas"),
                Triple("rifampicina", DrugIntent.INTERACTIONS, "linagliptina"),
            ),
            "dapagliflozin_saxagliptin" to listOf(
                Triple("ingredientes", DrugIntent.IDENTIFY, "no incluye metformina"),
                Triple("¿Cómo actúan", DrugIntent.MECHANISM, "incretinas"),
                Triple("ketoconazol", DrugIntent.INTERACTIONS, "CYP3A4"),
            ),
            "insulin_glulisine" to listOf(
                Triple("regular", DrugIntent.MECHANISM, "rápida"),
                Triple("bomba", DrugIntent.PRECAUTIONS, "fallo"),
                Triple("dispositivos", DrugIntent.FORMULATIONS, "vial o pluma"),
            ),
            "insulin_human_mix_70_30" to listOf(
                Triple("números", DrugIntent.IDENTIFY, "proporción"),
                Triple("aspart", DrugIntent.FORMULATIONS, "NPH y regular"),
                Triple("suspensión", DrugIntent.PRECAUTIONS, "formación"),
            ),
            "insulin_lispro_mix_75_25" to listOf(
                Triple("números", DrugIntent.IDENTIFY, "proporción"),
                Triple("protamina", DrugIntent.MECHANISM, "absorción"),
                Triple("bomba", DrugIntent.FORMULATIONS, "no autoriza"),
            ),
            "insulin_aspart_mix_70_30" to listOf(
                Triple("números", DrugIntent.IDENTIFY, "proporción"),
                Triple("humana", DrugIntent.FORMULATIONS, "NPH/regular"),
                Triple("protamina", DrugIntent.MECHANISM, "absorción"),
            ),
            "lisinopril" to listOf(
                Triple("angiotensina", DrugIntent.MECHANISM, "enzima"),
                Triple("tos", DrugIntent.SIDE_EFFECTS, "mareo"),
                Triple("renal", DrugIntent.PRECAUTIONS, "potasio"),
            ),
            "tolbutamida" to listOf(
                Triple("páncreas", DrugIntent.MECHANISM, "insulina"),
                Triple("plan", DrugIntent.PRECAUTIONS, "hipoglucemia"),
                Triple("warfarina", DrugIntent.INTERACTIONS, "rifampicina"),
            ),
            "ertugliflozina" to listOf(
                Triple("orina", DrugIntent.MECHANISM, "SGLT2"),
                Triple("pies", DrugIntent.PRECAUTIONS, "circulación"),
                Triple("sulfonilureas", DrugIntent.INTERACTIONS, "hipoglucemia"),
            ),
            "vildagliptina" to listOf(
                Triple("incretinas", DrugIntent.MECHANISM, "DPP-4"),
                Triple("hígado", DrugIntent.PRECAUTIONS, "función hepática"),
                Triple("corazón", DrugIntent.INTERACTIONS, "angioedema"),
            ),
            "teneligliptina" to listOf(
                Triple("GLP-1", DrugIntent.MECHANISM, "DPP-4"),
                Triple("intervalo QT", DrugIntent.PRECAUTIONS, "electrocardiograma"),
                Triple("Tailandia", DrugIntent.FORMULATIONS, "no confirma"),
            ),
            "lixisenatida" to listOf(
                Triple("GLP-1", DrugIntent.MECHANISM, "receptores"),
                Triple("orales", DrugIntent.INTERACTIONS, "absorción"),
                Triple("glargina", DrugIntent.FORMULATIONS, "combinación"),
            ),
            "tirzepatida" to listOf(
                Triple("GIP", DrugIntent.MECHANISM, "GLP-1"),
                Triple("orales", DrugIntent.INTERACTIONS, "absorción"),
                Triple("anestesia", DrugIntent.PRECAUTIONS, "sedación"),
            ),
            "rosiglitazona" to listOf(
                Triple("acción", DrugIntent.MECHANISM, "PPAR-gamma"),
                Triple("2013", DrugIntent.INTERACTIONS, "gemfibrozilo"),
                Triple("disponibilidad", DrugIntent.FORMULATIONS, "no confirma"),
            ),
            "repaglinida" to listOf(
                Triple("comidas", DrugIntent.MECHANISM, "insulina"),
                Triple("omitida", DrugIntent.PRECAUTIONS, "instrucciones"),
                Triple("gemfibrozilo", DrugIntent.INTERACTIONS, "contraindicado"),
            ),
            "nateglinida" to listOf(
                Triple("comidas", DrugIntent.MECHANISM, "insulina"),
                Triple("omitida", DrugIntent.PRECAUTIONS, "alimentos"),
                Triple("fluconazol", DrugIntent.INTERACTIONS, "procesamiento"),
            ),
            "acarbosa" to listOf(
                Triple("carbohidratos", DrugIntent.MECHANISM, "alfa-glucosidasas"),
                Triple("gases", DrugIntent.SIDE_EFFECTS, "bacterias"),
                Triple("baja", DrugIntent.INTERACTIONS, "sacarosa"),
            ),
            "miglitol" to listOf(
                Triple("carbohidratos", DrugIntent.MECHANISM, "alfa-glucosidasas"),
                Triple("renal", DrugIntent.PRECAUTIONS, "intestino"),
                Triple("glucosa baja", DrugIntent.INTERACTIONS, "sacarosa"),
            ),
            "voglibosa" to listOf(
                Triple("carbohidratos", DrugIntent.MECHANISM, "alfa-glucosidasas"),
                Triple("digestivos", DrugIntent.PRECAUTIONS, "obstrucción intestinal"),
                Triple("Nigeria", DrugIntent.FORMULATIONS, "no confirma"),
            ),
            "colesevelam" to listOf(
                Triple("biliares", DrugIntent.MECHANISM, "colesterol"),
                Triple("triglicéridos", DrugIntent.PRECAUTIONS, "tragar"),
                Triple("otros medicamentos", DrugIntent.INTERACTIONS, "absorción"),
            ),
            "bromocriptina_rapida" to listOf(
                Triple("acción", DrugIntent.MECHANISM, "dopamina"),
                Triple("mareos", DrugIntent.PRECAUTIONS, "caídas"),
                Triple("intercambiar", DrugIntent.FORMULATIONS, "instrucciones"),
            ),
            "pramlintida" to listOf(
                Triple("amilina", DrugIntent.MECHANISM, "glucagón"),
                Triple("formación", DrugIntent.PRECAUTIONS, "hipoglucemia"),
                Triple("2019", DrugIntent.FORMULATIONS, "no demuestra"),
            ),
            "metformin_pioglitazone" to listOf(
                Triple("¿Cómo actúan", DrugIntent.MECHANISM, "PPAR-gamma"),
                Triple("hinchazón", DrugIntent.SIDE_EFFECTS, "respirar"),
                Triple("vejiga", DrugIntent.PRECAUTIONS, "visión"),
            ),
        )
        val ask = AskBitavidaAi(repository)
        expected.forEach { (id, cases) ->
            val guide = requireNotNull(ask.readDrug(id)).drug
            assertEquals(id, cases.size, guide.relatedQuestions.size)
            cases.forEach { (fragment, intent, evidence) ->
                val matches = guide.relatedQuestions.filter { it.question.contains(fragment) }
                assertEquals("$id: $fragment", 1, matches.size)
                val question = matches.single()
                assertEquals(id, intent, question.intent)
                val answer = guide.sections.filter { it.intent == question.intent }.joinToString(" ") { it.body }
                assertTrue("$id: $fragment", EducationTokenizer.normalize(answer).contains(EducationTokenizer.normalize(evidence)))
            }
        }
    }

    @Test fun revisedIndicationsExplainProductScopeAndDocumentedLimitsOffline() {
        val ask = AskBitavidaAi(repository)
        fun purpose(id: String) = EducationTokenizer.normalize(
            requireNotNull(ask.readDrug(id)).drug.sections.single { it.intent == DrugIntent.PURPOSE }.body,
        )
        val lixisenatide = purpose("lixisenatida")
        assertTrue(lixisenatide.contains("alimentacion") && lixisenatide.contains("glargina"))
        val linagliptin = purpose("metformin_linagliptin")
        assertTrue(linagliptin.contains("diabetes tipo 1") && linagliptin.contains("no se ha estudiado"))
        assertTrue(linagliptin.contains("antecedentes de pancreatitis"))
        val pioglitazone = purpose("metformin_pioglitazone")
        assertTrue(pioglitazone.contains("diabetes tipo 1") && pioglitazone.contains("cetoacidosis"))
        val repaglinide = purpose("repaglinida")
        assertTrue(repaglinide.contains("comida") && repaglinide.contains("diabetes tipo 1"))
        assertTrue(repaglinide.contains("cetoacidosis"))
        assertTrue(purpose("miglitol").contains("despues de comer"))
        val combination = purpose("metformin_glibenclamide")
        assertTrue(combination.contains("metformina") && combination.contains("glibenclamida"))
        assertTrue(combination.contains("gliburida"))
        val glibenclamide = purpose("glibenclamida")
        assertTrue(glibenclamide.contains("gliburida") && glibenclamide.contains("diabetes tipo 1"))
        assertTrue(glibenclamide.contains("cetoacidosis"))
        assertTrue(purpose("gliclazida").contains("liberacion modificada"))
        val indiaCombination = purpose("metformin_glimepiride")
        assertTrue(indiaCombination.contains("dos capas") && indiaCombination.contains("india"))
        assertTrue(indiaCombination.contains("metformina de liberacion prolongada"))
        listOf("saxagliptina", "glipizida").forEach { id ->
            assertTrue(id, purpose(id).contains("diabetes tipo 1"))
            assertTrue(id, purpose(id).contains("cetoacidosis"))
        }
        assertTrue(purpose("alogliptina").contains("diabetes tipo 1"))
        assertTrue(purpose("glipizida").contains("liberacion prolongada"))
        listOf(
            "lixisenatida", "metformin_linagliptin", "metformin_pioglitazone",
            "repaglinida", "miglitol", "metformin_glibenclamide",
            "glibenclamida", "gliclazida", "metformin_glimepiride",
            "saxagliptina", "alogliptina", "glipizida",
        ).forEach { id ->
            val guide = requireNotNull(ask.readDrug(id)).drug
            assertTrue(EducationTokenizer.normalize(guide.statusNote).contains("pendiente de revision clinica"))
            assertTrue(purpose(id).contains("mexico"))
        }
    }

    @Test fun allSixtyFourDocumentedFormulationsHaveStableUniqueIdsAndCanBeOpenedOffline() {
        val entries = catalog
        assertEquals(64, entries.size)
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        val ask = AskBitavidaAi(repository)
        entries.forEach { drug ->
            assertTrue(drug.id, drug.id.matches(Regex("[a-z0-9_]+")))
            assertTrue(drug.id, drug.name.isNotBlank())
            assertTrue(drug.id, drug.family.isNotBlank())
            assertTrue(drug.id, drug.summary.isNotBlank())
            assertEquals(drug, ask.readDrug(drug.id)?.drug)
            assertTrue(drug.id, drug.relatedIds.all { id -> entries.any { it.id == id } })
            assertFalse(drug.id, drug.id in drug.relatedIds)
        }
        assertNull(ask.readDrug("principio_ficticio_fuera_del_catalogo"))
    }

    @Test fun everyCardCoversAllEducationalIntentionsWithSeparateContraindicationsAndPrecautions() {
        catalog.forEach { drug ->
            assertEquals(drug.id, DrugIntent.entries.toSet(), drug.sections.map { it.intent }.toSet())
            drug.sections.forEach { section ->
                assertTrue(drug.id, section.title.isNotBlank())
                assertTrue(drug.id, section.body.isNotBlank())
            }
            val contraindications = drug.sections.filter { it.intent == DrugIntent.CONTRAINDICATIONS }
                .joinToString { it.body }
            val precautions = drug.sections.filter { it.intent == DrugIntent.PRECAUTIONS }.joinToString { it.body }
            assertNotEquals(drug.id, contraindications, precautions)
        }
    }

    @Test fun referencesIdentifySpecificPrimaryDocumentsWithVersionsAndConsultationDates() {
        val allowedHosts = setOf(
            "dailymed.nlm.nih.gov", "accessdata.fda.gov", "fda.gov", "ema.europa.eu", "cima.aemps.es",
            "pmda.go.jp", "medicines.org.uk", "sanofi.com", "pi.lilly.com", "novonordisk-us.com",
            "ndi.fda.moph.go.th", "admin.greenbook.nafdac.gov.ng",
        )
        catalog.forEach { drug ->
            assertTrue(drug.id, drug.sources.isNotEmpty())
            drug.sources.forEach { source ->
                val uri = URI(source.url)
                assertEquals(source.url, "https", uri.scheme)
                assertTrue(source.url, uri.host.removePrefix("www.") in allowedHosts)
                assertTrue(source.url, uri.path.length > 1)
                assertTrue(drug.id, source.institution.isNotBlank())
                assertTrue(drug.id, source.document.isNotBlank())
                assertTrue(drug.id, source.version.contains(Regex("(?:19|20)\\d{2}")))
                if (source.reviewedOn != "Fecha no documentada") {
                    val date = Regex("""^(\d{1,2}) de ([a-záéíóú]+) de (\d{4})$""")
                        .matchEntire(source.reviewedOn)
                    assertNotNull("Fecha inválida de ${drug.id}: ${source.reviewedOn}", date)
                    val months = listOf(
                        "enero", "febrero", "marzo", "abril", "mayo", "junio",
                        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
                    )
                    val values = date!!.groupValues
                    val month = months.indexOf(values[2]) + 1
                    assertTrue(
                        "Fecha inválida de ${drug.id}: ${source.reviewedOn}",
                        runCatching { java.time.LocalDate.of(values[3].toInt(), month, values[1].toInt()) }.isSuccess,
                    )
                }
                assertFalse(source.url, source.url.contains("example."))
            }
        }
        // Prevent a single generic bibliography from silently replacing product-specific labels.
        assertEquals(catalog.size, catalog.map { it.sources.first().url }.toSet().size)
    }

    @Test fun unspecifiedSemaglutideAndExenatideRequireAFormulationSelection() {
        val resolver = DrugNameResolver(catalog)
        assertEquals(setOf("semaglutida_oral", "semaglutida_inyectable"),
            resolver.registered("  SEMAGLUTIDA  ").map { it.id }.toSet())
        assertEquals(setOf("exenatida_inmediata", "exenatida_prolongada"),
            resolver.registered("exenatida").map { it.id }.toSet())
        assertEquals(listOf("semaglutida_oral"), resolver.registered("semaglutida oral").map { it.id })
        assertEquals(listOf("exenatida_inmediata"), resolver.registered("exenatida inmediata").map { it.id })
        assertEquals(listOf("glibenclamida"), resolver.registered("gliburida").map { it.id })
        assertTrue(resolver.registered("marca ficticia").isEmpty())
        assertTrue(resolver.registered("metforminna").isEmpty())
    }

    @Test fun formulationSpecificWarningsAndDocumentsAreNotInheritedAcrossTheFamily() {
        fun card(id: String) = catalog.single { it.id == id }
        fun contraindications(id: String) = EducationTokenizer.normalize(
            card(id).sections.filter { it.intent == DrugIntent.CONTRAINDICATIONS }.joinToString { it.body },
        )
        assertNotEquals(card("exenatida_inmediata").sources, card("exenatida_prolongada").sources)
        assertFalse(contraindications("exenatida_inmediata").contains("medular"))
        assertTrue(contraindications("exenatida_prolongada").contains("medular"))
        assertFalse(contraindications("insulin_glargine_lixisenatide").contains("medular"))
        assertTrue(contraindications("insulin_degludec_liraglutide").contains("medular"))
        assertTrue(contraindications("metformin_glibenclamide").contains("bosentan"))
        assertFalse(contraindications("metformin_sitagliptin").contains("bosentan"))
    }

    @Test fun everyEntryDisclosesPendingReviewAndKeepsLinksOutOfEducationalBodies() {
        val unsupportedValidation = Regex(
            "clinicamente validado|validacion clinica completada|seguro para (ti|usted|el paciente)",
        )
        catalog.forEach { drug ->
            assertTrue(drug.id, EducationTokenizer.normalize(drug.statusNote).contains("pendiente de revision clinica"))
            assertTrue(drug.id, EducationTokenizer.normalize(drug.availabilityNote).contains("mexico"))
            assertTrue(drug.id, EducationTokenizer.normalize(drug.availabilityNote).contains("no verificadas"))
            val body = listOf(drug.summary).plus(drug.sections.map { it.body }).joinToString(" ")
            assertFalse(drug.id, Regex("https?://").containsMatchIn(body))
            assertFalse(drug.id, unsupportedValidation.containsMatchIn(EducationTokenizer.normalize(body)))
        }
        assertTrue(EducationTokenizer.normalize(catalog.single { it.id == "insulin_detemir" }.statusNote)
            .contains("historica"))
        assertTrue(EducationTokenizer.normalize(catalog.single { it.id == "rosiglitazona" }.statusNote)
            .contains("historica"))
    }

    @Test fun detemirDiscontinuedUsPresentationsHaveDatedFdaSourceAndLocalLimit() {
        val guide = catalog.single { it.id == "insulin_detemir" }
        val source = guide.sources.single { it.url == "https://www.fda.gov/media/186099/download?attachment=" }
        assertEquals("22 de noviembre de 2026", source.reviewedOn)
        assertEquals("Actualización 11/2026", source.version)
        assertTrue(source.document.contains("22–23"))
        val forms = guide.sections.single { it.intent == DrugIntent.FORMULATIONS }.body
        assertTrue(EducationTokenizer.normalize(forms).contains("estados unidos"))
        assertTrue(EducationTokenizer.normalize(forms).contains("no determina la disponibilidad en mexico"))
        assertTrue(EducationTokenizer.normalize(guide.statusNote).contains("historica"))
        assertTrue(EducationTokenizer.normalize(guide.availabilityNote).contains("no verificadas"))
    }

    @Test fun classificationUsesOneBundledModelWhileTheApplicationRequestsNoInternetPermission() = runBlocking {
        assertFalse(projectFile("app/src/main/AndroidManifest.xml").readText().contains("android.permission.INTERNET"))
        val model = projectFile("app/src/main/res/raw/bitavida_education_model.tsv").readText()
        assertTrue(model.startsWith("BITAVIDA_MNB"))
        var loads = 0
        val offline = BundledEducationRepository { loads++; model }
        listOf("qué efectos tiene un medicamento", "para qué sirve medir la presión").forEach { question ->
            val prediction = offline.classify(question)
            assertTrue(prediction.posterior.isFinite())
            assertTrue(prediction.vocabularyCoverage.isFinite())
        }
        assertEquals(1, loads)
    }

    @Test fun revisedMechanismsRemainSpecificAndAvailableThroughOfflineRepository() {
        val ask = AskBitavidaAi(repository)
        fun mechanism(id: String): String = EducationTokenizer.normalize(
            requireNotNull(ask.readDrug(id)).drug.sections.single { it.intent == DrugIntent.MECHANISM }.body,
        )
        val metformin = mechanism("metformina")
        val empagliflozin = mechanism("empagliflozina")
        val glargine = mechanism("insulin_glargine")
        assertTrue(metformin.contains("higado"))
        assertTrue(empagliflozin.contains("rinones") && empagliflozin.contains("orina"))
        assertTrue(glargine.contains("insulina") && glargine.contains("lentamente"))
        assertEquals(3, setOf(metformin, empagliflozin, glargine).size)
        assertTrue(repository.entry(EducationTopic.MEDICATION_MECHANISM).details.any {
            EducationTokenizer.normalize(it).contains("sglt2")
        })
    }

    @Test fun expandedDrugExplanationsStaySpecificAndRetainSafetyContextOffline() {
        fun section(id: String, intent: DrugIntent): String = EducationTokenizer.normalize(
            catalog.single { it.id == id }.sections.filter { it.intent == intent }.joinToString("\n") { it.body },
        )
        val metformin = section("metformina", DrugIntent.MECHANISM)
        assertTrue(metformin.contains("entre comidas"))
        assertTrue(metformin.contains("sulfonilureas"))
        assertTrue(section("metformina", DrugIntent.INTERACTIONS).contains("alcohol"))
        val empagliflozin = section("empagliflozina", DrugIntent.MECHANISM)
        assertTrue(empagliflozin.contains("sglt2") && empagliflozin.contains("orina"))
        assertTrue(section("empagliflozina", DrugIntent.SIDE_EFFECTS).contains("cetoacidosis"))
        val glargine = section("insulin_glargine", DrugIntent.MECHANISM)
        assertTrue(glargine.contains("debajo de la piel") && glargine.contains("lentamente"))
        assertTrue(section("insulin_glargine", DrugIntent.SIDE_EFFECTS).contains("hipoglucemia"))
        assertTrue(section("insulin_glargine", DrugIntent.FORMULATIONS).contains("dispositivo"))
        listOf("metformina", "empagliflozina", "insulin_glargine").forEach { id ->
            val drug = catalog.single { it.id == id }
            assertTrue(drug.statusNote.contains("pendiente de revisión clínica"))
            assertTrue(drug.sources.single().url.startsWith("https://dailymed.nlm.nih.gov/"))
        }
    }

    @Test fun sulfonylureaCardsExplainDifferentFormulationsAndHypoglycemiaOffline() {
        fun section(id: String, intent: DrugIntent) = EducationTokenizer.normalize(
            catalog.single { it.id == id }.sections.filter { it.intent == intent }.joinToString("\n") { it.body },
        )
        listOf("glibenclamida", "gliclazida").forEach { id ->
            assertTrue(id, section(id, DrugIntent.MECHANISM).contains("insulina"))
            assertTrue(id, section(id, DrugIntent.SIDE_EFFECTS).contains("hipoglucemia"))
            assertTrue(id, section(id, DrugIntent.PRECAUTIONS).contains("comidas"))
            assertTrue(id, section(id, DrugIntent.INTERACTIONS).contains("miconazol"))
        }
        assertTrue(section("gliclazida", DrugIntent.FORMULATIONS).contains("liberacion modificada"))
        assertTrue(section("glibenclamida", DrugIntent.FORMULATIONS).contains("glyburide"))
    }

    @Test fun additionalDrugCardsKeepDistinctMechanismsAndSpecificWarnings() {
        fun body(id: String, intent: DrugIntent) = EducationTokenizer.normalize(
            catalog.single { it.id == id }.sections.filter { it.intent == intent }.joinToString("\n") { it.body },
        )
        assertTrue(body("glimepirida", DrugIntent.MECHANISM).contains("pancreas"))
        assertTrue(body("glimepirida", DrugIntent.PRECAUTIONS).contains("g6pd"))
        assertTrue(body("dapagliflozina", DrugIntent.MECHANISM).contains("orina"))
        assertTrue(body("dapagliflozina", DrugIntent.SIDE_EFFECTS).contains("cetoacidosis"))
        assertTrue(body("canagliflozina", DrugIntent.SIDE_EFFECTS).contains("amputacion"))
        assertTrue(body("canagliflozina", DrugIntent.PRECAUTIONS).contains("ulceras"))
        assertTrue(body("linagliptina", DrugIntent.MECHANISM).contains("incretinas"))
        assertTrue(body("linagliptina", DrugIntent.SIDE_EFFECTS).contains("pancreatitis"))
        listOf("glimepirida", "dapagliflozina", "canagliflozina", "linagliptina").forEach { id ->
            assertTrue(catalog.single { it.id == id }.statusNote.contains("pendiente de revisión clínica"))
        }
    }

    @Test fun educationalBodiesNeverExposeEscapedNewlineSequences() {
        catalog.forEach { drug ->
            val texts = listOf(drug.summary) + drug.sections.map { it.body }
            texts.forEach { body ->
                assertFalse("El contenido de ${drug.id} contiene un salto de línea escapado visible",
                    body.contains("\\n"))
            }
        }
    }

    @Test fun insulinGuidesExplainTheirSpecificFormulationsAndSafety() {
        fun section(id: String, intent: DrugIntent) = EducationTokenizer.normalize(
            catalog.single { it.id == id }.sections.filter { it.intent == intent }.joinToString("\n") { it.body },
        )
        assertTrue(section("insulin_regular", DrugIntent.IDENTIFY).contains("accion intermedia"))
        assertTrue(section("insulin_nph", DrugIntent.IDENTIFY).contains("protamina"))
        assertTrue(section("insulin_nph", DrugIntent.FORMULATIONS).contains("suspension"))
        assertTrue(section("insulin_lispro", DrugIntent.FORMULATIONS).contains("u 200"))
        assertTrue(section("insulin_lispro", DrugIntent.FORMULATIONS).contains("bomba"))
        assertTrue(section("insulin_aspart", DrugIntent.PRECAUTIONS).contains("bomba"))
        listOf("insulin_regular", "insulin_nph", "insulin_lispro", "insulin_aspart").forEach { id ->
            assertTrue(id, section(id, DrugIntent.SIDE_EFFECTS).contains("hipoglucemia"))
            assertTrue(id, section(id, DrugIntent.CONSULTATION).contains("glucosa"))
            assertTrue(id, catalog.single { it.id == id }.statusNote.contains("pendiente de revisión clínica"))
        }
    }

    @Test fun premixedInsulinsExplainIngredientsAndDoNotConflateMatchingRatios() {
        fun section(id: String, intent: DrugIntent) = EducationTokenizer.normalize(
            catalog.single { it.id == id }.sections.filter { it.intent == intent }.joinToString(" ") { it.body },
        )
        assertTrue(section("insulin_glulisine", DrugIntent.MECHANISM).contains("higado"))
        assertTrue(section("insulin_human_mix_70_30", DrugIntent.IDENTIFY).contains("nph"))
        assertTrue(section("insulin_human_mix_70_30", DrugIntent.IDENTIFY).contains("aspart"))
        assertTrue(section("insulin_lispro_mix_75_25", DrugIntent.IDENTIFY).contains("protamina"))
        assertTrue(section("insulin_lispro_mix_75_25", DrugIntent.FORMULATIONS).contains("bomba"))
        assertTrue(section("insulin_aspart_mix_70_30", DrugIntent.IDENTIFY).contains("nph"))
        listOf("insulin_glulisine", "insulin_human_mix_70_30",
            "insulin_lispro_mix_75_25", "insulin_aspart_mix_70_30").forEach { id ->
            assertTrue(id, section(id, DrugIntent.SIDE_EFFECTS).contains("hipoglucemia"))
            assertTrue(id, catalog.single { it.id == id }.statusNote.contains("pendiente de revisión clínica"))
        }
    }

    @Test fun insulinCombinationsAndVildagliptinKeepTheirOwnWarnings() {
        fun section(id: String, intent: DrugIntent) = EducationTokenizer.normalize(
            catalog.single { it.id == id }.sections.filter { it.intent == intent }.joinToString(" ") { it.body },
        )
        assertTrue(section("insulin_detemir", DrugIntent.MECHANISM).contains("albumina"))
        assertTrue(section("insulin_glargine_lixisenatide", DrugIntent.MECHANISM).contains("lixisenatida"))
        assertTrue(section("insulin_glargine_lixisenatide", DrugIntent.PRECAUTIONS).contains("anestesia"))
        assertTrue(section("insulin_degludec_liraglutide", DrugIntent.CONTRAINDICATIONS).contains("medular"))
        assertFalse(section("insulin_glargine_lixisenatide", DrugIntent.CONTRAINDICATIONS).contains("medular"))
        assertTrue(section("vildagliptina", DrugIntent.PRECAUTIONS).contains("higado"))
        assertTrue(section("vildagliptina", DrugIntent.INTERACTIONS).contains("angioedema"))
        listOf("insulin_detemir", "insulin_glargine_lixisenatide",
            "insulin_degludec_liraglutide", "vildagliptina").forEach { id ->
            assertTrue(id, catalog.single { it.id == id }.statusNote.contains("Pendiente de revisión clínica", ignoreCase = true))
        }
    }

    @Test fun oralMedicinesExplainDistinctMechanismsAndProductSpecificPrecautions() {
        fun section(id: String, intent: DrugIntent) = EducationTokenizer.normalize(
            catalog.single { it.id == id }.sections.filter { it.intent == intent }.joinToString(" ") { it.body },
        )
        assertTrue(section("tolbutamida", DrugIntent.MECHANISM).contains("pancreas"))
        assertTrue(section("tolbutamida", DrugIntent.SIDE_EFFECTS).contains("hipoglucemia"))
        assertTrue(section("semaglutida_oral", DrugIntent.MECHANISM).contains("glucagon"))
        assertTrue(section("semaglutida_oral", DrugIntent.CONTRAINDICATIONS).contains("medular"))
        assertTrue(section("pioglitazona", DrugIntent.MECHANISM).contains("ppar"))
        assertTrue(section("pioglitazona", DrugIntent.SIDE_EFFECTS).contains("insuficiencia cardiaca"))
        assertTrue(section("acarbosa", DrugIntent.MECHANISM).contains("intestino"))
        assertTrue(section("acarbosa", DrugIntent.INTERACTIONS).contains("sacarosa"))
        listOf("tolbutamida", "semaglutida_oral", "pioglitazona", "acarbosa").forEach { id ->
            assertTrue(id, catalog.single { it.id == id }.statusNote.contains("pendiente de revisión clínica", ignoreCase = true))
        }
    }

    private fun projectFile(path: String): File =
        File(path).takeIf { it.isFile } ?: File("..", path)
}
